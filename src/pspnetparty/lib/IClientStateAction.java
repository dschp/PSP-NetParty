package pspnetparty.lib;

interface IClientStateAction<Type extends IClientState> {
	public void action(Type p);
}