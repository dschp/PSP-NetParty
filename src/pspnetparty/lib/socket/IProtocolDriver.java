package pspnetparty.lib.socket;


public interface IProtocolDriver {
	public ISocketConnection getConnection();

	public boolean process(PacketData data);

	public void connectionDisconnected();

	public void errorProtocolNumber(String number);
}
