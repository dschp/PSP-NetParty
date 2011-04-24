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
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniPublicServer;
import pspnetparty.lib.engine.SearchEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.IProtocol;

public class SearchServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s 検索サーバー  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("プロトコル: " + IProtocol.NUMBER);

		String iniFileName = "SearchServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("設定INIファイル名: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 40000);
		if (port < 1 || port > 65535) {
			System.out.println("ポート番号が不正です: " + port);
			return;
		}
		System.out.println("ポート: " + port);

		int maxUsers = settings.get(IniConstants.MAX_USERS, 30);
		if (maxUsers < 1) {
			System.out.println("最大ユーザー数が不正です: " + maxUsers);
			return;
		}
		System.out.println("最大ユーザー数: " + maxUsers);

		final String loginMessageFile = settings.get(IniConstants.LOGIN_MESSAGE_FILE, "");
		System.out.println("ログインメッセージファイル : " + loginMessageFile);

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

		ini.saveToIni();

		AsyncTcpServer tcpServer = new AsyncTcpServer(1000000);

		final SearchEngine engine = new SearchEngine(tcpServer, ServerUtils.createLogger(), new IniPublicServer());
		engine.setMaxUsers(maxUsers);
		engine.setDescriptionMaxLength(descriptionMaxLength);
		engine.setMaxSearchResults(maxSearchResults);
		engine.setLoginMessageFile(loginMessageFile);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tサーバーを終了させる");
				System.out.println("status\n\t現在のサーバーの状態を表示");
				System.out.println("set MaxUsers ユーザー数\n\t最大ユーザー数を設定");
				System.out.println("set MaxSearchResults 件数\n\t最大検索件数を設定");
				System.out.println("set DescriptionMaxLength 文字数\n\t部屋の紹介・備考の最大文字数を設定");
				System.out.println("notify メッセージ\n\t全員にメッセージを告知");
				System.out.println("rooms\n\t保持している部屋情報の一覧");
				System.out.println("portal list\n\t接続しているポータルサーバーの一覧");
				System.out.println("portal accept\n\tポータル登録の受付開始");
				System.out.println("portal reject\n\tポータル登録の受付停止");
			}
		});
		handlers.put("status", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("ポート: " + port);
				System.out.println("ユーザー数: " + engine.getCurrentUsers() + " / " + engine.getMaxUsers());
				System.out.println("登録部屋数: " + engine.getRoomEntryCount());
				System.out.println("最大検索件数: " + engine.getMaxSearchResults());
				System.out.println("部屋の紹介・備考の最大文字数: " + engine.getDescriptionMaxLength());
				System.out.println("ログインメッセージファイル : " + loginMessageFile);
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
				if (IniConstants.MAX_USERS.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						if (max < 1)
							return;
						engine.setMaxUsers(max);
						System.out.println("最大ユーザー数を " + max + " に設定しました");
					} catch (NumberFormatException e) {
					}
				} else if (IniConstants.MAX_SEARCH_RESULTS.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						if (max < 1)
							return;
						engine.setMaxSearchResults(max);
						System.out.println("最大検索件数を " + max + " に設定しました");
					} catch (NumberFormatException e) {
					}
				} else if (IniConstants.DESCRIPTION_MAX_LENGTH.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						if (max < 1)
							return;
						engine.setDescriptionMaxLength(max);
						System.out.println("部屋の紹介・備考の最大文字数を " + max + " に設定しました");
					} catch (NumberFormatException e) {
					}
				}
			}
		});
		handlers.put("rooms", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("[全部屋登録の一覧]");
				System.out.println(engine.allRoomsToString());
			}
		});
		handlers.put("portal", new ICommandHandler() {
			@Override
			public void process(String argument) {
				if ("list".equalsIgnoreCase(argument)) {
					System.out.println("[接続しているポータルサーバーの一覧]");
					System.out.println(engine.allPortalsToString());
				} else if ("accept".equalsIgnoreCase(argument)) {
					engine.setAcceptingPortal(true);
					System.out.println("ポータル接続の受付を開始しました");
				} else if ("reject".equalsIgnoreCase(argument)) {
					engine.setAcceptingPortal(false);
					System.out.println("ポータル接続の受付を停止しました");
				}
			}
		});
		handlers.put("notify", new ICommandHandler() {
			@Override
			public void process(String message) {
				if (Utility.isEmpty(message))
					return;

				engine.notifyAllClients(message);
				System.out.println("メッセージを告知しました : " + message);
			}
		});

		ServerUtils.promptCommand(handlers);

		tcpServer.stopListening();
	}
}
