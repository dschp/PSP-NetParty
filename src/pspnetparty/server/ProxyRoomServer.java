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

import pspnetparty.lib.Constants;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.ProxyRoomEngine;

public class ProxyRoomServer {
	public static void main(String[] args) throws IOException {
		System.out.printf("%s 部屋代理サーバー バージョン: %s\n", Constants.App.APP_NAME, Constants.App.VERSION);
		
		ProxyRoomEngine engine = new ProxyRoomEngine(new ILogger() {
			@Override
			public void log(String message) {
				System.out.println(message);
			}
		});

		engine.startListening(30000);

		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String line;
		while ((line = reader.readLine()) != null) {
			if ("list".equalsIgnoreCase(line)) {
				System.out.println(engine.toString());
			} else if ("shutdown".equalsIgnoreCase(line)) {
				break;
			}
		}

		engine.stopListening();
	}
}
