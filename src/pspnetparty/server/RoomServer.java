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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

import pspnetparty.lib.CommandHandler;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniParser;
import pspnetparty.lib.RoomEngine;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniConstants;

public class RoomServer {
	public static void main(String[] args) throws IOException {
		System.out.printf("%s ルームサーバー  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);

		String iniFileName = "RoomServer.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		System.out.println("設定INIファイル名: " + iniFileName);

		IniParser parser = new IniParser(iniFileName);
		IniParser.Section settings = parser.getSection(IniConstants.SECTION_SETTINGS);

		int port = settings.get(IniConstants.Server.PORT, 30000);
		if (port < 1 || port > 65535) {
			System.out.println("ポート番号が不正です: " + port);
			return;
		}
		System.out.println("ポート: " + port);

		int maxRooms = settings.get(IniConstants.Server.MAX_ROOMS, 10);
		if (maxRooms < 1) {
			System.out.println("部屋数が不正です: " + maxRooms);
			return;
		}
		System.out.println("最大部屋数: " + maxRooms);

		boolean passwordAllowed = settings.get(IniConstants.Server.ALLOW_ROOM_PASSWORD, true);
		System.out.println("部屋パスワード: " + (passwordAllowed ? "許可" : "禁止"));

		final String loginMessageFile = settings.get(IniConstants.Server.LOGIN_MESSAGE_FILE, "");
		System.out.println("ログインメッセージファイル : " + loginMessageFile);

		int lobbyCapacity = settings.get(IniConstants.Server.LOBBY_CAPACITY, 0);
		if (lobbyCapacity > 0) {
			System.out.println("ロビー上限人数: " + lobbyCapacity);
		} else {
			System.out.println("ロビー: オフ");
		}

		parser.saveToIni();

		final RoomEngine engine = new RoomEngine(new ILogger() {
			@Override
			public void log(String message) {
				System.out.println(message);
			}
		});
		engine.setMaxRooms(maxRooms);
		engine.setRoomPasswordAllowed(passwordAllowed);
		engine.setLoginMessageFile(loginMessageFile);

		engine.start(port, lobbyCapacity);

		HashMap<String, CommandHandler> handlers = new HashMap<String, CommandHandler>();
		handlers.put("help", new CommandHandler() {
			@Override
			public void process(String argument) {
				System.out.println("shutdown\n\tサーバーを終了させる");
				System.out.println("list\n\t現在の部屋リストを表示");
				System.out.println("status\n\t現在のサーバーの状態を表示");
				System.out.println("set MaxRooms 部屋数\n\t最大部屋数を部屋数に設定");
				System.out.println("set AllowRoomPassword Yes/No\n\t部屋パスワードの許可禁止を設定");
				System.out.println("set LobbyCapacity 人数\n\tロビーの上限人数を設定");
				System.out.println("notify メッセージ\n\t全員にメッセージを告知");
				System.out.println("destroy 部屋主名\n\t部屋主名の部屋を解体する");
				System.out.println("goma 部屋主名\n\t部屋主名の部屋の最大人数を増やす");
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
				System.out.println("部屋数: " + engine.getRoomCount() + " / " + engine.getMaxRooms());
				System.out.println("部屋パスワード: " + (engine.isRoomPasswordAllowed() ? "許可" : "禁止"));
				System.out.println("ログインメッセージファイル : " + loginMessageFile);

				int lobbyCapacity = engine.getLobbyCapacity();
				if (lobbyCapacity > 0) {
					System.out.println("ロビー: " + engine.getLobbyUserCount() + " / " + lobbyCapacity);
				} else {
					System.out.println("ロビー: オフ");
				}
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
				if (IniConstants.Server.MAX_ROOMS.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						engine.setMaxRooms(max);
						System.out.println("最大部屋数を " + max + " に設定しました");
					} catch (NumberFormatException e) {
					}
				} else if (IniConstants.Server.ALLOW_ROOM_PASSWORD.equalsIgnoreCase(key)) {
					value = value.toLowerCase();
					if ("yes".equals(value) || "y".equals(value)) {
						engine.setRoomPasswordAllowed(true);
						System.out.println("部屋パスワードを 許可 に設定しました");
					} else if ("no".equals(value) || "n".equals(value)) {
						engine.setRoomPasswordAllowed(false);
						System.out.println("部屋パスワードを 禁止 に設定しました");
					}
				} else if (IniConstants.Server.LOBBY_CAPACITY.equalsIgnoreCase(key)) {
					try {
						int max = Integer.parseInt(value);
						engine.setLobbyCapacity(max);
						System.out.println("ロビーの上限人数を " + max + " に設定しました");
					} catch (NumberFormatException e) {
					}
				}
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
		handlers.put("destroy", new CommandHandler() {
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
		handlers.put("goma", new CommandHandler() {
			@Override
			public void process(String masterName) {
				if (Utility.isEmpty(masterName))
					return;

				engine.hirakeGoma(masterName);
				System.out.println("部屋に入れるようになりました : " + masterName);
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
