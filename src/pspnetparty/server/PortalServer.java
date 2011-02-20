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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.HashMap;

import pspnetparty.lib.CommandHandler;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniParser;
import pspnetparty.lib.PortalEngine;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniConstants;

public class PortalServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s ポータルサーバー  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);

		String iniFileName = "PortalServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("設定INIファイル名: " + iniFileName);

		IniParser parser = new IniParser(iniFileName);
		IniParser.Section settings = parser.getSection(IniConstants.SECTION_SETTINGS);

		int port = settings.get(IniConstants.Server.PORT, 40000);
		if (port < 1 || port > 65535) {
			System.out.println("ポート番号が不正です: " + port);
			return;
		}
		System.out.println("ポート: " + port);

		int maxUsers = settings.get(IniConstants.Server.MAX_USERS, 30);
		if (maxUsers < 1) {
			System.out.println("最大ユーザー数が不正です: " + maxUsers);
			return;
		}
		System.out.println("最大ユーザー数: " + maxUsers);

		final String loginMessageFile = settings.get(IniConstants.Server.LOGIN_MESSAGE_FILE, "");
		System.out.println("ログインメッセージファイル : " + loginMessageFile);
		
		int maxSearchResults = settings.get(IniConstants.Server.MAX_SEARCH_RESULTS, 50);
		if (maxSearchResults < 1) {
			System.out.println("最大検索件数が不正です: " + maxSearchResults);
			return;
		}
		System.out.println("最大検索件数: " + maxSearchResults);

		int descriptionMaxLength = settings.get(IniConstants.Server.DESCRIPTION_MAX_LENGTH, 100);
		if (descriptionMaxLength < 1) {
			System.out.println("部屋の詳細・備考の最大サイズが不正です: " + descriptionMaxLength);
			return;
		}
		System.out.println("部屋の詳細・備考の最大文字数: " + descriptionMaxLength);

		String[] roomServerList = settings.get(IniConstants.Server.ROOM_SERVER_LIST, "").split(",");

		parser.saveToIni();

		final PortalEngine engine = new PortalEngine(new ILogger() {
			@Override
			public void log(String message) {
				System.out.println(message);
			}
		});
		engine.setMaxUsers(maxUsers);
		engine.setDescriptionMaxLength(descriptionMaxLength);
		engine.setMaxSearchResults(maxSearchResults);

		engine.start(port);

		for (String address : roomServerList) {
			String[] tokens = address.split(":");
			if (tokens.length != 2)
				continue;

			try {
				String hostname = tokens[0];
				int roomPort = Integer.parseInt(tokens[1]);

				InetSocketAddress socketAddress = new InetSocketAddress(hostname, roomPort);
				engine.addWatching(socketAddress);
			} catch (NumberFormatException e) {
			}
		}

		HashMap<String, CommandHandler> handlers = new HashMap<String, CommandHandler>();
		handlers.put("help", new CommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tサーバーを終了させる");
				System.out.println("list\n\t現在の全登録を表示");
				System.out.println("status\n\t現在のサーバーの状態を表示");
				System.out.println("set MaxUsers ユーザー数\n\t最大ユーザー数を設定");
				System.out.println("set MaxSearchResults 件数\n\t最大検索件数を設定");
				System.out.println("set DescriptionMaxLength 文字数\n\t部屋の紹介・備考の最大文字数を設定");
				System.out.println("room all\n\t監視している部屋サーバーの一覧");
				System.out.println("room active\n\t接続中の部屋サーバーの一覧");
				System.out.println("room dead\n\t切断された部屋サーバーの一覧");
				System.out.println("reconnect\n\t切断された部屋サーバーと再接続する");
				System.out.println("watch アドレス:ポート\n\t部屋サーバーを監視に加える");
				System.out.println("unwatch アドレス:ポート\n\t部屋サーバーを監視から外す");
				System.out.println("notify メッセージ\n\t全員にメッセージを告知");
			}
		});
		handlers.put("list", new CommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println(engine.toString());
			}
		});
		handlers.put("status", new CommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("ポート: " + engine.getPort());
				System.out.println("ユーザー数: " + engine.getCurrentUsers() + " / " + engine.getMaxUsers());
				System.out.println("登録部屋数: " + engine.getRoomEntryCount());
				System.out.println("最大検索件数: " + engine.getMaxSearchResults());
				System.out.println("部屋の紹介・備考の最大文字数: " + engine.getDescriptionMaxLength());
				System.out.println("ログインメッセージファイル : " + loginMessageFile);
			}
		});
		handlers.put("set", new CommandHandler() {
			@Override
			public void process(String argument) {
				String[] tokens = argument.split(" ");
				if (tokens.length != 2)
					return;

				String key = tokens[0];
				String value = tokens[1];
				if (IniConstants.Server.MAX_USERS.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						engine.setMaxUsers(max);
						System.out.println("最大ユーザー数を " + max + " に設定しました");
					} catch (NumberFormatException e) {
					}
				} else if (IniConstants.Server.MAX_SEARCH_RESULTS.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						engine.setMaxSearchResults(max);
						System.out.println("最大検索件数を " + max + " に設定しました");
					} catch (NumberFormatException e) {
					}
				} else if (IniConstants.Server.DESCRIPTION_MAX_LENGTH.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						engine.setDescriptionMaxLength(max);
						System.out.println("部屋の紹介・備考の最大文字数を " + max + " に設定しました");
					} catch (NumberFormatException e) {
					}
				}
			}
		});
		handlers.put("watch", new CommandHandler() {
			@Override
			public void process(String argument) {
				String[] tokens = argument.split(":");
				if (tokens.length != 2)
					return;

				try {
					String hostname = tokens[0];
					int port = Integer.parseInt(tokens[1]);

					InetSocketAddress address = new InetSocketAddress(hostname, port);
					engine.addWatching(address);
				} catch (NumberFormatException e) {
				}
			}
		});
		handlers.put("unwatch", new CommandHandler() {
			@Override
			public void process(String argument) {
				String[] tokens = argument.split(":");
				if (tokens.length != 2)
					return;

				try {
					String hostname = tokens[0];
					int port = Integer.parseInt(tokens[1]);

					InetSocketAddress address = new InetSocketAddress(hostname, port);
					engine.removeWatching(address);
				} catch (NumberFormatException e) {
				}
			}
		});
		handlers.put("room", new CommandHandler() {
			@Override
			public void process(String argument) {
				InetSocketAddress[] list;
				if ("all".equalsIgnoreCase(argument)) {
					System.out.println("[監視している部屋サーバーの一覧]");
					list = engine.listWatchlingAddress();
				} else if ("active".equalsIgnoreCase(argument)) {
					System.out.println("[接続中の部屋サーバーの一覧]");
					list = engine.listActiveAddress();
				} else if ("dead".equalsIgnoreCase(argument)) {
					System.out.println("[切断された部屋サーバーの一覧]");
					list = engine.listRetryAddress();
				} else {
					return;
				}

				for (InetSocketAddress address : list) {
					System.out.print(address.getHostName());
					System.out.print(':');
					System.out.print(address.getPort());
					System.out.println();
				}
			}
		});
		handlers.put("reconnect", new CommandHandler() {
			@Override
			public void process(String argument) {
				engine.reconnectNow();
			}
		});
		handlers.put("notify", new CommandHandler() {
			@Override
			public void process(String message) {
				if (Utility.isEmpty(message))
					return;

				engine.notifyAllPlayers(message);
				System.out.println("メッセージを告知しました : " + message);
			}
		});

		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String line;
		while ((line = reader.readLine()) != null) {
			int commandEndIndex = line.indexOf(" ");
			String command, argument;
			if (commandEndIndex > 0) {
				command = line.substring(0, commandEndIndex);
				argument = line.substring(commandEndIndex + 1);
			} else {
				command = line;
				argument = "";
			}
			command = command.toLowerCase();
			if ("shutdown".equals(command))
				break;

			CommandHandler handler = handlers.get(command);
			if (handler != null)
				handler.process(argument);
		}

		engine.stop();
	}
}
