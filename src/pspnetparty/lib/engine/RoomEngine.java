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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pspnetparty.lib.CountDownSynchronizer;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IServerNetwork;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.socket.AsyncTcpClient;
import pspnetparty.lib.socket.AsyncUdpClient;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.IProtocolMessageHandler;
import pspnetparty.lib.socket.IServer;
import pspnetparty.lib.socket.IServerListener;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.PacketData;
import pspnetparty.lib.socket.TextProtocolDriver;

public class RoomEngine {

	private static final long ROOM_AGE_DELAY = 24 * 60 * 60 * 1000;
	private static final long IDLE_TUNNEL_DELAY = 3 * 60 * 60 * 1000;
	private static final long NOTIFY_DELAY = 15 * 60 * 1000;

	private ILogger logger;
	private AsyncTcpClient tcpClient;
	private AsyncUdpClient udpClient;

	private ConcurrentHashMap<String, Room> masterNameRoomMap;
	private ConcurrentHashMap<InetSocketAddress, TunnelProtocolDriver> notYetLinkedTunnels;

	private int maxRooms = 10;
	private File loginMessageFile;

	private IServerNetwork serverNetwork;
	private ConcurrentHashMap<RoomStatusProtocolDriver, Object> portalConnections;
	private boolean isAcceptingPortal = true;

	private HashSet<PlayRoom> myRoomEntries = new HashSet<PlayRoom>();

	private CountDownSynchronizer countDownSynchronizer;
	private ExecutorService executorService = Executors.newCachedThreadPool();

	public RoomEngine(IServer roomServer, IServer tunnelServer, ILogger logger, IServerNetwork net) throws IOException {
		this.logger = logger;

		masterNameRoomMap = new ConcurrentHashMap<String, Room>(20, 0.75f, 1);
		notYetLinkedTunnels = new ConcurrentHashMap<InetSocketAddress, TunnelProtocolDriver>(30, 0.75f, 2);
		portalConnections = new ConcurrentHashMap<RoomStatusProtocolDriver, Object>(20, 0.75f, 2);

		tcpClient = new AsyncTcpClient(logger, 4000, 3000);
		udpClient = new AsyncUdpClient(logger);

		serverNetwork = net;

		countDownSynchronizer = new CountDownSynchronizer(2);

		IServerListener listener = new IServerListener() {
			@Override
			public void log(String message) {
				RoomEngine.this.logger.log(message);
			}

			@Override
			public void serverStartupFinished() {
				if (countDownSynchronizer.countDown() == 0) {
					initBackgroudThread();
					countDownSynchronizer = new CountDownSynchronizer(2);
				}
			}

			@Override
			public void serverShutdownFinished() {
				if (countDownSynchronizer.countDown() == 0) {
					shutdown();
				}
			}
		};

		roomServer.addServerListener(listener);
		roomServer.addProtocol(new RoomProtocol());
		roomServer.addProtocol(new RoomStatusProtocol());
		roomServer.addProtocol(new MyRoomProtocol());

		TunnelProtocol tunnelProtocol = new TunnelProtocol();
		roomServer.addProtocol(tunnelProtocol);

		tunnelServer.addServerListener(listener);
		tunnelServer.addProtocol(tunnelProtocol);
	}

	private void initBackgroudThread() {
		Thread cleanupThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!executorService.isShutdown()) {
						long deadline, notifyDeadline;

						deadline = System.currentTimeMillis() - ROOM_AGE_DELAY;
						notifyDeadline = deadline + NOTIFY_DELAY;
						for (Entry<String, Room> e : masterNameRoomMap.entrySet()) {
							Room room = e.getValue();
							if (room.createdTime < deadline) {
								destroyRoom(e.getKey());
							} else if (!room.tooOldNotified && room.createdTime < notifyDeadline) {
								room.tooOldNotified = true;
								for (Entry<String, RoomProtocolDriver> e2 : room.playersByName.entrySet()) {
									RoomProtocolDriver player = e2.getValue();
									player.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_AGE_OLD);
								}
							}
						}

						deadline = System.currentTimeMillis() - IDLE_TUNNEL_DELAY;
						notifyDeadline = deadline + NOTIFY_DELAY;
						for (Entry<String, Room> e : masterNameRoomMap.entrySet()) {
							Room room = e.getValue();
							for (Entry<String, RoomProtocolDriver> e2 : room.playersByName.entrySet()) {
								RoomProtocolDriver player = e2.getValue();
								TunnelProtocolDriver tunnel = player.tunnel;
								if (tunnel == null)
									continue;

								if (tunnel.lastTunnelTime < deadline) {
									player.getConnection().disconnect();
								} else if (tunnel.lastTunnelTime < notifyDeadline) {
									if (tunnel.inactiveNotified)
										continue;
									tunnel.inactiveNotified = true;
									player.getConnection().send(ProtocolConstants.Room.NOTIFY_TUNNEL_COMMUNICATION_IDLE);
								} else {
									tunnel.inactiveNotified = false;
								}
							}
						}

