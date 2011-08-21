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

import java.net.InetSocketAddress;
import java.util.HashMap;

import pspnetparty.lib.ICommandHandler;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniPublicServer;
import pspnetparty.lib.engine.RoomEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.AsyncUdpServer;
import pspnetparty.lib.socket.IProtocol;

public class RoomServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s ルームサーバー  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("プロトコル: " + IProtocol.NUMBER);

		String iniFileName = "RoomServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("設定INIファイル名: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 30000);
		if (port < 1 || port > 65535) {
			System.out.println("ポート番号が不正です: " + port);
			return;
		}
		System.out.println("ポート: " + port);

		int maxRooms = settings.get(IniConstants.MAX_ROOMS, 10);
		if (maxRooms < 1) {
			System.out.println("部屋数が不正です: " + maxRooms);
			return;
		}
		System.out.println("最大部屋数: " + maxRooms);

		final String loginMessageFile = settings.get(IniConstants.LOGIN_MESSAGE_FILE, "");
		System.out.println("ログインメッセージファイル : " + loginMessageFile);

		ini.saveToIni();

		ILogger logger = ServerUtils.createLogger();
		AsyncTcpServer tcpServer = new AsyncTcpServer(40000);
		AsyncUdpServer udpServer = new AsyncUdpServer();

		final RoomEngine engine = new RoomEngine(tcpServer, udpServer, logger, new IniPublicServer());
		engine.setMaxRooms(maxRooms);
		engine.setLoginMessageFile(loginMessageFile);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);
		udpServer.startListening(bindAddress);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tサーバーを終了させる");
				System.out.println("list\n\t現在の部屋リストを表示");
				System.out.println("status\n\t現在のサーバーの状態を表示");
				System.out.println("set MaxRooms 部屋数\n\t最大部屋数を部屋数に設定");
				System.out.println("notify メッセージ\n\t全員にメッセージを告知");
				System.out.println("destroy 部屋主名\n\t部屋主名の部屋を解体する");
				System.out.println("goma 部屋主名\n\t部屋主名の部屋の最大人数を増やす");
				System.out.println("myroom list\n\tマイルームの一覧");
				// System.out.println("myroom clear\n\tマイルームのクリア");
				System.out.println("myroom destroy ルームアドレス\n\t指定したマイルームの登録を削除する");
				System.out.println("portal list\n\t登録中のポータル一覧");
				System.out.println("portal accept\n\tポータル登録の受付開始");
				System.out.println("portal reject\n\tポータル登録の受付停止");
			}
		});
		handlers.put("list", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println(engine.allRoomsToString());
			}
		});
		handlers.put("status", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("ポート: " + port);
				System.out.println("部屋数: " + engine.getRoomCount() + " / " + engine.getMaxRooms());
				System.out.println("ログインメッセージファイル : " + loginMessageFile);
				System.out.println("ポータル登録: " + (engine.isAcceptingPortal() ? "受付中" : "停止中"));
			}
		});
		handlers.put("set", new ICommandHandler() {
			@Override
			public void process(String argument) {
				String[] tokens = argument.split(" ");
				if (tokens.length != 2)
					return;

				String key = tokens[0];
				String value = tokens[1];
				if (IniConstants.MAX_ROOMS.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						if (max < 0)
							return;
						engine.setMaxRooms(max);
						System.out.println("最大部屋数を " + max + " に設定しました");
					} catch (NumberFormatException e) {
					}
				}
			}
		});
		handlers.put("notify", new ICommandHandler() {
			@Override
			public void process(String message) {
				if (Utility.isEmpty(message))
					return;

				engine.notifyAllPlayers(message);
				System.out.println("メッセージを告知しました : " + message);
			}
		});
		handlers.put("destroy", new ICommandHandler() {
			@Override
			public void process(String masterName) {
				if (Utility.isEmpty(masterName))
					return;

				if (engine.destroyRoom(masterName)) {
					System.out.println("部屋を解体しました : " + masterName);
				} else {
					System.out.println("部屋を解体できませんでした");
				}
			}
		});
		handlers.put("goma", new ICommandHandler() {
			@Override
			public void process(String masterName) {
				if (Utility.isEmpty(masterName))
					return;

				engine.hirakeGoma(masterName);
				System.out.println("部屋に入れるようになりました : " + masterName);
			}
		});
		handlers.put("myroom", new ICommandHandler() {
			@Override
			public void process(String argument) {
				String[] tokens = argument.trim().split(" ");
				if (tokens.length == 0)
					return;

				String action = tokens[0].toLowerCase();
				if ("list".equals(action)) {
					System.out.println("[登録マイルームの一覧]");
					System.out.println(engine.myRoomsToString());
					// } else if ("clear".equals(action)) {
					// engine.clearAllMyRoomGhosts();
					// System.out.println("マイルームゴーストをクリアしました");
				} else if ("destroy".equals(action)) {
					if (tokens.length == 2) {
						String roomAddress = tokens[1];
						if (engine.destroyMyRoom(roomAddress)) {
							System.out.println("マイルームの登録を削除しました : " + roomAddress);
						} else {
							System.out.println("マイルームの登録を削除できませんでした");
						}
					}
				}
			}
		});
		handlers.put("portal", new ICommandHandler() {
			@Override
			public void process(String argument) {
				String action = argument.trim();
				if ("list".equalsIgnoreCase(action)) {
					System.out.println("[登録中のポータルサーバーの一覧]");
					for (InetSocketAddress address : engine.getPortalAddresses()) {
						System.out.println(address.getAddress().getHostAddress() + ":" + address.getPort());
					}
				} else if ("accept".equalsIgnoreCase(action)) {
					engine.setAcceptingPortal(true);
					System.out.println("ポータル接続の受付を開始しました");
				} else if ("reject".equalsIgnoreCase(action)) {
					engine.setAcceptingPortal(false);
					System.out.println("ポータル接続の受付を停止しました");
				}
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
		udpServer.stopListening();
	}
}
