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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class RoomEngine {

	private HashMap<String, PlayerState> playersByName = new LinkedHashMap<String, PlayerState>();

	private HashMap<String, TunnelState> tunnelsByMacAddress = new HashMap<String, TunnelState>();
	private HashMap<String, Long> masterMacAddresses = new HashMap<String, Long>();

	private Map<InetSocketAddress, TunnelState> notYetLinkedTunnels = new ConcurrentHashMap<InetSocketAddress, TunnelState>();
	
	private String masterName;
	private IRoomMasterHandler roomMasterHandler;

	private int maxPlayers = 4;
	private String title;
	private String description = "";
	private String password = "";

	private IServer<PlayerState> roomServer;
	private RoomHandler roomHandler;

	private IServer<TunnelState> tunnelServer;
	private TunnelHandler tunnelHandler;

	private Object serverCountLock = new Object();
	private int activeServerCount = 0;

	public RoomEngine(IRoomMasterHandler masterHandler) {
		this.roomMasterHandler = masterHandler;

		roomHandler = new RoomHandler();
		roomServer = new AsyncTcpServer<PlayerState>();

		tunnelHandler = new TunnelHandler();
		tunnelServer = new AsyncUdpServer<TunnelState>();
	}

	public boolean isStarted() {
		return activeServerCount > 0;
	}

	public void openRoom(int port, String masterName) throws IOException {
		if (isStarted())
			throw new IOException();
		if (Utility.isEmpty(masterName) || Utility.isEmpty(title))
			throw new IOException();
		this.masterName = masterName;

		playersByName.clear();
		notYetLinkedTunnels.clear();
		tunnelsByMacAddress.clear();
		masterMacAddresses.clear();

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		roomServer.startListening(bindAddress, roomHandler);
		tunnelServer.startListening(bindAddress, tunnelHandler);
	}

	public void closeRoom() {
		if (!isStarted())
			return;

		roomServer.stopListening();
		tunnelServer.stopListening();
	}

	private void serverStartupFinished() {
		synchronized (serverCountLock) {
			activeServerCount++;
			if (activeServerCount == 2)
				roomMasterHandler.roomOpened();
		}
	}

	private void serverShutdownFinished() {
		synchronized (serverCountLock) {
			activeServerCount--;
			if (activeServerCount == 0)
				roomMasterHandler.roomClosed();
		}
	}

	private void appendRoomInfo(StringBuilder sb) {
		sb.append(' ').append(masterName);
		sb.append(' ').append(maxPlayers);
		sb.append(' ').append(title);
		sb.append(" \"").append(password).append('"');
		sb.append(" \"").append(description).append('"');
	}

	private void appendNotifyUserList(StringBuilder sb) {
		sb.append(Constants.Protocol.NOTIFY_USER_LIST);
		sb.append(' ').append(masterName);
		synchronized (playersByName) {
			for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
				PlayerState state = entry.getValue();
				sb.append(' ').append(state.name);
			}
		}
	}

	private void forEachParticipant(PlayerStateAction action) {
		synchronized (playersByName) {
			for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
				PlayerState state = entry.getValue();
				action.action(state);
			}
		}
	}

	public void updateRoom() {
		StringBuilder sb = new StringBuilder();
		sb.append(Constants.Protocol.NOTIFY_ROOM_UPDATED);
		appendRoomInfo(sb);

		final String notify = sb.toString();

		forEachParticipant(new PlayerStateAction() {
			@Override
			public void action(PlayerState p) {
				p.getConnection().send(notify);
			}
		});
	}

	public void kickPlayer(String name) {
		PlayerState kickedPlayer;
		synchronized (playersByName) {
			kickedPlayer = playersByName.remove(name);
			if (kickedPlayer == null)
				return;
		}
		final String notify = Constants.Protocol.NOTIFY_ROOM_PLAYER_KICKED + " " + name;

		forEachParticipant(new PlayerStateAction() {
			@Override
			public void action(PlayerState p) {
				p.getConnection().send(notify);
			}
		});

		if (kickedPlayer.tunnelState != null)
			notYetLinkedTunnels.remove(kickedPlayer.tunnelState.getConnection().getRemoteAddress());

		kickedPlayer.getConnection().send(Constants.Protocol.NOTIFY_ROOM_PLAYER_KICKED + " " + name);
		kickedPlayer.getConnection().disconnect();
	}

	public void sendChat(String text) {
		processChat(masterName, text);
	}

	private void processChat(String player, String text) {
		String chatText = String.format("<%s> %s", player, text);
		final String chatMessage = Constants.Protocol.COMMAND_CHAT + " " + chatText;
		forEachParticipant(new PlayerStateAction() {
			@Override
			public void action(PlayerState p) {
				p.getConnection().send(chatMessage);
			}
		});
		roomMasterHandler.chatReceived(chatText);
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

	public void setMaxPlayers(int maxPlayers) {
		if (maxPlayers < 2)
			throw new IllegalArgumentException();
		this.maxPlayers = maxPlayers;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		if (Utility.isEmpty(title))
			throw new IllegalArgumentException();
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		if (Utility.isEmpty(description))
			description = "";
		this.description = description;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		if (Utility.isEmpty(password))
			password = "";
		this.password = password;
	}

	class RoomHandler implements IServerHandler<PlayerState> {

		private HashMap<String, PlayerMessageHandler> firstStageHandler = new HashMap<String, PlayerMessageHandler>();
		private HashMap<String, PlayerMessageHandler> secondStageHandler = new HashMap<String, PlayerMessageHandler>();
		private HashMap<String, PlayerMessageHandler> thirdStageHandler = new HashMap<String, PlayerMessageHandler>();

		RoomHandler() {
			firstStageHandler.put(Constants.Protocol.COMMAND_VERSION, new VersionMatchHandler());

			secondStageHandler.put(Constants.Protocol.COMMAND_LOGIN, new LoginHandler());
			secondStageHandler.put(Constants.Protocol.COMMAND_LOGOUT, new LogoutHandler());

			thirdStageHandler.put(Constants.Protocol.COMMAND_LOGOUT, new LogoutHandler());
			thirdStageHandler.put(Constants.Protocol.COMMAND_CHAT, new ChatHandler());
			thirdStageHandler.put(Constants.Protocol.COMMAND_PING, new PingHandler());
			thirdStageHandler.put(Constants.Protocol.COMMAND_INFORM_PING, new InformPingHandler());
			thirdStageHandler.put(Constants.Protocol.COMMAND_INFORM_TUNNEL_UDP_PORT, new InformTunnelPortHandler());
		}

		@Override
		public void serverStartupFinished() {
			RoomEngine.this.serverStartupFinished();
		}

		@Override
		public void serverShutdownFinished() {
			RoomEngine.this.serverShutdownFinished();
		}

		@Override
		public void log(String message) {
			roomMasterHandler.log(message);
		}

		@Override
		public PlayerState createState(IServerConnection connection) {
			PlayerState state = new PlayerState(connection);
			state.messageHandlers = firstStageHandler;
			return state;
		}

		@Override
		public void disposeState(PlayerState state) {
			if (state.tunnelState != null)
				notYetLinkedTunnels.remove(state.tunnelState.getConnection().getRemoteAddress());

			if (!Utility.isEmpty(state.name)) {
				synchronized (playersByName) {
					playersByName.remove(state.name);
				}

				final String notify = Constants.Protocol.NOTIFY_USER_EXITED + " " + state.name;
				forEachParticipant(new PlayerStateAction() {
					@Override
					public void action(PlayerState p) {
						p.getConnection().send(notify);
					}
				});
				roomMasterHandler.playerExited(state.name);
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
					sessionContinue = handler.process(state, argument);
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
					state.getConnection().send(Constants.Protocol.SERVER_ROOM);
					return true;
				} else {
					state.getConnection().send(errorMessage);
					return false;
				}
			}
		}

		class LoginHandler implements PlayerMessageHandler {
			@Override
			public boolean process(final PlayerState state, String argument) {
				// LI "" loginName password
				String[] tokens = argument.split(" ");

				String loginRoomMasterName = Utility.removeQuotations(tokens[0]);
				String loginName = tokens[1];
				String sentPassword = tokens.length == 2 ? null : tokens[1];

				if (loginRoomMasterName.length() != 0 || loginName.length() == 0) {
					return false;
				}

				if (!Utility.isEmpty(RoomEngine.this.password)) {
					if (sentPassword == null) {
						state.getConnection().send(Constants.Protocol.NOTIFY_ROOM_PASSWORD_REQUIRED);
						return true;
					}
					if (!RoomEngine.this.password.equals(sentPassword)) {
						state.getConnection().send(Constants.Protocol.ERROR_ROOM_ENTER_PASSWORD_FAIL);
						return true;
					}
				}

				if (masterName.equals(loginName) || playersByName.containsKey(loginName)) {
					// 同名のユーザーが存在するので接続を拒否します
					state.getConnection().send(Constants.Protocol.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}

				synchronized (playersByName) {
					if (playersByName.size() < maxPlayers - 1) {
						playersByName.put(loginName, state);
						state.messageHandlers = thirdStageHandler;

						state.name = loginName;
					} else {
						// 最大人数を超えたので接続を拒否します
						state.getConnection().send(Constants.Protocol.ERROR_LOGIN_BEYOND_CAPACITY);
						return false;
					}
				}

				final String notify = Constants.Protocol.NOTIFY_USER_ENTERED + " " + loginName;
				forEachParticipant(new PlayerStateAction() {
					@Override
					public void action(PlayerState p) {
						if (p != state)
							p.getConnection().send(notify);
					}
				});
				roomMasterHandler.playerEntered(loginName);

				StringBuilder sb = new StringBuilder();

				sb.append(Constants.Protocol.COMMAND_LOGIN);
				appendRoomInfo(sb);

				sb.append(Constants.Protocol.MESSAGE_SEPARATOR);
				appendNotifyUserList(sb);

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
				processChat(state.name, argument);
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
				try {
					int ping = Integer.parseInt(argument);
					final String message = Constants.Protocol.COMMAND_INFORM_PING + " " + state.name + " " + argument;
					forEachParticipant(new PlayerStateAction() {
						@Override
						public void action(PlayerState p) {
							if (p != state)
								p.getConnection().send(message);
						}
					});
					roomMasterHandler.pingInformed(state.name, ping);
				} catch (NumberFormatException e) {
				}
				return true;
			}
		}

		class InformTunnelPortHandler implements PlayerMessageHandler {
			@Override
			public boolean process(PlayerState state, String argument) {
				try {
					int port = Integer.parseInt(argument);
					InetSocketAddress remoteEP = new InetSocketAddress(state.getConnection().getRemoteAddress().getAddress(), port);
					state.tunnelState = notYetLinkedTunnels.remove(remoteEP);

					if (state.tunnelState != null)
						state.getConnection().send(Constants.Protocol.COMMAND_INFORM_TUNNEL_UDP_PORT);
				} catch (NumberFormatException e) {
				}
				return true;
			}
		}
	}

	class TunnelHandler implements IServerHandler<TunnelState> {
		@Override
		public void serverStartupFinished() {
			RoomEngine.this.serverStartupFinished();
		}

		@Override
		public void serverShutdownFinished() {
			RoomEngine.this.serverShutdownFinished();
		}

		@Override
		public void log(String message) {
			roomMasterHandler.log(message);
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

			String destMac = Utility.makeMacAddressString(packet, 0, false);
			String srcMac = Utility.makeMacAddressString(packet, 6, false);

			tunnelsByMacAddress.put(srcMac, state);

			if (Utility.isMacBroadCastAddress(destMac)) {
				roomMasterHandler.tunnelPacketReceived(packet);

				synchronized (playersByName) {
					for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
						PlayerState sendTo = entry.getValue();
						if (sendTo.tunnelState != null && sendTo.tunnelState != state) {
							packet.position(0);
							sendTo.tunnelState.getConnection().send(packet);
							sendTo.tunnelState.lastTunnelTime = System.currentTimeMillis();
						}
					}
				}
			} else if (masterMacAddresses.containsKey(destMac)) {
				masterMacAddresses.put(destMac, System.currentTimeMillis());
				roomMasterHandler.tunnelPacketReceived(packet);
			} else if (tunnelsByMacAddress.containsKey(destMac)) {
				TunnelState sendTo = tunnelsByMacAddress.get(destMac);
				sendTo.getConnection().send(packet);
				sendTo.lastTunnelTime = System.currentTimeMillis();
			}

			return true;
		}
	}

	public void sendTunnelPacketToParticipants(ByteBuffer packet, String srcMac, String destMac) {
		masterMacAddresses.put(srcMac, System.currentTimeMillis());

		if (Utility.isMacBroadCastAddress(destMac)) {
			synchronized (playersByName) {
				for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
					PlayerState sendTo = entry.getValue();
					if (sendTo.tunnelState != null) {
						packet.position(0);
						sendTo.tunnelState.getConnection().send(packet);
						sendTo.tunnelState.lastTunnelTime = System.currentTimeMillis();
					}
				}
			}
		} else if (tunnelsByMacAddress.containsKey(destMac)) {
			TunnelState sendTo = tunnelsByMacAddress.get(destMac);
			sendTo.getConnection().send(packet);
			sendTo.lastTunnelTime = System.currentTimeMillis();
		}
	}
}
