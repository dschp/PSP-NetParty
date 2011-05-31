package pspnetparty.client.swt.message;

import java.util.List;

import org.eclipse.swt.custom.StyleRange;

import pspnetparty.client.swt.config.IniAppearance;

public interface IMessage {

	public long getTimestamp();

	public String getName();

	public int length();

	public String getMessage();

	public void configureStyle(List<StyleRange> styles, IniAppearance appearance);
}
