package pspnetparty.lib.socket;

import pspnetparty.lib.ILogger;

public interface IProtocol extends ILogger {
	public static final String NUMBER = "6";

	static final int HEADER_BYTE_SIZE = Integer.SIZE / 8;
	static final long PING_INTERVAL = 30000L;
	static final long PING_DEADLINE = 1000000L;

	static final String PROTOCOL_OK = "OK";
	static final String PROTOCOL_NG = "NG";
	static final String SEPARATOR = " ";

	public String getProtocol();

	public IProtocolDriver createDriver(ISocketConnection connection);
}
