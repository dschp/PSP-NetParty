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
package pspnetparty.lib;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.ProtocolConstants;

public class AsyncTcpClient {

	private final int initialReadBufferSize;
	private final int maxPacketSize;

	private Selector selector;
	private ConcurrentLinkedQueue<Connection> newConnectionQueue = new ConcurrentLinkedQueue<Connection>();
	private ConcurrentHashMap<Connection, Object> establishedConnections;
	private final Object valueObject = new Object();

	private ByteBuffer readHeaderBuffer = ByteBuffer.allocateDirect(ProtocolConstants.INTEGER_BYTE_SIZE);

	private Thread selectorThread;

	public AsyncTcpClient(int maxPacketSize, final int selectTimeout) {
		this.maxPacketSize = maxPacketSize;
		this.initialReadBufferSize = Math.min(maxPacketSize, 2000);
		
		establishedConnections = new ConcurrentHashMap<AsyncTcpClient.Connection, Object>(16, 0.75f, 2);
		try {
			selector = Selector.open();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		selectorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (selector.isOpen()) {
						int s = selector.select(selectTimeout);
						// System.out.println("Select: " + s);
						if (s > 0) {
							for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
								SelectionKey key = it.next();
								it.remove();
								Connection connection = (Connection) key.attachment();
								boolean success = false;
								try {
									if (key.isConnectable()) {
										connection.connectReady();
									} else if (key.isReadable()) {
										connection.readReady();
									}
									success = true;
								} catch (CancelledKeyException e) {
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
							conn.prepareConnect();
					}
				} catch (ClosedSelectorException e) {
				} catch (IOException e) {
				}
			}
		}, AsyncTcpClient.class.getName());
		selectorThread.setDaemon(true);
		selectorThread.start();
	}

	public ISocketConnection connect(InetSocketAddress address, IAsyncClientHandler handler) {
		if (address == null || handler == null)
			throw new IllegalArgumentException();

		Connection conn = new Connection(address, handler);
		newConnectionQueue.offer(conn);
		selector.wakeup();

		return conn;
	}

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
		private SocketChannel channel;

		private InetSocketAddress remoteAddress;
		private IAsyncClientHandler handler;

		private ByteBuffer readDataBuffer = ByteBuffer.allocateDirect(initialReadBufferSize);
		private PacketData packetData = new PacketData(readDataBuffer);

		public Connection(InetSocketAddress address, IAsyncClientHandler handler) {
			this.remoteAddress = address;
			this.handler = handler;
		}

		private void prepareConnect() {
			try {
				channel = SocketChannel.open();
				channel.configureBlocking(false);

				channel.register(selector, SelectionKey.OP_CONNECT, this);
				channel.connect(remoteAddress);

				establishedConnections.put(this, valueObject);
				return;
			} catch (UnresolvedAddressException e) {
			} catch (RuntimeException e) {
				handler.log(this, Utility.makeStackTrace(e));
			} catch (IOException e) {
				handler.log(this, Utility.makeStackTrace(e));
			}
			handler.disconnectCallback(this);
		}

		private void connectReady() throws IOException {
			channel.finishConnect();
			channel.register(selector, SelectionKey.OP_READ, this);

			handler.connectCallback(this);
		}

		private void readReady() throws IOException {
			if (readDataBuffer.position() == 0) {
				readHeaderBuffer.clear();
				if (channel.read(readHeaderBuffer) < 0) {
					disconnect();
					return;
				}
				readHeaderBuffer.flip();

				int dataSize = readHeaderBuffer.getInt();
				if (dataSize < 1 || dataSize > maxPacketSize) {
					readHeaderBuffer.position(0);
					System.out.println(Utility.decode(readHeaderBuffer));
					throw new IOException("Too big data size: " + dataSize);
				}

				if (dataSize > readDataBuffer.capacity()) {
					readDataBuffer = ByteBuffer.allocateDirect(dataSize);
					packetData.replaceBuffer(readDataBuffer);
				} else {
					readDataBuffer.limit(dataSize);
				}
			}

			if (channel.read(readDataBuffer) < 0) {
				disconnect();
				return;
			}

			if (readDataBuffer.remaining() == 0) {
				readDataBuffer.position(0);
				handler.readCallback(this, packetData);
				readDataBuffer.clear();
			}
		}

		@Override
		protected void finalize() throws Throwable {
			super.finalize();
			disconnect();
		}

		@Override
		public boolean isConnected() {
			return channel != null && channel.isOpen();
		}

		@Override
		public void disconnect() {
			if (establishedConnections.remove(this) == null)
				return;
			try {
				handler.disconnectCallback(this);
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
			ByteBuffer buffer = AppConstants.CHARSET.encode(data);
			send(buffer);
		}

		@Override
		public void send(byte[] data) {
			ByteBuffer buffer = ByteBuffer.wrap(data);
			send(buffer);
		}

		@Override
		public void send(ByteBuffer buffer) {
			if (!isConnected())
				return;

			try {
				ByteBuffer headerData = ByteBuffer.allocate(ProtocolConstants.INTEGER_BYTE_SIZE);
				headerData.putInt(buffer.limit());
				headerData.flip();

				ByteBuffer[] array = new ByteBuffer[] { headerData, buffer };
				channel.write(array);
			} catch (IOException e) {
			}
		}
	}

	public static void main(String[] args) throws Exception {
		final AsyncTcpClient client = new AsyncTcpClient(100000, 0);
		final InetSocketAddress address = new InetSocketAddress("localhost", 30000);
		final ISocketConnection conn = client.connect(address, new IAsyncClientHandler() {
			@Override
			public void log(ISocketConnection connection, String message) {
				System.out.println(message);
			}

			@Override
			public void connectCallback(ISocketConnection conn) {
				System.out.println("接続しました: " + conn.getRemoteAddress());
			}

			@Override
			public void readCallback(ISocketConnection connection, PacketData data) {
				for (String msg : data.getMessages()) {
					System.out.println("受信(" + msg.length() + ")");
				}
			}

			@Override
			public void disconnectCallback(ISocketConnection connection) {
				System.out.println("切断しました");
			}
		});

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
				String text = "S" + makeLongString('T', 39998) + "E";
				System.out.println("length: " + text.length());
				try {
					Thread.sleep(500);
					for (int i = 0; i < 3; i++) {
						conn.send(text);
						Thread.sleep(1000);
					}
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}

				conn.disconnect();
				client.dispose();
			}
		});
		sendThread.start();
	}
}
