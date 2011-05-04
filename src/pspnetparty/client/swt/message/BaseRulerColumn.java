/*
Copyright (C) 2011 monte

This file is part of PSP NetParty.

PSP NetParty is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package pspnetparty.client.swt.message;

import org.eclipse.jface.text.source.AbstractRulerColumn;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;

public abstract class BaseRulerColumn extends AbstractRulerColumn {
	private StyledText textWidget;
	private int padding;

	private int lastLinePixel;
	private Color foreground;

	public BaseRulerColumn(StyledText textWidget, int padding) {
		this.textWidget = textWidget;
		this.padding = padding;
	}

	@Override
	protected void paintLine(GC gc, int modelLine, int widgetLine, int linePixel, int lineHeight) {
		if (modelLine == 0)
			lastLinePixel = 0;
		lineHeight += textWidget.getLineSpacing();

		int width = getWidth();

		if (linePixel < lastLinePixel)
			gc.fillRectangle(0, linePixel, width, lineHeight);
		else
			gc.fillRectangle(0, lastLinePixel, width, linePixel + lineHeight);

		String label = getLabel(modelLine);
		if (label == null)
			return;

		// System.out.println(modelLine + "," + widgetLine + "," + linePixel + "," + lineHeight);
		// super.paintLine(gc, modelLine, widgetLine, linePixel, lineHeight);

		if (foreground != null)
			gc.setForeground(foreground);
		gc.drawString(label, padding, linePixel);

		lastLinePixel = linePixel + lineHeight;
	}

	public void setColor(Color bgColor, Color fgColor) {
		setDefaultBackground(bgColor);
		foreground = fgColor;
	}

	protected abstract String getLabel(int line);
}
