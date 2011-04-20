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
package pspnetparty.lib.engine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import pspnetparty.lib.ILogger;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.socket.AsyncTcpClient;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.IProtocolMessageHandler;
import pspnetparty.lib.socket.IServer;
import pspnetparty.lib.socket.IServerListener;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.TextProtocolDriver;

public class PortalEngine {
	private interface IServerProtocol extends IProtocol {
		public String getTypeName();
	}

	private static final int RETRY_INTERVAL = 5 * 60 * 1000;

	private ILogger logger;
	private AsyncTcpClient tcpClient;

	private RoomServerStatusProtocol roomServerProtocol = new RoomServerStatusProtocol();
	private ConcurrentSkipListSet<RoomServerStatusProtocolDriver> roomServers;
	private ConcurrentSkipListMap<String, ISocketConnection> roomServerConnections;
	private ConcurrentSkipListMap<String, RetryInfo> roomServerRetryAddresses;

	private SearchServerStatusProtocol searchServerProtocol = new SearchServerStatusProtocol();
	private ConcurrentSkipListSet<SearchServerStatusProtocolDriver> searchServers;
	private ConcurrentSkipListMap<String, ISocketConnection> searchServerConnections;
	private ConcurrentSkipListMap<String, RetryInfo> searchServerRetryAddresses;

	private LobbyServerStatusProtocol lobbyServerProtocol = new LobbyServerStatusProtocol();
	private ConcurrentSkipListSet<LobbyServerStatusProtocolDriver> lobbyServers;
	private ConcurrentSkipListMap<String, ISocketConnection> lobbyServerConnections;
	private ConcurrentSkipListMap<String, RetryInfo> lobbyServerRetryAddresses;

	private final Object reconnectLock = new Object();
	private boolean isStarted = false;

	private static class RetryInfo {
		private long timestamp;
		private InetSocketAddress address;

		private RetryInfo(InetSocketAddress address) {
			this.address = address;
			this.timestamp = System.currentTimeMillis() + RETRY_INTERVAL;
		}
	}

