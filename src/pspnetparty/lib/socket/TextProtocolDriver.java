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
