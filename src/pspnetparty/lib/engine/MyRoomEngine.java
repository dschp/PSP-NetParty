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
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import pspnetparty.lib.CountDownSynchronizer;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.AsyncUdpServer;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.IProtocolMessageHandler;
import pspnetparty.lib.socket.IServerListener;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.PacketData;
import pspnetparty.lib.socket.TextProtocolDriver;

public class MyRoomEngine {

	private ConcurrentSkipListMap<String, RoomProtocolDriver> playersByName;

	private HashMap<String, TunnelProtocolDriver> tunnelsByMacAddress;
	private HashMap<String, Object> masterMacAddresses;
	private final Object placeHolderValueObject = new Object();

	private ConcurrentHashMap<InetSocketAddress, TunnelProtocolDriver> notYetLinkedTunnels;

	private String masterName;
	private String masterSsid = "";

	private IMyRoomMasterHandler myRoomMasterHandler;

	private long createdTime;
	private int maxPlayers = 4;
	private String title;
	private String password = "";
	private String description = "";
	private String remarks = "";

	private boolean isMacAdressBlackListEnabled = false;
	private HashSet<String> macAddressWhiteList = new HashSet<String>();
	private boolean isMacAdressWhiteListEnabled = false;
	private HashSet<String> macAddressBlackList = new HashSet<String>();

	private String roomMasterAuthCode;
	private boolean allowEmptyMasterNameLogin = true;

	private AsyncTcpServer roomServer;
	private AsyncUdpServer tunnelServer;

	private boolean isStarted = false;
	private CountDownSynchronizer countDownSynchronizer;

	public MyRoomEngine(IMyRoomMasterHandler masterHandler) {
		this.myRoomMasterHandler = masterHandler;

		playersByName = new ConcurrentSkipListMap<String, MyRoomEngine.RoomProtocolDriver>();
		tunnelsByMacAddress = new HashMap<String, TunnelProtocolDriver>();
		masterMacAddresses = new HashMap<String, Object>();
		notYetLinkedTunnels = new ConcurrentHashMap<InetSocketAddress, TunnelProtocolDriver>(16, 0.75f, 2);

		IServerListener listener = new IServerListener() {
			@Override
			public void serverStartupFinished() {
				if (countDownSynchronizer.countDown() == 0) {
					isStarted = true;
					roomMasterAuthCode = Utility.makeAuthCode();
					createdTime = System.currentTimeMillis();

					myRoomMasterHandler.roomOpened();
				}
			}

			@Override
			public void serverShutdownFinished() {
				if (countDownSynchronizer.countDown() == 0) {
					isStarted = false;
					myRoomMasterHandler.roomClosed();
				}
			}

			@Override
			public void log(String message) {
				myRoomMasterHandler.log(message);
			}
		};

		ILogger logger = new ILogger() {
			@Override
			public void log(String message) {
				myRoomMasterHandler.log(message);
			}
		};

		roomServer = new AsyncTcpServer(logger, 40000);
		roomServer.addServerListener(listener);
		roomServer.addProtocol(new RoomProtocol());

		TunnelProtocol tunnelProtocol = new TunnelProtocol();
		roomServer.addProtocol(tunnelProtocol);

		tunnelServer = new AsyncUdpServer(logger);
		tunnelServer.addServerListener(listener);
		tunnelServer.addProtocol(tunnelProtocol);
	}

	public boolean isStarted() {
		return isStarted;
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

		macAddressWhiteList.clear();
		macAddressBlackList.clear();

		countDownSynchronizer = new CountDownSynchronizer(2);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		roomServer.startListening(bindAddress);
		tunnelServer.startListening(bindAddress);
	}

	public void closeRoom() {
		if (!isStarted())
			return;

		countDownSynchronizer = new CountDownSynchronizer(2);

		roomServer.stopListening();
		tunnelServer.stopListening();
	}

	public void enableMacAddressWhiteList(boolean enable) {
		isMacAdressWhiteListEnabled = enable;
	}

	public void addMacAddressToWhiteList(String macAddress) {
		macAddressWhiteList.add(macAddress);
	}

	public void removeMacAddressFromWhiteList(String macAddress) {
		macAddressWhiteList.remove(macAddress);
	}

