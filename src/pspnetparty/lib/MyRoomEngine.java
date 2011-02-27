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
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import pspnetparty.lib.constants.ProtocolConstants;

public class MyRoomEngine {

	private ConcurrentSkipListMap<String, PlayerState> playersByName;

	private HashMap<String, TunnelState> tunnelsByMacAddress;
	private HashMap<String, Long> masterMacAddresses;

	private ConcurrentHashMap<InetSocketAddress, TunnelState> notYetLinkedTunnels;

	private String masterName;
	private String masterSsid = "";

	private IMyRoomMasterHandler roomMasterHandler;

	private int maxPlayers = 4;
	private String title;
	private String description = "";
	private String password = "";

	private String roomMasterAuthCode;

	private boolean allowEmptyMasterNameLogin = true;

	private IServer<PlayerState> roomServer;
	private RoomHandler roomHandler;

	private IServer<TunnelState> tunnelServer;
	private TunnelHandler tunnelHandler;

	private Object serverCountLock = new Object();
	private int activeServerCount = 0;

	public MyRoomEngine(IMyRoomMasterHandler masterHandler) {
		this.roomMasterHandler = masterHandler;

		playersByName = new ConcurrentSkipListMap<String, PlayerState>();
		tunnelsByMacAddress = new HashMap<String, TunnelState>();
		masterMacAddresses = new HashMap<String, Long>();
		notYetLinkedTunnels = new ConcurrentHashMap<InetSocketAddress, TunnelState>(16, 0.75f, 2);

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
			if (activeServerCount == 2) {
				roomMasterAuthCode = Utility.makeAuthCode();
				roomMasterHandler.roomOpened(roomMasterAuthCode);
			}
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
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(masterName);
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
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(masterName);
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(masterSsid);
		for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
			PlayerState state = entry.getValue();
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(state.name);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(state.ssid);
		}
	}

	private void forEachParticipant(IClientStateAction<PlayerState> action) {
		for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
			PlayerState state = entry.getValue();
			action.action(state);
		}
	}

	public void updateRoom() {
		StringBuilder sb = new StringBuilder();
		sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
		appendRoomInfo(sb);

		final String notify = sb.toString();

		forEachParticipant(new IClientStateAction<PlayerState>() {
			@Override
			public void action(PlayerState p) {
				p.getConnection().send(notify);
			}
		});
	}

	public void kickPlayer(String name) {
		PlayerState kickedPlayer;
		kickedPlayer = playersByName.remove(name);
		if (kickedPlayer == null)
			return;
		final String notify = ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED + ProtocolConstants.ARGUMENT_SEPARATOR + name;

		forEachParticipant(new IClientStateAction<PlayerState>() {
			@Override
			public void action(PlayerState p) {
				p.getConnection().send(notify);
			}
		});

		if (kickedPlayer.tunnelState != null)
			notYetLinkedTunnels.remove(kickedPlayer.tunnelState.getConnection().getRemoteAddress());

		kickedPlayer.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED + ProtocolConstants.ARGUMENT_SEPARATOR + name);
		kickedPlayer.getConnection().disconnect();
	}

	public void sendChat(String text) {
		processChat(masterName, text);
	}

	public void informSSID(String ssid) {
		masterSsid = ssid != null ? ssid : "";

		StringBuilder sb = new StringBuilder();
		sb.append(ProtocolConstants.Room.NOTIFY_SSID_CHANGED);
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(masterName);
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(masterSsid);

		final String message = sb.toString();
		forEachParticipant(new IClientStateAction<PlayerState>() {
			@Override
			public void action(PlayerState p) {
				p.getConnection().send(message);
			}
		});
	}

	private void processChat(String player, String text) {
		String chatText = String.format("<%s> %s", player, text);
		final String chatMessage = ProtocolConstants.Room.COMMAND_CHAT + ProtocolConstants.ARGUMENT_SEPARATOR + chatText;
		forEachParticipant(new IClientStateAction<PlayerState>() {
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

	public boolean isAllowEmptyMasterNameLogin() {
		return allowEmptyMasterNameLogin;
	}

	public void setAllowEmptyMasterNameLogin(boolean allowEmptyMasterNameLogin) {
		this.allowEmptyMasterNameLogin = allowEmptyMasterNameLogin;
	}

	private static class PlayerState implements IClientState {

		private ISocketConnection connection;

		private HashMap<String, IServerMessageHandler<PlayerState>> messageHandlers;
		private String name;
		private TunnelState tunnelState;
		private String ssid = "";

		PlayerState(ISocketConnection conn) {
			connection = conn;
		}

		@Override
		public ISocketConnection getConnection() {
			return connection;
		}
	}

	class RoomHandler implements IAsyncServerHandler<PlayerState> {

		private HashMap<String, IServerMessageHandler<PlayerState>> protocolHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();
		private HashMap<String, IServerMessageHandler<PlayerState>> loginHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();
		private HashMap<String, IServerMessageHandler<PlayerState>> sessionHandlers = new HashMap<String, IServerMessageHandler<PlayerState>>();

		RoomHandler() {
			protocolHandlers.put(ProtocolConstants.PROTOCOL_ROOM, new ProtocolMatchHandler());

			loginHandlers.put(ProtocolConstants.Room.COMMAND_LOGIN, new LoginHandler());
			loginHandlers.put(ProtocolConstants.Room.COMMAND_LOGOUT, new LogoutHandler());
			loginHandlers.put(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE, new ConfirmAuthCodeHandler());

			sessionHandlers.put(ProtocolConstants.Room.COMMAND_LOGOUT, new LogoutHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_CHAT, new ChatHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_PING, new PingHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_PING, new InformPingHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_UDP_PORT, new InformTunnelPortHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER, new MacAddressPlayerHandler());
			sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_SSID, new InformSSIDHandler());
		}

		@Override
		public void serverStartupFinished() {
			MyRoomEngine.this.serverStartupFinished();
		}

		@Override
		public void serverShutdownFinished() {
			MyRoomEngine.this.serverShutdownFinished();
		}

		@Override
		public void log(String message) {
			roomMasterHandler.log(message);
		}

		@Override
		public PlayerState createState(ISocketConnection connection) {
			PlayerState state = new PlayerState(connection);
			state.messageHandlers = protocolHandlers;
			return state;
		}

		@Override
		public void disposeState(PlayerState state) {
			if (state.tunnelState != null)
				notYetLinkedTunnels.remove(state.tunnelState.getConnection().getRemoteAddress());

			if (!Utility.isEmpty(state.name)) {
				playersByName.remove(state.name);

				final String notify = ProtocolConstants.Room.NOTIFY_USER_EXITED + ProtocolConstants.ARGUMENT_SEPARATOR + state.name;
				forEachParticipant(new IClientStateAction<PlayerState>() {
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
					sessionContinue = handler.process(state, argument);
				}

				if (!sessionContinue)
					break;
			}

			return sessionContinue;
		}

		private class ProtocolMatchHandler implements IServerMessageHandler<PlayerState> {
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
				String authCode = tokens[1];
				if (masterName.equals(MyRoomEngine.this.masterName) && authCode.equals(roomMasterAuthCode)) {
					state.getConnection().send(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE);
				} else {
					state.getConnection().send(ProtocolConstants.Room.ERROR_CONFIRM_INVALID_AUTH_CODE);
				}
				return false;
			}
		}

		private class LoginHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(final PlayerState state, String argument) {
				// LI loginName "masterName" password
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);

				String loginName = tokens[0];
				if (loginName.length() == 0) {
					return false;
				}

				String loginRoomMasterName = tokens[1];
				if (loginRoomMasterName.length() == 0) {
					if (!allowEmptyMasterNameLogin) {
						return false;
					}
				} else if (!loginRoomMasterName.equals(masterName)) {
					return false;
				}

				String sentPassword = tokens.length == 2 ? null : tokens[2];
				if (!Utility.isEmpty(MyRoomEngine.this.password)) {
					if (sentPassword == null) {
						state.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_PASSWORD_REQUIRED);
						return true;
					}
					if (!MyRoomEngine.this.password.equals(sentPassword)) {
						state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_PASSWORD_FAIL);
						return true;
					}
				}

				if (masterName.equals(loginName)) {
					state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}

				if (playersByName.size() >= maxPlayers - 1) {
					// 最大人数を超えたので接続を拒否します
					state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_BEYOND_CAPACITY);
					return false;
				}

				if (playersByName.putIfAbsent(loginName, state) != null) {
					// 同名のユーザーが存在するので接続を拒否します
					state.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}
				state.messageHandlers = sessionHandlers;
				state.name = loginName;

				final String notify = ProtocolConstants.Room.NOTIFY_USER_ENTERED + ProtocolConstants.ARGUMENT_SEPARATOR + loginName;
				forEachParticipant(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						if (p != state)
							p.getConnection().send(notify);
					}
				});
				roomMasterHandler.playerEntered(loginName);

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Room.COMMAND_LOGIN);
				appendRoomInfo(sb);

				sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
				appendNotifyUserList(sb);

				state.getConnection().send(sb.toString());

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
				processChat(state.name, argument);
				return true;
			}
		}

		private class PingHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				state.getConnection().send(ProtocolConstants.Room.COMMAND_PINGBACK + ProtocolConstants.ARGUMENT_SEPARATOR + argument);
				return true;
			}
		}

		private class InformPingHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(final PlayerState state, String argument) {
				try {
					int ping = Integer.parseInt(argument);
					final String message = ProtocolConstants.Room.COMMAND_INFORM_PING + ProtocolConstants.ARGUMENT_SEPARATOR + state.name
							+ ProtocolConstants.ARGUMENT_SEPARATOR + argument;
					forEachParticipant(new IClientStateAction<PlayerState>() {
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

		private class InformTunnelPortHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(PlayerState state, String argument) {
				try {
					int port = Integer.parseInt(argument);
					InetSocketAddress remoteEP = new InetSocketAddress(state.getConnection().getRemoteAddress().getAddress(), port);
					state.tunnelState = notYetLinkedTunnels.remove(remoteEP);

					if (state.tunnelState != null) {
						state.getConnection().send(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_UDP_PORT);
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
				String playerName;
				if (masterMacAddresses.containsKey(macAddress)) {
					playerName = masterName;
				} else {
					TunnelState tunnelState = tunnelsByMacAddress.get(macAddress);
					if (tunnelState == null)
						return true;
					if (Utility.isEmpty(tunnelState.playerName))
						return true;

					playerName = tunnelState.playerName;
				}

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(macAddress);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(playerName);

				state.connection.send(sb.toString());
				return true;
			}
		}

		private class InformSSIDHandler implements IServerMessageHandler<PlayerState> {
			@Override
			public boolean process(final PlayerState state, String argument) {
				state.ssid = argument;

				roomMasterHandler.ssidInformed(state.name, state.ssid);

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.NOTIFY_SSID_CHANGED);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(state.name);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(state.ssid);

				final String notify = sb.toString();
				forEachParticipant(new IClientStateAction<PlayerState>() {
					@Override
					public void action(PlayerState p) {
						if (p != state)
							p.getConnection().send(notify);
					}
				});

				return true;
			}
		}
	}

	private static class TunnelState implements IClientState {

		private ISocketConnection connection;
		private long lastTunnelTime;
		private String playerName;

		public TunnelState(ISocketConnection connection) {
			this.connection = connection;
		}

		@Override
		public ISocketConnection getConnection() {
			return connection;
		}
	}

	class TunnelHandler implements IAsyncServerHandler<TunnelState> {
		@Override
		public void serverStartupFinished() {
			MyRoomEngine.this.serverStartupFinished();
		}

		@Override
		public void serverShutdownFinished() {
			MyRoomEngine.this.serverShutdownFinished();
		}

		@Override
		public void log(String message) {
			roomMasterHandler.log(message);
		}

		@Override
		public TunnelState createState(ISocketConnection connection) {
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

			PlayerState playerSendFrom = playersByName.get(state.playerName);
			if (playerSendFrom == null)
				return true;
			boolean playerSendFromSsidIsNotEmpty = !Utility.isEmpty(playerSendFrom.ssid);

			String destMac = Utility.makeMacAddressString(packet, 0, false);
			String srcMac = Utility.makeMacAddressString(packet, 6, false);

			tunnelsByMacAddress.put(srcMac, state);

			if (Utility.isMacBroadCastAddress(destMac)) {
				roomMasterHandler.tunnelPacketReceived(packet, state.playerName);

				for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
					PlayerState playerSendTo = entry.getValue();
					if (playerSendTo.tunnelState == null || playerSendTo.tunnelState == state)
						continue;

					if (playerSendFromSsidIsNotEmpty && !Utility.isEmpty(playerSendTo.ssid))
						if (!playerSendFrom.ssid.equals(playerSendTo.ssid))
							continue;

					packet.position(0);
					playerSendTo.tunnelState.getConnection().send(packet);
					playerSendTo.tunnelState.lastTunnelTime = System.currentTimeMillis();
				}
			} else if (masterMacAddresses.containsKey(destMac)) {
				masterMacAddresses.put(destMac, System.currentTimeMillis());
				roomMasterHandler.tunnelPacketReceived(packet, state.playerName);
			} else if (tunnelsByMacAddress.containsKey(destMac)) {
				TunnelState tunnelSendTo = tunnelsByMacAddress.get(destMac);
				if (tunnelSendTo == null)
					return true;
				PlayerState playerSendTo = playersByName.get(tunnelSendTo.playerName);
				if (playerSendFromSsidIsNotEmpty && !Utility.isEmpty(playerSendTo.ssid))
					if (!playerSendFrom.ssid.equals(playerSendTo.ssid))
						return true;

				tunnelSendTo.getConnection().send(packet);
				tunnelSendTo.lastTunnelTime = System.currentTimeMillis();
			}

			return true;
		}
	}

	public void sendTunnelPacketToParticipants(ByteBuffer packet, String srcMac, String destMac) {
		masterMacAddresses.put(srcMac, System.currentTimeMillis());

		if (Utility.isMacBroadCastAddress(destMac)) {
			for (Entry<String, PlayerState> entry : playersByName.entrySet()) {
				PlayerState sendTo = entry.getValue();
				if (sendTo.tunnelState != null) {
					packet.position(0);
					sendTo.tunnelState.getConnection().send(packet);
					sendTo.tunnelState.lastTunnelTime = System.currentTimeMillis();
				}
			}
		} else if (tunnelsByMacAddress.containsKey(destMac)) {
			TunnelState sendTo = tunnelsByMacAddress.get(destMac);
			sendTo.getConnection().send(packet);
			sendTo.lastTunnelTime = System.currentTimeMillis();
		}
	}
}
