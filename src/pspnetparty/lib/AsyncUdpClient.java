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
import java.util.Iterator;

public class AsyncUdpClient implements IAsyncClient {

	private static final int BUFFER_SIZE = 2000;

	private IAsyncClientHandler handler;

	private InetSocketAddress remoteAddress;

	private Selector selector;
	private DatagramChannel channel;
	private boolean isConnected = false;

	public AsyncUdpClient(IAsyncClientHandler handler) {
		this.handler = handler;
	}

	@Override
	public void connect(InetSocketAddress address) throws IOException {
		if (address == null)
			throw new IllegalArgumentException();

		this.remoteAddress = address;

		selector = Selector.open();

		channel = DatagramChannel.open();
		channel.configureBlocking(false);
		channel.register(selector, SelectionKey.OP_READ);
		channel.connect(address);

		isConnected = true;
		
		Runnable run = new Runnable() {
			private ByteBuffer readBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
			private PacketData packetData = new PacketData(readBuffer);

			@Override
			public void run() {
				try {
					while (isConnected) {
						if (selector.select(1000) > 0) {
							for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
								SelectionKey key = it.next();
								it.remove();

								if (key.isReadable()) {
									readBuffer.clear();
									channel.read(readBuffer);
									readBuffer.flip();

									handler.readCallback(AsyncUdpClient.this, packetData);
								}
							}
						}
					}
				} catch (IOException e) {
					handler.log(AsyncUdpClient.this, Utility.makeStackTrace(e));
				} finally {
					isConnected = false;
					handler.disconnectCallback(AsyncUdpClient.this);
					if (channel != null && channel.isOpen()) {
						try {
							channel.close();
						} catch (IOException e) {
						}
					}
				}
			}
		};
		
		handler.connectCallback(AsyncUdpClient.this);
		Thread dispatchThread = new Thread(run, AsyncUdpClient.class.getName());
		dispatchThread.setDaemon(true);
		dispatchThread.start();
	}

	@Override
	public void disconnect() {
		isConnected = false;
	}

	@Override
	public boolean isConnected() {
		return isConnected;
	}

	@Override
	public InetSocketAddress getSocketAddress() {
		return remoteAddress;
	}

	@Override
	public void send(ByteBuffer buffer) {
		if (!isConnected)
			return;
		
		try {
			channel.send(buffer, remoteAddress);
		} catch (IOException e) {
			handler.log(this, buffer.toString());
			handler.log(this, Utility.makeStackTrace(e));
		}
	}

	@Override
	public void send(byte[] data) {
		send(ByteBuffer.wrap(data));
	}

	@Override
	public void send(String data) {
		ByteBuffer buffer = Constants.CHARSET.encode(data);// + Constants.Protocol.MESSAGE_SEPARATOR);
		send(buffer);
	}
	
	public static void main(String[] args) throws IOException {
		IAsyncClientHandler handler = new IAsyncClientHandler() {
			int count = 0;

			@Override
			public void log(IAsyncClient client, String message) {
				System.out.println(message);
			}

			@Override
			public void readCallback(IAsyncClient client, PacketData data) {
				for (String msg : data.getMessages()) {
					System.out.println("éÛêMÅF" + msg);
				}

				if (count++ > 10)
					client.disconnect();
				else
					try {
						Thread.sleep(1000);
						client.send("TEST " + count);
					} catch (InterruptedException e) {
					}
			}

			@Override
			public void disconnectCallback(IAsyncClient client) {
				System.out.println("êÿífÇµÇ‹ÇµÇΩ");
			}

			@Override
			public void connectCallback(IAsyncClient client) {
				System.out.println("ê⁄ë±ÇµÇ‹ÇµÇΩ: " + client.getSocketAddress());
				client.send("TEST");
			}
		};
		
		AsyncUdpClient client = new AsyncUdpClient(handler);
		client.connect(new InetSocketAddress("localhost", 30000));
	}
}
