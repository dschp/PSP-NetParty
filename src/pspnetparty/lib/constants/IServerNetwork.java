package pspnetparty.lib.constants;

import java.net.InetAddress;

public interface IServerNetwork {

	public abstract void reload();

	public abstract boolean isValidPortalServer(InetAddress address);

}