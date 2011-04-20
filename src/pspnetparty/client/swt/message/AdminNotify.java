package pspnetparty.client.swt.message;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;

import pspnetparty.client.swt.IniAppearance;

public class AdminNotify extends AbstractMessage {

	public AdminNotify(String message) {
		super("サーバー告知", message);
	}

	@Override
	public void configureStyle(List<StyleRange> styles, IniAppearance appearance) {
		StyleRange range = new StyleRange();
		range.start = 0;
		range.length = length();
		range.foreground = appearance.getColorLogServer();
		range.fontStyle = SWT.BOLD;
		styles.add(range);
	}
}
