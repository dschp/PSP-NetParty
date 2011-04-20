package pspnetparty.client.swt.message;

import java.util.List;

import org.eclipse.swt.custom.StyleRange;

import pspnetparty.client.swt.IniAppearance;

public class Chat extends AbstractMessage {

	private boolean isMine;

	public Chat(String name, String message, boolean isMine) {
		super(name, message);
		this.isMine = isMine;
	}

	public boolean isMine() {
		return isMine;
	}

	@Override
	public void configureStyle(List<StyleRange> styles, IniAppearance appearance) {
		StyleRange range = new StyleRange();
		range.start = 0;
		range.length = length();
		range.foreground = isMine ? appearance.getColorChatMine() : appearance.getColorChatOthers();
		styles.add(range);
	}
}
