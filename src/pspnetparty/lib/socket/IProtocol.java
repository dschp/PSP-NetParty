package pspnetparty.lib.socket;

import pspnetparty.lib.ILogger;

public interface IProtocol extends ILogger {
	public static final String NUMBER = "6";

	static final int HEADER_BYTE_SIZE = Integer.SIZE / 8;
	static final long KEEPALIVE_INTERVAL = 6000L;
	static final long KEEPALIVE_DEADLINE = 150000L;

	static final String PROTOCOL_OK = "OK";
	static final String PROTOCOL_NG = "NG";
	static final String SEPARATOR = " ";

	public String getProtocol();

	public IProtocolDriver createDriver(ISocketConnection connection);
}
