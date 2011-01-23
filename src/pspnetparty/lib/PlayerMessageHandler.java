package pspnetparty.lib;

interface PlayerMessageHandler {
	public boolean process(PlayerState state, String argument);
}