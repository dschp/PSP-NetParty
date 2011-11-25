package pspnetparty.client.swt.message;

public class LobbyCircleChat extends Chat {

	private String circleName;

	public LobbyCircleChat(String name, String message, boolean isMine, String circleName) {
		super(name, message, isMine);
		this.circleName = circleName;
	}

	public String getCircleName() {
		return circleName;
	}
}
