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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class AsyncTcpServer<Type extends IClientState> implements IServer<Type> {

	private static final int READ_BUFFER_SIZE = 2000;

	private Selector selector;
	private IServerHandler<Type> handler;

	private ServerSocketChannel serverChannel;

	public AsyncTcpServer() {
	}

	@Override
	public void startListening(InetSocketAddress bindAddress, IServerHandler<Type> handler) throws IOException {
		stopListening();
		this.handler = handler;

		selector = Selector.open();
		serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.socket().bind(bindAddress);
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);
		
		ServerSocket socket = serverChannel.socket();
		System.out.println("TCP: Listening on " + socket.getLocalSocketAddress());
		
		Runnable run = new Runnable() {
			private ByteBuffer readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
			private PacketData data = new PacketData(readBuffer);
			
			@Override
			public void run() {
				try {
					while (selector.select() > 0) {
						for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
							SelectionKey key = it.next();
							it.remove();

							if (key.isAcceptable()) {
								ServerSocketChannel channel = (ServerSocketChannel) key.channel();
								try {
									doAccept(channel);
								} catch (IOException e) {
									key.cancel();
									e.printStackTrace();
								}
							} else if (key.isReadable()) {
								@SuppressWarnings("unchecked")
								Session<Type> session = (Session<Type>) key.attachment();
								try {
									readBuffer.clear();
									if (session.channel.read(readBuffer) < 0) {
										throw new IOException("Client has disconnected.");
									}
									readBuffer.flip();

									AsyncTcpServer.this.handler.processIncomingData(session.state, data);
									
								} catch (IOException e) {
									// Disconnected
									AsyncTcpServer.this.handler.disposeState(session.state);
									session.channel.close();
									key.cancel();
									// e.printStackTrace();
								}
							}
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				} catch (CancelledKeyException e) {
				}
			}
		};
		
		Thread asyncLoopThread = new Thread(run);
		asyncLoopThread.setName(AsyncTcpServer.class.getName());
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

	private static class Connection implements IServerConnection {
		private SocketChannel channel;

		Connection(SocketChannel channel) {
			this.channel = channel;
		}

		@Override
		public InetSocketAddress getRemoteAddress() {
			return (InetSocketAddress) channel.socket().getRemoteSocketAddress();
		}

		@Override
		public void send(ByteBuffer buffer) {
			try {
				channel.write(buffer);
			} catch (IOException e) {
			}
		}

		@Override
		public void send(String message) {
			ByteBuffer buffer = Constants.CHARSET.encode(message + Constants.Protocol.MESSAGE_SEPARATOR);
			send(buffer);
		}
	}

	private static class Session<Type extends IClientState> {
		Type state;
		SocketChannel channel;
	}

	private void doAccept(ServerSocketChannel serverChannel) throws IOException {
		SocketChannel channel = serverChannel.accept();

		IServerConnection conn = new Connection(channel);
		Type state = handler.createState(conn);

		Session<Type> session = new Session<Type>();
		session.channel = channel;
		session.state = state;

		channel.configureBlocking(false);
		channel.register(selector, SelectionKey.OP_READ, session);
	}

	public static void main(String[] args) throws IOException {
		InetSocketAddress address = new InetSocketAddress(30000);
		AsyncTcpServer<IClientState> server = new AsyncTcpServer<IClientState>();
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
