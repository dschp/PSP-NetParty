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
import pspnetparty.lib.engine.LobbyEngine;
import pspnetparty.lib.server.IniConstants;
import pspnetparty.lib.server.ServerUtils;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.IProtocol;

public class LobbyServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s ロビーサーバー  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);
		System.out.println("プロトコル: " + IProtocol.NUMBER);

		String iniFileName = "LobbyServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("設定INIファイル名: " + iniFileName);

		IniFile ini = new IniFile(iniFileName);
		IniSection settings = ini.getSection(IniConstants.SECTION_SETTINGS);

		final int port = settings.get(IniConstants.PORT, 60000);
		if (port < 1 || port > 65535) {
			System.out.println("ポート番号が不正です: " + port);
			return;
		}
		System.out.println("ポート: " + port);

		String title = settings.get(IniConstants.LOBBY_TITLE, "ロビー");
		System.out.println("ロビー名: " + title);

		final String loginMessageFile = settings.get(IniConstants.LOGIN_MESSAGE_FILE, "");
		System.out.println("ログインメッセージファイル : " + loginMessageFile);

		ini.saveToIni();

		AsyncTcpServer tcpServer = new AsyncTcpServer(40000);

		final LobbyEngine engine = new LobbyEngine(tcpServer, ServerUtils.createLogger(), new IniPublicServer());
		engine.setTitle(title);
		engine.setLoginMessageFile(loginMessageFile);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		tcpServer.startListening(bindAddress);

		HashMap<String, ICommandHandler> handlers = new HashMap<String, ICommandHandler>();
		handlers.put("help", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tサーバーを終了させる");
				System.out.println("status\n\t現在のサーバーの状態を表示");
				System.out.println("set Title ロビー名\n\tロビー名を設定");
				System.out.println("notify メッセージ\n\t全員にメッセージを告知");
				System.out.println("portal list\n\t登録中のポータル一覧");
				System.out.println("portal accept\n\tポータル登録の受付開始");
				System.out.println("portal reject\n\tポータル登録の受付停止");
			}
		});
		handlers.put("status", new ICommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("ポート: " + port);
				System.out.println("ロビー名: " + engine.getTitle());
				System.out.println("ユーザー数: " + engine.getCurrentPlayers());
				System.out.println("ログインメッセージファイル : " + loginMessageFile);
				System.out.println("ポータル登録: " + (engine.isAcceptingPortal() ? "受付中" : "停止中"));
			}
		});
		handlers.put("notify", new ICommandHandler() {
			@Override
			public void process(String message) {
				if (Utility.isEmpty(message))
					return;

				engine.notifyAllUsers(message);
				System.out.println("メッセージを告知しました : " + message);
			}
		});
		handlers.put("set", new ICommandHandler() {
			@Override
			public void process(String argument) {
				String[] tokens = argument.split(" ", 2);
				if (tokens.length != 2)
					return;

				String key = tokens[0];
				String value = tokens[1];
				if (IniConstants.LOBBY_TITLE.equalsIgnoreCase(key)) {
					engine.setTitle(value);
					System.out.println("ロビー名を " + value + " に設定しました");
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
	}
}
