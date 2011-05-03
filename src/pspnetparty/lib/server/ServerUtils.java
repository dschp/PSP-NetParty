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
