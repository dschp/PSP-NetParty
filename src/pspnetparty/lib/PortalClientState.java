package pspnetparty.lib;

import java.util.HashMap;

public class PortalClientState implements IClientState {

	private ISocketConnection connection;
	public HashMap<String, IServerMessageHandler<PortalClientState>> messageHandlers;

	public PortalClientState(ISocketConnection connection) {
		this.connection = connection;
	}

	@Override
	public ISocketConnection getConnection() {
		return connection;
	}

}