	public void enableMacAddressBlackList(boolean enable) {
		isMacAdressBlackListEnabled = enable;
	}

	public void addMacAddressToBlackList(String macAddress) {
		macAddressBlackList.add(macAddress);
	}

	public void removeMacAddressFromBlackList(String macAddress) {
		macAddressBlackList.remove(macAddress);
	}

	private void appendRoomInfo(StringBuilder sb) {
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(masterName);
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
		sb.append(masterName);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(masterSsid);
		for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
			RoomProtocolDriver p = entry.getValue();
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(p.name);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(p.ssid);
		}
	}

	public void updateRoom() {
		StringBuilder sb = new StringBuilder();
		sb.append(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED);
		appendRoomInfo(sb);

		final String notify = sb.toString();

		for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
			RoomProtocolDriver p = entry.getValue();
			p.getConnection().send(notify);
		}
	}

	public void kickPlayer(String name) {
		RoomProtocolDriver kickedPlayer = playersByName.remove(name);
		if (kickedPlayer == null)
			return;
		final String notify = ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED + TextProtocolDriver.ARGUMENT_SEPARATOR + name;

		for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
			RoomProtocolDriver p = entry.getValue();
			p.getConnection().send(notify);
		}

		if (kickedPlayer.tunnel != null)
			notYetLinkedTunnels.remove(kickedPlayer.tunnel.getConnection().getRemoteAddress());

		kickedPlayer.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED + TextProtocolDriver.ARGUMENT_SEPARATOR + name);
		kickedPlayer.getConnection().disconnect();
	}

	public void sendChat(String text) {
		processChat(masterName, text);
	}

	public void informSSID(String ssid) {
		masterSsid = ssid != null ? ssid : "";

		StringBuilder sb = new StringBuilder();
		sb.append(ProtocolConstants.Room.NOTIFY_SSID_CHANGED);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(masterName);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(masterSsid);

		final String message = sb.toString();
		for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
			RoomProtocolDriver p = entry.getValue();
			p.getConnection().send(message);
		}
	}

	private void processChat(String player, String chat) {
		StringBuilder sb = new StringBuilder();
		sb.append(ProtocolConstants.Room.COMMAND_CHAT);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(player);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(chat);

		String message = sb.toString();
		for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
			RoomProtocolDriver p = entry.getValue();
			p.getConnection().send(message);
		}
		myRoomMasterHandler.chatReceived(player, chat);
	}

	public String getAuthCode() {
		return roomMasterAuthCode;
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

	public void setMaxPlayers(int maxPlayers) {
		if (maxPlayers < 2)
			throw new IllegalArgumentException();
		this.maxPlayers = maxPlayers;
	}

	public long getCreatedTime() {
		return createdTime;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		if (Utility.isEmpty(title))
			throw new IllegalArgumentException();
		this.title = title;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		if (Utility.isEmpty(password))
			password = "";
		this.password = password;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		if (Utility.isEmpty(description))
			description = "";
		this.description = description;
	}

	public String getRemarks() {
		return remarks;
	}

	public void setRemarks(String remarks) {
		if (Utility.isEmpty(remarks))
			remarks = "";
		this.remarks = remarks;
	}

	public boolean isAllowEmptyMasterNameLogin() {
		return allowEmptyMasterNameLogin;
	}

	public void setAllowEmptyMasterNameLogin(boolean allowEmptyMasterNameLogin) {
		this.allowEmptyMasterNameLogin = allowEmptyMasterNameLogin;
	}

	private class RoomProtocol implements IProtocol {
		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_ROOM;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			RoomProtocolDriver driver = new RoomProtocolDriver(connection);
			return driver;
		}

		@Override
		public void log(String message) {
			myRoomMasterHandler.log(message);
		}
	}

	private class RoomProtocolDriver extends TextProtocolDriver {
		private String name;
		private TunnelProtocolDriver tunnel;
		private String ssid = "";

		private RoomProtocolDriver(ISocketConnection connection) {
			super(connection, loginHandlers);
		}

		@Override
		public void log(String message) {
			myRoomMasterHandler.log(message);
		}

		@Override
		public void connectionDisconnected() {
			if (tunnel != null) {
				InetSocketAddress address = tunnel.getConnection().getRemoteAddress();
				if (address != null)
					notYetLinkedTunnels.remove(address);
			}

			if (!Utility.isEmpty(name)) {
				playersByName.remove(name);

				final String notify = ProtocolConstants.Room.NOTIFY_USER_EXITED + TextProtocolDriver.ARGUMENT_SEPARATOR + name;
				for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
					RoomProtocolDriver p = entry.getValue();
					p.getConnection().send(notify);
				}
				myRoomMasterHandler.playerExited(name);
			}
		}

		@Override
		public void errorProtocolNumber(String number) {
		}
	}

	private HashMap<String, IProtocolMessageHandler> loginHandlers = new HashMap<String, IProtocolMessageHandler>();
	private HashMap<String, IProtocolMessageHandler> sessionHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		loginHandlers.put(ProtocolConstants.Room.COMMAND_LOGIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				// LI loginName "masterName" password
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);

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
						player.getConnection().send(ProtocolConstants.Room.NOTIFY_ROOM_PASSWORD_REQUIRED);
						return true;
					}
					if (!MyRoomEngine.this.password.equals(sentPassword)) {
						player.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_PASSWORD_FAIL);
						return true;
					}
				}

				if (masterName.equals(loginName)) {
					player.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}

				if (playersByName.size() >= maxPlayers - 1) {
					// 最大人数を超えたので接続を拒否します
					player.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_BEYOND_CAPACITY);
					return false;
				}

				if (playersByName.putIfAbsent(loginName, player) != null) {
					// 同名のユーザーが存在するので接続を拒否します
					player.getConnection().send(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME);
					return false;
				}
				player.setMessageHandlers(sessionHandlers);
				player.name = loginName;

				final String notify = ProtocolConstants.Room.NOTIFY_USER_ENTERED + TextProtocolDriver.ARGUMENT_SEPARATOR + loginName;
				for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
					RoomProtocolDriver p = entry.getValue();
					if (p != player)
						p.getConnection().send(notify);
				}
				myRoomMasterHandler.playerEntered(loginName);

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Room.COMMAND_LOGIN);
				appendRoomInfo(sb);

				sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
				appendNotifyUserList(sb);

				player.getConnection().send(sb.toString());

				return true;
			}
		});
		loginHandlers.put(ProtocolConstants.Room.COMMAND_LOGOUT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				return false;
			}
		});
		loginHandlers.put(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// CAC masterName authCode
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return false;

				String masterName = tokens[0];
				String authCode = tokens[1];
				if (masterName.equals(MyRoomEngine.this.masterName) && authCode.equals(roomMasterAuthCode)) {
					driver.getConnection().send(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE);
				} else {
					driver.getConnection().send(ProtocolConstants.Room.ERROR_CONFIRM_INVALID_AUTH_CODE);
				}
				return false;
			}
		});

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

				processChat(player.name, argument);
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_PING, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				driver.getConnection().send(ProtocolConstants.Room.COMMAND_PINGBACK + TextProtocolDriver.ARGUMENT_SEPARATOR + argument);
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_PING, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver state = (RoomProtocolDriver) driver;

				try {
					int ping = Integer.parseInt(argument);
					final String message = ProtocolConstants.Room.COMMAND_INFORM_PING + TextProtocolDriver.ARGUMENT_SEPARATOR + state.name
							+ TextProtocolDriver.ARGUMENT_SEPARATOR + argument;
					for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
						RoomProtocolDriver p = entry.getValue();
						if (p != state)
							p.getConnection().send(message);
					}
					myRoomMasterHandler.pingInformed(state.name, ping);
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_PORT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver player = (RoomProtocolDriver) driver;

				try {
					int port = Integer.parseInt(argument);
					InetSocketAddress remoteEP = new InetSocketAddress(player.getConnection().getRemoteAddress().getAddress(), port);

					TunnelProtocolDriver tunnel = notYetLinkedTunnels.remove(remoteEP);
					player.tunnel = tunnel;
					if (tunnel != null) {
						player.getConnection().send(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_PORT);
						tunnel.player = player;
					}
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String macAddress) {
				String playerName;
				if (masterMacAddresses.containsKey(macAddress)) {
					playerName = masterName;
				} else {
					TunnelProtocolDriver tunnel = tunnelsByMacAddress.get(macAddress);
					if (tunnel == null)
						return true;
					RoomProtocolDriver player = tunnel.player;
					if (player == null)
						return true;

					playerName = player.name;
				}

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(macAddress);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(playerName);

				driver.getConnection().send(sb.toString());
				return true;
			}
		});
		sessionHandlers.put(ProtocolConstants.Room.COMMAND_INFORM_SSID, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				RoomProtocolDriver state = (RoomProtocolDriver) driver;

				state.ssid = argument;

				myRoomMasterHandler.ssidInformed(state.name, state.ssid);

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.NOTIFY_SSID_CHANGED);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(state.name);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(state.ssid);

				final String notify = sb.toString();
				for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
					RoomProtocolDriver p = entry.getValue();
					if (p != state)
						p.getConnection().send(notify);
				}

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
			TunnelProtocolDriver driver = new TunnelProtocolDriver();
			driver.connection = connection;
			notYetLinkedTunnels.put(connection.getRemoteAddress(), driver);
			return driver;
		}

		@Override
		public void log(String message) {
			myRoomMasterHandler.log(message);
		}
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

	private class TunnelProtocolDriver implements IProtocolDriver {
		private ISocketConnection connection;
		private RoomProtocolDriver player;

		@Override
		public ISocketConnection getConnection() {
			return connection;
		}

		@Override
		public boolean process(PacketData data) {
			InetSocketAddress remoteEP = connection.getRemoteAddress();

			ByteBuffer packet = data.getBuffer();
			if (!Utility.isPspPacket(packet)) {
				if (notYetLinkedTunnels.containsKey(remoteEP)) {
					connection.send(Integer.toString(remoteEP.getPort()));
				}
				return true;
			}

			RoomProtocolDriver srcPlayer = player;
			if (srcPlayer == null)
				return true;
			boolean srcPlayerSsidIsNotEmpty = !Utility.isEmpty(srcPlayer.ssid);

			String destMac = Utility.macAddressToString(packet, 0, false);
			String srcMac = Utility.macAddressToString(packet, 6, false);

			tunnelsByMacAddress.put(srcMac, this);

			if (Utility.isMacBroadCastAddress(destMac)) {
				if (testWhiteListBlackList(srcMac))
					return true;

				String srcPlayerName = srcPlayer.name;
				if (!Utility.isEmpty(srcPlayerName))
					srcPlayerName = "";
				myRoomMasterHandler.tunnelPacketReceived(packet, srcPlayerName);

				for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
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
			} else if (masterMacAddresses.containsKey(destMac)) {
				if (testWhiteListBlackList(srcMac))
					return true;

				masterMacAddresses.put(destMac, placeHolderValueObject);
				myRoomMasterHandler.tunnelPacketReceived(packet, player.name);
			} else if (tunnelsByMacAddress.containsKey(destMac)) {
				if (testWhiteListBlackList(srcMac, destMac))
					return true;

				TunnelProtocolDriver destTunnel = tunnelsByMacAddress.get(destMac);
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
			player = null;

			InetSocketAddress address = connection.getRemoteAddress();
			if (address != null)
				notYetLinkedTunnels.remove(address);
		}

		@Override
		public void errorProtocolNumber(String number) {
		}
	}

	public void sendTunnelPacketToParticipants(ByteBuffer packet, String srcMac, String destMac) {
		masterMacAddresses.put(srcMac, placeHolderValueObject);

		if (Utility.isMacBroadCastAddress(destMac)) {
			for (Entry<String, RoomProtocolDriver> entry : playersByName.entrySet()) {
				RoomProtocolDriver sendTo = entry.getValue();
				if (sendTo.tunnel != null) {
					packet.position(0);
					sendTo.tunnel.getConnection().send(packet);
				}
			}
		} else if (tunnelsByMacAddress.containsKey(destMac)) {
			TunnelProtocolDriver sendTo = tunnelsByMacAddress.get(destMac);
			sendTo.getConnection().send(packet);
		}
	}
}
