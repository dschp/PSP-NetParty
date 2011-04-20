package pspnetparty.client.swt.plugin;

import pspnetparty.client.swt.IApplication;

public interface IPlugin {

	public void initPlugin(IApplication application);

	public void disposePlugin();
}
