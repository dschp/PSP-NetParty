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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;

public class AsyncTcpServer {

	private static final int BUF_SIZE = 1000;

	private Selector selector;
	private int port;

	private ByteBuffer readBuffer = ByteBuffer.allocate(BUF_SIZE);
	
	public AsyncTcpServer(int port) {
		this.port = port;
	}

	public void run() {
		ServerSocketChannel serverChannel = null;
		try {
			selector = Selector.open();
			serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false);
			serverChannel.socket().bind(new InetSocketAddress(port));
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);

			ServerSocket socket = serverChannel.socket();
			System.out.println("TCP: Listening on " + socket.getLocalSocketAddress());

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
						SocketChannel channel = (SocketChannel) key.channel();
						try {
							doRead(channel);
						} catch (IOException e) {
							// Disconnected
							System.out.println(channel.socket().getRemoteSocketAddress() + "[êÿífÇ≥ÇÍÇ‹ÇµÇΩ]");
							channel.close();
							key.cancel();
							//e.printStackTrace();
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (serverChannel != null && serverChannel.isOpen()) {
				try {
					System.out.println("Now shuting down...");
					serverChannel.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private void doAccept(ServerSocketChannel serverChannel) throws IOException {
		SocketChannel channel = serverChannel.accept();
		String remoteAddress = channel.socket().getRemoteSocketAddress().toString();
		System.out.println(remoteAddress + "[ê⁄ë±Ç≥ÇÍÇ‹ÇµÇΩ]");
		channel.configureBlocking(false);
		channel.register(selector, SelectionKey.OP_READ);
	}

	private void doRead(SocketChannel channel) throws IOException {
		String remoteAddress = channel.socket().getRemoteSocketAddress().toString();
		readBuffer.clear();
		if (channel.read(readBuffer) < 0) {
			throw new IOException("Client has disconnected.");
		}
		readBuffer.flip();
		System.out.println(remoteAddress + ">" + Utility.decode(readBuffer));
		readBuffer.flip();
		channel.write(readBuffer);
	}
	
	public static void main(String[] args) {
		new AsyncTcpServer(30000).run();
	}
}
