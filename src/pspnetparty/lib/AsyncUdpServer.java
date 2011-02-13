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
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import pspnetparty.lib.constants.AppConstants;

public class AsyncUdpServer<Type extends IClientState> implements IServer<Type> {

	private static final int READ_BUFFER_SIZE = 20000;

	private Selector selector;
	private IAsyncServerHandler<Type> handler;

	private DatagramChannel serverChannel;
	private ByteBuffer readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
	private PacketData data = new PacketData(readBuffer);

	private ConcurrentHashMap<InetSocketAddress, Connection> establishedConnections;

	private Thread selectorThread;
	private Thread sessionCleanupThread;

	public AsyncUdpServer() {
		establishedConnections = new ConcurrentHashMap<InetSocketAddress, Connection>(30, 0.75f, 3);
	}

	private class Connection implements ISocketConnection {
		private InetSocketAddress remoteAddress;

		public Type state;
		public long lastUsedTime;

		public Connection(InetSocketAddress remoteAddress) {
			this.remoteAddress = remoteAddress;
		}

		@Override
		public InetSocketAddress getRemoteAddress() {
			return remoteAddress;
		}
		
		@Override
		public InetSocketAddress getLocalAddress() {
			return (InetSocketAddress) serverChannel.socket().getLocalSocketAddress();
		}

		@Override
		public boolean isConnected() {
			return serverChannel.isConnected();
		}

		@Override
		public void send(String message) {
			ByteBuffer buffer = AppConstants.CHARSET.encode(message);
			send(buffer);
		}

		@Override
		public void send(ByteBuffer buffer) {
			try {
				serverChannel.send(buffer, remoteAddress);
			} catch (IOException e) {
			}
		}

		@Override
		public void send(byte[] data) {
			ByteBuffer buffer = ByteBuffer.wrap(data);
			send(buffer);
		}

		@Override
		public void disconnect() {
			if (establishedConnections.remove(remoteAddress) == null)
				return;
			if (state != null) {
				handler.disposeState(state);
				state = null;
			}

			lastUsedTime = 0;
		}
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

		serverChannel = DatagramChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.socket().bind(bindAddress);
		serverChannel.register(selector, SelectionKey.OP_READ);

		handler.log("UDP: Listening on " + bindAddress);

		selectorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				AsyncUdpServer.this.handler.serverStartupFinished();
				try {
					while (serverChannel.isOpen())
						while (selector.select() > 0) {
							for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
								SelectionKey key = it.next();
								it.remove();

								if (key.isReadable()) {
									try {
										readBuffer.clear();
										InetSocketAddress remoteAddress = (InetSocketAddress) serverChannel.receive(readBuffer);
										if (remoteAddress == null) {
											throw new IOException("Client has disconnected.");
										}
										readBuffer.flip();

										Connection conn = establishedConnections.get(remoteAddress);
										if (conn == null) {
											conn = new Connection(remoteAddress);
											conn.state = handler.createState(conn);

											establishedConnections.put(remoteAddress, conn);
										}
										conn.lastUsedTime = System.currentTimeMillis();

										boolean sessionContinue = false;
										try {
											sessionContinue = handler.processIncomingData(conn.state, data);
										} catch (Exception e) {
										}

										if (!sessionContinue) {
											conn.disconnect();
										}
									} catch (Exception e) {
									}
								}
							}
						}
				} catch (CancelledKeyException e) {
				} catch (ClosedSelectorException e) {
				} catch (IOException e) {
				} catch (RuntimeException e) {
					AsyncUdpServer.this.handler.log(Utility.makeStackTrace(e));
				}

				AsyncUdpServer.this.handler.log("UDP: Now shuting down...");
				AsyncUdpServer.this.handler.serverShutdownFinished();
				sessionCleanupThread.interrupt();
			}
		});
		selectorThread.setName(AsyncUdpServer.class.getName());
		selectorThread.start();

		sessionCleanupThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (isListening()) {
						long deadline = System.currentTimeMillis() - 60000;
						for (Entry<InetSocketAddress, Connection> entry : establishedConnections.entrySet()) {
							Connection conn = entry.getValue();
							if (conn.lastUsedTime < deadline) {
								conn.disconnect();
							}
						}
						// AsyncUdpServer.this.handler.log(establishedConnections.toString());
						Thread.sleep(20000);
					}
				} catch (InterruptedException e) {
				}
			}
		});
		sessionCleanupThread.setName(AsyncUdpServer.class.getName() + " SessionCleanup");
		sessionCleanupThread.setDaemon(true);
		sessionCleanupThread.start();
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
		for (Entry<InetSocketAddress, Connection> entry : establishedConnections.entrySet()) {
			Connection conn = entry.getValue();
			conn.disconnect();
		}
	}

	public static void main(String[] args) throws IOException {
		InetSocketAddress address = new InetSocketAddress(30000);
		AsyncUdpServer<IClientState> server = new AsyncUdpServer<IClientState>();
		server.startListening(address, new IAsyncServerHandler<IClientState>() {
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
			public IClientState createState(final ISocketConnection connection) {
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
	}
}
