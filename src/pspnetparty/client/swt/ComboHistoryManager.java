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

import java.util.LinkedList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import pspnetparty.lib.Utility;

public class ComboHistoryManager {

	private Combo combo;
	private int maxHistory;

	private int historyStartIndex = 0;
	private LinkedList<String> history = new LinkedList<String>();

	private int lastSelectedIndex = 0;

	public ComboHistoryManager(Combo combo, String[] history, int maxHistory, boolean initialSelect) {
		this.combo = combo;
		this.maxHistory = maxHistory;

		if (combo.getItemCount() > 0) {
			combo.add("----------履歴----------");
			combo.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (ComboHistoryManager.this.combo.getSelectionIndex() == historyStartIndex - 1) {
						ComboHistoryManager.this.combo.select(lastSelectedIndex);
					} else {
						lastSelectedIndex = ComboHistoryManager.this.combo.getSelectionIndex();
					}
				}
			});
		}
		this.historyStartIndex = combo.getItemCount();

		if (history != null)
			for (String s : history) {
				if (Utility.isEmpty(s))
					continue;
				combo.add(s);
				this.history.add(s);
				if (this.history.size() == maxHistory)
					break;
			}

		if (initialSelect && combo.getItemCount() > 0)
			combo.select(0);
		else
			combo.setText("");
	}

	public void addCurrentItem() {
		try {
			String item = combo.getText();
			if (Utility.isEmpty(item))
				return;

			int index = history.indexOf(item);
			if (index == -1) {
				history.add(0, item);
				combo.add(item, historyStartIndex);
				if (history.size() > maxHistory) {
					history.removeLast();
					combo.remove(combo.getItemCount() - 1);
				}
			} else {
				history.remove(index);
				history.add(0, item);

				combo.remove(historyStartIndex + index);
				combo.add(item, historyStartIndex);

				combo.setText(item);
				combo.setSelection(new Point(item.length(), item.length()));
			}
		} catch (SWTException e) {
		}
	}

	public String makeCSV() {
		StringBuilder sb = new StringBuilder();
		for (String s : history) {
			sb.append(s).append(',');
		}
		if (sb.length() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}

	public static int addList(Combo combo, String[] list) {
		int count = 0;
		for (String s : list) {
			if (Utility.isEmpty(s))
				continue;
			combo.add(s);
			count++;
		}
		return count;
	}
}
