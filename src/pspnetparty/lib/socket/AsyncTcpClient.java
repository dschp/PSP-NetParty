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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import pspnetparty.lib.ILogger;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;

public class AsyncTcpClient implements IClient {

	private final int initialReadBufferSize;
	private final int maxPacketSize;
	private ILogger logger;

	private Selector selector;
	private ConcurrentLinkedQueue<Connection> newConnectionQueue = new ConcurrentLinkedQueue<Connection>();
	private ConcurrentHashMap<Connection, Object> establishedConnections;
	private final Object valueObject = new Object();

	public AsyncTcpClient(ILogger logger, int maxPacketSize, final int selectTimeout) {
		this.logger = logger;
		this.maxPacketSize = maxPacketSize;
		this.initialReadBufferSize = Math.min(maxPacketSize, 2000);

		establishedConnections = new ConcurrentHashMap<AsyncTcpClient.Connection, Object>(16, 0.75f, 3);
		try {
			selector = Selector.open();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		Thread selectorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				selectorLoop(selectTimeout);
			}
		}, getClass().getName() + " Selector");
		selectorThread.setDaemon(true);
		selectorThread.start();

		Thread pingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					pingLoop();
				} catch (InterruptedException e) {
					AsyncTcpClient.this.logger.log(Utility.stackTraceToString(e));
				}
			}
		}, getClass().getName() + " Ping");
		pingThread.setDaemon(true);
		pingThread.start();
	}

	private void selectorLoop(int timeout) {
		try {
			while (selector.isOpen()) {
				int s = selector.select(timeout);
				// System.out.println("Select: " + s);
				if (s > 0) {
					for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
						SelectionKey key = it.next();
						it.remove();
						try {
							if (!key.isReadable())
								continue;
						} catch (CancelledKeyException e) {
						}

						Connection connection = (Connection) key.attachment();
						boolean success = false;
						try {
							success = connection.doRead();
						} catch (IOException e) {
						} catch (RuntimeException e) {
						}

						if (!success) {
							connection.disconnect();
							key.cancel();
						}
					}
				}

				Connection conn;
				while ((conn = newConnectionQueue.poll()) != null)
					conn.channel.register(selector, SelectionKey.OP_READ, conn);
			}
		} catch (ClosedSelectorException e) {
		} catch (IOException e) {
		}
	}

	private void pingLoop() throws InterruptedException {
		ByteBuffer pingBuffer = ByteBuffer.allocate(IProtocol.HEADER_BYTE_SIZE);
		pingBuffer.putInt(0);
		while (selector.isOpen()) {
			long deadline = System.currentTimeMillis() - IProtocol.PING_DEADLINE;
			// int pingCount = 0, disconnectCount = 0;

			for (Connection conn : establishedConnections.keySet()) {
				try {
					if (conn.lastPingTime < deadline) {
						logger.log(Utility.makePingDisconnectLog("TCP", conn.remoteAddress, deadline, conn.lastPingTime));
						conn.disconnect();
						// disconnectCount++;
					} else {
						pingBuffer.clear();
						conn.channel.write(pingBuffer);
						// pingCount++;
					}
				} catch (RuntimeException e) {
					logger.log(Utility.stackTraceToString(e));
				} catch (Exception e) {
					logger.log(Utility.stackTraceToString(e));
				}
			}

			// logger.log("TCP Client Ping送信: p=" + pingCount + " d=" +
			// disconnectCount);
			Thread.sleep(IProtocol.PING_INTERVAL);
		}
	}

	@Override
	public void connect(InetSocketAddress address, int timeout, IProtocol protocol) throws IOException {
		if (address == null || protocol == null)
			throw new IllegalArgumentException();

		try {
			SocketChannel channel = SocketChannel.open();
			channel.socket().connect(address, timeout);
			channel.configureBlocking(false);

			Connection conn = new Connection(channel);

			conn.send(protocol.getProtocol() + IProtocol.SEPARATOR + IProtocol.NUMBER);

			conn.driver = protocol.createDriver(conn);
			if (conn.driver == null) {
				channel.close();
				return;
			}

			establishedConnections.put(conn, valueObject);

			newConnectionQueue.add(conn);
			selector.wakeup();
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

	private static void send(SocketChannel channel, ByteBuffer buffer) {
		try {
			ByteBuffer headerData = ByteBuffer.allocate(IProtocol.HEADER_BYTE_SIZE);
			headerData.putInt(buffer.limit());
			headerData.flip();

			ByteBuffer[] array = new ByteBuffer[] { headerData, buffer };
			channel.write(array);
		} catch (IOException e) {
		}
	}

	private class Connection implements ISocketConnection {
		private SocketChannel channel;
		private InetSocketAddress remoteAddress;
		private long lastPingTime;

		private IProtocolDriver driver;

		private ByteBuffer headerReadBuffer = ByteBuffer.allocate(IProtocol.HEADER_BYTE_SIZE);
		private ByteBuffer dataReadBuffer = ByteBuffer.allocateDirect(initialReadBufferSize);

		private PacketData packetData = new PacketData(dataReadBuffer);
		private boolean protocolMatched = false;

		public Connection(SocketChannel channel) {
			this.channel = channel;
			this.remoteAddress = (InetSocketAddress) channel.socket().getRemoteSocketAddress();
			lastPingTime = System.currentTimeMillis();
		}

		private boolean doRead() throws IOException {
			if (headerReadBuffer.remaining() != 0) {
				if (channel.read(headerReadBuffer) < 0)
					return false;
				if (headerReadBuffer.remaining() != 0)
					return true;

				int dataSize = headerReadBuffer.getInt(0);
				if (dataSize == 0) {
					lastPingTime = System.currentTimeMillis();
					headerReadBuffer.position(0);
					// logger.log(Utility.makePingLog("TCP Client",
					// getLocalAddress(), remoteAddress, lastPingTime));
					return true;
				}
				if (dataSize < 1 || dataSize > maxPacketSize) {
					/* Invalid data size */
					// readHeaderBuffer.position(0);
					// System.out.println(Utility.decode(readHeaderBuffer));
					return false;
				}

				if (dataSize > dataReadBuffer.capacity()) {
					dataReadBuffer = ByteBuffer.allocateDirect(dataSize);
					packetData.replaceBuffer(dataReadBuffer);
				} else {
					dataReadBuffer.limit(dataSize);
				}
			}

			if (channel.read(dataReadBuffer) < 0)
				return false;

			if (dataReadBuffer.remaining() != 0)
				return true;

			dataReadBuffer.position(0);
			if (protocolMatched) {
				driver.process(packetData);
			} else {
				String message = packetData.getMessage();
				if (IProtocol.PROTOCOL_OK.equals(message)) {
					protocolMatched = true;
				} else if (IProtocol.PROTOCOL_NG.equals(message)) {
					return false;
				} else {
					driver.errorProtocolNumber(message);
					return false;
				}
			}

			headerReadBuffer.position(0);
			dataReadBuffer.clear();
			return true;
		}

		@Override
		protected void finalize() throws Throwable {
			super.finalize();
			disconnect();
		}

		@Override
		public boolean isConnected() {
			return channel != null && channel.isConnected();
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
				if (channel.isOpen())
					channel.close();
			} catch (IOException e) {
			}
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
		public void send(String data) {
			if (!isConnected())
				return;

			ByteBuffer buffer = AppConstants.CHARSET.encode(data);
			AsyncTcpClient.send(channel, buffer);
		}

		@Override
		public void send(byte[] data) {
			if (!isConnected())
				return;

			ByteBuffer buffer = ByteBuffer.wrap(data);
			AsyncTcpClient.send(channel, buffer);
		}

		@Override
		public void send(ByteBuffer buffer) {
			if (!isConnected())
				return;

			AsyncTcpClient.send(channel, buffer);
		}
	}

	public static void main(String[] args) throws Exception {
		final AsyncTcpClient client = new AsyncTcpClient(new ILogger() {
			@Override
			public void log(String message) {
			}
		}, 100000, 0);
		InetSocketAddress address = new InetSocketAddress("localhost", 30000);

		client.connect(address, 1000, new IProtocol() {
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
					private String makeLongString(char c, int length) {
						StringBuilder sb = new StringBuilder();
						for (int i = 0; i < length; i++) {
							sb.append(c);
						}
						return sb.toString();
					}

					@Override
					public void run() {
						try {
							Thread.sleep(500);

							if (connection.isConnected()) {
								String text = "S" + makeLongString('T', 39998) + "E";
								System.out.println("length: " + text.length());
								for (int i = 0; i < 3; i++) {
									if (!connection.isConnected())
										break;

									connection.send(text);
									Thread.sleep(1000);
								}
								Thread.sleep(100);
							}
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
						return connection;
					}

					@Override
					public boolean process(PacketData data) {
						String msg = data.getMessage();
						System.out.println("受信(" + msg.length() + ")");
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