	public PortalEngine(IServer server, ILogger logger) {
		this.logger = logger;

		roomServers = new ConcurrentSkipListSet<RoomServerStatusProtocolDriver>();
		roomServerConnections = new ConcurrentSkipListMap<String, ISocketConnection>();
		roomServerRetryAddresses = new ConcurrentSkipListMap<String, RetryInfo>();

		searchServers = new ConcurrentSkipListSet<SearchServerStatusProtocolDriver>();
		searchServerConnections = new ConcurrentSkipListMap<String, ISocketConnection>();
		searchServerRetryAddresses = new ConcurrentSkipListMap<String, RetryInfo>();

		lobbyServers = new ConcurrentSkipListSet<PortalEngine.LobbyServerStatusProtocolDriver>();
		lobbyServerConnections = new ConcurrentSkipListMap<String, ISocketConnection>();
		lobbyServerRetryAddresses = new ConcurrentSkipListMap<String, PortalEngine.RetryInfo>();

		tcpClient = new AsyncTcpClient(1000000, 2000);

		server.addServerListener(new IServerListener() {
			@Override
			public void log(String message) {
				PortalEngine.this.logger.log(message);
			}

			@Override
			public void serverStartupFinished() {
				isStarted = true;

				Thread retryThread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							while (isStarted) {
								synchronized (reconnectLock) {
									for (Entry<String, RetryInfo> e : roomServerRetryAddresses.entrySet()) {
										RetryInfo info = e.getValue();
										retry(info, roomServerProtocol, true);
									}
									for (Entry<String, RetryInfo> e : searchServerRetryAddresses.entrySet()) {
										RetryInfo info = e.getValue();
										retry(info, searchServerProtocol, true);
									}
									for (Entry<String, RetryInfo> e : lobbyServerRetryAddresses.entrySet()) {
										RetryInfo info = e.getValue();
										retry(info, lobbyServerProtocol, true);
									}
								}

								Thread.sleep(5000);
							}
						} catch (InterruptedException e) {
						}
					}
				}, PortalEngine.class.getName() + " Retry");
				retryThread.setDaemon(true);
				retryThread.start();
			}

			@Override
			public void serverShutdownFinished() {
				isStarted = false;

				for (SearchServerStatusProtocolDriver driver : searchServers) {
					driver.getConnection().disconnect();
				}
				searchServers.clear();

				for (RoomServerStatusProtocolDriver driver : roomServers) {
					driver.getConnection().disconnect();
				}
				roomServers.clear();

				for (LobbyServerStatusProtocolDriver driver : lobbyServers) {
					driver.getConnection().disconnect();
				}
				lobbyServers.clear();
			}
		});
		server.addProtocol(new PortalProtocol());
	}

	public String statusToString() {
		StringBuilder sb = new StringBuilder();

		sb.append("Room  : ");
		sb.append(roomServers);
		sb.append(AppConstants.NEW_LINE);

		sb.append("Search: ");
		sb.append(searchServers);
		sb.append(AppConstants.NEW_LINE);

		sb.append("Lobby : ");
		sb.append(lobbyServers);
		sb.append(AppConstants.NEW_LINE);

		return sb.toString();
	}

	public String allRoomsToString() {
		StringBuilder sb = new StringBuilder();

		for (RoomServerStatusProtocolDriver d : roomServers) {
			for (Entry<String, PortalPlayRoom> e : d.playRooms.entrySet()) {
				PlayRoom room = e.getValue();
				sb.append(room.getRoomAddress());
				sb.append('\t');
				sb.append(room.getTitle());
				sb.append('\t');
				sb.append(room.getCurrentPlayers());
				sb.append('/');
				sb.append(room.getMaxPlayers());
				sb.append(AppConstants.NEW_LINE);
			}
		}

		return sb.toString();
	}

	private class PortalProtocol implements IProtocol {
		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_PORTAL;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			PortalProtocolDriver driver = new PortalProtocolDriver(connection);
			return driver;
		}
	}

	private class PortalProtocolDriver extends TextProtocolDriver {

		public PortalProtocolDriver(ISocketConnection connection) {
			super(connection, portalHandlers);
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public void connectionDisconnected() {
		}

		@Override
		public void errorProtocolNumber(String number) {
		}
	}

	private HashMap<String, IProtocolMessageHandler> portalHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		portalHandlers.put(ProtocolConstants.Portal.COMMAND_FIND_SEARCH_SERVER, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				try {
					// System.out.println("Query " + searchServers);
					SearchServerStatusProtocolDriver status = searchServers.first();
					driver.getConnection().send(status.address);
				} catch (NoSuchElementException e) {
				}
				return false;
			}
		});
		portalHandlers.put(ProtocolConstants.Portal.COMMAND_FIND_ROOM_SERVER, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				try {
					// System.out.println("Query " + roomServers);
					RoomServerStatusProtocolDriver status = roomServers.first();
					driver.getConnection().send(status.address);
				} catch (NoSuchElementException e) {
				}
				return false;
			}
		});
		portalHandlers.put(ProtocolConstants.Portal.COMMAND_LIST_LOBBY_SERVERS, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				StringBuilder sb = new StringBuilder();

				for (LobbyServerStatusProtocolDriver d : lobbyServers) {
					sb.append(d.address);
					sb.append('\t');
					sb.append(d.currentUsers);
					sb.append('\t');
					sb.append(d.title);
					sb.append('\n');
				}

				driver.getConnection().send(sb.toString());
				return false;
			}
		});
	}

	private void logConnectionError(InetSocketAddress socketAddress, IOException ex) {
		logger.log("( " + socketAddress + " ) " + ex.toString());
	}

	private void retry(RetryInfo info, IServerProtocol protocol, boolean checkTimestamp) {
		if (checkTimestamp && info.timestamp > System.currentTimeMillis())
			return;

		InetSocketAddress socketAddress = info.address;
		try {
			tcpClient.connect(socketAddress, ProtocolConstants.TIMEOUT, protocol);
		} catch (IOException ex) {
			info.timestamp = System.currentTimeMillis() + RETRY_INTERVAL;
			logConnectionError(socketAddress, ex);
		}
	}

	private void connect(Set<String> addresses, Map<String, ISocketConnection> activeMap, Map<String, RetryInfo> retryMap,
			IServerProtocol protocol) {
		HashSet<String> currentServers = new HashSet<String>();
		currentServers.addAll(activeMap.keySet());
		currentServers.addAll(retryMap.keySet());

		for (String address : currentServers) {
			if (!addresses.contains(address)) {
				retryMap.remove(address);
				ISocketConnection conn = activeMap.remove(address);
				if (conn != null) {
					conn.disconnect();
				}
			}
		}
		for (String address : addresses) {
			if (activeMap.containsKey(address)) {
			} else if (retryMap.containsKey(address)) {
				synchronized (reconnectLock) {
					RetryInfo info = retryMap.get(address);
					retry(info, protocol, false);
				}
			} else {
				InetSocketAddress socketAddress = Utility.parseSocketAddress(address);
				if (socketAddress != null)
					try {
						tcpClient.connect(socketAddress, ProtocolConstants.TIMEOUT, protocol);
					} catch (IOException ex) {
						RetryInfo info = new RetryInfo(socketAddress);
						retryMap.put(address, info);
						logConnectionError(socketAddress, ex);
					}
			}
		}
	}

	public void connectRoomServers(Set<String> addresses) {
		connect(addresses, roomServerConnections, roomServerRetryAddresses, roomServerProtocol);
	}

	public String[] listActiveRoomServers() {
		return roomServerConnections.keySet().toArray(new String[roomServerConnections.size()]);
	}

	public String[] listDeadRoomServers() {
		return roomServerRetryAddresses.keySet().toArray(new String[roomServerRetryAddresses.size()]);
	}

	public void connectSearchServers(Set<String> addresses) {
		connect(addresses, searchServerConnections, searchServerRetryAddresses, searchServerProtocol);
	}

	public String[] listActiveSearchServers() {
		return searchServerConnections.keySet().toArray(new String[searchServerConnections.size()]);
	}

	public String[] listDeadSearchServers() {
		return searchServerRetryAddresses.keySet().toArray(new String[searchServerRetryAddresses.size()]);
	}

	public void connectLobbyServers(Set<String> addresses) {
		connect(addresses, lobbyServerConnections, lobbyServerRetryAddresses, lobbyServerProtocol);
	}

	public String[] listActiveLobbyServers() {
		return lobbyServerConnections.keySet().toArray(new String[lobbyServerConnections.size()]);
	}

	public String[] listDeadLobbyServers() {
		return lobbyServerRetryAddresses.keySet().toArray(new String[lobbyServerRetryAddresses.size()]);
	}

	public void reconnectNow() {
		synchronized (reconnectLock) {
			for (Entry<String, RetryInfo> e : roomServerRetryAddresses.entrySet()) {
				RetryInfo info = e.getValue();
				retry(info, roomServerProtocol, false);
			}
			for (Entry<String, RetryInfo> e : searchServerRetryAddresses.entrySet()) {
				RetryInfo info = e.getValue();
				retry(info, searchServerProtocol, false);
			}
			for (Entry<String, RetryInfo> e : lobbyServerRetryAddresses.entrySet()) {
				RetryInfo info = e.getValue();
				retry(info, lobbyServerProtocol, false);
			}
		}
	}

	private class LobbyServerStatusProtocol implements IServerProtocol {
		@Override
		public void log(String message) {
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_LOBBY_STATUS;
		}

		@Override
		public String getTypeName() {
			return "ロビーサーバー";
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			LobbyServerStatusProtocolDriver driver = new LobbyServerStatusProtocolDriver(connection);

			lobbyServerConnections.put(driver.address, connection);
			lobbyServerRetryAddresses.remove(driver.address);

			logger.log(getTypeName() + "と接続しました: " + connection.getRemoteAddress());
			return driver;
		}
	}

	private class LobbyServerStatusProtocolDriver extends TextProtocolDriver implements Comparable<LobbyServerStatusProtocolDriver> {
		private String address;

		private String title;
		private int currentUsers;

		public LobbyServerStatusProtocolDriver(ISocketConnection connection) {
			super(connection, lobbyServerHandlers);

			address = Utility.socketAddressToStringByHostName(connection.getRemoteAddress());
		}

		@Override
		public void connectionDisconnected() {
			lobbyServers.remove(this);

			InetSocketAddress socketAddress = getConnection().getRemoteAddress();
			ISocketConnection conn = lobbyServerConnections.remove(address);
			if (conn != null) {
				RetryInfo info = new RetryInfo(socketAddress);
				lobbyServerRetryAddresses.put(address, info);

				logger.log(lobbyServerProtocol.getTypeName() + "との接続が切断されました: " + socketAddress);
			} else {
				logger.log(lobbyServerProtocol.getTypeName() + "との接続を切断しました: " + socketAddress);
			}
		}

		@Override
		public void errorProtocolNumber(String number) {
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public int compareTo(LobbyServerStatusProtocolDriver d) {
			int diff = d.currentUsers - currentUsers;
			if (diff == 0)
				return address.compareTo(d.address);
			return diff;
		}

		@Override
		public String toString() {
			return "LobbyServer(" + address + "," + currentUsers + "," + title + ")";
		}
	}

	private HashMap<String, IProtocolMessageHandler> lobbyServerHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		lobbyServerHandlers.put(ProtocolConstants.SERVER_STATUS, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				try {
					int currentUsers = Integer.parseInt(argument);

					LobbyServerStatusProtocolDriver status = (LobbyServerStatusProtocolDriver) driver;
					// System.out.println("Before: " + lobbyServers);
					lobbyServers.remove(status);

					status.currentUsers = currentUsers;

					lobbyServers.add(status);
					// System.out.println("After : " + lobbyServers);
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		lobbyServerHandlers.put(ProtocolConstants.LobbyStatus.NOTIFY_LOBBY_INFO, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String title) {
				LobbyServerStatusProtocolDriver status = (LobbyServerStatusProtocolDriver) driver;
				status.title = title;
				return true;
			}
		});
	}

	private static class PortalPlayRoom extends PlayRoom {
		private boolean isMyRoom;

		private PortalPlayRoom(String serverAddress, String masterName, String title, boolean hasPassword, int currentPlayers,
				int maxPlayers, long created, boolean isMyRoom) {
			super(serverAddress, masterName, title, hasPassword, currentPlayers, maxPlayers, created);

			this.isMyRoom = isMyRoom;
		}

		private void appendRoomCreated(StringBuilder sb, String source) {
			sb.append(ProtocolConstants.SearchStatus.NOTIFY_ROOM_CREATED);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(source);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			if (isMyRoom)
				sb.append(getServerAddress());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(getMasterName());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(getTitle());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(getCurrentPlayers());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(getMaxPlayers());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(hasPassword() ? "Y" : "N");
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(getCreatedTime());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(getDescription());
		}
	}

	private class SearchServerStatusProtocol implements IServerProtocol {
		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_SEARCH_STATUS;
		}

		@Override
		public String getTypeName() {
			return "検索サーバー";
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			SearchServerStatusProtocolDriver driver = new SearchServerStatusProtocolDriver(connection);

			searchServerConnections.put(driver.address, connection);
			searchServerRetryAddresses.remove(driver.address);

			logger.log(getTypeName() + "と接続しました: " + connection.getRemoteAddress());
			return driver;
		}
	}

	private class SearchServerStatusProtocolDriver extends TextProtocolDriver implements Comparable<SearchServerStatusProtocolDriver> {
		private String address;

		private int currentUsers;
		private int maxUsers;
		private boolean feedRoomData = false;

		private SearchServerStatusProtocolDriver(ISocketConnection connection) {
			super(connection, searchServerHandlers);

			address = Utility.socketAddressToStringByHostName(connection.getRemoteAddress());
		}

		@Override
		public void connectionDisconnected() {
			searchServers.remove(this);

			InetSocketAddress socketAddress = getConnection().getRemoteAddress();
			ISocketConnection conn = searchServerConnections.remove(address);
			if (conn != null) {
				RetryInfo info = new RetryInfo(socketAddress);
				searchServerRetryAddresses.put(address, info);

				logger.log(searchServerProtocol.getTypeName() + "との接続が切断されました: " + socketAddress);
			} else {
				logger.log(searchServerProtocol.getTypeName() + "との接続を切断しました: " + socketAddress);
			}
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public void errorProtocolNumber(String number) {
		}

		@Override
		public int compareTo(SearchServerStatusProtocolDriver d) {
			if (maxUsers == 0) {
				if (d.maxUsers == 0) {
					return address.compareTo(d.address);
				}
				return 1;
			} else if (d.maxUsers == 0) {
				return -1;
			} else if (currentUsers == 0 && d.currentUsers == 0) {
				int diff = maxUsers - d.maxUsers;
				if (diff < 0)
					return 1;
				else if (diff > 0)
					return -1;
				else
					return address.compareTo(d.address);
			}

			double rate1 = (double) currentUsers / (double) maxUsers;
			double rate2 = (double) d.currentUsers / (double) d.maxUsers;

			double diff = rate1 - rate2;
			if (diff < 0d)
				return -1;
			if (diff > 0d)
				return 1;

			return address.compareTo(d.address);
		}

		@Override
		public String toString() {
			return "SearchServer(" + address + "," + currentUsers + "/" + maxUsers + (feedRoomData ? " *" : "") + ")";
		}
	}

	private HashMap<String, IProtocolMessageHandler> searchServerHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		searchServerHandlers.put(ProtocolConstants.SERVER_STATUS, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// SS currentUsers maxUsers
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR);
				if (tokens.length != 2)
					return true;

				try {
					SearchServerStatusProtocolDriver status = (SearchServerStatusProtocolDriver) driver;
					// System.out.println("Before: " + searchServers);
					searchServers.remove(status);

					int currentUsers = Integer.parseInt(tokens[0]);
					int maxUsers = Integer.parseInt(tokens[1]);

					status.currentUsers = currentUsers;
					status.maxUsers = maxUsers;

					searchServers.add(status);
					// System.out.println("After : " + searchServers);
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		searchServerHandlers.put(ProtocolConstants.SearchStatus.COMMAND_ASK_ROOM_DATA, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				SearchServerStatusProtocolDriver status = (SearchServerStatusProtocolDriver) driver;
				status.feedRoomData = true;

				StringBuilder sb = new StringBuilder();
				for (RoomServerStatusProtocolDriver d : roomServers) {
					for (Entry<String, PortalPlayRoom> e : d.playRooms.entrySet()) {
						PortalPlayRoom room = e.getValue();
						room.appendRoomCreated(sb, d.address);
						sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
					}
				}
				if (sb.length() > 0) {
					sb.deleteCharAt(sb.length() - 1);
					status.getConnection().send(sb.toString());
				}
				return true;
			}
		});
	}

	private class RoomServerStatusProtocol implements IServerProtocol {
		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_ROOM_STATUS;
		}

		@Override
		public String getTypeName() {
			return "ルームサーバー";
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			RoomServerStatusProtocolDriver driver = new RoomServerStatusProtocolDriver(connection);

			roomServerConnections.put(driver.address, connection);
			roomServerRetryAddresses.remove(driver.address);

			logger.log(getTypeName() + "と接続しました: " + connection.getRemoteAddress());
			return driver;
		}
	}

	private void sendRoomServerNotifyToSearchServer(String notify) {
		for (SearchServerStatusProtocolDriver s : searchServers) {
			if (s.feedRoomData)
				s.getConnection().send(notify);
		}
	}

	private class RoomServerStatusProtocolDriver extends TextProtocolDriver implements Comparable<RoomServerStatusProtocolDriver> {
		private String address;

		private int currentRooms;
		private int maxRooms;
		private int myRoomCount;

		private ConcurrentHashMap<String, PortalPlayRoom> playRooms;

		public RoomServerStatusProtocolDriver(ISocketConnection connection) {
			super(connection, roomServerHandlers);

			playRooms = new ConcurrentHashMap<String, PortalPlayRoom>();
			address = Utility.socketAddressToStringByHostName(connection.getRemoteAddress());
		}

		@Override
		public void connectionDisconnected() {
			roomServers.remove(this);

			String notify = ProtocolConstants.SearchStatus.NOTIFY_ROOM_SERVER_REMOVED + TextProtocolDriver.ARGUMENT_SEPARATOR + address;
			sendRoomServerNotifyToSearchServer(notify);

			InetSocketAddress socketAddress = getConnection().getRemoteAddress();
			ISocketConnection conn = roomServerConnections.remove(address);
			if (conn != null) {
				RetryInfo info = new RetryInfo(socketAddress);
				roomServerRetryAddresses.put(address, info);

				logger.log(roomServerProtocol.getTypeName() + "との接続が切断されました: " + socketAddress);
			} else {
				logger.log(roomServerProtocol.getTypeName() + "との接続を切断しました: " + socketAddress);
			}
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		private String supplementServerAddress(String roomAddress) {
			if (roomAddress.startsWith(":")) {
				return this.address + roomAddress;
			} else {
				return roomAddress;
			}
		}

		@Override
		public int compareTo(RoomServerStatusProtocolDriver d) {
			if (maxRooms == 0) {
				if (d.maxRooms == 0) {
					return address.compareTo(d.address);
				}
				return 1;
			} else if (d.maxRooms == 0) {
				return -1;
			} else if (currentRooms == 0 && d.currentRooms == 0) {
				int diff = maxRooms - d.maxRooms;
				if (diff < 0)
					return 1;
				else if (diff > 0)
					return -1;
				else
					return address.compareTo(d.address);
			}

			double rate1 = (double) currentRooms / (double) maxRooms;
			double rate2 = (double) d.currentRooms / (double) d.maxRooms;

			double diff = rate1 - rate2;
			if (diff < 0d)
				return -1;
			if (diff > 0d)
				return 1;

			return address.compareTo(d.address);
		}

		@Override
		public void errorProtocolNumber(String number) {
		}

		@Override
		public String toString() {
			return "RoomServer(" + address + "," + currentRooms + "/" + maxRooms + ")";
		}
	}

	private HashMap<String, IProtocolMessageHandler> roomServerHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		roomServerHandlers.put(ProtocolConstants.SERVER_STATUS, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// NRRC roomCount maxRooms myRoomCount
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 3)
					return true;

				try {
					int currentRooms = Integer.parseInt(tokens[0]);
					int maxRooms = Integer.parseInt(tokens[1]);
					int myRoomCount = Integer.parseInt(tokens[2]);

					RoomServerStatusProtocolDriver status = (RoomServerStatusProtocolDriver) driver;
					// System.out.println("Before: " + roomServers);
					roomServers.remove(status);

					status.currentRooms = currentRooms;
					status.maxRooms = maxRooms;
					status.myRoomCount = myRoomCount;

					roomServers.add(status);
					// System.out.println("After : " + roomServers);
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		roomServerHandlers.put(ProtocolConstants.RoomStatus.NOTIFY_ROOM_CREATED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// R hostName:port masterName title currentPlayers
				// maxPlayers hasPassword createdTime description
				final String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 8)
					return true;

				try {
					RoomServerStatusProtocolDriver status = (RoomServerStatusProtocolDriver) driver;

					String serverAddress = tokens[0];
					boolean isMyRoom = !Utility.isEmpty(serverAddress);
					if (!isMyRoom)
						serverAddress = status.address;
					String masterName = tokens[1];
					String title = tokens[2];

					int currentPlayers = Integer.parseInt(tokens[3]);
					int maxPlayers = Integer.parseInt(tokens[4]);
					boolean hasPassword = "Y".equals(tokens[5]);
					long created = Long.parseLong(tokens[6]);
					String description = tokens[7];

					PortalPlayRoom room = new PortalPlayRoom(serverAddress, masterName, title, hasPassword, currentPlayers, maxPlayers,
							created, isMyRoom);
					room.setDescription(description);

					status.playRooms.put(room.getRoomAddress(), room);

					StringBuilder sb = new StringBuilder();
					room.appendRoomCreated(sb, status.address);

					sendRoomServerNotifyToSearchServer(sb.toString());
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		roomServerHandlers.put(ProtocolConstants.RoomStatus.NOTIFY_ROOM_UPDATED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// U hostname:port:master title maxPlayers hasPassword
				// description
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 5)
					return true;

				try {
					RoomServerStatusProtocolDriver status = (RoomServerStatusProtocolDriver) driver;

					String address = status.supplementServerAddress(tokens[0]);
					String title = tokens[1];
					int maxPlayers = Integer.parseInt(tokens[2]);
					boolean hasPassword = "Y".equals(tokens[3]);
					String description = tokens[4];

					PlayRoom room = status.playRooms.get(address);
					if (room == null)
						return true;

					room.setTitle(title);
					room.setMaxPlayers(maxPlayers);
					room.setHasPassword(hasPassword);
					room.setDescription(description);

					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.SearchStatus.NOTIFY_ROOM_UPDATED);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(address);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(title);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(maxPlayers);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(hasPassword ? "Y" : "N");
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(description);

					sendRoomServerNotifyToSearchServer(sb.toString());
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		roomServerHandlers.put(ProtocolConstants.RoomStatus.NOTIFY_ROOM_DELETED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomServerStatusProtocolDriver status = (RoomServerStatusProtocolDriver) driver;

				// NRD hostname:port:master
				String address = status.supplementServerAddress(argument);

				status.playRooms.remove(address);

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.SearchStatus.NOTIFY_ROOM_DELETED);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(address);

				sendRoomServerNotifyToSearchServer(sb.toString());
				return true;
			}
		});
		roomServerHandlers.put(ProtocolConstants.RoomStatus.NOTIFY_ROOM_PLAYER_COUNT_CHANGED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomServerStatusProtocolDriver status = (RoomServerStatusProtocolDriver) driver;

				// NRPC hostname:port:master playerCount
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return true;

				String address = status.supplementServerAddress(tokens[0]);

				PlayRoom room = status.playRooms.get(address);
				if (room == null)
					return true;

				try {
					int playerCount = Integer.parseInt(tokens[1]);
					room.setCurrentPlayers(playerCount);

					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.SearchStatus.NOTIFY_ROOM_PLAYER_COUNT_CHANGED);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(address);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(playerCount);

					sendRoomServerNotifyToSearchServer(sb.toString());
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
	}
}
