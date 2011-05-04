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
package pspnetparty.lib.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import pspnetparty.lib.ILogger;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;

public class AsyncUdpServer implements IServer {

	private static final int READ_BUFFER_SIZE = 20000;

	private ILogger logger;
	private Selector selector;
	private ConcurrentHashMap<IServerListener, Object> serverListeners;

	private DatagramChannel serverChannel;
	private ByteBuffer readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
	private PacketData data = new PacketData(readBuffer);

	private HashMap<String, IProtocol> protocolHandlers = new HashMap<String, IProtocol>();
	private ConcurrentHashMap<InetSocketAddress, Connection> establishedConnections;

	private ByteBuffer bufferProtocolOK = AppConstants.CHARSET.encode(IProtocol.PROTOCOL_OK);
	private ByteBuffer bufferProtocolNG = AppConstants.CHARSET.encode(IProtocol.PROTOCOL_NG);

	private Thread selectorThread;
	private Thread pingThread;

	public AsyncUdpServer(ILogger logger) {
		this.logger = logger;
		serverListeners = new ConcurrentHashMap<IServerListener, Object>();
		establishedConnections = new ConcurrentHashMap<InetSocketAddress, Connection>(30, 0.75f, 3);
	}

	@Override
	public void addServerListener(IServerListener listener) {
		serverListeners.put(listener, this);
	}

	@Override
	public void addProtocol(IProtocol handler) {
		protocolHandlers.put(handler.getProtocol(), handler);
	}

	@Override
	public boolean isListening() {
		return selector != null && selector.isOpen();
	}

