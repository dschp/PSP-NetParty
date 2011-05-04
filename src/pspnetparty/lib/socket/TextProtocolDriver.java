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
package pspnetparty.lib.socket;

import java.util.Map;

import pspnetparty.lib.ILogger;
import pspnetparty.lib.Utility;

public abstract class TextProtocolDriver implements IProtocolDriver, ILogger {
	public static final String ARGUMENT_SEPARATOR = "\t";
	public static final String MESSAGE_SEPARATOR = "\f";

	private ISocketConnection connection;

	private Map<String, IProtocolMessageHandler> messageHandlers;

	public TextProtocolDriver(ISocketConnection conn, Map<String, IProtocolMessageHandler> handlers) {
		connection = conn;
		messageHandlers = handlers;
	}

	@Override
	public final ISocketConnection getConnection() {
		return connection;
	}

	public void setMessageHandlers(Map<String, IProtocolMessageHandler> messageHandlers) {
		this.messageHandlers = messageHandlers;
	}

	@Override
	public final boolean process(PacketData data) {
		if (messageHandlers == null)
			return false;

		boolean sessionContinue = false;

		String messages = data.getMessage();
		for (String message : messages.split(MESSAGE_SEPARATOR)) {
			int commandEndIndex = message.indexOf(ARGUMENT_SEPARATOR);
			String command, argument;
			if (commandEndIndex > 0) {
				command = message.substring(0, commandEndIndex);
				argument = message.substring(commandEndIndex + 1);
			} else {
				command = message;
				argument = "";
			}

			IProtocolMessageHandler handler = messageHandlers.get(command);
			if (handler != null) {
				try {
					sessionContinue = handler.process(this, argument);
				} catch (RuntimeException e) {
					log(Utility.stackTraceToString(e));
				}
			}

			if (!sessionContinue)
				break;
		}

		return sessionContinue;
	}
}
