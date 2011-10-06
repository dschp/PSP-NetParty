package pspnetparty.client.swt;

public interface IAppWindow {
	public enum Type {
		ARENA, ROOM,
	}

	public Type getType();

	public void settingChanged();
}
