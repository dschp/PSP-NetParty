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

import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniParser;
import pspnetparty.lib.ProxyRoomEngine;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniConstants;

public class ProxyRoomServer {
	public static void main(String[] args) throws IOException {
		System.out.printf("%s 部屋代理サーバー  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);

		String iniFileName = "ProxyRoomServer.ini";
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

		boolean passwordAllowed = "Yes".equals(settings.get(IniConstants.Server.ROOM_PASSWORD_ALLOWED, "Yes"));
		settings.set(IniConstants.Server.ROOM_PASSWORD_ALLOWED, passwordAllowed ? "Yes" : "No");
		System.out.println("パスワード: " + (passwordAllowed ? "許可" : "禁止"));

		parser.saveToIni();

		ProxyRoomEngine engine = new ProxyRoomEngine(new ILogger() {
			@Override
			public void log(String message) {
				System.out.println(message);
			}
		});
		engine.setMaxRooms(maxRooms);
		engine.setPasswordAllowed(passwordAllowed);

		engine.start(port);

		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String line;
		while ((line = reader.readLine()) != null) {
			if ("list".equalsIgnoreCase(line)) {
				System.out.println(engine.toString());
			} else if ("shutdown".equalsIgnoreCase(line)) {
				break;
			}
		}

		engine.stop();
	}
}
