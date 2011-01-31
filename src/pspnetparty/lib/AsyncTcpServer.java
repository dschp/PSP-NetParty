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
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.ProtocolConstants;

public class AsyncTcpServer<Type extends IClientState> implements IServer<Type> {

	private static final int INITIAL_READ_BUFFER_SIZE = 2000;
	private static final int MAX_PACKET_SIZE = 40000;

	private Selector selector;
	private IAsyncServerHandler<Type> handler;

	private ServerSocketChannel serverChannel;
	private ByteBuffer headerBuffer = ByteBuffer.allocateDirect(Integer.SIZE / 8);

	private ConcurrentHashMap<Connection, Object> establishedConnections;
	private final Object valueObject = new Object();

	public AsyncTcpServer() {
		establishedConnections = new ConcurrentHashMap<AsyncTcpServer<Type>.Connection, Object>(30, 0.75f, 2);
	}

	@Override
	public boolean isListening() {
		return selector != null && selector.isOpen();
	}

	@Override
	public void startListening(InetSocketAddress bindAddress, final IAsyncServerHandler<Type> handler) throws IOException {
		if (isListening())
			stopListening();
		this.handler = handler;

		selector = Selector.open();
		serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.socket().bind(bindAddress);
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);

		ServerSocket socket = serverChannel.socket();
		handler.log("TCP: Listening on " + socket.getLocalSocketAddress());

		Thread asyncLoopThread = new Thread(new Runnable() {
			@Override
			public void run() {
				AsyncTcpServer.this.handler.serverStartupFinished();
				try {
					while (serverChannel.isOpen())
						while (selector.select() > 0) {
							for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
								SelectionKey key = it.next();
								it.remove();

								try {
									if (key.isAcceptable()) {
										ServerSocketChannel channel = (ServerSocketChannel) key.channel();
										try {
											doAccept(channel);
										} catch (IOException e) {
											key.cancel();
										}
									} else if (key.isReadable()) {
										@SuppressWarnings("unchecked")
										Connection conn = (Connection) key.attachment();
										try {
											doRead(conn);
										} catch (IOException e) {
											// Disconnected
											conn.disconnect();
											key.cancel();
										}
									}
								} catch (CancelledKeyException e) {
								}
							}
						}
				} catch (IOException e) {
				} catch (ClosedSelectorException e) {
				} catch (RuntimeException e) {
					AsyncTcpServer.this.handler.log(Utility.makeStackTrace(e));
				}

				AsyncTcpServer.this.handler.log("TCP: Now shuting down...");
				AsyncTcpServer.this.handler.serverShutdownFinished();
			}
		}, AsyncTcpServer.class.getName());
		asyncLoopThread.start();
	}

	@Override
	public void stopListening() {
		if (!isListening())
			return;

		try {
			selector.close();
		} catch (IOException e) {
		}
		selector = null;

		if (serverChannel != null && serverChannel.isOpen()) {
			try {
				serverChannel.close();
			} catch (IOException e) {
			}
		}
		for (Connection conn : establishedConnections.keySet()) {
			conn.disconnect();
		}
	}

	private class Connection implements ISocketConnection {
		private SocketChannel channel;
		private Type state;

		private ByteBuffer readBuffer = ByteBuffer.allocate(INITIAL_READ_BUFFER_SIZE);
		private PacketData packetData = new PacketData(readBuffer);

		Connection(SocketChannel channel) {
			this.channel = channel;
		}

		@Override
		public InetSocketAddress getRemoteAddress() {
			return (InetSocketAddress) channel.socket().getRemoteSocketAddress();
		}
		
		@Override
		public InetSocketAddress getLocalAddress() {
			return (InetSocketAddress) channel.socket().getLocalSocketAddress();
		}

		@Override
		public boolean isConnected() {
			return channel.isConnected();
		}

		@Override
		public void send(ByteBuffer buffer) {
			if (!channel.isConnected())
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

		@Override
		public void send(String message) {
			ByteBuffer buffer = AppConstants.CHARSET.encode(message);
			send(buffer);
		}

		@Override
		public void send(byte[] data) {
			ByteBuffer buffer = ByteBuffer.wrap(data);
			send(buffer);
		}

		@Override
		public void disconnect() {
			if (establishedConnections.remove(this) == null)
				return;
			if (state == null)
				return;

			handler.disposeState(state);
			state = null;

			try {
				if (channel.isOpen())
					channel.close();
			} catch (IOException e) {
			}
		}
	}

	private void doAccept(ServerSocketChannel serverChannel) throws IOException {
		SocketChannel channel = serverChannel.accept();

		Connection conn = new Connection(channel);
		conn.state = handler.createState(conn);

		channel.configureBlocking(false);
		channel.register(selector, SelectionKey.OP_READ, conn);

		establishedConnections.put(conn, valueObject);
	}

	private void doRead(Connection conn) throws IOException {
		if (conn.readBuffer.position() == 0) {
			headerBuffer.clear();
			if (conn.channel.read(headerBuffer) < 0) {
				throw new IOException("Client has disconnected.");
			}
			headerBuffer.flip();

			int dataSize = headerBuffer.getInt();
			// System.out.println("Data size=" + dataSize);
			if (dataSize < 1 || dataSize > MAX_PACKET_SIZE) {
				headerBuffer.position(0);
				System.out.println(Utility.decode(headerBuffer));
				throw new IOException("Too big data size: " + dataSize);
			}

			if (dataSize > conn.readBuffer.capacity()) {
				conn.readBuffer = ByteBuffer.allocate(dataSize);
				conn.packetData.replaceBuffer(conn.readBuffer);
			} else {
				conn.readBuffer.limit(dataSize);
			}
		}

		int readBytes = conn.channel.read(conn.readBuffer);
		if (readBytes < 0) {
			throw new IOException("Client has disconnected.");
		}

		if (conn.readBuffer.remaining() == 0) {
			conn.readBuffer.position(0);
			if (handler.processIncomingData(conn.state, conn.packetData)) {
				conn.readBuffer.clear();
			} else {
				throw new IOException("Session is invalidated.");
			}
		}
	}

	public static void main(String[] args) throws IOException {
		InetSocketAddress address = new InetSocketAddress(30000);
		AsyncTcpServer<IClientState> server = new AsyncTcpServer<IClientState>();
		server.startListening(address, new IAsyncServerHandler<IClientState>() {
			@Override
			public boolean processIncomingData(IClientState state, PacketData data) {
				String remoteAddress = state.getConnection().getRemoteAddress().toString();
				String message = data.getMessage();

				System.out.println(remoteAddress + "(" + message.length() + ")");
				state.getConnection().send(message);

				return true;
			}

			@Override
			public void disposeState(IClientState state) {
				System.out.println(state.getConnection().getRemoteAddress() + "[切断されました]");
			}

			@Override
			public IClientState createState(final ISocketConnection connection) {
				System.out.println(connection.getRemoteAddress() + "[接続されました]");

				return new IClientState() {
					@Override
					public ISocketConnection getConnection() {
						return connection;
					}
				};
			}

			@Override
			public void serverStartupFinished() {
			}

			@Override
			public void serverShutdownFinished() {
			}

			@Override
			public void log(String message) {
				System.out.println(message);
			}
		});

		while (System.in.read() != '\n') {
		}

		server.stopListening();

		while (System.in.read() != '\n') {
		}
	}
}
