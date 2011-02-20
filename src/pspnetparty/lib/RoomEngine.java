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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.ProtocolConstants;

public class RoomEngine {

	private ConcurrentHashMap<String, Room> masterNameRoomMap;
	private ConcurrentHashMap<InetSocketAddress, TunnelState> notYetLinkedTunnels;
	private Room lobbyRoom;

	private IServer<PlayerState> roomServer;
	private RoomHandler roomHandler;

	private IServer<TunnelState> tunnelServer;
	private TunnelHandler tunnelHandler;

	private AsyncTcpClient tcpClient;
	private AsyncUdpClient udpClient;

	private int port = -1;
	private int maxRooms = 10;
	private boolean passwordAllowed = true;
	private File loginMessageFile;

	private ConcurrentHashMap<ISocketConnection, Object> watcherConnections;
	private HashSet<PlayRoom> myRoomEntries = new HashSet<PlayRoom>();

	private boolean isStarted = false;
	private ILogger logger;

	public RoomEngine(ILogger logger) {
		this.logger = logger;

		masterNameRoomMap = new ConcurrentHashMap<String, Room>(20, 0.75f, 1);
		notYetLinkedTunnels = new ConcurrentHashMap<InetSocketAddress, TunnelState>(30, 0.75f, 2);
		watcherConnections = new ConcurrentHashMap<ISocketConnection, Object>(20, 0.75f, 2);

		roomServer = new AsyncTcpServer<PlayerState>();
		roomHandler = new RoomHandler();

		tunnelServer = new AsyncUdpServer<TunnelState>();
		tunnelHandler = new TunnelHandler();

		tcpClient = new AsyncTcpClient(4000, 3000);
		udpClient = new AsyncUdpClient();
	}

	public void start(int port, int lobbyCapacity) throws IOException {
		if (isStarted)
			throw new IllegalStateException();
		logger.log("プロトコル: " + ProtocolConstants.PROTOCOL_NUMBER);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		roomServer.startListening(bindAddress, roomHandler);
		tunnelServer.startListening(bindAddress, tunnelHandler);

		if (lobbyCapacity > 0) {
			createLobby(lobbyCapacity);
		}

		this.port = port;
		isStarted = true;
	}

	public void stop() {
		if (!isStarted)
			return;

		roomServer.stopListening();
		tunnelServer.stopListening();
		this.port = -1;
		this.lobbyRoom = null;
	}

	public int getPort() {
		return port;
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
		for (ISocketConnection conn : watcherConnections.keySet()) {
			conn.send(notify);
		}
	}

	private void createLobby(int lobbyCapacity) {
		lobbyRoom = new Room();
		lobbyRoom.title = "ロビー";
		lobbyRoom.maxPlayers = lobbyCapacity;
		lobbyRoom.roomMaster = new PlayerState(null);
		lobbyRoom.roomMaster.name = "";
		masterNameRoomMap.put("", lobbyRoom);
	}

	public int getLobbyCapacity() {
		return lobbyRoom == null ? 0 : lobbyRoom.maxPlayers;
	}

	public void setLobbyCapacity(int lobbyCapacity) {
		if (lobbyCapacity < 1)
			return;

		if (lobbyRoom == null) {
			createLobby(lobbyCapacity);

			StringBuilder sb = new StringBuilder();
			appendServerStatus(sb);

			sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
			lobbyRoom.appendNotifyRoomCreated(sb);

			String notify = sb.toString();
			for (ISocketConnection conn : watcherConnections.keySet()) {
				conn.send(notify);
			}
		} else {
			lobbyRoom.maxPlayers = lobbyCapacity;

			StringBuilder sb = new StringBuilder();

			sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
			lobbyRoom.appendRoomInfoForParticipants(sb);

			final String notify = sb.toString();

			lobbyRoom.forEachPlayer(new IClientStateAction<PlayerState>() {
				@Override
				public void action(PlayerState p) {
					p.getConnection().send(notify);
				}
			});

			sb.delete(0, sb.length());

			sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(':').append(port).append(':');
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(lobbyRoom.title);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(lobbyRoom.maxPlayers);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(!Utility.isEmpty(lobbyRoom.password) ? "Y" : "N");
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(lobbyRoom.description);

			String watcherNotify = sb.toString();
			for (ISocketConnection conn : watcherConnections.keySet()) {
				conn.send(watcherNotify);
			}
		}
	}

	public int getLobbyUserCount() {
		return lobbyRoom == null ? 0 : lobbyRoom.playersByName.size();
	}

	public boolean isRoomPasswordAllowed() {
		return passwordAllowed;
	}

