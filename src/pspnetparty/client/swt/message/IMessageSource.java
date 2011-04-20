package pspnetparty.client.swt.message;

public interface IMessageSource {

	public void addMessageListener(IMessageListener listener);

	public void removeMessageListener(IMessageListener listener);
}
