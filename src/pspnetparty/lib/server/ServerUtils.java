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
package pspnetparty.lib.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Map;

import pspnetparty.lib.ICommandHandler;
import pspnetparty.lib.ILogger;

public class ServerUtils {
	private ServerUtils() {
	}

	public static ILogger createLogger() {
		return new ILogger() {
			private Date now = new Date();

			@Override
			public void log(String message) {
				now.setTime(System.currentTimeMillis());
				System.out.println(DATE_FORMAT.format(now) + " - " + message);
			}
		};
	}

	public static void promptCommand(Map<String, ICommandHandler> handlers) throws IOException, InterruptedException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			String line = reader.readLine();
			if (line == null) {
				while (true)
					Thread.sleep(Long.MAX_VALUE);
			}

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

			ICommandHandler handler = handlers.get(command);
			if (handler != null)
				handler.process(argument);
		}
	}
}