	public void setRoomPasswordAllowed(boolean passwordAllowed) {
		this.passwordAllowed = passwordAllowed;

		StringBuilder sb = new StringBuilder();
		appendServerStatus(sb);

		String notify = sb.toString();
		for (ISocketConnection conn : watcherConnections.keySet()) {
			conn.send(notify);
		}
	}

	public void setLoginMessageFile(String loginMessageFile) {
		if (Utility.isEmpty(loginMessageFile)) {
			this.loginMessageFile = null;
		} else {
			this.loginMessageFile = new File(loginMessageFile);
		}
	}

	@Override
	public String toString() {
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
		final String notify = ProtocolConstants.Room.NOTIFY_FROM_ADMIN + ProtocolConstants.ARGUMENT_SEPARATOR + message;
		IClientStateAction<PlayerState> action = new IClientStateAction<PlayerState>() {
			@Override
			public void action(PlayerState p) {
				p.connection.send(notify);
			}
		};
		for (Entry<String, Room> entry : masterNameRoomMap.entrySet()) {
			Room room = entry.getValue();
			room.forEachPlayer(action);
		}
	}

	private void appendServerStatus(StringBuilder sb) {
		sb.append(ProtocolConstants.Room.SERVER_STATUS);
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(masterNameRoomMap.size());
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(maxRooms);
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(passwordAllowed ? "Y" : "N");
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(myRoomEntries.size());
	}

	public boolean destroyRoom(String masterName) {
		Room room = masterNameRoomMap.remove(masterName);
		if (room == null)
			return false;

		room.forEachPlayer(new IClientStateAction<PlayerState>() {
			@Override
			public void action(PlayerState p) {
				p.room = null;
				p.name = "";
				p.connection.send(ProtocolConstants.Room.NOTIFY_ROOM_DELETED);
				p.connection.disconnect();
			}
		});

		StringBuilder sb = new StringBuilder();
		sb.append(ProtocolConstants.Room.NOTIFY_ROOM_DELETED);
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(':').append(port).append(':').append(masterName);

		sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
		appendServerStatus(sb);

		String notify = sb.toString();
		for (ISocketConnection conn : watcherConnections.keySet()) {
			conn.send(notify);
		}
		return true;
	}

	public void hirakeGoma(String masterName) {
		Room room = masterNameRoomMap.get(masterName);
		if (room == null)
			return;

		room.maxPlayers++;
	}

	private static class PlayerState implements IClientState {

		private ISocketConnection connection;

		private HashMap<String, IServerMessageHandler<PlayerState>> messageHandlers;
		private String name;
		private Room room;
		private TunnelState tunnelState;

		private PlayRoom myRoom;

		PlayerState(ISocketConnection conn) {
			connection = conn;
		}

		@Override
		public ISocketConnection getConnection() {
			return connection;
		}
	}

	private class Room {
		private ConcurrentSkipListMap<String, PlayerState> playersByName;
		private HashMap<String, TunnelState> tunnelsByMacAddress;

		private PlayerState roomMaster;

		private int maxPlayers = 4;
		private String title;
		private String description = "";
		private String password = "";

		private String roomMasterAuthCode;

		private Room() {
			playersByName = new ConcurrentSkipListMap<String, PlayerState>();
			tunnelsByMacAddress = new HashMap<String, TunnelState>();
		}

		private void forEachPlayer(IClientStateAction<PlayerState> action) {
			for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
				PlayerState state = entry.getValue();
				action.action(state);
			}
		}

		private void appendRoomInfoForParticipants(StringBuilder sb) {
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(roomMaster.name);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(maxPlayers);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(title);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(password);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(description);
		}

