package pspnetparty.client.swt.plugin;

import pspnetparty.client.swt.IPlayClient;

public interface IPlugin {

	public void initPlugin(IPlayClient application);

	public void disposePlugin();
}
