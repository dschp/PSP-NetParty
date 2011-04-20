package pspnetparty.lib.socket;

public interface IProtocolMessageHandler {
	public boolean process(IProtocolDriver driver, String argument);
}