		private void appendNotifyUserList(StringBuilder sb) {
			sb.append(ProtocolConstants.Room.NOTIFY_USER_LIST);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR).append(roomMaster.name);
			for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
				PlayerState state = entry.getValue();
				if (state != roomMaster)
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR).append(state.name);
			}
		}

		private void appendNotifyRoomCreated(StringBuilder sb) {
			sb.append(ProtocolConstants.Room.NOTIFY_ROOM_CREATED);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(':').append(port);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(roomMaster != null ? roomMaster.name : "");
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(title);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(playersByName.size());
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(maxPlayers);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(!Utility.isEmpty(password) ? "Y" : "N");
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(description);
		}
	}

	private void appendLoginMessage(StringBuilder sb) {
		String loginMessage = Utility.getFileContent(loginMessageFile);
		if (!Utility.isEmpty(loginMessage)) {
			sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
			sb.append(ProtocolConstants.Room.NOTIFY_FROM_ADMIN);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(loginMessage);
		}
	}

	private class RoomHandler implements IAsyncServerHandler<PlayerState> {

		private HashMap<String, IServerMessageHandler<PlayerState>> protocolHandlers;
		private HashMap<String, IServerMessageHandler<PlayerState>> loginHandlers;
		private HashMap<String, IServerMessageHandler<PlayerState>> sessionHandlers;

		private HashMap<String, IServerMessageHandler<PlayerState>> myRoomEntryHandlers;
		private HashMap<String, IServerMessageHandler<PlayerState>> myRoomHandlers;

		private HashMap<String, IServerMessageHandler<PlayerState>> watcherHandler;

		RoomHandler() {
			protocolHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();
			protocolHandlers.put(ProtocolConstants.PROTOCOL_ROOM, new RoomProtocolHandler());
			protocolHandlers.put(ProtocolConstants.PROTOCOL_WATCHER, new WatcherProtocolHandler());
			protocolHandlers.put(ProtocolConstants.PROTOCOL_MY_ROOM, new MyRoomProtocolHandler());

			loginHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();
			loginHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_CREATE, new RoomCreateHandler());
			loginHandlers.put(ProtocolConstants.Room.COMMAND_LOGIN, new LoginHandler());
			loginHandlers.put(ProtocolConstants.Room.COMMAND_LOGOUT, new LogoutHandler());
			loginHandlers.put(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE, new ConfirmAuthCodeHandler());

			sessionHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_LOGOUT, new LogoutHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_CHAT, new ChatHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_PING, new PingHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_PING, new InformPingHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_UDP_PORT, new InformTunnelPortHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER, new MacAddressPlayerHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_UPDATE, new RoomUpdateHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_KICK_PLAYER, new RoomKickPlayerHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_ROOM_MASTER_TRANSFER, new RoomMasterTransferHandler());

			myRoomEntryHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();
			myRoomEntryHandlers.put(ProtocolConstants.MyRoom.COMMAND_ENTRY, new MyRoomEntryHandler());

			myRoomHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();
			myRoomHandlers.put(ProtocolConstants.MyRoom.COMMAND_LOGOUT, new LogoutHandler());
			myRoomHandlers.put(ProtocolConstants.MyRoom.COMMAND_UPDATE, new MyRoomUpdateHandler());
			myRoomHandlers.put(ProtocolConstants.MyRoom.COMMAND_UPDATE_PLAYER_COUNT, new MYRoomUpdatePlayerCountHandler());

			watcherHandler = new HashMap<String, IServerMessageHandler<PlayerState>>();
			watcherHandler.put(ProtocolConstants.Room.COMMAND_LOGOUT, new LogoutHandler());
		}

		@Override
		public void serverStartupFinished() {
		}

		@Override
		public void serverShutdownFinished() {
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public PlayerState createState(ISocketConnection connection) {
			PlayerState state = new PlayerState(connection);
			state.messageHandlers = protocolHandlers;
			return state;
		}

		@Override
		public void disposeState(PlayerState state) {
			if (state.tunnelState != null) {
				state.tunnelState = null;
			}
			watcherConnections.remove(state.connection);

			if (state.myRoom != null) {
				PlayRoom room = state.myRoom;
				state.myRoom = null;
				myRoomEntries.remove(room);

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_DELETED);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(room.getRoomAddress());

				sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
				appendServerStatus(sb);

				String notify = sb.toString();
				for (ISocketConnection conn : watcherConnections.keySet()) {
					conn.send(notify);
				}
				return;
			}

			Room room = state.room;
			if (room == null)
				return;

			String name = state.name;
			room.playersByName.remove(name);

			if (state == room.roomMaster) {
				masterNameRoomMap.remove(name);
				PlayerState newRoomMaster = null;
				for (Entry<String, PlayerState> entry : room.playersByName.entrySet()) {
					String playerName = entry.getKey();
					if (masterNameRoomMap.putIfAbsent(playerName, room) == null) {
						newRoomMaster = entry.getValue();
						room.roomMaster = newRoomMaster;
						break;
					}
				}
				if (newRoomMaster == null) {
					for (Entry<String, PlayerState> entry : room.playersByName.entrySet()) {
						PlayerState p = entry.getValue();

						p.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_DELETED);
						p.getConnection().disconnect();
					}
					room.playersByName.clear();

					StringBuilder sb = new StringBuilder();

					sb.append(ProtocolConstants.Room.NOTIFY_ROOM_DELETED);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(':').append(port).append(':').append(name);

					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
					appendServerStatus(sb);

					String notify = sb.toString();
					for (ISocketConnection conn : watcherConnections.keySet()) {
						conn.send(notify);
					}
				} else {
					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.Room.NOTIFY_USER_EXITED);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(name);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
					sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
					room.appendRoomInfoForParticipants(sb);

					final String notify = sb.toString();
					room.forEachPlayer(new IClientStateAction<PlayerState>() {
						@Override
						public void action(PlayerState p) {
							p.getConnection().send(notify);
						}
					});

					sb.delete(0, sb.length());

					room.roomMasterAuthCode = Utility.makeAuthCode();
					sb.append(ProtocolConstants.Room.NOTIFY_ROOM_MASTER_AUTH_CODE);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.roomMasterAuthCode);

					newRoomMaster.getConnection().send(sb.toString());

					sb.delete(0, sb.length());

					sb.append(ProtocolConstants.Room.NOTIFY_ROOM_DELETED);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(':').append(port).append(':').append(name);

					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
					room.appendNotifyRoomCreated(sb);

					String watcherNotify = sb.toString();
					for (ISocketConnection conn : watcherConnections.keySet()) {
						conn.send(watcherNotify);
					}
				}
			} else {
				final String notify = ProtocolConstants.Room.NOTIFY_USER_EXITED + ProtocolConstants.ARGUMENT_SEPARATOR + name;
				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(notify);
					}
				});

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_COUNT_CHANGED);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(':').append(port).append(':').append(room.roomMaster.name);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(room.playersByName.size());

				String watcherNotify = sb.toString();
				for (ISocketConnection conn : watcherConnections.keySet()) {
					conn.send(watcherNotify);
				}

			}
		}

		@Override
		public boolean processIncomingData(PlayerState state, PacketData data) {
			boolean sessionContinue = false;

			for (String message : data.getMessages()) {
				int commandEndIndex = message.indexOf(ProtocolConstants.ARGUMENT_SEPARATOR);
				String command, argument;
				if (commandEndIndex > 0) {
					command = message.substring(0, commandEndIndex);
					argument = message.substring(commandEndIndex + 1);
				} else {
					command = message;
					argument = "";
				}

				IServerMessageHandler<PlayerState> handler = state.messageHandlers.get(command);
				if (handler != null) {
					try {
						sessionContinue = handler.process(state, argument);
					} catch (RuntimeException e) {
						logger.log(Utility.makeStackTrace(e));
					}
				}

				if (!sessionContinue)
					break;
			}

			return sessionContinue;
		}

		private class RoomProtocolHandler implements IServerMessageHandler<PlayerState> {
			String errorMessage = ProtocolConstants.ERROR_PROTOCOL_MISMATCH + ProtocolConstants.ARGUMENT_SEPARATOR
					+ ProtocolConstants.PROTOCOL_NUMBER;

			@Override
			public boolean process(PlayerState state, String argument) {
				if (ProtocolConstants.PROTOCOL_NUMBER.equals(argument)) {
					state.messageHandlers = loginHandlers;
					return true;
				} else {
					state.getConnection().send(errorMessage);
					return false;
				}
			}
		}

		private class ConfirmAuthCodeHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				// CAC masterName authCode
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return false;

				String masterName = tokens[0];

				Room room = masterNameRoomMap.get(masterName);
				if (room == null)
					return false;

				String authCode = tokens[1];
				if (authCode.equals(room.roomMasterAuthCode)) {
					state.getConnection().send(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE);
				} else {
					state.getConnection().send(ProtocolConstants.Room.ERROR_CONFIRM_INVALID_AUTH_CODE);
				}
				return false;
			}
		}

		private class RoomCreateHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				// RC masterName maxPlayers title password description
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 5) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
					return false;
				}

				String password = tokens[3];
				if (!passwordAllowed && !Utility.isEmpty(password)) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_PASSWORD_NOT_ALLOWED);
					return false;
				}

				String name = tokens[0];
				if (name.length() == 0) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
					return false;
				}

				int maxPlayers;
				try {
					maxPlayers = Integer.parseInt(tokens[1]);
					if (maxPlayers < 2 || maxPlayers > ProtocolConstants.Room.MAX_ROOM_PLAYERS) {
						state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
						return false;
					}
				} catch (NumberFormatException e) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
					return false;
				}

				String title = tokens[2];
				if (title.length() == 0) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
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
					state.getConnection().send(errorMsg);
					return false;
				}

				newRoom.playersByName.put(name, state);

				newRoom.roomMaster = state;
				newRoom.title = title;
				newRoom.maxPlayers = maxPlayers;
				newRoom.password = password;
				newRoom.description = tokens[4];

				state.room = newRoom;

				state.name = name;
				state.messageHandlers = sessionHandlers;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.COMMAND_ROOM_CREATE);
				sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

				newRoom.roomMasterAuthCode = Utility.makeAuthCode();
				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_MASTER_AUTH_CODE);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(newRoom.roomMasterAuthCode);

				appendLoginMessage(sb);

				if (lobbyRoom != null) {
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
					sb.append(ProtocolConstants.Room.NOTIFY_LOBBY_ADDRESS);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					// sb.append(':').append(port);
				}

				state.getConnection().send(sb.toString());

				sb.delete(0, sb.length());
				newRoom.appendNotifyRoomCreated(sb);

				sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
				appendServerStatus(sb);

				String watcherNotify = sb.toString();
				for (ISocketConnection conn : watcherConnections.keySet()) {
					conn.send(watcherNotify);
				}

				return true;
			}
		}

		private class LoginHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(final PlayerState state, String argument) {
				// LI loginName "masterName" password
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);

				if (tokens.length < 2)
					return false;

				String loginName = tokens[0];
				if (Utility.isEmpty(loginName)) {
					return false;
				}

				String masterName = tokens[1];

				if (masterName.equals(loginName)) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}

				Room room = Utility.isEmpty(masterName) ? lobbyRoom : masterNameRoomMap.get(masterName);
				if (room == null) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_ROOM_NOT_EXIST);
					return false;
				}

				String sentPassword = tokens.length == 3 ? tokens[2] : null;
				if (!Utility.isEmpty(room.password)) {
					if (sentPassword == null) {
						state.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_PASSWORD_REQUIRED);
						return true;
					}
					if (!room.password.equals(sentPassword)) {
						state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_PASSWORD_FAIL);
						return true;
					}
				}

				if (room.playersByName.size() >= room.maxPlayers) {
					// 最大人数を超えたので接続を拒否します
					state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_BEYOND_CAPACITY);
					return false;
				}

				if (room.playersByName.putIfAbsent(loginName, state) != null) {
					// 同名のユーザーが存在するので接続を拒否します
					state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}

				state.messageHandlers = sessionHandlers;
				state.name = loginName;

				state.room = room;

				final String notify = ProtocolConstants.Room.NOTIFY_USER_ENTERED + ProtocolConstants.ARGUMENT_SEPARATOR + loginName;
				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						if (p != state)
							p.getConnection().send(notify);
					}
				});

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Room.COMMAND_LOGIN);
				room.appendRoomInfoForParticipants(sb);

				sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
				room.appendNotifyUserList(sb);

				appendLoginMessage(sb);

				if (room != lobbyRoom && lobbyRoom != null) {
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
					sb.append(ProtocolConstants.Room.NOTIFY_LOBBY_ADDRESS);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					// sb.append(':').append(port);
				}

				state.getConnection().send(sb.toString());

				sb.delete(0, sb.length());
				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_COUNT_CHANGED);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(':').append(port).append(':').append(masterName);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(room.playersByName.size());

				String watcherNotify = sb.toString();
				for (ISocketConnection conn : watcherConnections.keySet()) {
					conn.send(watcherNotify);
				}

				return true;
			}
		}

		private class LogoutHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				return false;
			}
		}

		private class ChatHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = state.room;
				if (room == null)
					return false;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.COMMAND_CHAT);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append('<').append(state.name).append("> ");
				sb.append(argument);
				final String message = sb.toString();
				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(message);
					}
				});
				return true;
			}
		}

		private class PingHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				if (state.room == lobbyRoom)
					return true;
				state.getConnection().send(ProtocolConstants.Room.COMMAND_PINGBACK + ProtocolConstants.ARGUMENT_SEPARATOR + argument);
				return true;
			}
		}

		private class InformPingHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(final PlayerState state, String argument) {
				Room room = state.room;
				if (room == null)
					return false;
				if (room == lobbyRoom)
					return true;

				try {
					Integer.parseInt(argument);
					final String message = ProtocolConstants.Room.COMMAND_INFORM_PING + ProtocolConstants.ARGUMENT_SEPARATOR + state.name
							+ ProtocolConstants.ARGUMENT_SEPARATOR + argument;
					room.forEachPlayer(new IClientStateAction<PlayerState>() {
						@Override
						public void action(PlayerState p) {
							if (p != state)
								p.getConnection().send(message);
						}
					});
				} catch (NumberFormatException e) {
				}
				return true;
			}
		}

		private class InformTunnelPortHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = state.room;
				if (room == null)
					return false;
				if (room == lobbyRoom)
					return true;

				try {
					int port = Integer.parseInt(argument);
					InetSocketAddress remoteEP = new InetSocketAddress(state.getConnection().getRemoteAddress().getAddress(), port);
					state.tunnelState = notYetLinkedTunnels.remove(remoteEP);

					if (state.tunnelState != null) {
						state.getConnection().send(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_UDP_PORT);
						state.tunnelState.room = room;
						state.tunnelState.playerName = state.name;
					}
				} catch (NumberFormatException e) {
				}
				return true;
			}
		}

		private class MacAddressPlayerHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String macAddress) {
				Room room = state.room;
				if (room == null)
					return false;
				if (room == lobbyRoom)
					return true;

				TunnelState tunnelState = room.tunnelsByMacAddress.get(macAddress);
				if (tunnelState == null)
					return true;
				if (Utility.isEmpty(tunnelState.playerName))
					return true;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(macAddress);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(tunnelState.playerName);

				state.connection.send(sb.toString());
				return true;
			}
		}

		private class RoomUpdateHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(final PlayerState state, String argument) {
				Room room = state.room;
				if (room == null || state != room.roomMaster)
					return false;

				// RU maxPlayers title "password" "description"
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 4) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY);
					return true;
				}
				String password = tokens[2];
				if (!passwordAllowed && !Utility.isEmpty(password)) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_CREATE_PASSWORD_NOT_ALLOWED);
					return true;
				}

				room.maxPlayers = Math.min(Integer.parseInt(tokens[0]), ProtocolConstants.Room.MAX_ROOM_PLAYERS);
				room.title = tokens[1];
				room.password = password;
				room.description = tokens[3];

				state.getConnection().send(ProtocolConstants.Room.COMMAND_ROOM_UPDATE);

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
				room.appendRoomInfoForParticipants(sb);

				final String notify = sb.toString();

				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						if (p != state)
							p.getConnection().send(notify);
					}
				});

				sb.delete(0, sb.length());

				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(':').append(port).append(':').append(state.name);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(room.title);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(room.maxPlayers);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(!Utility.isEmpty(password) ? "Y" : "N");
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(room.description);

				String watcherNotify = sb.toString();
				for (ISocketConnection conn : watcherConnections.keySet()) {
					conn.send(watcherNotify);
				}

				return true;
			}
		}

		private class RoomKickPlayerHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = state.room;
				if (room == null || state != room.roomMaster)
					return false;

				String name = argument;
				if (Utility.equals(name, state.name))
					return true;

				PlayerState kickedPlayer = null;
				synchronized (room.playersByName) {
					kickedPlayer = room.playersByName.remove(name);
					if (kickedPlayer == null)
						return true;
				}

				final String notify = ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED + ProtocolConstants.ARGUMENT_SEPARATOR + name;

				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(notify);
					}
				});

				kickedPlayer.getConnection().send(
						ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED + ProtocolConstants.ARGUMENT_SEPARATOR + name);
				kickedPlayer.getConnection().disconnect();

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_COUNT_CHANGED);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(':').append(port).append(':').append(name);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(room.playersByName.size());

				String watcherNotify = sb.toString();
				for (ISocketConnection conn : watcherConnections.keySet()) {
					conn.send(watcherNotify);
				}

				return true;
			}
		}

		private class RoomMasterTransferHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = state.room;
				if (room == null || state != room.roomMaster)
					return false;

				String name = argument;
				if (Utility.equals(name, state.name))
					return true;

				PlayerState newRoomMaster = room.playersByName.get(name);
				if (newRoomMaster == null)
					return true;

				if (masterNameRoomMap.putIfAbsent(name, room) != null) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_ROOM_TRANSFER_DUPLICATED_NAME);
					return true;
				}

				masterNameRoomMap.remove(state.name);
				room.roomMaster = newRoomMaster;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
				room.appendRoomInfoForParticipants(sb);

				final String notify = sb.toString();
				room.forEachPlayer(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(notify);
					}
				});

				room.roomMasterAuthCode = Utility.makeAuthCode();
				newRoomMaster.getConnection().send(
						ProtocolConstants.Room.NOTIFY_ROOM_MASTER_AUTH_CODE + ProtocolConstants.ARGUMENT_SEPARATOR
								+ room.roomMasterAuthCode);

				sb.delete(0, sb.length());

				sb.append(ProtocolConstants.Room.NOTIFY_ROOM_DELETED);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(':').append(port).append(':').append(state.name);

				sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
				room.appendNotifyRoomCreated(sb);

				String watcherNotify = sb.toString();
				for (ISocketConnection conn : watcherConnections.keySet()) {
					conn.send(watcherNotify);
				}

				return true;
			}
		}

		private class WatcherProtocolHandler implements IServerMessageHandler<PlayerState> {
			String errorMessage = ProtocolConstants.ERROR_PROTOCOL_MISMATCH + ProtocolConstants.ARGUMENT_SEPARATOR
					+ ProtocolConstants.PROTOCOL_NUMBER;

			@Override
			public boolean process(PlayerState state, String argument) {
				if (ProtocolConstants.PROTOCOL_NUMBER.equals(argument)) {
					watcherConnections.put(state.connection, this);

					StringBuilder sb = new StringBuilder();
					appendServerStatus(sb);

					if (masterNameRoomMap.size() > 0) {
						for (Room room : masterNameRoomMap.values()) {
							sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
							room.appendNotifyRoomCreated(sb);
						}
					}
					if (myRoomEntries.size() > 0) {
						for (PlayRoom room : myRoomEntries) {
							sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
							appendMyRoomCreated(room, sb);
						}
					}

					state.connection.send(sb.toString());

					state.messageHandlers = watcherHandler;
					return true;
				} else {
					state.getConnection().send(errorMessage);
					return false;
				}
			}
		}

		private void appendMyRoomCreated(PlayRoom room, StringBuilder sb) {
			sb.append(ProtocolConstants.Room.NOTIFY_ROOM_CREATED);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(room.getRoomAddress());
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(room.getMasterName());
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(room.getTitle());
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(room.getCurrentPlayers());
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(room.getMaxPlayers());
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(room.hasPassword() ? "Y" : "N");
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(room.getDescription());
		}

		private class MyRoomProtocolHandler implements IServerMessageHandler<PlayerState> {
			String errorMessage = ProtocolConstants.ERROR_PROTOCOL_MISMATCH + ProtocolConstants.ARGUMENT_SEPARATOR
					+ ProtocolConstants.PROTOCOL_NUMBER;

			@Override
			public boolean process(PlayerState state, String argument) {
				if (ProtocolConstants.PROTOCOL_NUMBER.equals(argument)) {
					state.messageHandlers = myRoomEntryHandlers;
					return true;
				} else {
					state.getConnection().send(errorMessage);
					return false;
				}
			}
		}

		private boolean checkMyRoomEntry(final PlayerState state, final InetSocketAddress address, final PlayRoom room,
				final String authCode) {
			tcpClient.connect(address, new IAsyncClientHandler() {
				boolean tcpReadSuccess = false;

				@Override
				public void log(ISocketConnection connection, String message) {
					logger.log(connection.getRemoteAddress().toString());
					logger.log(message);
				}

				@Override
				public void connectCallback(ISocketConnection connection) {
					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.PROTOCOL_ROOM);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(ProtocolConstants.PROTOCOL_NUMBER);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

					sb.append(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.getMasterName());
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(authCode);

					connection.send(sb.toString());
				}

				@Override
				public void readCallback(ISocketConnection connection, PacketData data) {
					String message = data.getMessage();
					if (ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE.equals(message)) {
						udpClient.connect(address, new IAsyncClientHandler() {
							boolean udpReadSuccess = false;

							@Override
							public void log(ISocketConnection connection, String message) {
								logger.log(connection.getRemoteAddress().toString());
								logger.log(message);
							}

							@Override
							public void connectCallback(ISocketConnection connection) {
								connection.send(ProtocolConstants.Room.TUNNEL_DUMMY_PACKET);
							}

							@Override
							public void readCallback(ISocketConnection connection, PacketData data) {
								String message = data.getMessage();
								if (!Utility.isEmpty(message)) {
									udpReadSuccess = true;
								}
								connection.disconnect();
							}

							@Override
							public void disconnectCallback(ISocketConnection connection) {
								if (udpReadSuccess) {
									state.myRoom = room;
									myRoomEntries.add(room);

									StringBuilder sb = new StringBuilder();

									appendMyRoomCreated(room, sb);

									sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
									appendServerStatus(sb);

									String watcherNotify = sb.toString();
									for (ISocketConnection conn : watcherConnections.keySet()) {
										conn.send(watcherNotify);
									}

									state.getConnection().send(ProtocolConstants.MyRoom.COMMAND_ENTRY);
									state.messageHandlers = myRoomHandlers;

								} else {
									state.getConnection().send(ProtocolConstants.MyRoom.ERROR_UDP_PORT_NOT_OPEN);
									state.getConnection().disconnect();
								}
							}
						});
					} else {
						state.getConnection().send(ProtocolConstants.MyRoom.ERROR_INVALID_AUTH_CODE);
						state.getConnection().disconnect();
					}

					tcpReadSuccess = true;
					connection.disconnect();
				}

				@Override
				public void disconnectCallback(ISocketConnection connection) {
					if (!tcpReadSuccess) {
						state.getConnection().send(ProtocolConstants.MyRoom.ERROR_TCP_PORT_NOT_OPEN);
						state.getConnection().disconnect();
					}
				}
			});

			return true;
		}

		private class MyRoomEntryHandler implements IServerMessageHandler<PlayerState> {
			private String makeHostName(String s, PlayerState state) {
				return s.length() > 0 ? s : state.getConnection().getRemoteAddress().getAddress().getHostAddress();
			}

			@Override
			public boolean process(PlayerState state, String argument) {
				// R authCode hostName:port masterName title currentPlayers
				// maxPlayers hasPassword description
				final String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 8)
					return false;

				try {
					final String authCode = tokens[0];

					String[] address = tokens[1].split(":");
					String hostname = makeHostName(address[0], state);
					int port = Integer.parseInt(address[1]);

					String masterName = tokens[2];
					String title = tokens[3];

					int currentPlayers = Integer.parseInt(tokens[4]);
					int maxPlayers = Integer.parseInt(tokens[5]);
					boolean hasPassword = "Y".equals(tokens[6]);
					String description = tokens[7];

					PlayRoom room = new PlayRoom(hostname + ":" + port, masterName, title, hasPassword, currentPlayers, maxPlayers);
					room.setDescription(description);

					InetSocketAddress socketAddress = new InetSocketAddress(hostname, port);

					return checkMyRoomEntry(state, socketAddress, room, authCode);
				} catch (NumberFormatException e) {
					return false;
				}
			}
		}

		private class MyRoomUpdateHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				if (state.myRoom == null)
					return false;

				// U title maxPlayers hasPassword description
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
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

					sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.getRoomAddress());
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.getTitle());
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.getMaxPlayers());
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.hasPassword() ? "Y" : "N");
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.getDescription());

					String watcherNotify = sb.toString();
					for (ISocketConnection conn : watcherConnections.keySet()) {
						conn.send(watcherNotify);
					}

					return true;
				} catch (NumberFormatException e) {
				}
				return false;
			}
		}

		private class MYRoomUpdatePlayerCountHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				PlayRoom room = state.myRoom;
				if (room == null)
					return false;

				try { // C playerCount
					int playerCount = Integer.parseInt(argument);
					if (playerCount >= room.getMaxPlayers()) {
						return false;
					}
					room.setCurrentPlayers(playerCount);

					StringBuilder sb = new StringBuilder();

					sb.append(ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_COUNT_CHANGED);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(room.getRoomAddress());
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(playerCount);

					String watcherNotify = sb.toString();
					for (ISocketConnection conn : watcherConnections.keySet()) {
						conn.send(watcherNotify);
					}

					return true;
				} catch (NumberFormatException e) {
				}
				return false;
			}
		}
	}

	private static class TunnelState implements IClientState {

		private ISocketConnection connection;
		private Room room;
		private String playerName;
		private long lastTunnelTime;

		TunnelState(ISocketConnection connection) {
			this.connection = connection;
		}

		@Override
		public ISocketConnection getConnection() {
			return connection;
		}
	}

	private class TunnelHandler implements IAsyncServerHandler<TunnelState> {
		@Override
		public void serverStartupFinished() {
		}

		@Override
		public void serverShutdownFinished() {
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public TunnelState createState(ISocketConnection connection) {
			TunnelState state = new TunnelState(connection);
			notYetLinkedTunnels.put(connection.getRemoteAddress(), state);
			return state;
		}

		@Override
		public void disposeState(TunnelState state) {
			state.room = null;
			state.playerName = "";
			notYetLinkedTunnels.remove(state.getConnection().getRemoteAddress());
		}

		@Override
		public boolean processIncomingData(TunnelState state, PacketData data) {
			InetSocketAddress remoteEP = state.getConnection().getRemoteAddress();

			ByteBuffer packet = data.getBuffer();
			if (!Utility.isPspPacket(packet)) {
				if (notYetLinkedTunnels.containsKey(remoteEP)) {
					state.getConnection().send(Integer.toString(remoteEP.getPort()));
				}
				return true;
			}

			Room room = state.room;
			if (room == null)
				return true;

			String destMac = Utility.makeMacAddressString(packet, 0, false);
			String srcMac = Utility.makeMacAddressString(packet, 6, false);

			room.tunnelsByMacAddress.put(srcMac, state);

			if (Utility.isMacBroadCastAddress(destMac)) {
				for (Entry<String, PlayerState> entry : room.playersByName.entrySet()) {
					PlayerState sendTo = entry.getValue();
					if (sendTo.tunnelState != null && sendTo.tunnelState != state) {
						packet.position(0);
						sendTo.tunnelState.getConnection().send(packet);
						sendTo.tunnelState.lastTunnelTime = System.currentTimeMillis();
					}
				}
			} else {
				TunnelState sendTo = room.tunnelsByMacAddress.get(destMac);
				if (sendTo != null) {
					sendTo.getConnection().send(packet);
					sendTo.lastTunnelTime = System.currentTimeMillis();
				}
			}

			return true;
		}
	}
}
