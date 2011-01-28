package pspnetparty.lib;

interface IServerMessageHandler<Type extends IClientState> {
	public boolean process(Type state, String argument);
}