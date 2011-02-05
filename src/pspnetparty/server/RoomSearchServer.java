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

import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniParser;
import pspnetparty.lib.RoomSearchEngine;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniConstants;

public class RoomSearchServer {
	public static void main(String[] args) throws Exception {
		System.out.printf("%s 部屋検索サーバー  version %s\n", AppConstants.APP_NAME, AppConstants.VERSION);

		String iniFileName = "RoomSearchServer.ini";
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
		System.out.println("部屋の詳細・備考の最大サイズ: " + descriptionMaxLength);

		parser.saveToIni();

		RoomSearchEngine engine = new RoomSearchEngine(new ILogger() {
			@Override
			public void log(String message) {
				System.out.println(message);
			}
		});
		engine.setDescriptionMaxLength(descriptionMaxLength);
		engine.setMaxSearchResults(maxSearchResults);

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
