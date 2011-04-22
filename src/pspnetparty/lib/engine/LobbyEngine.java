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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import pspnetparty.lib.ILogger;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.IniPublicServer;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.IProtocolMessageHandler;
import pspnetparty.lib.socket.IServer;
import pspnetparty.lib.socket.IServerListener;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.TextProtocolDriver;

public class LobbyEngine {

	private ILogger logger;
	private File loginMessageFile;

	private String title = "未設定";
	private int maxUsers = Integer.MAX_VALUE;
	private ConcurrentHashMap<String, LobbyProtocolDriver> loginUsers;

	private IniPublicServer iniPublicServer;
	private ConcurrentHashMap<LobbyStatusProtocolDriver, Object> portalConnections;
	private boolean isAcceptingPortal;

	public LobbyEngine(IServer server, ILogger logger) throws IOException {
		this.logger = logger;

		iniPublicServer = new IniPublicServer();

		loginUsers = new ConcurrentHashMap<String, LobbyProtocolDriver>();
		portalConnections = new ConcurrentHashMap<LobbyStatusProtocolDriver, Object>();

		server.addServerListener(new IServerListener() {
			@Override
			public void log(String message) {
				LobbyEngine.this.logger.log(message);
			}

			@Override
			public void serverStartupFinished() {
			}

			@Override
			public void serverShutdownFinished() {
			}
		});
		server.addProtocol(new LobbyProtocol());
		server.addProtocol(new LobbyStatusProtocol());
	}

	public void setLoginMessageFile(String loginMessageFile) {
		if (Utility.isEmpty(loginMessageFile)) {
			this.loginMessageFile = null;
		} else {
			this.loginMessageFile = new File(loginMessageFile);
		}
	}

	private void appendLoginMessage(StringBuilder sb) {
		String loginMessage = Utility.getFileContent(loginMessageFile);
		if (!Utility.isEmpty(loginMessage)) {
			sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
			sb.append(ProtocolConstants.Lobby.NOTIFY_FROM_ADMIN);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(loginMessage);
		}
	}

	public Set<InetSocketAddress> getPortalAddresses() {
		HashSet<InetSocketAddress> addresses = new HashSet<InetSocketAddress>();
		for (LobbyStatusProtocolDriver portal : portalConnections.keySet()) {
			addresses.add(portal.getConnection().getRemoteAddress());
		}
		return addresses;
	}

	public boolean isAcceptingPortal() {
		return isAcceptingPortal;
	}

	public void setAcceptingPortal(boolean isAcceptingPortal) {
		if (!isAcceptingPortal) {
			for (LobbyStatusProtocolDriver portal : portalConnections.keySet()) {
				portal.getConnection().disconnect();
			}
			portalConnections.clear();
		}

		this.isAcceptingPortal = isAcceptingPortal;
	}

	public int getCurrentPlayers() {
		return loginUsers.size();
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		if (Utility.isEmpty(title))
			return;
		if (title.equals(this.title))
			return;

		this.title = title;

		StringBuilder sb = new StringBuilder();
		appendNotifyLobbyInfoForClient(sb);

		String notify = sb.toString();
		for (Entry<String, LobbyProtocolDriver> e : loginUsers.entrySet()) {
			LobbyProtocolDriver user = e.getValue();
			user.getConnection().send(notify);
		}

		sb.delete(0, sb.length());
		appendNotifyLobbyInfoForPortal(sb);
		notify = sb.toString();
		for (LobbyStatusProtocolDriver portal : portalConnections.keySet()) {
			portal.getConnection().send(notify);
		}
	}

	public void notifyAllUsers(String message) {
		String notify = ProtocolConstants.Lobby.NOTIFY_FROM_ADMIN + TextProtocolDriver.ARGUMENT_SEPARATOR + message;

		for (Entry<String, LobbyProtocolDriver> entry : loginUsers.entrySet()) {
			LobbyProtocolDriver user = entry.getValue();
			user.getConnection().send(notify);
		}
	}

