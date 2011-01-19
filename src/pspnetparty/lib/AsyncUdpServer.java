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
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;

public class AsyncUdpServer<Type extends IClientState> implements IServer<Type> {

	private static final int READ_BUFFER_SIZE = 1000;

	private Selector selector;
	private IServerHandler<Type> handler;

	private DatagramChannel serverChannel;

	private HashMap<InetSocketAddress, Type> establishedStates = new HashMap<InetSocketAddress, Type>();

	public AsyncUdpServer() {
	}

	private static class Connection implements IServerConnection {
		private DatagramChannel channel;
		private InetSocketAddress remoteAddress;

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
			ByteBuffer buffer = Constants.CHARSET.encode(message + Constants.Protocol.MESSAGE_SEPARATOR);
			send(buffer);
		}

		@Override
		public void send(ByteBuffer buffer) {
			try {
				channel.send(buffer, remoteAddress);
			} catch (IOException e) {
			}
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

		System.out.println("UDP: Listening on " + bindAddress);

		Runnable run = new Runnable() {
			private ByteBuffer readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
			private PacketData data = new PacketData(readBuffer);

			@Override
			public void run() {
				try {
					while (serverChannel.isOpen())
						while (selector.select(2000) > 0) {
							for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
								SelectionKey key = it.next();
								it.remove();

								if (key.isReadable()) {
									DatagramChannel channel = (DatagramChannel) key.channel();
									Type state = null;

									try {
										readBuffer.clear();
										InetSocketAddress remoteAddress = (InetSocketAddress) channel.receive(readBuffer);
										if (remoteAddress == null) {
											throw new IOException("Client has disconnected.");
										}
										readBuffer.flip();

										state = establishedStates.get(remoteAddress);
										if (state == null) {
											Connection conn = new Connection(channel, remoteAddress);
											state = AsyncUdpServer.this.handler.createState(conn);

											establishedStates.put(remoteAddress, state);
										}

										AsyncUdpServer.this.handler.processIncomingData(state, data);

									} catch (IOException e) {
										// Disconnected
										if (state != null)
											AsyncUdpServer.this.handler.disposeState(state);
										channel.close();
										key.cancel();
										// e.printStackTrace();
									}
								}
							}
						}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};

		Thread asyncLoopThread = new Thread(run);
		asyncLoopThread.setName(AsyncUdpServer.class.getName());
		asyncLoopThread.start();
	}

	@Override
	public void stopListening() {
		if (serverChannel != null && serverChannel.isOpen()) {
			try {
				System.out.println("Now shuting down...");
				serverChannel.close();
			} catch (IOException e) {
			}
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

				return new IClientState() {
					@Override
					public IServerConnection getConnection() {
						return connection;
					}
				};
			}
		});

		while (System.in.read() != '\n') {
		}

		server.stopListening();
	}
}
