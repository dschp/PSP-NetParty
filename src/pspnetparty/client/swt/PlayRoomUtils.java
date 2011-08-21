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
import java.util.Date;
import java.util.HashMap;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.graphics.Image;

import pspnetparty.lib.Utility;
import pspnetparty.lib.engine.PlayRoom;

public class PlayRoomUtils {
	private PlayRoomUtils() {
	}

	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("M/d HH:mm:ss");

	public class ListContentProvider implements IStructuredContentProvider {
		@Override
		public void dispose() {
		}

		@Override
		public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
		}

		@Override
		public Object[] getElements(Object input) {
			@SuppressWarnings("unchecked")
			HashMap<String, PlayRoom> playRooms = (HashMap<String, PlayRoom>) input;
			return playRooms.values().toArray();
		}
	}

	public static class LabelProvider implements ITableLabelProvider {
		@Override
		public void addListener(ILabelProviderListener arg0) {
		}

		@Override
		public void dispose() {
		}

		@Override
		public boolean isLabelProperty(Object arg0, String arg1) {
			return false;
		}

		@Override
		public void removeListener(ILabelProviderListener arg0) {
		}

		@Override
		public Image getColumnImage(Object element, int columnIndex) {
			return null;
		}

		@Override
		public String getColumnText(Object element, int columnIndex) {
			PlayRoom room = (PlayRoom) element;

			switch (columnIndex) {
			case 0:
				return room.getMasterName();
			case 1:
				return room.getTitle();
			case 2:
				return room.getCurrentPlayers() + " / " + room.getMaxPlayers();
			case 3:
				return room.hasPassword() ? "有" : "";
			case 4:
				// return Long.toString(room.getCreatedTime());
				return DATE_FORMAT.format(new Date(room.getCreatedTime()));
			case 5:
				return room.getDescription();
			case 6:
				return room.getServerAddress();
			}

			return "";
		}
	}

	public static final ViewerSorter ADDRESS_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			PlayRoom r1 = (PlayRoom) e1;
			PlayRoom r2 = (PlayRoom) e2;
			return r1.getRoomAddress().compareTo(r2.getRoomAddress());
		}
	};

	public static final ViewerSorter MASTER_NAME_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			PlayRoom r1 = (PlayRoom) e1;
			PlayRoom r2 = (PlayRoom) e2;
			return r1.getMasterName().compareTo(r2.getMasterName());
		}
	};

	public static final ViewerSorter TITLE_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			PlayRoom r1 = (PlayRoom) e1;
			PlayRoom r2 = (PlayRoom) e2;
			return r1.getTitle().compareTo(r2.getTitle());
		}
	};

	public static final ViewerSorter CAPACITY_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			PlayRoom r1 = (PlayRoom) e1;
			PlayRoom r2 = (PlayRoom) e2;
			int capacity1 = r1.getMaxPlayers() - r1.getCurrentPlayers();
			int capacity2 = r2.getMaxPlayers() - r2.getCurrentPlayers();
			return Utility.compare(capacity1, capacity2);
		}
	};

	public static final ViewerSorter HAS_PASSWORD_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			PlayRoom r1 = (PlayRoom) e1;
			PlayRoom r2 = (PlayRoom) e2;
			return Boolean.valueOf(r1.hasPassword()).compareTo(r2.hasPassword());
		}
	};

	public static final ViewerSorter TIMESTAMP_SORTER = new ViewerSorter() {
		public int compare(Viewer viewer, Object e1, Object e2) {
			PlayRoom r1 = (PlayRoom) e1;
			PlayRoom r2 = (PlayRoom) e2;
			return Utility.compare(r1.getCreatedTime(), r2.getCreatedTime());
		};
	};
}
