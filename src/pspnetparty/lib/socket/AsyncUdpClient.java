/*
Copyright (C) 2011 monte

This file is part of PSP NetParty.

PSP NetParty is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package pspnetparty.lib.socket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import pspnetparty.lib.ILogger;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;

public class AsyncUdpClient implements IClient {

	private static final int BUFFER_SIZE = 20000;

	private ILogger logger;

	private Selector selector;
	private ConcurrentLinkedQueue<Connection> newConnectionQueue = new ConcurrentLinkedQueue<Connection>();
	private ConcurrentHashMap<Connection, Object> establishedConnections;

	public AsyncUdpClient(ILogger logger) {
		this.logger = logger;
		establishedConnections = new ConcurrentHashMap<AsyncUdpClient.Connection, Object>(16, 0.75f, 2);
		try {
			selector = Selector.open();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		Thread selectorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				selectorLoop();
			}
		}, getClass().getName() + " Selector");
		selectorThread.setDaemon(true);
		selectorThread.start();

		Thread keepAliveThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					keepAliveLoop();
				} catch (InterruptedException e) {
					AsyncUdpClient.this.logger.log(Utility.stackTraceToString(e));
				}
			}
		}, getClass().getName() + " KeepAlive");
		keepAliveThread.setDaemon(true);
		keepAliveThread.start();
	}

	private void selectorLoop() {
		try {
			while (selector.isOpen()) {
				if (selector.select() > 0) {
					for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
						SelectionKey key = it.next();
						it.remove();

						Connection conn = null;
						try {
							conn = (Connection) key.attachment();
							if (key.isReadable()) {
								if (conn.doRead()) {
								} else if (conn.sendBufferQueue.isEmpty() || conn.serverDisconnected) {
									conn.disconnect();
									key.cancel();
								} else {
									conn.toBeClosed = true;
								}
							} else if (key.isWritable()) {
								SendBufferQueue<Connection>.Allotment allot = conn.sendBufferQueue.poll();
								if (allot == null) {
									if (conn.toBeClosed) {
										conn.disconnect();
										key.cancel();
									} else {
										key.interestOps(SelectionKey.OP_READ);
									}
								} else if (!conn.serverDisconnected) {
									conn.channel.write(allot.getBuffer());
								}
							}
						} catch (CancelledKeyException e) {
						} catch (IOException e) {
							if (conn != null)
								conn.disconnect();
							key.cancel();
						} catch (RuntimeException e) {
							if (conn != null)
								conn.disconnect();
							key.cancel();
						}
					}
				}

				Connection conn;
				while ((conn = newConnectionQueue.poll()) != null)
					conn.selectionKey = conn.channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, conn);
			}
		} catch (ClosedSelectorException e) {
		} catch (IOException e) {
		}
	}

	private void keepAliveLoop() throws InterruptedException {
		ByteBuffer keepAliveBuffer = ByteBuffer.wrap(new byte[] { 1 });
		while (selector.isOpen()) {
			long deadline = System.currentTimeMillis() - IProtocol.KEEPALIVE_DEADLINE;

			for (Connection conn : establishedConnections.keySet()) {
				try {
					if (conn.lastKeepAliveReceived < deadline) {
						logger.log(Utility.makeKeepAliveDisconnectLog("UDP", conn.remoteAddress, deadline, conn.lastKeepAliveReceived));
						conn.disconnect();
					} else {
						keepAliveBuffer.clear();
						conn.send(keepAliveBuffer);
					}
				} catch (RuntimeException e) {
					logger.log(Utility.stackTraceToString(e));
				} catch (Exception e) {
					logger.log(Utility.stackTraceToString(e));
				}
			}

			Thread.sleep(IProtocol.KEEPALIVE_INTERVAL);
		}
	}

	@Override
	public void connect(InetSocketAddress address, int timeout, IProtocol protocol) throws IOException {
		if (address == null || protocol == null)
			throw new IllegalArgumentException();

		try {
			DatagramChannel channel = DatagramChannel.open();
			channel.connect(address);

			Connection conn = new Connection(channel);

			ByteBuffer data = AppConstants.CHARSET.encode(protocol.getProtocol() + IProtocol.SEPARATOR + IProtocol.NUMBER);
			channel.write(data);

			channel.socket().setSoTimeout(timeout);

			byte[] buffer = new byte[5];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			channel.socket().receive(packet);

			conn.driver = protocol.createDriver(conn);
			if (conn.driver == null) {
				channel.close();
				return;
			}

			String message = Utility.decode(ByteBuffer.wrap(buffer, 0, packet.getLength()));
			if (IProtocol.PROTOCOL_OK.equals(message)) {
				establishedConnections.put(conn, conn);

				channel.configureBlocking(false);

				newConnectionQueue.offer(conn);
				selector.wakeup();
			} else if (IProtocol.PROTOCOL_NG.equals(message)) {
				channel.close();
				conn.driver.connectionDisconnected();
			} else {
				channel.close();

				conn.driver.errorProtocolNumber(message);
				conn.driver.connectionDisconnected();
			}
		} catch (RuntimeException e) {
			protocol.log(Utility.stackTraceToString(e));
			throw new IOException(e);
		}
	}

	@Override
	public void dispose() {
		if (!selector.isOpen())
			return;
		try {
			selector.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		for (Connection conn : establishedConnections.keySet()) {
			conn.disconnect();
		}
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		dispose();
	}

	private class Connection implements ISocketConnection {
		private DatagramChannel channel;
		private SelectionKey selectionKey;
		private InetSocketAddress remoteAddress;
		private IProtocolDriver driver;

		private long lastKeepAliveReceived;
		private boolean toBeClosed = false;
		private boolean serverDisconnected = false;

		private ByteBuffer readBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
		private PacketData packetData = new PacketData(readBuffer);

		private SendBufferQueue<Connection> sendBufferQueue = new SendBufferQueue<Connection>(20000);

		public Connection(DatagramChannel channel) {
			this.channel = channel;
			this.remoteAddress = (InetSocketAddress) channel.socket().getRemoteSocketAddress();
			lastKeepAliveReceived = System.currentTimeMillis();
		}

		private boolean doRead() throws IOException {
			readBuffer.clear();
			channel.read(readBuffer);
			if (toBeClosed || serverDisconnected)
				return true;
			readBuffer.flip();

			if (readBuffer.limit() == 1) {
				switch (readBuffer.get(0)) {
				case 0:
					serverDisconnected = true;
					return false;
				case 1:
					lastKeepAliveReceived = System.currentTimeMillis();
					return true;
				}
			}
			return driver.process(packetData);
		}

		@Override
		public void disconnect() {
			if (establishedConnections.remove(this) == null)
				return;
			try {
				if (driver != null) {
					driver.connectionDisconnected();
					driver = null;
				}
			} catch (RuntimeException re) {
			}
			try {
				if (channel.isOpen()) {
					ByteBuffer terminateBuffer = ByteBuffer.wrap(new byte[] { 0 });
					channel.send(terminateBuffer, remoteAddress);
					channel.close();
				}
			} catch (IOException e) {
			}
		}

		@Override
		public boolean isConnected() {
			return channel != null && channel.isConnected();
		}

		@Override
		public InetSocketAddress getRemoteAddress() {
			return remoteAddress;
		}

		@Override
		public InetSocketAddress getLocalAddress() {
			return (InetSocketAddress) channel.socket().getLocalSocketAddress();
		}

		@Override
		public void send(ByteBuffer buffer) {
			if (!isConnected())
				return;
			addToSendQueue(buffer);
		}

		private void addToSendQueue(ByteBuffer buffer) {
			sendBufferQueue.queue(buffer, false, this);

			try {
				if (selectionKey != null) {
					selector.wakeup();
					selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
				}
			} catch (CancelledKeyException e) {
			}
		}
	}

	public static void main(String[] args) throws IOException {
		final AsyncUdpClient client = new AsyncUdpClient(new ILogger() {
			@Override
			public void log(String message) {
			}
		});
		InetSocketAddress address = new InetSocketAddress("localhost", 30000);

		client.connect(address, 5000, new IProtocol() {
			@Override
			public void log(String message) {
				System.out.println(message);
			}

			@Override
			public String getProtocol() {
				return "TEST";
			}

			@Override
			public IProtocolDriver createDriver(final ISocketConnection connection) {
				System.out.println("接続しました: " + connection.getRemoteAddress());

				Thread sendThread = new Thread(new Runnable() {
					@Override
					public void run() {
						for (int i = 0; i < 3; i++)
							try {
								Thread.sleep(500);
								connection.send(Utility.encode("TEST " + i));
								Thread.sleep(500);
							} catch (InterruptedException e) {
							}

						connection.disconnect();
						client.dispose();
					}
				});
				sendThread.start();

				return new IProtocolDriver() {
					@Override
					public ISocketConnection getConnection() {
						return null;
					}

					@Override
					public boolean process(PacketData data) {
						String msg = data.getMessage();
						System.out.println("受信(" + msg.length() + ")： " + msg);
						return true;
					}

					@Override
					public void connectionDisconnected() {
						System.out.println("切断しました");
					}

					@Override
					public void errorProtocolNumber(String number) {
						System.out.println("プロトコルエラー: " + number);
					}
				};
			}
		});
	}
}
