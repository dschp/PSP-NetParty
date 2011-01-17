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
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class AsyncTcpClient implements IAsyncClient {

	private static final int BUFFER_SIZE = 2000;

	private IAsyncClientHandler handler;

	private Selector selector;
	private SocketChannel channel;
	private boolean isConnected = false;

	private InetSocketAddress remoteAddress;

	public AsyncTcpClient(IAsyncClientHandler handler) {
		this.handler = handler;
	}
	
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		disconnect();
	}

	@Override
	public void connect(InetSocketAddress address) throws IOException {
		if (address == null)
			throw new IllegalArgumentException();

		this.remoteAddress = address;

		channel = SocketChannel.open();
		channel.configureBlocking(false);

		selector = Selector.open();
		channel.register(selector, SelectionKey.OP_CONNECT);// | SelectionKey.OP_READ);

		channel.connect(address);

		isConnected = true;

		Runnable run = new Runnable() {
			private ByteBuffer readBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
			private PacketData packetData = new PacketData(readBuffer);

			@Override
			public void run() {
				try {
					while (isConnected) {
						//System.out.println("LOOP");
						if (selector.select() > 0) {
							//System.out.println("SELECT");
							for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
								SelectionKey key = it.next();
								it.remove();

								if (key.isConnectable()) {
									channel.finishConnect();
									channel.register(selector, SelectionKey.OP_READ);

									handler.connectCallback(AsyncTcpClient.this);
								} else if (key.isReadable()) {
									readBuffer.clear();
									if (channel.read(readBuffer) > 0) {
										readBuffer.flip();
										handler.readCallback(AsyncTcpClient.this, packetData);
									} else {
										isConnected = false;
										break;
									}
								}
							}
						}
					}
				} catch (IOException e) {
					handler.log(AsyncTcpClient.this, Utility.makeStackTrace(e));
				} finally {
					isConnected = false;
					handler.disconnectCallback(AsyncTcpClient.this);
					if (channel != null && channel.isOpen()) {
						try {
							channel.close();
						} catch (IOException e) {
						}
					}
				}
			}
		};

		Thread dispatchThread = new Thread(run, AsyncTcpClient.class.getName());
		dispatchThread.setDaemon(true);
		dispatchThread.start();
	}

	@Override
	public boolean isConnected() {
		return isConnected;
	}

	@Override
	public void disconnect() {
		isConnected = false;
	}

	@Override
	public InetSocketAddress getSocketAddress() {
		return remoteAddress;
	}

	@Override
	public void send(String data) {
		if (!isConnected)
			return;

		ByteBuffer b = Constants.CHARSET.encode(data + Constants.Protocol.MESSAGE_SEPARATOR);
		try {
			channel.write(b);
		} catch (IOException e) {
		}
	}

	@Override
	public void send(ByteBuffer buffer) {
		if (!isConnected)
			return;
		
		try {
			channel.write(buffer);
		} catch (IOException e) {
		}
	}

	@Override
	public void send(byte[] data) {
		if (!isConnected)
			return;

		ByteBuffer buffer = ByteBuffer.wrap(data);
		try {
			channel.write(buffer);
		} catch (IOException e) {
		}
	}

	public static void main(String[] args) throws Exception {
		IAsyncClientHandler handler = new IAsyncClientHandler() {
			int count = 0;

			@Override
			public void log(IAsyncClient client, String message) {
				System.out.println(message);
			}

			@Override
			public void connectCallback(IAsyncClient client) {
				System.out.println("ê⁄ë±ÇµÇ‹ÇµÇΩ: " + client.getSocketAddress());
				client.send("TEST");
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
		};

		AsyncTcpClient client = new AsyncTcpClient(handler);
		client.connect(new InetSocketAddress("localhost", 30000));
	}
}
