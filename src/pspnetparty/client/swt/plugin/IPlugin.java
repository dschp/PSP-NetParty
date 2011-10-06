package pspnetparty.client.swt.plugin;

import pspnetparty.client.swt.PlayClient;

public interface IPlugin {

	public void initPlugin(PlayClient application);

	public void disposePlugin();
}
