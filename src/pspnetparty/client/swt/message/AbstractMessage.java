package pspnetparty.client.swt.message;


public abstract class AbstractMessage implements IMessage {

	private long timestamp = System.currentTimeMillis();
	private String name;
	private String message;

	protected AbstractMessage(String name, String message) {
		this.name = name;
		this.message = message;
	}

	@Override
	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int length() {
		return message.length();
	}

	@Override
	public String getMessage() {
		return message;
	}
}