						Thread.sleep(60000);
					}
				} catch (InterruptedException e) {
				}
			}
		}, RoomEngine.class.getName() + " Cleanup");
		cleanupThread.setDaemon(true);
		cleanupThread.start();
	}

	private void shutdown() {
		try {
			executorService.shutdown();
		} catch (RuntimeException e) {
			logger.log(Utility.stackTraceToString(e));
		}
	}

	public int getRoomCount() {
		return masterNameRoomMap.size();
	}

	public int getMaxRooms() {
		return maxRooms;
	}

	public void setMaxRooms(int maxRooms) {
		if (maxRooms < 0)
			return;

		this.maxRooms = maxRooms;

		StringBuilder sb = new StringBuilder();
		appendServerStatus(sb);

		String notify = sb.toString();
		for (RoomStatusProtocolDriver client : portalConnections.keySet()) {
			client.getConnection().send(notify);
		}
	}

	public void setLoginMessageFile(String loginMessageFile) {
		if (Utility.isEmpty(loginMessageFile)) {
			this.loginMessageFile = null;
		} else {
			this.loginMessageFile = new File(loginMessageFile);
		}
	}

	public Set<InetSocketAddress> getPortalAddresses() {
		HashSet<InetSocketAddress> addresses = new HashSet<InetSocketAddress>();
		for (RoomStatusProtocolDriver driver : portalConnections.keySet()) {
			addresses.add(driver.getConnection().getRemoteAddress());
		}
		return addresses;
	}

	public boolean isAcceptingPortal() {
		return isAcceptingPortal;
	}

	public void setAcceptingPortal(boolean isAcceptingPortal) {
		if (!isAcceptingPortal) {
			for (RoomStatusProtocolDriver driver : portalConnections.keySet()) {
				driver.getConnection().disconnect();
			}
			portalConnections.clear();
		}

		this.isAcceptingPortal = isAcceptingPortal;
	}

	public String allRoomsToString() {
		StringBuilder sb = new StringBuilder();
		for (Entry<String, Room> entry : masterNameRoomMap.entrySet()) {
			Room room = entry.getValue();
			sb.append(entry.getKey());
			sb.append('\t').append(room.title);
			sb.append('\t').append(room.playersByName.size()).append('/').append(room.maxPlayers);
			sb.append('\t').append(room.password);
			sb.append(AppConstants.NEW_LINE);
		}
		return sb.toString();
	}

	public void notifyAllPlayers(String message) {
		final String notify = ProtocolConstants.Room.NOTIFY_FROM_ADMIN + TextProtocolDriver.ARGUMENT_SEPARATOR + message;

		for (Entry<String, Room> entry : masterNameRoomMap.entrySet()) {
			Room room = entry.getValue();
			for (Entry<String, RoomProtocolDriver> entry2 : room.playersByName.entrySet()) {
				RoomProtocolDriver p = entry2.getValue();
				p.getConnection().send(notify);
			}
		}
	}

	private void appendServerStatus(StringBuilder sb) {
		sb.append(ProtocolConstants.SERVER_STATUS);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(masterNameRoomMap.size());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(maxRooms);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(myRoomEntries.size());
	}

	public boolean destroyRoom(String masterName) {
		Room room = masterNameRoomMap.remove(masterName);
		if (room == null)
			return false;

		for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
			RoomProtocolDriver p = entry.getValue();
			p.room = null;
			p.name = "";
			p.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_DELETED);
			p.getConnection().disconnect();
		}

		StringBuilder sb = new StringBuilder();
		sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_DELETED);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(":").append(masterName);

		sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
		appendServerStatus(sb);

		String notify = sb.toString();
		for (RoomStatusProtocolDriver client : portalConnections.keySet()) {
			client.getConnection().send(notify);
		}
		return true;
	}

	public void hirakeGoma(String masterName) {
		Room room = masterNameRoomMap.get(masterName);
		if (room == null)
			return;

		room.maxPlayers++;
	}

	private static class Room {
		private ConcurrentSkipListMap<String, RoomProtocolDriver> playersByName;
		private HashMap<String, TunnelProtocolDriver> tunnelsByMacAddress;

		private RoomProtocolDriver roomMaster;

		private int maxPlayers = 4;
		private String title;
		private String password = "";
		private String description = "";
		private String remarks = "";

		private long createdTime;
		private boolean tooOldNotified = false;

		private boolean isMacAdressBlackListEnabled = false;
		private HashSet<String> macAddressWhiteList = new HashSet<String>();
		private boolean isMacAdressWhiteListEnabled = false;
		private HashSet<String> macAddressBlackList = new HashSet<String>();

		private Room() {
			playersByName = new ConcurrentSkipListMap<String, RoomProtocolDriver>();
			tunnelsByMacAddress = new HashMap<String, TunnelProtocolDriver>();
			createdTime = System.currentTimeMillis();
		}

		private void appendRoomInfoForParticipants(StringBuilder sb) {
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(roomMaster.name);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(maxPlayers);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(title);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(password);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(createdTime);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(description);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(remarks);
		}

		private void appendNotifyUserList(StringBuilder sb) {
			sb.append(ProtocolConstants.Room.NOTIFY_USER_LIST);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(roomMaster.name);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(roomMaster.ssid);
			for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
				RoomProtocolDriver client = entry.getValue();
				if (client == roomMaster)
					continue;

				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(client.name);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(client.ssid);
			}
		}

		private void appendNotifyRoomCreated(StringBuilder sb) {
			sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_CREATED);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(roomMaster != null ? roomMaster.name : "");
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(title);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(playersByName.size());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(maxPlayers);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(!Utility.isEmpty(password) ? "Y" : "N");
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(createdTime);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(description);
		}

		private boolean testWhiteListBlackList(String mac) {
			if (isMacAdressWhiteListEnabled && !macAddressWhiteList.isEmpty() && !macAddressWhiteList.contains(mac))
				return true;
			else if (isMacAdressBlackListEnabled && macAddressBlackList.contains(mac))
				return true;
			return false;
		}

		private boolean testWhiteListBlackList(String mac1, String mac2) {
			if (isMacAdressWhiteListEnabled && !macAddressWhiteList.isEmpty() && !macAddressWhiteList.contains(mac1)
					&& !macAddressWhiteList.contains(mac2))
				return true;
			else if (isMacAdressBlackListEnabled && (macAddressBlackList.contains(mac1) || macAddressBlackList.contains(mac2)))
				return true;
			return false;
		}
	}

	private void appendLoginMessage(StringBuilder sb) {
		String loginMessage = Utility.getFileContent(loginMessageFile);
		if (!Utility.isEmpty(loginMessage)) {
			sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
			sb.append(ProtocolConstants.Room.NOTIFY_FROM_ADMIN);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(loginMessage);
		}
	}

	private class RoomProtocol implements IProtocol {
		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_ROOM;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			RoomProtocolDriver client = new RoomProtocolDriver(connection);
			return client;
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}
	}

	private class RoomProtocolDriver extends TextProtocolDriver {
		private String name;
		private Room room;
		private TunnelProtocolDriver tunnel;
		private String ssid = "";

		private RoomProtocolDriver(ISocketConnection connection) {
			super(connection, loginHandlers);
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public void connectionDisconnected() {
			if (tunnel != null) {
				tunnel = null;
			}

			Room room = this.room;
			if (room == null)
				return;

			room.playersByName.remove(name);

			if (this == room.roomMaster) {
				masterNameRoomMap.remove(name);
				RoomProtocolDriver newRoomMaster = null;
				for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
					String playerName = entry.getKey();
					if (masterNameRoomMap.putIfAbsent(playerName, room) == null) {
						newRoomMaster = entry.getValue();
						room.roomMaster = newRoomMaster;
						break;
					}
				}
				if (newRoomMaster == null) {
					for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
						RoomProtocolDriver p = entry.getValue();

						p.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_DELETED);
						p.getConnection().disconnect();
					}
					room.playersByName.clear();

					StringBuilder sb = new StringBuilder();

					sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_DELETED);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(":").append(name);

					sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
					appendServerStatus(sb);

					String notify = sb.toString();
					for (RoomStatusProtocolDriver portal : portalConnections.keySet()) {
						portal.getConnection().send(notify);
					}
				} else {
					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.Room.NOTIFY_USER_EXITED);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(name);
					sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
					sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
					room.appendRoomInfoForParticipants(sb);

					final String notify = sb.toString();
					for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
						RoomProtocolDriver p = entry.getValue();
						p.getConnection().send(notify);
					}

					sb.delete(0, sb.length());

					sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_DELETED);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(":").append(name);

					sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
					room.appendNotifyRoomCreated(sb);

					String portalNotify = sb.toString();
					for (RoomStatusProtocolDriver portal : portalConnections.keySet()) {
						portal.getConnection().send(portalNotify);
					}

					room.macAddressWhiteList.clear();
					room.macAddressBlackList.clear();
					room.isMacAdressWhiteListEnabled = false;
					room.isMacAdressBlackListEnabled = false;
				}
			} else {
				final String notify = ProtocolConstants.Room.NOTIFY_USER_EXITED + TextProtocolDriver.ARGUMENT_SEPARATOR + name;
				for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
					RoomProtocolDriver p = entry.getValue();
					p.getConnection().send(notify);
				}

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_PLAYER_COUNT_CHANGED);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(":").append(room.roomMaster.name);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(room.playersByName.size());

				String portalNotify = sb.toString();
				for (RoomStatusProtocolDriver client : portalConnections.keySet()) {
					client.getConnection().send(portalNotify);
				}
			}
		}

		@Override
		public void errorProtocolNumber(String number) {
		}
	}

	private final HashMap<String, IProtocolMessageHandler> loginHandlers;
	private final HashMap<String, IProtocolMessageHandler> sessionHandlers;
	{
		loginHandlers = new HashMap<String, IProtocolMessageHandler>();
		loginHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_CREATE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				// RC masterName maxPlayers title password description remarks
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 6) {
					player.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
					return false;
				}

				String password = tokens[3];

				String name = tokens[0];
				if (!Utility.isValidUserName(name)) {
					player.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
					return false;
				}

				int maxPlayers;
				try {
					maxPlayers = Integer.parseInt(tokens[1]);
					if (maxPlayers < 2 || maxPlayers > ProtocolConstants.Room.MAX_ROOM_PLAYERS) {
						player.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
						return false;
					}
				} catch (NumberFormatException e) {
					player.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
					return false;
				}

				String title = tokens[2];
				if (title.length() == 0) {
					player.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
					return false;
				}

				Room newRoom = null;
				String errorMsg = null;
				if (masterNameRoomMap.size() >= maxRooms) {
					errorMsg = ProtocolConstants.Room.ERROR_ROOM_CREATE_BEYOND_LIMIT;
				} else if (masterNameRoomMap.containsKey(name)) {
					errorMsg = ProtocolConstants.Room.ERROR_ROOM_CREATE_DUPLICATED_NAME;
				} else {
					newRoom = new Room();
					if (masterNameRoomMap.putIfAbsent(name, newRoom) != null) {
						errorMsg = ProtocolConstants.Room.ERROR_ROOM_CREATE_DUPLICATED_NAME;
					}
				}
				if (errorMsg != null) {
					player.getConnection().send(errorMsg);
					return false;
				}

				newRoom.playersByName.put(name, player);

				newRoom.roomMaster = player;
				newRoom.title = title;
				newRoom.maxPlayers = maxPlayers;
				newRoom.password = password;
				newRoom.description = tokens[4];
				newRoom.remarks = tokens[5];

				player.room = newRoom;

				player.name = name;
				player.setMessageHandlers(sessionHandlers);

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.COMMAND_ROOM_CREATE);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(newRoom.createdTime);
				sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);

				appendLoginMessage(sb);

				player.getConnection().send(sb.toString());

				sb.delete(0, sb.length());
				newRoom.appendNotifyRoomCreated(sb);

				sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
				appendServerStatus(sb);

				String portalNotify = sb.toString();
				for (RoomStatusProtocolDriver stat : portalConnections.keySet()) {
					stat.getConnection().send(portalNotify);
				}

				return true;
			}
		});
		loginHandlers.put(ProtocolConstants.Room.COMMAND_LOGIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				// LI loginName "masterName" password
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);

				if (tokens.length < 2)
					return false;

				String name = tokens[0];
				if (!Utility.isValidUserName(name)) {
					return false;
				}

				String masterName = tokens[1];

				if (masterName.equals(name)) {
					player.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}

				Room room = masterNameRoomMap.get(masterName);
				if (room == null) {
					player.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_ROOM_NOT_EXIST);
					return false;
				}

				String sentPassword = tokens.length == 3 ? tokens[2] : null;
				if (!Utility.isEmpty(room.password)) {
					if (sentPassword == null) {
						player.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_PASSWORD_REQUIRED);
						return true;
					}
					if (!room.password.equals(sentPassword)) {
						player.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_PASSWORD_FAIL);
						return true;
					}
				}

				if (room.playersByName.size() >= room.maxPlayers) {
					// 最大人数を超えたので接続を拒否します
					player.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_BEYOND_CAPACITY);
					return false;
				}

				if (room.playersByName.putIfAbsent(name, player) != null) {
					// 同名のユーザーが存在するので接続を拒否します
					player.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}

				player.setMessageHandlers(sessionHandlers);
				player.name = name;

				player.room = room;

				final String notify = ProtocolConstants.Room.NOTIFY_USER_ENTERED + TextProtocolDriver.ARGUMENT_SEPARATOR + name;
				for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
					RoomProtocolDriver p = entry.getValue();
					if (p != player)
						p.getConnection().send(notify);
				}

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Room.COMMAND_LOGIN);
				room.appendRoomInfoForParticipants(sb);

				sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
				room.appendNotifyUserList(sb);

				appendLoginMessage(sb);

				player.getConnection().send(sb.toString());

				sb.delete(0, sb.length());
				sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_PLAYER_COUNT_CHANGED);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(":").append(masterName);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(room.playersByName.size());

				String portalNotify = sb.toString();
				for (RoomStatusProtocolDriver stat : portalConnections.keySet()) {
					stat.getConnection().send(portalNotify);
				}

				return true;
			}
		});
		loginHandlers.put(ProtocolConstants.Room.COMMAND_LOGOUT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				return false;
			}
		});

		sessionHandlers = new HashMap<String, IProtocolMessageHandler>();
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_LOGOUT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				return false;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_CHAT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				Room room = player.room;
				if (room == null)
					return false;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.COMMAND_CHAT);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(player.name);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(argument);

				String message = sb.toString();
				for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
					RoomProtocolDriver p = entry.getValue();
					p.getConnection().send(message);
				}
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_PING, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver state = (RoomProtocolDriver) driver;

				state.getConnection().send(ProtocolConstants.Room.COMMAND_PINGBACK + TextProtocolDriver.ARGUMENT_SEPARATOR + argument);
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_PING, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				Room room = player.room;
				if (room == null)
					return false;

				try {
					Integer.parseInt(argument);
					final String message = ProtocolConstants.Room.COMMAND_INFORM_PING + TextProtocolDriver.ARGUMENT_SEPARATOR + player.name
							+ TextProtocolDriver.ARGUMENT_SEPARATOR + argument;
					for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
						RoomProtocolDriver p = entry.getValue();
						if (p != player)
							p.getConnection().send(message);
					}
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_PORT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				Room room = player.room;
				if (room == null)
					return false;

				try {
					int port = Integer.parseInt(argument);
					InetSocketAddress remoteEP = new InetSocketAddress(player.getConnection().getRemoteAddress().getAddress(), port);

					TunnelProtocolDriver tunnel = notYetLinkedTunnels.remove(remoteEP);
					player.tunnel = tunnel;
					if (tunnel != null) {
						player.getConnection().send(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_PORT);
						tunnel.room = room;
						tunnel.player = player;
						tunnel.lastTunnelTime = System.currentTimeMillis();
					}
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_UPDATE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				Room room = player.room;
				if (room == null || player != room.roomMaster)
					return false;

				// RU maxPlayers title password description remarks
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 5) {
					player.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
					return true;
				}
				String password = tokens[2];

				room.maxPlayers = Math.min(Integer.parseInt(tokens[0]), ProtocolConstants.Room.MAX_ROOM_PLAYERS);
				room.title = tokens[1];
				room.password = password;
				room.description = tokens[3];
				room.remarks = tokens[4];

				player.getConnection().send(ProtocolConstants.Room.COMMAND_ROOM_UPDATE);

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
				room.appendRoomInfoForParticipants(sb);

				final String notify = sb.toString();

				for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
					RoomProtocolDriver p = entry.getValue();
					if (p != player)
						p.getConnection().send(notify);
				}

				sb.delete(0, sb.length());

				sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_UPDATED);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(":").append(player.name);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(room.title);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(room.maxPlayers);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(!Utility.isEmpty(password) ? "Y" : "N");
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(room.description);

				String portalNotify = sb.toString();
				for (RoomStatusProtocolDriver stat : portalConnections.keySet()) {
					stat.getConnection().send(portalNotify);
				}

				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_KICK_PLAYER, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				Room room = player.room;
				if (room == null || player != room.roomMaster)
					return false;

				String name = argument;
				if (Utility.equals(name, player.name))
					return true;

				RoomProtocolDriver kickedPlayer = null;
				synchronized (room.playersByName) {
					kickedPlayer = room.playersByName.remove(name);
					if (kickedPlayer == null)
						return true;
				}

				String notify = ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED + TextProtocolDriver.ARGUMENT_SEPARATOR + name;

				for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
					RoomProtocolDriver p = entry.getValue();
					p.getConnection().send(notify);
				}

				kickedPlayer.getConnection().send(
						ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED + TextProtocolDriver.ARGUMENT_SEPARATOR + name);
				kickedPlayer.getConnection().disconnect();

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_PLAYER_COUNT_CHANGED);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(":").append(name);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(room.playersByName.size());

				String portalNotify = sb.toString();
				for (RoomStatusProtocolDriver stat : portalConnections.keySet()) {
					stat.getConnection().send(portalNotify);
				}

				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_MASTER_TRANSFER, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				Room room = player.room;
				if (room == null || player != room.roomMaster)
					return false;

				String name = argument;
				if (Utility.equals(name, player.name))
					return true;

				RoomProtocolDriver newRoomMaster = room.playersByName.get(name);
				if (newRoomMaster == null)
					return true;

				if (masterNameRoomMap.putIfAbsent(name, room) != null) {
					player.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_TRANSFER_DUPLICATED_NAME);
					return true;
				}

				masterNameRoomMap.remove(player.name);
				room.roomMaster = newRoomMaster;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
				room.appendRoomInfoForParticipants(sb);

				String notify = sb.toString();
				for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
					RoomProtocolDriver p = entry.getValue();
					p.getConnection().send(notify);
				}

				sb.delete(0, sb.length());

				sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_DELETED);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(":").append(player.name);

				sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
				room.appendNotifyRoomCreated(sb);

				String portalNotify = sb.toString();
				for (RoomStatusProtocolDriver stat : portalConnections.keySet()) {
					stat.getConnection().send(portalNotify);
				}

				room.macAddressWhiteList.clear();
				room.macAddressBlackList.clear();
				room.isMacAdressWhiteListEnabled = false;
				room.isMacAdressBlackListEnabled = false;

				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_DELETE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				Room room = player.room;
				if (room == null || player != room.roomMaster)
					return false;

				destroyRoom(player.name);

				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String macAddress) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				Room room = player.room;
				if (room == null)
					return false;

				TunnelProtocolDriver tunnelState = room.tunnelsByMacAddress.get(macAddress);
				if (tunnelState == null)
					return true;
				try {
					String name = tunnelState.player.name;
					if (Utility.isEmpty(name))
						return true;

					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(macAddress);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(name);

					player.getConnection().send(sb.toString());
				} catch (NullPointerException e) {
				}
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_SSID, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				player.ssid = argument;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.NOTIFY_SSID_CHANGED);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(player.name);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(player.ssid);

				String notify = sb.toString();
				for (Entry<String, RoomProtocolDriver> entry : player.room.playersByName.entrySet()) {
					RoomProtocolDriver p = entry.getValue();
					if (p != player)
						p.getConnection().send(notify);
				}

				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_WHITELIST_ENABLE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;
				Room room = player.room;
				if (room == null || player != room.roomMaster)
					return false;

				room.isMacAdressWhiteListEnabled = "Y".equals(argument);
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_WHITELIST_ADD, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String args) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;
				Room room = player.room;
				if (room == null || player != room.roomMaster)
					return false;

				for (String macAddress : args.split(TextProtocolDriver.ARGUMENT_SEPARATOR))
					room.macAddressWhiteList.add(macAddress);
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_WHITELIST_REMOVE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String macAddress) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;
				Room room = player.room;
				if (room == null || player != room.roomMaster)
					return false;

				room.macAddressWhiteList.remove(macAddress);
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_BLACKLIST_ENABLE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;
				Room room = player.room;
				if (room == null || player != room.roomMaster)
					return false;

				room.isMacAdressBlackListEnabled = "Y".equals(argument);
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_BLACKLIST_ADD, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String args) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;
				Room room = player.room;
				if (room == null || player != room.roomMaster)
					return false;

				for (String macAddress : args.split(TextProtocolDriver.ARGUMENT_SEPARATOR))
					room.macAddressBlackList.add(macAddress);
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_BLACKLIST_REMOVE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String macAddress) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;
				Room room = player.room;
				if (room == null || player != room.roomMaster)
					return false;

				room.macAddressBlackList.remove(macAddress);
				return true;
			}
		});
	}

	private class TunnelProtocol implements IProtocol {
		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_TUNNEL;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			TunnelProtocolDriver client = new TunnelProtocolDriver();
			client.connection = connection;
			notYetLinkedTunnels.put(connection.getRemoteAddress(), client);
			return client;
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}
	}

	private class TunnelProtocolDriver implements IProtocolDriver {
		private ISocketConnection connection;
		private Room room;
		private RoomProtocolDriver player;
		private long lastTunnelTime;
		private boolean inactiveNotified = false;

		private void updateTunnelTime() {
			lastTunnelTime = System.currentTimeMillis();
		}

		@Override
		public ISocketConnection getConnection() {
			return connection;
		}

		@Override
		public boolean process(PacketData data) {
			ByteBuffer packet = data.getBuffer();

			if (!Utility.isPspPacket(packet)) {
				InetSocketAddress remoteAddress = connection.getRemoteAddress();
				if (notYetLinkedTunnels.containsKey(remoteAddress)) {
					connection.send(Integer.toString(remoteAddress.getPort()));
				}
				return true;
			}

			Room room = this.room;
			if (room == null)
				return true;

			RoomProtocolDriver srcPlayer = player;
			if (srcPlayer == null)
				return true;
			boolean srcPlayerSsidIsNotEmpty = !Utility.isEmpty(srcPlayer.ssid);
			updateTunnelTime();

			String destMac = Utility.macAddressToString(packet, 0, false);
			String srcMac = Utility.macAddressToString(packet, 6, false);

			room.tunnelsByMacAddress.put(srcMac, this);
			if (player == room.roomMaster)
				room.macAddressWhiteList.add(srcMac);

			if (Utility.isMacBroadCastAddress(destMac)) {
				if (room.testWhiteListBlackList(srcMac))
					return true;

				for (Entry<String, RoomProtocolDriver> entry : room.playersByName.entrySet()) {
					RoomProtocolDriver destPlayer = entry.getValue();
					TunnelProtocolDriver destTunnel = destPlayer.tunnel;
					if (destTunnel == null || destTunnel == this)
						continue;
					if (srcPlayerSsidIsNotEmpty && !Utility.isEmpty(destPlayer.ssid))
						if (!srcPlayer.ssid.equals(destPlayer.ssid))
							continue;

					packet.position(0);
					destTunnel.connection.send(packet);
				}
			} else {
				if (room.testWhiteListBlackList(srcMac, destMac))
					return true;

				TunnelProtocolDriver destTunnel = room.tunnelsByMacAddress.get(destMac);
				if (destTunnel == null)
					return true;
				RoomProtocolDriver destPlayer = destTunnel.player;
				if (destPlayer == null)
					return true;
				if (srcPlayerSsidIsNotEmpty && !Utility.isEmpty(destPlayer.ssid))
					if (!srcPlayer.ssid.equals(destPlayer.ssid))
						return true;

				destTunnel.connection.send(packet);
			}

			return true;
		}

		@Override
		public void connectionDisconnected() {
			try {
				player.tunnel = null;
			} catch (NullPointerException e) {
			}
			room = null;
			player = null;

			InetSocketAddress address = connection.getRemoteAddress();
			if (address != null)
				notYetLinkedTunnels.remove(address);
		}

		@Override
		public void errorProtocolNumber(String number) {
		}
	}

	private void appendMyRoomCreated(PlayRoom room, StringBuilder sb) {
		sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_CREATED);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(room.getServerAddress());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(room.getMasterName());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(room.getTitle());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(room.getCurrentPlayers());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(room.getMaxPlayers());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(room.hasPassword() ? "Y" : "N");
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(room.getCreatedTime());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(room.getDescription());
	}

	private class RoomStatusProtocol implements IProtocol {
		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_ROOM_STATUS;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			if (!isAcceptingPortal)
				return null;

			serverNetwork.reload();
			if (!serverNetwork.isValidPortalServer(connection.getRemoteAddress().getAddress()))
				return null;

			RoomStatusProtocolDriver driver = new RoomStatusProtocolDriver(connection);
			portalConnections.put(driver, this);

			logger.log("ポータルから接続されました: " + driver.address);

			StringBuilder sb = new StringBuilder();
			appendServerStatus(sb);

			if (!masterNameRoomMap.isEmpty()) {
				for (Room room : masterNameRoomMap.values()) {
					sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
					room.appendNotifyRoomCreated(sb);
				}
			}
			if (!myRoomEntries.isEmpty()) {
				for (PlayRoom room : myRoomEntries) {
					sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
					appendMyRoomCreated(room, sb);
				}
			}

			connection.send(sb.toString());

			return driver;
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}
	}

	private class RoomStatusProtocolDriver extends TextProtocolDriver {
		private String address;

		private RoomStatusProtocolDriver(ISocketConnection connection) {
			super(connection, portalHandlers);

			address = Utility.socketAddressToStringByIP(getConnection().getRemoteAddress());
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public void connectionDisconnected() {
			portalConnections.remove(this);
			logger.log("ポータルから切断されました: " + address);
		}

		@Override
		public void errorProtocolNumber(String number) {
		}
	}

	private final HashMap<String, IProtocolMessageHandler> portalHandlers;
	{
		portalHandlers = new HashMap<String, IProtocolMessageHandler>();
	}

	private class MyRoomProtocol implements IProtocol {
		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_MY_ROOM_ENTRY;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			MyRoomProtocolDriver driver = new MyRoomProtocolDriver(connection);
			return driver;
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}
	}

	private class MyRoomProtocolDriver extends TextProtocolDriver {

		private PlayRoom myRoom;

		private MyRoomProtocolDriver(ISocketConnection connection) {
			super(connection, myRoomEntryHandlers);
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public void connectionDisconnected() {
			if (myRoom != null) {
				PlayRoom room = myRoom;
				myRoom = null;
				myRoomEntries.remove(room);

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_DELETED);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(room.getRoomAddress());

				sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
				appendServerStatus(sb);

				String notify = sb.toString();
				for (RoomStatusProtocolDriver driver : portalConnections.keySet()) {
					driver.getConnection().send(notify);
				}
			}
		}

		@Override
		public void errorProtocolNumber(String number) {
		}
	}

	private final HashMap<String, IProtocolMessageHandler> myRoomEntryHandlers;
	private final HashMap<String, IProtocolMessageHandler> myRoomHandlers;
	{
		myRoomEntryHandlers = new HashMap<String, IProtocolMessageHandler>();
		myRoomEntryHandlers.put(ProtocolConstants.MyRoom.COMMAND_ENTRY, new IProtocolMessageHandler() {
			private String makeHostName(String s, IProtocolDriver driver) {
				return s.length() > 0 ? s : driver.getConnection().getRemoteAddress().getAddress().getHostAddress();
			}

			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				MyRoomProtocolDriver myRoomClient = (MyRoomProtocolDriver) driver;

				// R authCode hostName:port masterName title currentPlayers
				// maxPlayers hasPassword created description
				final String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 9)
					return false;

				try {
					String authCode = tokens[0];

					String[] address = tokens[1].split(":");
					String hostname = makeHostName(address[0], myRoomClient);
					int port = Integer.parseInt(address[1]);

					String masterName = tokens[2];
					String title = tokens[3];

					int currentPlayers = Integer.parseInt(tokens[4]);
					int maxPlayers = Integer.parseInt(tokens[5]);
					boolean hasPassword = "Y".equals(tokens[6]);
					long created = Long.parseLong(tokens[7]);
					String description = tokens[8];

					PlayRoom room = new PlayRoom(hostname + ":" + port, masterName, title, hasPassword, currentPlayers, maxPlayers, created);
					room.setDescription(description);

					InetSocketAddress socketAddress = new InetSocketAddress(hostname, port);

					return checkMyRoomEntry(myRoomClient, socketAddress, room, authCode);
				} catch (NumberFormatException e) {
					return false;
				}
			}

			private boolean checkMyRoomEntry(final MyRoomProtocolDriver driver, final InetSocketAddress address, final PlayRoom room,
					final String authCode) {
				Runnable task = new Runnable() {
					@Override
					public void run() {
						IProtocol tunnelProtocol = new IProtocol() {
							@Override
							public void log(String message) {
								logger.log(message);
							}

							@Override
							public String getProtocol() {
								return ProtocolConstants.PROTOCOL_TUNNEL;
							}

							@Override
							public IProtocolDriver createDriver(final ISocketConnection connection) {
								return null;
							}
						};

						try {
							udpClient.connect(address, 5000, tunnelProtocol);
						} catch (IOException e) {
							driver.getConnection().send(ProtocolConstants.MyRoom.ERROR_UDP_PORT_NOT_OPEN);
							driver.getConnection().disconnect();
						}

						IProtocol roomProtocol = new IProtocol() {
							@Override
							public void log(String message) {
								logger.log(message);
							}

							@Override
							public String getProtocol() {
								return ProtocolConstants.PROTOCOL_ROOM;
							}

							@Override
							public IProtocolDriver createDriver(final ISocketConnection connection) {
								StringBuilder sb = new StringBuilder();

								sb.append(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE);
								sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
								sb.append(room.getMasterName());
								sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
								sb.append(authCode);

								connection.send(sb.toString());

								return new IProtocolDriver() {
									private boolean success = false;

									@Override
									public ISocketConnection getConnection() {
										return connection;
									}

									@Override
									public boolean process(PacketData data) {
										String message = data.getMessage();
										if (ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE.equals(message)) {
											success = true;
										}
										return false;
									}

									@Override
									public void connectionDisconnected() {
										if (success) {
											driver.myRoom = room;
											myRoomEntries.add(room);

											StringBuilder sb = new StringBuilder();

											appendMyRoomCreated(room, sb);

											sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
											appendServerStatus(sb);

											String portalNotify = sb.toString();
											for (RoomStatusProtocolDriver client : portalConnections.keySet()) {
												client.getConnection().send(portalNotify);
											}

											driver.getConnection().send(ProtocolConstants.MyRoom.COMMAND_ENTRY);
											driver.setMessageHandlers(myRoomHandlers);
										} else {
											driver.getConnection().send(ProtocolConstants.MyRoom.ERROR_INVALID_AUTH_CODE);
											driver.getConnection().disconnect();
										}
									}

									@Override
									public void errorProtocolNumber(String number) {
									}
								};
							}
						};

						try {
							tcpClient.connect(address, 10000, roomProtocol);
						} catch (IOException e) {
							driver.getConnection().send(ProtocolConstants.MyRoom.ERROR_TCP_PORT_NOT_OPEN);
							driver.getConnection().disconnect();
						}
					}
				};

				executorService.execute(task);

				return true;
			}
		});

		myRoomHandlers = new HashMap<String, IProtocolMessageHandler>();
		myRoomHandlers.put(ProtocolConstants.MyRoom.COMMAND_LOGOUT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				return false;
			}
		});
		myRoomHandlers.put(ProtocolConstants.MyRoom.COMMAND_UPDATE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				MyRoomProtocolDriver state = (MyRoomProtocolDriver) driver;

				if (state.myRoom == null)
					return false;

				// U title maxPlayers hasPassword description
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 4)
					return false;

				try {
					String title = tokens[0];
					int maxPlayers = Integer.parseInt(tokens[1]);
					boolean hasPassword = "Y".equals(tokens[2]);
					String description = tokens[3];

					PlayRoom room = state.myRoom;
					room.setTitle(title);
					room.setMaxPlayers(maxPlayers);
					room.setHasPassword(hasPassword);
					room.setDescription(description);

					StringBuilder sb = new StringBuilder();

					sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_UPDATED);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(room.getRoomAddress());
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(room.getTitle());
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(room.getMaxPlayers());
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(room.hasPassword() ? "Y" : "N");
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(room.getDescription());

					String portalNotify = sb.toString();
					for (RoomStatusProtocolDriver stat : portalConnections.keySet()) {
						stat.getConnection().send(portalNotify);
					}

					return true;
				} catch (NumberFormatException e) {
				}
				return false;
			}
		});
		myRoomHandlers.put(ProtocolConstants.MyRoom.COMMAND_UPDATE_PLAYER_COUNT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				MyRoomProtocolDriver state = (MyRoomProtocolDriver) driver;

				PlayRoom room = state.myRoom;
				if (room == null)
					return false;

				try { // C playerCount
					int playerCount = Integer.parseInt(argument);
					room.setCurrentPlayers(playerCount);

					StringBuilder sb = new StringBuilder();

					sb.append(ProtocolConstants.RoomStatus.NOTIFY_ROOM_PLAYER_COUNT_CHANGED);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(room.getRoomAddress());
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(playerCount);

					String portalNotify = sb.toString();
					for (RoomStatusProtocolDriver stat : portalConnections.keySet()) {
						stat.getConnection().send(portalNotify);
					}

					return true;
				} catch (NumberFormatException e) {
				}
				return false;
			}
		});
	}
}
