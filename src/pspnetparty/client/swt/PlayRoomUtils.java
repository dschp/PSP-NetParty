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

import java.util.HashMap;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.graphics.Image;

import pspnetparty.lib.PlayRoom;

public class PlayRoomUtils {
	private PlayRoomUtils() {
	}

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

			String result = "";
			switch (columnIndex) {
			case 0:
				result = room.getServerAddress();
				break;
			case 1:
				result = room.getMasterName();
				break;
			case 2:
				result = room.getTitle();
				break;
			case 3:
				result = room.getCurrentPlayers() + " / " + room.getMaxPlayers();
				break;
			case 4:
				result = room.hasPassword() ? "有" : "";
				break;
			case 5:
				result = room.getDescription();
				break;
			}

			return result;
		}
	}

	public static class AddressSorter extends ViewerSorter {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			PlayRoom r1 = (PlayRoom) e1;
			PlayRoom r2 = (PlayRoom) e2;
			return r1.getRoomAddress().compareTo(r2.getRoomAddress());
		}
	}

	public static class MasterNameSorter extends ViewerSorter {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			PlayRoom r1 = (PlayRoom) e1;
			PlayRoom r2 = (PlayRoom) e2;
			return r1.getMasterName().compareTo(r2.getMasterName());
		}
	}

	public static class TitleSorter extends ViewerSorter {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			PlayRoom r1 = (PlayRoom) e1;
			PlayRoom r2 = (PlayRoom) e2;
			return r1.getTitle().compareTo(r2.getTitle());
		}
	}

	public static class CapacitySorter extends ViewerSorter {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			PlayRoom r1 = (PlayRoom) e1;
			PlayRoom r2 = (PlayRoom) e2;
			return Integer.valueOf(r1.getCurrentPlayers()).compareTo(r2.getCurrentPlayers());
		}
	}

	public static class HasPasswordSorter extends ViewerSorter {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			PlayRoom r1 = (PlayRoom) e1;
			PlayRoom r2 = (PlayRoom) e2;
			return Boolean.valueOf(r1.hasPassword()).compareTo(r2.hasPassword());
		}
	}
}
