package pspnetparty.lib.socket;

import pspnetparty.lib.ILogger;

public interface IServerListener extends ILogger {
	public void serverStartupFinished();
	public void serverShutdownFinished();
}
