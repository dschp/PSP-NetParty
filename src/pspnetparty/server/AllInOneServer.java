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
package pspnetparty.server;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import pspnetparty.lib.ICommandHandler;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IServerRegistry;
import pspnetparty.lib.engine.LobbyEngine;
import pspnetparty.lib.engine.PortalEngine;
import pspnetparty.lib.engine.RoomEngine;
import pspnetparty.lib.engine.SearchEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.AsyncUdpServer;
import pspnetparty.lib.socket.IProtocol;

public class AllInOneServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s オールインワンサーバー  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("プロトコル: " + IProtocol.NUMBER);

		String iniFileName = "AllInOneServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("設定INIファイル名: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 20000);
		if (port < 1 || port > 65535) {
			System.out.println("ポート番号が不正です: " + port);
			return;
		}
		System.out.println("ポート: " + port);

		String hostname = settings.get("HostName", "localhost");
		if (Utility.isEmpty(hostname)) {
			System.out.println("ホスト名が設定されていません: " + hostname);
			return;
		}
		System.out.println("ホスト名: " + hostname);

		final String loginMessageFile = settings.get(IniConstants.LOGIN_MESSAGE_FILE, "");
		System.out.println("ルームログインメッセージファイル : " + loginMessageFile);

		ILogger logger = ServerUtils.createLogger();

		AsyncTcpServer tcpServer = new AsyncTcpServer(100000);
		AsyncUdpServer udpServer = new AsyncUdpServer();

		IServerRegistry registry = new IServerRegistry() {
			@Override
			public void reload() {
			}

			@Override
			public boolean isValidPortalServer(InetAddress address) {
				return true;
			}

			@Override
			public String[] getPortalServers() {
				return null;
			}

			@Override
			public String[] getRoomServers() {
				return null;
			}

			@Override
			public String[] getSearchServers() {
				return null;
			}

			@Override
			public String[] getLobbyServers() {
				return null;
			}

			@Override
			public Iterator<String> getPortalRotator() {
				return null;
			}
		};

		{
			int maxRooms = settings.get(IniConstants.MAX_ROOMS, 10);
			if (maxRooms < 1) {
				System.out.println("部屋数が不正です: " + maxRooms);
				return;
			}
			System.out.println("最大部屋数: " + maxRooms);

			RoomEngine roomEngine = new RoomEngine(tcpServer, udpServer, logger, registry);
			roomEngine.setMaxRooms(maxRooms);
			roomEngine.setLoginMessageFile(loginMessageFile);
		}
		{
			int maxUsers = settings.get(IniConstants.MAX_USERS, 30);
			if (maxUsers < 1) {
				System.out.println("最大検索ユーザー数が不正です: " + maxUsers);
				return;
			}
			System.out.println("最大検索ユーザー数: " + maxUsers);

			int maxSearchResults = settings.get(IniConstants.MAX_SEARCH_RESULTS, 50);
			if (maxSearchResults < 1) {
				System.out.println("最大検索件数が不正です: " + maxSearchResults);
				return;
			}
			System.out.println("最大検索件数: " + maxSearchResults);

			int descriptionMaxLength = settings.get(IniConstants.DESCRIPTION_MAX_LENGTH, 100);
			if (descriptionMaxLength < 1) {
				System.out.println("部屋の詳細・備考の最大サイズが不正です: " + descriptionMaxLength);
				return;
			}
			System.out.println("部屋の詳細・備考の最大文字数: " + descriptionMaxLength);

			SearchEngine searchEngine = new SearchEngine(tcpServer, logger, registry);
			searchEngine.setMaxUsers(maxUsers);
			searchEngine.setMaxSearchResults(maxSearchResults);
			searchEngine.setDescriptionMaxLength(descriptionMaxLength);
		}
		{
			LobbyEngine lobbyEngine = new LobbyEngine(tcpServer, logger, registry);
			lobbyEngine.setLoginMessageFile(loginMessageFile);
		}

		ini.saveToIni();

		PortalEngine portalEngine = new PortalEngine(tcpServer, logger);

		String address = hostname + ":" + port;
		HashSet<String> addresses = new HashSet<String>();
		addresses.add(address);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);
		udpServer.startListening(bindAddress);

		portalEngine.connectRoomServers(addresses);
		portalEngine.connectSearchServers(addresses);
		portalEngine.connectLobbyServers(addresses);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tサーバーを終了させる");
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
		udpServer.stopListening();
	}
}
