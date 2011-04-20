package pspnetparty.client.swt.message;

import java.util.List;

import org.eclipse.swt.custom.StyleRange;

import pspnetparty.client.swt.IniAppearance;

public class PrivateChat extends Chat {

	private String receiver;

	public PrivateChat(String sender, String receiver, String message, boolean isMine) {
		super(sender, message, isMine);
		this.receiver = receiver;
	}

	@Override
	public String getName() {
		return isMine() ? "→ " + receiver : super.getName();
	}

	public String getReceiver() {
		return receiver;
	}

	@Override
	public void configureStyle(List<StyleRange> styles, IniAppearance appearance) {
		StyleRange range = new StyleRange();
		range.start = 0;
		range.length = length();
		range.foreground = appearance.getColorChatPrivate();
		styles.add(range);
	}
}
