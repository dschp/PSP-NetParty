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
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

public class AsyncUdpServer {

	private static final int BUF_SIZE = 1000;

	private Selector selector;
	private int port;

	private ByteBuffer readBuffer = ByteBuffer.allocate(BUF_SIZE);
	
	public AsyncUdpServer(int port) {
		this.port = port;
	}

	public void run() {
		DatagramChannel serverChannel = null;
		InetSocketAddress socketAddress = new InetSocketAddress(port);
		try {
			selector = Selector.open();
			
			serverChannel = DatagramChannel.open();
			serverChannel.configureBlocking(false);
			serverChannel.socket().bind(socketAddress);
			serverChannel.register(selector, SelectionKey.OP_READ);

			//ServerSocket socket = serverChannel.socket();
			System.out.println("UDP: Listening on " + socketAddress);

			while (selector.select() > 0) {
				for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
					SelectionKey key = it.next();
					it.remove();

					if (key.isReadable()) {
						DatagramChannel channel = (DatagramChannel) key.channel();
						try {
							doRead(channel);
						} catch (IOException e) {
							// Disconnected
							System.out.println(channel.socket().getRemoteSocketAddress() + "[Ø’f‚³‚ê‚Ü‚µ‚½]");
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

	private void doRead(DatagramChannel channel) throws IOException {
		readBuffer.clear();
		SocketAddress remoteAddress = channel.receive(readBuffer);
		if (remoteAddress == null) {
			throw new IOException("Client has disconnected.");
		}
		readBuffer.flip();
		System.out.println(remoteAddress + ">" + Utility.decode(readBuffer));
		readBuffer.flip();
		channel.send(readBuffer, remoteAddress);
	}
	
	public static void main(String[] args) {
		new AsyncUdpServer(30000).run();
	}
}
