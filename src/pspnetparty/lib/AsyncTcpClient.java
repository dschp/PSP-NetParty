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
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class AsyncTcpClient implements IAsyncClient {

	private static final int INITIAL_READ_BUFFER_SIZE = 2000;
	private static final int MAX_PACKET_SIZE = 100000;

	private IAsyncClientHandler handler;

	private Selector selector;
	private SocketChannel channel;
	private boolean isConnected = false;

	private InetSocketAddress remoteAddress;

	// private ByteBuffer sendHeaderBuffer =
	// ByteBuffer.allocate(Constants.Protocol.INTEGER_BYTE_SIZE);

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
		channel.register(selector, SelectionKey.OP_CONNECT);

		channel.connect(address);

		isConnected = true;

		Runnable run = new Runnable() {
			private ByteBuffer readDataBuffer = ByteBuffer.allocateDirect(INITIAL_READ_BUFFER_SIZE);
			private PacketData packetData = new PacketData(readDataBuffer);

			private ByteBuffer readHeaderBuffer = ByteBuffer.allocateDirect(Constants.Protocol.INTEGER_BYTE_SIZE);

			@Override
			public void run() {
				try {
					while (isConnected) {
						// System.out.println("LOOP");
						if (selector.select(1000) > 0) {
							// System.out.println("SELECT");
							for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext();) {
								SelectionKey key = it.next();
								it.remove();

								if (key.isConnectable()) {
									channel.finishConnect();
									channel.register(selector, SelectionKey.OP_READ);

									handler.connectCallback(AsyncTcpClient.this);
								} else if (key.isReadable()) {
									if (readDataBuffer.position() == 0) {
										readHeaderBuffer.clear();
										if (channel.read(readHeaderBuffer) < 0) {
											throw new IOException("Server has disconnected.");
										}
										readHeaderBuffer.flip();

										int dataSize = readHeaderBuffer.getInt();
										//System.out.println("Data size=" + dataSize);
										if (dataSize < 1 || dataSize > MAX_PACKET_SIZE) {
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
										throw new IOException("Server has disconnected.");
									}

									if (readDataBuffer.remaining() == 0) {
										readDataBuffer.position(0);
										handler.readCallback(AsyncTcpClient.this, packetData);
										readDataBuffer.clear();
									}
								}
							}
						}
					}
				} catch (ConnectException e) {
				} catch (IOException e) {
					handler.log(AsyncTcpClient.this, Utility.makeStackTrace(e));
				} finally {
					isConnected = false;
					try {
						handler.disconnectCallback(AsyncTcpClient.this);
					} catch (RuntimeException re) {
					}
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
		// dispatchThread.setDaemon(true);
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
		ByteBuffer buffer = Constants.CHARSET.encode(data);
		send(buffer);
	}

	@Override
	public void send(byte[] data) {
		ByteBuffer buffer = ByteBuffer.wrap(data);
		send(buffer);
	}

	@Override
	public void send(ByteBuffer buffer) {
		if (!isConnected)
			return;

		try {
			ByteBuffer headerData = ByteBuffer.allocate(Constants.Protocol.INTEGER_BYTE_SIZE);
			headerData.putInt(buffer.limit());
			headerData.flip();

			ByteBuffer[] array = new ByteBuffer[] { headerData, buffer };
			channel.write(array);
		} catch (IOException e) {
		}
	}

	public static void main(String[] args) throws Exception {
		IAsyncClientHandler handler = new IAsyncClientHandler() {
			@Override
			public void log(IAsyncClient client, String message) {
				System.out.println(message);
			}

			@Override
			public void connectCallback(IAsyncClient client) {
				System.out.println("接続しました: " + client.getSocketAddress());
			}

			@Override
			public void readCallback(IAsyncClient client, PacketData data) {
				for (String msg : data.getMessages()) {
					System.out.println("受信(" + msg.length() + ")");
				}
			}

			@Override
			public void disconnectCallback(IAsyncClient client) {
				System.out.println("切断しました");
			}
		};

		final AsyncTcpClient client = new AsyncTcpClient(handler);
		client.connect(new InetSocketAddress("localhost", 30000));

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
					for (int i = 0; i < 10; i++) {
						client.send(text);
						Thread.sleep(1500);
					}
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}

				client.disconnect();
			}
		});
		sendThread.start();
	}
}
