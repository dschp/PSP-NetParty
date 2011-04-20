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
