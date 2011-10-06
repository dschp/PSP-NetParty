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
import pspnetparty.lib.LobbyUser;
import pspnetparty.lib.LobbyUserState;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.IServerRegistry;
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
	private int maxUsers = Integer.MAX_VALUE;

	private ConcurrentHashMap<String, LobbyProtocolDriver> loginUsers;
	private ConcurrentHashMap<String, ConcurrentHashMap<String, LobbyProtocolDriver>> circleMap;

	private IServerRegistry serverNetWork;
	private ConcurrentHashMap<LobbyStatusProtocolDriver, Object> portalConnections;
	private boolean isAcceptingPortal = true;

	public LobbyEngine(IServer server, ILogger logger, IServerRegistry net) throws IOException {
		this.logger = logger;

		serverNetWork = net;
		portalConnections = new ConcurrentHashMap<LobbyStatusProtocolDriver, Object>();

		loginUsers = new ConcurrentHashMap<String, LobbyProtocolDriver>();
		circleMap = new ConcurrentHashMap<String, ConcurrentHashMap<String, LobbyProtocolDriver>>();

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

	public void notifyAllUsers(String message) {
		String notify = ProtocolConstants.Lobby.NOTIFY_FROM_ADMIN + TextProtocolDriver.ARGUMENT_SEPARATOR + message;

		for (Entry<String, LobbyProtocolDriver> entry : loginUsers.entrySet()) {
			LobbyProtocolDriver user = entry.getValue();
			user.getConnection().send(notify);
		}
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
		private LobbyUser bean;

		public LobbyProtocolDriver(ISocketConnection connection) {
			super(connection, lobbyLoginHandlers);
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public void connectionDisconnected() {
			if (bean == null)
				return;
			String name = bean.getName();
			loginUsers.remove(name);

			for (ConcurrentHashMap<String, LobbyProtocolDriver> members : circleMap.values()) {
				members.remove(name);
			}

			final String notify = ProtocolConstants.Lobby.NOTIFY_LOGOUT + TextProtocolDriver.ARGUMENT_SEPARATOR + name;
			for (Entry<String, LobbyProtocolDriver> e : loginUsers.entrySet()) {
				LobbyProtocolDriver user = e.getValue();

				user.getConnection().send(notify);
			}
		}

		@Override
		public void errorProtocolNumber(String number) {
		}

		private void appendUserInitializer(StringBuilder sb) {
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(bean.getName());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(bean.getState().getAbbreviation());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(bean.getUrl());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(bean.getIconUrl());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(bean.getProfile());

			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			for (String circle : bean.getCircles()) {
				sb.append(circle).append("\n");
			}
		}

		private void appendUserProfile(StringBuilder sb) {
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(bean.getName());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(bean.getUrl());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(bean.getIconUrl());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(bean.getProfile());
		}

		private void appendUserState(StringBuilder sb) {
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(bean.getName());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(bean.getState().getAbbreviation());
		}
	}

	private final HashMap<String, IProtocolMessageHandler> lobbyLoginHandlers = new HashMap<String, IProtocolMessageHandler>();
	private final HashMap<String, IProtocolMessageHandler> lobbyHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		lobbyLoginHandlers.put(ProtocolConstants.Lobby.COMMAND_LOGIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 4)
					return false;

				String name = tokens[0];
				if (!Utility.isValidNameString(name))
					return false;
				if (loginUsers.size() > maxUsers) {
					driver.getConnection().send(ProtocolConstants.Lobby.ERROR_LOGIN_USER_BEYOND_CAPACITY);
					return false;
				}

				String url = tokens[1].trim();
				String iconUrl = tokens[2].trim();
				String profile = tokens[3].trim();

				LobbyProtocolDriver user = (LobbyProtocolDriver) driver;

				if (loginUsers.putIfAbsent(name, user) != null) {
					driver.getConnection().send(ProtocolConstants.Lobby.ERROR_LOGIN_USER_DUPLICATED_NAME);
					return false;
				}

				user.bean = new LobbyUser(name, LobbyUserState.LOGIN);
				user.bean.setUrl(url);
				user.bean.setIconUrl(iconUrl);
				user.bean.setProfile(profile);

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Lobby.NOTIFY_LOGIN);
				user.appendUserProfile(sb);
				final String loginNotify = sb.toString();

				sb.delete(0, sb.length());

				sb.append(ProtocolConstants.Lobby.COMMAND_LOGIN);
				for (Entry<String, LobbyProtocolDriver> e : loginUsers.entrySet()) {
					LobbyProtocolDriver u = e.getValue();
					if (u == user)
						continue;

					u.getConnection().send(loginNotify);

					u.appendUserInitializer(sb);
				}
				appendLoginMessage(sb);

				user.setMessageHandlers(lobbyHandlers);
				user.getConnection().send(sb.toString());

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

				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return true;

				String circle = tokens[0];
				String message = tokens[1];

				ConcurrentHashMap<String, LobbyProtocolDriver> list;
				if (Utility.isEmpty(circle)) {
					list = loginUsers;
				} else {
					list = circleMap.get(circle);
					if (list == null)
						return true;
				}

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Lobby.COMMAND_CHAT);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(user.bean.getName());
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(circle);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(message);
				String notify = sb.toString();

				for (LobbyProtocolDriver u : list.values()) {
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
				sb.append(user.bean.getName());
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

				user.bean.setState(state);

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Lobby.NOTIFY_STATE_CHANGE);
				user.appendUserState(sb);

				String notify = sb.toString();
				for (Entry<String, LobbyProtocolDriver> e : loginUsers.entrySet()) {
					LobbyProtocolDriver u = e.getValue();

					u.getConnection().send(notify);
				}
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_UPDATE_PROFILE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				LobbyProtocolDriver user = (LobbyProtocolDriver) driver;

				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 3)
					return true;

				user.bean.setUrl(tokens[0].trim());
				user.bean.setIconUrl(tokens[1].trim());
				user.bean.setProfile(tokens[2].trim());

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.Lobby.NOTIFY_PROFILE_UPDATE);
				user.appendUserProfile(sb);

				String notify = sb.toString();
				for (Entry<String, LobbyProtocolDriver> e : loginUsers.entrySet()) {
					LobbyProtocolDriver u = e.getValue();

					u.getConnection().send(notify);
				}

				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_CIRCLE_JOIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 1)
					return true;
				String circleName = tokens[0];
				if (!Utility.isValidNameString(circleName))
					return true;

				LobbyProtocolDriver user = (LobbyProtocolDriver) driver;

				ConcurrentHashMap<String, LobbyProtocolDriver> members = circleMap.get(circleName);
				if (members == null) {
					members = new ConcurrentHashMap<String, LobbyProtocolDriver>();
					circleMap.put(circleName, members);
				}
				String name = user.bean.getName();
				if (members.putIfAbsent(name, user) != null)
					return true;
				user.bean.addCircle(circleName);

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Lobby.NOTIFY_CIRCLE_JOIN);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(name);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(circleName);

				String notify = sb.toString();
				for (Entry<String, LobbyProtocolDriver> e : loginUsers.entrySet()) {
					LobbyProtocolDriver u = e.getValue();

					u.getConnection().send(notify);
				}

				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_CIRCLE_LEAVE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 1)
					return true;
				String circleName = tokens[0];
				if (!Utility.isValidNameString(circleName))
					return true;

				LobbyProtocolDriver user = (LobbyProtocolDriver) driver;

				ConcurrentHashMap<String, LobbyProtocolDriver> members = circleMap.get(circleName);
				if (members == null) {
					return true;
				}

				String name = user.bean.getName();
				if (members.remove(name) == null)
					return true;
				user.bean.removeCircle(circleName);

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Lobby.NOTIFY_CIRCLE_LEAVE);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(name);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(circleName);

				String notify = sb.toString();
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
			if (!isAcceptingPortal)
				return null;
			serverNetWork.reload();
			if (!serverNetWork.isValidPortalServer(connection.getRemoteAddress().getAddress()))
				return null;

			LobbyStatusProtocolDriver driver = new LobbyStatusProtocolDriver(connection);

			logger.log("ポータルから接続されました: " + driver.address);

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
