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

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

public class SwtUtils {
	private SwtUtils() {
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
}
