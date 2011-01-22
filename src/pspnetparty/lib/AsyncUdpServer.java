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
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

public class AsyncUdpServer<Type extends IClientState> implements IServer<Type> {

	private static final int READ_BUFFER_SIZE = 20000;

	private Selector selector;
	private IServerHandler<Type> handler;

	private DatagramChannel serverChannel;

	private ByteBuffer readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
	private PacketData data = new PacketData(readBuffer);

	private HashMap<InetSocketAddress, Connection> establishedConnections = new HashMap<InetSocketAddress, Connection>();

	public AsyncUdpServer() {
	}

	private class Connection implements IServerConnection {
		private DatagramChannel channel;
		private InetSocketAddress remoteAddress;

		public Type state;
		public long lastUsedTime;

		public Connection(DatagramChannel channel, InetSocketAddress remoteAddress) {
			this.channel = channel;
			this.remoteAddress = remoteAddress;
		}

		@Override
		public InetSocketAddress getRemoteAddress() {
			return remoteAddress;
		}

		@Override
		public void send(String message) {
			ByteBuffer buffer = Constants.CHARSET.encode(message);
			send(buffer);
		}

		@Override
		public void send(ByteBuffer buffer) {
			try {
				channel.send(buffer, remoteAddress);
			} catch (IOException e) {
			}
		}

		@Override
		public void disconnect() {
			if (state != null) {
				handler.disposeState(state);
				state = null;
			}
			if (channel != null) {
				try {
					channel.close();
					channel = null;
				} catch (IOException e) {
				}
			}

			lastUsedTime = 0;
		}
	}

	@Override
	public void startListening(InetSocketAddress bindAddress, IServerHandler<Type> handler) throws IOException {
		stopListening();
		this.handler = handler;

		selector = Selector.open();

		serverChannel = DatagramChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.socket().bind(bindAddress);
		serverChannel.register(selector, SelectionKey.OP_READ);

		handler.log("UDP: Listening on " + bindAddress);

		Thread asyncLoopThread = new Thread(new Runnable() {
			@Override
			public void run() {
				AsyncUdpServer.this.handler.serverStartupFinished();
				try {
					while (serverChannel.isOpen())
						while (selector.select(2000) > 0) {
							for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
								SelectionKey key = it.next();
								it.remove();

								if (key.isReadable()) {
									DatagramChannel channel = (DatagramChannel) key.channel();
									try {
										doRead(channel);
									} catch (Exception e) {
										key.cancel();
										// e.printStackTrace();
									}
								}
							}
						}
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				AsyncUdpServer.this.handler.log("UDP: Now shuting down...");
				AsyncUdpServer.this.handler.serverShutdownFinished();
			}
		});
		asyncLoopThread.setName(AsyncUdpServer.class.getName());
		asyncLoopThread.start();

		Thread sessionCleanupThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (serverChannel.isOpen()) {
						long deadline = System.currentTimeMillis() - 60000;
						Iterator<Entry<InetSocketAddress, Connection>> iter = establishedConnections.entrySet().iterator();
						while (iter.hasNext()) {
							Entry<InetSocketAddress, Connection> entry = iter.next();
							Connection conn = entry.getValue();
							if (conn.lastUsedTime < deadline) {
								iter.remove();
								conn.disconnect();
							}
						}
						Thread.sleep(20000);
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		});
		sessionCleanupThread.setName(AsyncUdpServer.class.getName() + " SessionCleanup");
		sessionCleanupThread.setDaemon(true);
		// sessionCleanupThread.start();
	}

	@Override
	public void stopListening() {
		if (serverChannel != null && serverChannel.isOpen()) {
			try {
				serverChannel.close();
			} catch (IOException e) {
			}
		}
	}

	private void doRead(DatagramChannel channel) throws IOException {
		Connection conn = null;
		try {
			readBuffer.clear();
			InetSocketAddress remoteAddress = (InetSocketAddress) channel.receive(readBuffer);
			if (remoteAddress == null) {
				throw new IOException("Client has disconnected.");
			}
			readBuffer.flip();

			conn = establishedConnections.get(remoteAddress);
			if (conn == null) {
				conn = new Connection(channel, remoteAddress);

				conn.state = handler.createState(conn);

				establishedConnections.put(remoteAddress, conn);
			}
			conn.lastUsedTime = System.currentTimeMillis();

			boolean sessionContinue = handler.processIncomingData(conn.state, data);
			
			if (!sessionContinue) {
				conn.disconnect();
				establishedConnections.remove(remoteAddress);
			}

		} catch (IOException e) {
			// Disconnected
			if (conn != null) {
				conn.disconnect();
			}
			throw e;
		}
	}

	public static void main(String[] args) throws IOException {
		InetSocketAddress address = new InetSocketAddress(30000);
		AsyncUdpServer<IClientState> server = new AsyncUdpServer<IClientState>();
		server.startListening(address, new IServerHandler<IClientState>() {
			@Override
			public boolean processIncomingData(IClientState state, PacketData data) {
				String remoteAddress = state.getConnection().getRemoteAddress().toString();
				String message = data.getMessage();

				System.out.println(remoteAddress + ">" + message);
				state.getConnection().send(message);

				return true;
			}

			@Override
			public void disposeState(IClientState state) {
				System.out.println(state.getConnection().getRemoteAddress() + "[切断されました]");
			}

			@Override
			public IClientState createState(final IServerConnection connection) {
				System.out.println(connection.getRemoteAddress() + "[接続されました]");

				Thread pingThread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							Thread.sleep(20000);
							System.out.println("Send PING");
							connection.send("PING");
						} catch (InterruptedException e) {
						}
					}
				});
				pingThread.setDaemon(true);
				// pingThread.start();

				return new IClientState() {
					@Override
					public IServerConnection getConnection() {
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
	}
}
