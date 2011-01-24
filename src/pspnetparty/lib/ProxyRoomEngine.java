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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyRoomEngine {

	private HashMap<String, Room> masterNameRoomMap = new HashMap<String, ProxyRoomEngine.Room>();
	private HashMap<PlayerState, Room> playerRoomMap = new HashMap<PlayerState, Room>();

	private Map<TunnelState, Room> tunnelRoomMap = new ConcurrentHashMap<TunnelState, Room>();
	private Map<InetSocketAddress, TunnelState> notYetLinkedTunnels = new ConcurrentHashMap<InetSocketAddress, TunnelState>();

	private IServer<PlayerState> roomServer;
	private RoomHandler roomHandler;

	private IServer<TunnelState> tunnelServer;
	private TunnelHandler tunnelHandler;

	private int maxRooms = 20;

	private boolean isStarted = false;
	private ILogger logger;

	public ProxyRoomEngine(ILogger logger) {
		this.logger = logger;

		roomServer = new AsyncTcpServer<PlayerState>();
		roomHandler = new RoomHandler();

		tunnelServer = new AsyncUdpServer<TunnelState>();
		tunnelHandler = new TunnelHandler();
	}

	public void startListening(int port) throws IOException {
		if (isStarted)
			throw new IllegalStateException();
		logger.log("プロトコル: " + Constants.Protocol.PROTOCOL_NUMBER);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		roomServer.startListening(bindAddress, roomHandler);
		tunnelServer.startListening(bindAddress, tunnelHandler);

		isStarted = true;
	}

	public void stopListening() {
		if (!isStarted)
			return;

		roomServer.stopListening();
		tunnelServer.stopListening();
	}

	public int getMaxRooms() {
		return maxRooms;
	}

	public void setMaxRooms(int maxRooms) {
		if (maxRooms > 0)
			this.maxRooms = maxRooms;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		synchronized (masterNameRoomMap) {
			for (Entry<String, Room> entry : masterNameRoomMap.entrySet()) {
				Room room = entry.getValue();
				sb.append(entry.getKey());
				sb.append('\t').append(room.title);
				sb.append('\t').append(room.playersByName.size()).append('/').append(room.maxPlayers);
				sb.append('\t').append(room.password);
				sb.append(Constants.NEW_LINE);
			}
		}
		return sb.toString();
	}

	class Room {
		private Map<String, PlayerState> playersByName = new LinkedHashMap<String, PlayerState>();
		private HashMap<String, TunnelState> tunnelsByMacAddress = new HashMap<String, TunnelState>();

		private PlayerState roomMaster;

		private int maxPlayers = 4;
		private String title;
		private String description = "";
		private String password = "";

		private void forEachParticipant(PlayerStateAction action) {
			synchronized (playersByName) {
				for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
					PlayerState state = entry.getValue();
					action.action(state);
				}
			}
		}

		private void appendRoomInfo(StringBuilder sb) {
			sb.append(' ').append(roomMaster.name);
			sb.append(' ').append(maxPlayers);
			sb.append(' ').append(title);
			sb.append(" \"").append(password).append('"');
			sb.append(" \"").append(description).append('"');
		}

		private void appendNotifyUserList(StringBuilder sb) {
			sb.append(Constants.Protocol.NOTIFY_USER_LIST);
			sb.append(' ').append(roomMaster.name);
			synchronized (playersByName) {
				for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
					PlayerState state = entry.getValue();
					sb.append(' ').append(state.name);
				}
			}
		}
	}

	class RoomHandler implements IServerHandler<PlayerState> {

		private HashMap<String, PlayerMessageHandler> firstStageHandler = new HashMap<String, PlayerMessageHandler>();
		private HashMap<String, PlayerMessageHandler> secondStageHandler = new HashMap<String, PlayerMessageHandler>();
		private HashMap<String, PlayerMessageHandler> thirdStageHandler = new HashMap<String, PlayerMessageHandler>();

		RoomHandler() {
			firstStageHandler.put(Constants.Protocol.COMMAND_VERSION, new VersionMatchHandler());

			secondStageHandler.put(Constants.Protocol.COMMAND_ROOM_CREATE, new RoomCreateHandler());
			secondStageHandler.put(Constants.Protocol.COMMAND_LOGIN, new LoginHandler());
			secondStageHandler.put(Constants.Protocol.COMMAND_LOGOUT, new LogoutHandler());

			thirdStageHandler.put(Constants.Protocol.COMMAND_LOGOUT, new LogoutHandler());
			thirdStageHandler.put(Constants.Protocol.COMMAND_CHAT, new ChatHandler());
			thirdStageHandler.put(Constants.Protocol.COMMAND_PING, new PingHandler());
			thirdStageHandler.put(Constants.Protocol.COMMAND_INFORM_PING, new InformPingHandler());
			thirdStageHandler.put(Constants.Protocol.COMMAND_INFORM_TUNNEL_UDP_PORT, new InformTunnelPortHandler());
			thirdStageHandler.put(Constants.Protocol.COMMAND_ROOM_UPDATE, new RoomUpdateHandler());
			thirdStageHandler.put(Constants.Protocol.COMMAND_ROOM_KICK_PLAYER, new RoomKickPlayerHandler());
			thirdStageHandler.put(Constants.Protocol.COMMAND_ROOM_MASTER_TRANSFER, new RoomMasterTransferHandler());
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
		public PlayerState createState(IServerConnection connection) {
			PlayerState state = new PlayerState(connection);
			state.messageHandlers = firstStageHandler;
			return state;
		}

		@Override
		public void disposeState(PlayerState state) {
			if (state.tunnelState != null) {
				tunnelRoomMap.remove(state.tunnelState);
				state.tunnelState = null;
			}

			String name = state.name;
			Room room = playerRoomMap.remove(state);
			if (room == null)
				return;

			synchronized (room.playersByName) {
				room.playersByName.remove(name);
			}

			if (masterNameRoomMap.containsKey(name)) {
				masterNameRoomMap.remove(name);

				String newMasterName = null;
				synchronized (room.playersByName) {
					for (Entry<String, PlayerState> entry : room.playersByName.entrySet()) {
						String candidate = entry.getKey();
						if (!masterNameRoomMap.containsKey(candidate)) {
							newMasterName = candidate;
							masterNameRoomMap.put(newMasterName, room);
							room.roomMaster = entry.getValue();
							break;
						}
					}
				}
				if (newMasterName == null) {
					Map<String, PlayerState> backup;
					synchronized (room.playersByName) {
						backup = room.playersByName;
						room.playersByName = Collections.emptyMap();
					}

					for (Entry<String, PlayerState> entry : backup.entrySet()) {
						PlayerState p = entry.getValue();
						playerRoomMap.remove(p);

						p.getConnection().send(Constants.Protocol.NOTIFY_ROOM_DELETED);
						p.getConnection().disconnect();
					}
				} else {
					StringBuilder sb = new StringBuilder();
					sb.append(Constants.Protocol.NOTIFY_USER_EXITED);
					sb.append(' ').append(name);
					sb.append(Constants.Protocol.MESSAGE_SEPARATOR);
					sb.append(Constants.Protocol.NOTIFY_ROOM_UPDATED);
					room.appendRoomInfo(sb);

					final String notify = sb.toString();
					room.forEachParticipant(new PlayerStateAction() {
						@Override
						public void action(PlayerState p) {
							p.getConnection().send(notify);
						}
					});
				}
			} else {
				final String notify = Constants.Protocol.NOTIFY_USER_EXITED + " " + name;
				room.forEachParticipant(new PlayerStateAction() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(notify);
					}
				});
			}
		}

		@Override
		public boolean processIncomingData(PlayerState state, PacketData data) {
			boolean sessionContinue = false;

			for (String message : data.getMessages()) {
				int commandEndIndex = message.indexOf(' ');
				String command, argument;
				if (commandEndIndex > 0) {
					command = message.substring(0, commandEndIndex);
					argument = message.substring(commandEndIndex + 1);
				} else {
					command = message;
					argument = "";
				}

				PlayerMessageHandler handler = state.messageHandlers.get(command);
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

		class VersionMatchHandler implements PlayerMessageHandler {
			String errorMessage = Constants.Protocol.ERROR_VERSION_MISMATCH + " " + Constants.Protocol.PROTOCOL_NUMBER;

			@Override
			public boolean process(PlayerState state, String argument) {
				if (Constants.Protocol.PROTOCOL_NUMBER.equals(argument)) {
					state.messageHandlers = secondStageHandler;
					// state.getConnection().send(Constants.Protocol.SERVER_ROOM);
					return true;
				} else {
					state.getConnection().send(errorMessage);
					return false;
				}
			}
		}

		class RoomCreateHandler implements PlayerMessageHandler {
			@Override
			public boolean process(PlayerState state, String argument) {
				// RC masterName maxPlayers title "password" "description"
				String[] tokens = argument.split(" ");
				if (tokens.length != 5)
					return false;

				String name = tokens[0];
				if (name.length() == 0)
					return false;

				int maxPlayers;
				try {
					maxPlayers = Integer.parseInt(tokens[1]);
					if (maxPlayers < 2 || maxPlayers > Constants.Protocol.MAX_ROOM_PLAYERS)
						return false;
				} catch (NumberFormatException e) {
					return false;
				}

				String title = tokens[2];
				if (title.length() == 0)
					return false;

				Room newRoom = null;
				String errorMsg = null;
				synchronized (masterNameRoomMap) {
					if (masterNameRoomMap.size() >= maxRooms) {
						errorMsg = Constants.Protocol.ERROR_ROOM_CREATE_BEYOND_LIMIT;
					} else if (masterNameRoomMap.containsKey(name)) {
						errorMsg = Constants.Protocol.ERROR_LOGIN_DUPLICATED_NAME;
					} else {
						newRoom = new Room();
						masterNameRoomMap.put(name, newRoom);
					}
				}
				if (newRoom == null) {
					state.getConnection().send(errorMsg);
					return false;
				}

				newRoom.roomMaster = state;
				newRoom.title = title;
				newRoom.maxPlayers = maxPlayers;
				newRoom.password = Utility.removeQuotations(tokens[3]);
				newRoom.description = Utility.removeQuotations(tokens[4]);

				playerRoomMap.put(state, newRoom);

				newRoom.playersByName.put(name, state);

				state.name = name;
				state.messageHandlers = thirdStageHandler;

				state.getConnection().send(Constants.Protocol.COMMAND_ROOM_CREATE);
				return true;
			}
		}

		class LoginHandler implements PlayerMessageHandler {
			@Override
			public boolean process(final PlayerState state, String argument) {
				// LI "masterName" loginName password
				String[] tokens = argument.split(" ");

				if (tokens.length < 2)
					return false;

				String masterName = Utility.removeQuotations(tokens[0]);
				String loginName = tokens[1];
				String sentPassword = tokens.length == 3 ? tokens[2] : null;

				if (Utility.isEmpty(loginName)) {
					return false;
				}
				if (Utility.isEmpty(masterName)) {
					// default room
					masterName = "";
				}

				Room room = masterNameRoomMap.get(masterName);
				if (room == null)
					return false;

				if (!Utility.isEmpty(room.password)) {
					if (sentPassword == null) {
						state.getConnection().send(Constants.Protocol.NOTIFY_ROOM_PASSWORD_REQUIRED);
						return true;
					}
					if (!room.password.equals(sentPassword)) {
						state.getConnection().send(Constants.Protocol.ERROR_ROOM_ENTER_PASSWORD_FAIL);
						return true;
					}
				}

				if (masterName.equals(loginName) || room.playersByName.containsKey(loginName)) {
					// 同名のユーザーが存在するので接続を拒否します
					state.getConnection().send(Constants.Protocol.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}

				synchronized (room.playersByName) {
					if (room.playersByName.size() < room.maxPlayers) {
						room.playersByName.put(loginName, state);
						state.messageHandlers = thirdStageHandler;

						state.name = loginName;
					} else {
						// 最大人数を超えたので接続を拒否します
						state.getConnection().send(Constants.Protocol.ERROR_LOGIN_BEYOND_CAPACITY);
						return false;
					}
				}

				playerRoomMap.put(state, room);

				final String notify = Constants.Protocol.NOTIFY_USER_ENTERED + " " + loginName;
				room.forEachParticipant(new PlayerStateAction() {
					@Override
					public void action(PlayerState p) {
						if (p != state)
							p.getConnection().send(notify);
					}
				});

				StringBuilder sb = new StringBuilder();

				sb.append(Constants.Protocol.COMMAND_LOGIN);
				room.appendRoomInfo(sb);

				sb.append(Constants.Protocol.MESSAGE_SEPARATOR);
				room.appendNotifyUserList(sb);

				state.getConnection().send(sb.toString());

				return true;
			}
		}

		class LogoutHandler implements PlayerMessageHandler {
			@Override
			public boolean process(PlayerState state, String argument) {
				return false;
			}
		}

		class ChatHandler implements PlayerMessageHandler {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = playerRoomMap.get(state);
				if (room == null)
					return false;

				final String message = String.format("%s <%s> %s", Constants.Protocol.COMMAND_CHAT, state.name, argument);
				room.forEachParticipant(new PlayerStateAction() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(message);
					}
				});
				return true;
			}
		}

		class PingHandler implements PlayerMessageHandler {
			@Override
			public boolean process(PlayerState state, String argument) {
				state.getConnection().send(Constants.Protocol.COMMAND_PINGBACK + " " + argument);
				return true;
			}
		}

		class InformPingHandler implements PlayerMessageHandler {
			@Override
			public boolean process(final PlayerState state, String argument) {
				Room room = playerRoomMap.get(state);
				if (room == null)
					return false;

				try {
					Integer.parseInt(argument);
					final String message = Constants.Protocol.COMMAND_INFORM_PING + " " + state.name + " " + argument;
					room.forEachParticipant(new PlayerStateAction() {
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

		class InformTunnelPortHandler implements PlayerMessageHandler {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = playerRoomMap.get(state);
				if (room == null)
					return false;

				try {
					int port = Integer.parseInt(argument);
					InetSocketAddress remoteEP = new InetSocketAddress(state.getConnection().getRemoteAddress().getAddress(), port);
					state.tunnelState = notYetLinkedTunnels.remove(remoteEP);

					if (state.tunnelState != null) {
						state.getConnection().send(Constants.Protocol.COMMAND_INFORM_TUNNEL_UDP_PORT);
						tunnelRoomMap.put(state.tunnelState, room);
					}
				} catch (NumberFormatException e) {
				}
				return true;
			}
		}

		class RoomUpdateHandler implements PlayerMessageHandler {
			@Override
			public boolean process(final PlayerState state, String argument) {
				Room room = playerRoomMap.get(state);
				if (room == null || state != room.roomMaster)
					return false;

				// RU maxPlayers title "password" "description"
				String[] tokens = argument.split(" ");
				if (tokens.length != 4)
					return true;

				room.maxPlayers = Math.min(Integer.parseInt(tokens[0]), Constants.Protocol.MAX_ROOM_PLAYERS);
				room.title = tokens[1];
				room.password = Utility.removeQuotations(tokens[2]);
				room.description = Utility.removeQuotations(tokens[3]);

				state.getConnection().send(Constants.Protocol.COMMAND_ROOM_UPDATE);

				StringBuilder sb = new StringBuilder();

				sb.append(Constants.Protocol.NOTIFY_ROOM_UPDATED);
				room.appendRoomInfo(sb);

				final String notify = sb.toString();

				room.forEachParticipant(new PlayerStateAction() {
					@Override
					public void action(PlayerState p) {
						if (p != state)
							p.getConnection().send(notify);
					}
				});

				return true;
			}
		}

		class RoomKickPlayerHandler implements PlayerMessageHandler {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = playerRoomMap.get(state);
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

				final String notify = Constants.Protocol.NOTIFY_ROOM_PLAYER_KICKED + " " + name;

				room.forEachParticipant(new PlayerStateAction() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(notify);
					}
				});

				kickedPlayer.getConnection().send(Constants.Protocol.NOTIFY_ROOM_PLAYER_KICKED + " " + name);
				kickedPlayer.getConnection().disconnect();

				return true;
			}
		}
		
		class RoomMasterTransferHandler implements PlayerMessageHandler {
			@Override
			public boolean process(PlayerState state, String argument) {
				Room room = playerRoomMap.get(state);
				if (room == null || state != room.roomMaster)
					return false;

				String name = argument;
				if (Utility.equals(name, state.name))
					return true;

				PlayerState newMasterPlayer = room.playersByName.get(name);
				if (newMasterPlayer == null)
					return true;
				
				if (masterNameRoomMap.containsKey(name)) {
					state.getConnection().send(Constants.Protocol.ERROR_ROOM_TRANSFER_DUPLICATED_NAME);
					return true;
				}
				
				synchronized (masterNameRoomMap) {
					masterNameRoomMap.remove(state.name);
					masterNameRoomMap.put(name, room);
					room.roomMaster = newMasterPlayer;
				}
				
				StringBuilder sb = new StringBuilder();
				sb.append(Constants.Protocol.NOTIFY_ROOM_UPDATED);
				room.appendRoomInfo(sb);

				final String notify = sb.toString();
				room.forEachParticipant(new PlayerStateAction() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(notify);
					}
				});
				return true;
			}
		}
	}

	class TunnelHandler implements IServerHandler<TunnelState> {
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
		public TunnelState createState(IServerConnection connection) {
			TunnelState state = new TunnelState(connection);
			notYetLinkedTunnels.put(connection.getRemoteAddress(), state);
			return state;
		}

		@Override
		public void disposeState(TunnelState state) {
			notYetLinkedTunnels.remove(state.getConnection().getRemoteAddress());
			tunnelRoomMap.remove(state);
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

			Room room = tunnelRoomMap.get(state);
			if (room == null)
				return true;

			String destMac = Utility.makeMacAddressString(packet, 0, false);
			String srcMac = Utility.makeMacAddressString(packet, 6, false);

			room.tunnelsByMacAddress.put(srcMac, state);

			if (Utility.isMacBroadCastAddress(destMac)) {
				synchronized (room.playersByName) {
					for (Entry<String, PlayerState> entry : room.playersByName.entrySet()) {
						PlayerState sendTo = entry.getValue();
						if (sendTo.tunnelState != null && sendTo.tunnelState != state) {
							packet.position(0);
							sendTo.tunnelState.getConnection().send(packet);
							sendTo.tunnelState.lastTunnelTime = System.currentTimeMillis();
						}
					}
				}
			} else if (room.tunnelsByMacAddress.containsKey(destMac)) {
				TunnelState sendTo = room.tunnelsByMacAddress.get(destMac);
				sendTo.getConnection().send(packet);
				sendTo.lastTunnelTime = System.currentTimeMillis();
			}

			return true;
		}
	}
}
