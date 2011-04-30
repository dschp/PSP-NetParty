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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;

import pspnetparty.lib.ICommandHandler;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniPublicServer;
import pspnetparty.lib.engine.PortalEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.IProtocol;

public class PortalServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s ポータルサーバー  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("プロトコル: " + IProtocol.NUMBER);

		String iniFileName = "PortalServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("設定INIファイル名: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 50000);
		if (port < 1 || port > 65535) {
			System.out.println("ポート番号が不正です: " + port);
			return;
		}
		System.out.println("ポート: " + port);

		ini.saveToIni();

		ILogger logger = ServerUtils.createLogger();
		AsyncTcpServer tcpServer = new AsyncTcpServer(logger, 40000);

		final PortalEngine engine = new PortalEngine(tcpServer, logger);

		connect(engine);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tサーバーを終了させる");
				System.out.println("status\n\t現在のサーバーの状態を表示");
				System.out.println("rooms\n\t保持している部屋情報の一覧");
				System.out.println("server active\n\t接続中のサーバーの一覧");
				System.out.println("server dead\n\t接続していないサーバーの一覧");
				System.out.println("server reload\n\tサーバーリストを再読み込みして接続を更新する");
				System.out.println("reconnect\n\t接続していないサーバーと再接続を試みる");
			}
		});
		handlers.put("status", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("ポート: " + port);
				System.out.println(engine.statusToString());
			}
		});
		handlers.put("rooms", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println(engine.allRoomsToString());
			}
		});
		handlers.put("server", new ICommandHandler() {
			@Override
			public void process(String argument) {
				if ("active".equalsIgnoreCase(argument)) {
					System.out.println("[接続中のルームサーバーの一覧]");
					printList(engine.listActiveRoomServers());
					System.out.println("[接続中の検索サーバーの一覧]");
					printList(engine.listActiveSearchServers());
					System.out.println("[接続中のロビーサーバーの一覧]");
					printList(engine.listActiveLobbyServers());
				} else if ("dead".equalsIgnoreCase(argument)) {
					System.out.println("[切断されたルームサーバーの一覧]");
					printList(engine.listDeadRoomServers());
					System.out.println("[切断された検索サーバーの一覧]");
					printList(engine.listDeadSearchServers());
					System.out.println("[切断されたロビーサーバーの一覧]");
					printList(engine.listDeadLobbyServers());
				} else if ("reload".equalsIgnoreCase(argument)) {
					try {
						connect(engine);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					return;
				}
			}
		});
		handlers.put("reconnect", new ICommandHandler() {
			@Override
			public void process(String argument) {
				engine.reconnectNow();
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
	}

	private static void printList(String[] list) {
		for (String s : list)
			System.out.println(s);
	}

	private static void connect(PortalEngine engine) throws IOException {
		IniPublicServer publicServer = new IniPublicServer();
		HashSet<String> addresses = new HashSet<String>();

		for (String address : publicServer.getRoomServers()) {
			addresses.add(address);
		}
		engine.connectRoomServers(addresses);

		addresses.clear();
		for (String address : publicServer.getSearchServers()) {
			addresses.add(address);
		}
		engine.connectSearchServers(addresses);

		addresses.clear();
		for (String address : publicServer.getLobbyServers()) {
			addresses.add(address);
		}
		engine.connectLobbyServers(addresses);
	}
}