	private void appendNotifyLobbyInfoForClient(StringBuilder sb) {
		sb.append(ProtocolConstants.Lobby.NOTIFY_LOBBY_INFO);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(title);
	}

	private void appendNotifyLobbyInfoForPortal(StringBuilder sb) {
		sb.append(ProtocolConstants.LobbyStatus.NOTIFY_LOBBY_INFO);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(title);
	}

	private void appendServerStatus(StringBuilder sb) {
		sb.append(ProtocolConstants.SERVER_STATUS);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(loginUsers.size());
	}

	private class LobbyProtocol implements IProtocol {
		@Override
		public void log(String message) {
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_LOBBY;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			LobbyProtocolDriver driver = new LobbyProtocolDriver(connection);
			return driver;
		}
	}

	private class LobbyProtocolDriver extends TextProtocolDriver {
		private String name;
		private LobbyUserState state = LobbyUserState.OFFLINE;

		public LobbyProtocolDriver(ISocketConnection connection) {
			super(connection, lobbyLoginHandlers);
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public void connectionDisconnected() {
			if (Utility.isEmpty(name))
				return;

			loginUsers.remove(name);

			String notify = ProtocolConstants.Lobby.NOTIFY_USER_LOGOUT + TextProtocolDriver.ARGUMENT_SEPARATOR + name;
			for (Entry<String, LobbyProtocolDriver> e : loginUsers.entrySet()) {
				LobbyProtocolDriver user = e.getValue();

				user.getConnection().send(notify);
			}

			StringBuilder sb = new StringBuilder();
			appendServerStatus(sb);

			notify = sb.toString();
			for (LobbyStatusProtocolDriver d : portalConnections.keySet()) {
				d.getConnection().send(notify);
			}
		}

		@Override
		public void errorProtocolNumber(String number) {
		}

		private void appendUserInfo(StringBuilder sb) {
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(name);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(state.getAbbreviation());
		}
	}

	private final HashMap<String, IProtocolMessageHandler> lobbyLoginHandlers = new HashMap<String, IProtocolMessageHandler>();
	private final HashMap<String, IProtocolMessageHandler> lobbyHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		lobbyLoginHandlers.put(ProtocolConstants.Lobby.COMMAND_LOGIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String origName) {
				if (!Utility.isValidUserName(origName))
					return false;
				if (loginUsers.size() > maxUsers) {
					driver.getConnection().send(ProtocolConstants.Lobby.ERROR_LOGIN_USER_BEYOND_CAPACITY);
					return false;
				}

				LobbyProtocolDriver user = (LobbyProtocolDriver) driver;
				String name = origName;
				boolean success = false;
				for (int i = 2; i < 10; i++) {
					if (loginUsers.putIfAbsent(name, user) == null) {
						success = true;
						break;
					}
					name = origName + i;
				}
				if (!success) {
					driver.getConnection().send(ProtocolConstants.Lobby.ERROR_LOGIN_USER_DUPLICATED_NAME);
					return false;
				}

				user.name = name;
				user.state = LobbyUserState.LOGIN;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Lobby.NOTIFY_USER_INFO);
				user.appendUserInfo(sb);
				String notify = sb.toString();

				sb.delete(0, sb.length());

				if (loginUsers.size() > 1) {
					sb.append(ProtocolConstants.Lobby.NOTIFY_USER_LIST);
					for (Entry<String, LobbyProtocolDriver> e : loginUsers.entrySet()) {
						LobbyProtocolDriver u = e.getValue();
						if (u == user)
							continue;

						u.getConnection().send(notify);

						u.appendUserInfo(sb);
					}
					sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
				}
				sb.append(ProtocolConstants.Lobby.COMMAND_LOGIN);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(name);
				appendLoginMessage(sb);

				sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
				appendNotifyLobbyInfoForClient(sb);

				user.getConnection().send(sb.toString());
				user.setMessageHandlers(lobbyHandlers);

				sb.delete(0, sb.length());
				appendServerStatus(sb);

