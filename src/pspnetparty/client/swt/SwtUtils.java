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
package pspnetparty.client.swt;

import java.text.SimpleDateFormat;

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

public class SwtUtils {
	private SwtUtils() {
	}

	public static final Display DISPLAY = Display.getDefault();
	private static final Thread UI_THREAD = DISPLAY.getThread();

	public static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

	public static boolean isNotUIThread() {
		return Thread.currentThread() != UI_THREAD;
	}

	public static final VerifyListener NOT_ACCEPT_CONTROL_CHAR_LISTENER = new VerifyListener() {
		@Override
		public void verifyText(VerifyEvent e) {
			switch (e.character) {
			case '\t':
				e.doit = false;
				break;
			case '\0':
				e.text = e.text.replace("\t", "").trim();
				break;
			}
		}
	};

	public static final VerifyListener NOT_ACCEPT_SPACE_CONTROL_CHAR_LISTENER = new VerifyListener() {
		@Override
		public void verifyText(VerifyEvent e) {
			if (" ".equals(e.text) || "　".equals(e.text)) {
				e.doit = false;
			} else {
				switch (e.character) {
				case '\t':
					e.doit = false;
					break;
				case '\0':
					e.text = e.text.replaceAll("[\\t\\s　]", "");
					break;
				}
			}
		}
	};

	public static void enableColumnDrag(Table table) {
		for (TableColumn column : table.getColumns())
			column.setMoveable(true);
	}

	public static void installSorter(final TableViewer viewer, final TableColumn column, final ViewerSorter sorter) {
		final ViewerSorter reverseSorter = new ViewerSorter() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				return -sorter.compare(viewer, e1, e2);
			}
		};

		column.addListener(SWT.Selection, new Listener() {
			int direction = SWT.NONE;

			@Override
			public void handleEvent(Event event) {
				ViewerSorter viewerSorter;
				switch (direction) {
				case SWT.UP:
					viewerSorter = reverseSorter;
					direction = SWT.DOWN;
					break;
				case SWT.DOWN:
					viewerSorter = null;
					direction = SWT.NONE;
					break;
				default:
					viewerSorter = sorter;
					direction = SWT.UP;
					break;
				}
				viewer.setSorter(viewerSorter);
				Table table = viewer.getTable();
				table.setSortColumn(column);
				table.setSortDirection(direction);
			}
		});
	}

	public static String serializeTableColumns(Table table) {
		if (table.getColumnCount() == 0)
			return "";

		StringBuilder sb = new StringBuilder();

		int[] order = table.getColumnOrder();
		for (int i = 0; i < order.length; i++) {
			sb.append(order[i]);
			sb.append(',');
			sb.append(table.getColumn(i).getWidth());
			sb.append('|');
		}
		sb.deleteCharAt(sb.length() - 1);

		return sb.toString();
	}

	public static void deserializeTableColumns(Table table, String string, int[] defaultWidths) {
		if (table.getColumnCount() != defaultWidths.length)
			throw new RuntimeException("Column count mismatch: " + table);

		String[] tokens = string.split("\\|");
		if (tokens.length == defaultWidths.length) {
			int[] order = new int[tokens.length];
			int[] widths = defaultWidths;
			for (int i = 0; i < tokens.length; i++) {
				String[] values = tokens[i].split(",");
				if (values.length != 2)
					continue;

				try {
					order[i] = Integer.parseInt(values[0]);
					widths[i] = Integer.parseInt(values[1]);
				} catch (NumberFormatException e) {
				}
			}

			try {
				table.setColumnOrder(order);
			} catch (RuntimeException e) {
			}
		}

		for (int i = 0; i < defaultWidths.length; i++) {
			try {
				table.getColumn(i).setWidth(defaultWidths[i]);
			} catch (RuntimeException e) {
			}
		}
	}

	public static Color loadColor(String setting, RGB defaultRGB) {
		String[] values = setting.split(",");
		if (values.length == 3) {
			try {
				int r = Integer.parseInt(values[0]);
				int g = Integer.parseInt(values[1]);
				int b = Integer.parseInt(values[2]);

				return new Color(DISPLAY, r, g, b);
			} catch (RuntimeException e) {
			}
		}
		return new Color(DISPLAY, defaultRGB);
	}

	public static String colorToString(Color color) {
		return color.getRed() + "," + color.getGreen() + "," + color.getBlue();
	}

	public static Font loadFont(String setting, FontData[] defaultFont) {
		String[] values = setting.split(",");
		if (values.length == 3) {
			String name = values[0];
			int size = Integer.parseInt(values[1]);
			int style = parseFontStyle(values[2]);

			return new Font(DISPLAY, name, size, style);
		}
		return new Font(DISPLAY, defaultFont);
	}

	private static int parseFontStyle(String style) {
		if ("B".equals(style))
			return SWT.BOLD;
		if ("I".equals(style))
			return SWT.ITALIC;
		if ("BI".equals(style))
			return SWT.BOLD | SWT.ITALIC;
		return SWT.NORMAL;
	}

	public static String fontToString(Font font) {
		FontData data = font.getFontData()[0];

		String style;
		switch (data.getStyle()) {
		case SWT.NORMAL:
			style = "N";
			break;
		case SWT.BOLD:
			style = "B";
			break;
		case SWT.ITALIC:
			style = "I";
			break;
		case SWT.BOLD | SWT.ITALIC:
			style = "BI";
			break;
		default:
			style = "N";
		}
		return data.getName() + "," + data.getHeight() + "," + style;
	}
}
