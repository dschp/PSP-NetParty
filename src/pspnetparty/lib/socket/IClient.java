package pspnetparty.lib.socket;

import java.io.IOException;
import java.net.InetSocketAddress;

public interface IClient {
	public void connect(InetSocketAddress address, int timeout, IProtocol protocol) throws IOException;
	public void dispose();
}