				notify = sb.toString();
				for (LobbyStatusProtocolDriver d : portalConnections.keySet()) {
					d.getConnection().send(notify);
				}

				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_LOGOUT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				return false;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_CHAT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				LobbyProtocolDriver user = (LobbyProtocolDriver) driver;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Lobby.COMMAND_CHAT);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(user.name);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(argument);
				String notify = sb.toString();

				for (Entry<String, LobbyProtocolDriver> e : loginUsers.entrySet()) {
					LobbyProtocolDriver u = e.getValue();

					u.getConnection().send(notify);
				}
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_PRIVATE_CHAT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				LobbyProtocolDriver user = (LobbyProtocolDriver) driver;

				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR);
				if (tokens.length != 2)
					return true;

				LobbyProtocolDriver sendTo = loginUsers.get(tokens[0]);
				if (sendTo == null || user == sendTo)
					return true;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Lobby.COMMAND_PRIVATE_CHAT);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(user.name);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(tokens[1]);
				String notify = sb.toString();

				sendTo.getConnection().send(notify);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_CHANGE_STATE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				LobbyProtocolDriver user = (LobbyProtocolDriver) driver;

				LobbyUserState state = LobbyUserState.findState(argument);
				if (state == null)
					return false;
				switch (state) {
				case OFFLINE:
					return false;
				}

				user.state = state;

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Lobby.NOTIFY_USER_INFO);
				user.appendUserInfo(sb);

				String notify = sb.toString();
				for (Entry<String, LobbyProtocolDriver> e : loginUsers.entrySet()) {
					LobbyProtocolDriver u = e.getValue();

					u.getConnection().send(notify);
				}
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_CHANGE_NAME, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String name) {
				LobbyProtocolDriver user = (LobbyProtocolDriver) driver;
				if (user.name.equals(name))
					return true;
				if (!Utility.isValidUserName(name))
					return false;

				if (loginUsers.putIfAbsent(name, user) != null) {
					driver.getConnection().send(ProtocolConstants.Lobby.ERROR_LOGIN_USER_DUPLICATED_NAME);
					return true;
				}
				String oldName = user.name;
				loginUsers.remove(oldName);

				user.name = name;

				String notify = ProtocolConstants.Lobby.NOTIFY_USER_NAME_CHANGED + TextProtocolDriver.ARGUMENT_SEPARATOR + oldName
						+ TextProtocolDriver.ARGUMENT_SEPARATOR + name;

				for (Entry<String, LobbyProtocolDriver> e : loginUsers.entrySet()) {
					LobbyProtocolDriver u = e.getValue();

					u.getConnection().send(notify);
				}
				return true;
			}
		});
	}

	private class LobbyStatusProtocol implements IProtocol {
		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_LOBBY_STATUS;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			iniPublicServer.reload();
			if (!iniPublicServer.isValidPortalServer(connection.getRemoteAddress().getAddress()))
				return null;

			LobbyStatusProtocolDriver driver = new LobbyStatusProtocolDriver(connection);

			logger.log("ポータルから接続されました: " + driver.address);

			StringBuilder sb = new StringBuilder();
			appendNotifyLobbyInfoForPortal(sb);
			sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
			appendServerStatus(sb);

			connection.send(sb.toString());

			portalConnections.put(driver, this);
			return driver;
		}
	}

	private class LobbyStatusProtocolDriver extends TextProtocolDriver {
		private String address;

		public LobbyStatusProtocolDriver(ISocketConnection connection) {
			super(connection, portalHandlers);

			address = Utility.socketAddressToStringByIP(getConnection().getRemoteAddress());
		}

		@Override
		public void connectionDisconnected() {
			portalConnections.remove(this);
			logger.log("ポータルから切断されました: " + address);
		}

		@Override
		public void errorProtocolNumber(String number) {
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}
	}

	private final HashMap<String, IProtocolMessageHandler> portalHandlers;
	{
		portalHandlers = new HashMap<String, IProtocolMessageHandler>();
	}
}
