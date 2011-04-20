package pspnetparty.client.swt.message;

import java.util.List;

import org.eclipse.swt.custom.StyleRange;

import pspnetparty.client.swt.IniAppearance;

public class InfoLog extends AbstractMessage {

	public InfoLog(String message) {
		super("", message);
	}

	@Override
	public void configureStyle(List<StyleRange> styles, IniAppearance appearance) {
		StyleRange range = new StyleRange();
		range.start = 0;
		range.length = length();
		range.foreground = appearance.getColorLogInfo();
		styles.add(range);
	}
}