	@Override
	public void startListening(InetSocketAddress bindAddress) throws IOException {
		if (isListening())
			stopListening();

		selector = Selector.open();

		serverChannel = DatagramChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.socket().bind(bindAddress);
		serverChannel.register(selector, SelectionKey.OP_READ);

		for (IServerListener listener : serverListeners.keySet())
			listener.log("UDP: Listening on " + bindAddress);

		selectorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				for (IServerListener listener : serverListeners.keySet())
					listener.serverStartupFinished();

				selectorLoop();

				for (IServerListener listener : serverListeners.keySet()) {
					listener.log("UDP: Now shuting down...");
					listener.serverShutdownFinished();
				}
				pingThread.interrupt();
			}
		});
		selectorThread.setName(getClass().getName() + " Selector");
		selectorThread.setDaemon(true);
		selectorThread.start();

		pingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					pingLoop();
				} catch (InterruptedException e) {
				}
			}
		});
		pingThread.setName(getClass().getName() + " Ping");
		pingThread.setDaemon(true);
		pingThread.start();
	}

	private void selectorLoop() {
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
									continue;
								}
								readBuffer.flip();

								Connection conn = establishedConnections.get(remoteAddress);
								if (conn == null) {
									String message = data.getMessage();
									String[] tokens = message.split(IProtocol.SEPARATOR);
									if (tokens.length != 2) {
										bufferProtocolNG.position(0);
										serverChannel.send(bufferProtocolNG, remoteAddress);
										continue;
									}

									String protocol = tokens[0];
									String number = tokens[1];

									IProtocol handler = protocolHandlers.get(protocol);
									if (handler == null) {
										bufferProtocolNG.position(0);
										serverChannel.send(bufferProtocolNG, remoteAddress);
										continue;
									}

									conn = new Connection(remoteAddress);
									if (!number.equals(IProtocol.NUMBER)) {
										conn.send(IProtocol.NUMBER);
										continue;
									}

									conn.driver = handler.createDriver(conn);
									if (conn.driver == null) {
										bufferProtocolNG.position(0);
										conn.send(bufferProtocolNG);
										continue;
									}

									bufferProtocolOK.position(0);
									conn.send(bufferProtocolOK);

									establishedConnections.put(remoteAddress, conn);
								} else {
									conn.processData();
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
			for (IServerListener listener : serverListeners.keySet())
				listener.log(Utility.stackTraceToString(e));
		}
	}

	private void pingLoop() throws InterruptedException {
		ByteBuffer pingBuffer = ByteBuffer.wrap(new byte[] { 1 });
		while (isListening()) {
			long deadline = System.currentTimeMillis() - IProtocol.PING_DEADLINE;
			// int pingCount = 0, disconnectCount = 0;

			for (Entry<InetSocketAddress, Connection> entry : establishedConnections.entrySet()) {
				try {
					Connection conn = entry.getValue();
					if (conn.lastPingTime < deadline) {
						logger.log(Utility.makePingDisconnectLog("UDP", conn.remoteAddress, deadline, conn.lastPingTime));
						conn.disconnect();
						// disconnectCount++;
					} else {
						pingBuffer.clear();
						conn.send(pingBuffer);
						// pingCount++;
					}
				} catch (RuntimeException e) {
					logger.log(Utility.stackTraceToString(e));
				} catch (Exception e) {
					logger.log(Utility.stackTraceToString(e));
				}
			}

			// logger.log("UDP Server Ping送信: p=" + pingCount + " d=" +
			// disconnectCount);
			Thread.sleep(IProtocol.PING_INTERVAL);
		}
	}

	@Override
	public void stopListening() {
		if (!isListening())
			return;

		try {
			selector.close();
		} catch (IOException e) {
		}
		// selector = null;

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

	private class Connection implements ISocketConnection {
		private InetSocketAddress remoteAddress;
		private IProtocolDriver driver;

		public long lastPingTime;

		public Connection(InetSocketAddress remoteAddress) {
			this.remoteAddress = remoteAddress;
			lastPingTime = System.currentTimeMillis();
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

			ByteBuffer terminateBuffer = ByteBuffer.wrap(new byte[] { 0 });
			send(terminateBuffer);

			if (driver != null) {
				driver.connectionDisconnected();
				driver = null;
			}

			lastPingTime = 0;
		}

		private void processData() {
			if (readBuffer.limit() == 1) {
				switch (readBuffer.get(0)) {
				case 0:
					disconnect();
					return;
				case 1:
					lastPingTime = System.currentTimeMillis();
					// logger.log(Utility.makePingLog("UDP Server",
					// getLocalAddress(), remoteAddress, lastPingTime));
					return;
				}
			}

			boolean sessionContinue = false;
			try {
				sessionContinue = driver.process(data);
			} catch (Exception e) {
			}

			if (!sessionContinue) {
				disconnect();
			}
		}
	}

	public static void main(String[] args) throws IOException {
		InetSocketAddress address = new InetSocketAddress(30000);
		AsyncUdpServer server = new AsyncUdpServer(new ILogger() {
			@Override
			public void log(String message) {
			}
		});
		server.addServerListener(new IServerListener() {
			@Override
			public void log(String message) {
				System.out.println(message);
			}

			@Override
			public void serverStartupFinished() {
			}

			@Override
			public void serverShutdownFinished() {
			}
		});

		server.addProtocol(new IProtocol() {
			@Override
			public void log(String message) {
				System.out.println(message);
			}

			@Override
			public String getProtocol() {
				return "TEST";
			}

			@Override
			public IProtocolDriver createDriver(final ISocketConnection connection) {
				System.out.println(connection.getRemoteAddress() + " [接続されました]");

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

				return new IProtocolDriver() {
					@Override
					public boolean process(PacketData data) {
						String remoteAddress = connection.getRemoteAddress().toString();
						String message = data.getMessage();

						System.out.println(remoteAddress + " >" + message);
						connection.send(message);

						return true;
					}

					@Override
					public ISocketConnection getConnection() {
						return connection;
					}

					@Override
					public void connectionDisconnected() {
						System.out.println(connection.getRemoteAddress() + " [切断されました]");
					}

					@Override
					public void errorProtocolNumber(String number) {
					}
				};
			}
		});

		server.startListening(address);

		while (System.in.read() != '\n') {
		}

		server.stopListening();
	}
}
