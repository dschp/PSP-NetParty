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
import org.eclipse.swt.graphics.Image;

public class PlayRoom {

	private String address;
	private String masterName;
	private String title;
	private boolean hasPassword = false;
	private int currentPlayerCount = 0;
	private int maxPlayers;
	private String description;

	public PlayRoom(String address, String masterName, String title, boolean hasPassword, int currentPlayerCount, int maxPlayers) {
		this.address = address;
		this.masterName = masterName;
		this.title = title;
		this.hasPassword = hasPassword;
		this.currentPlayerCount = currentPlayerCount;
		this.maxPlayers = maxPlayers;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getMasterName() {
		return masterName;
	}

	public void setMasterName(String masterName) {
		this.masterName = masterName;
	}

	public boolean hasPassword() {
		return hasPassword;
	}

	public void setHasPassword(boolean hasPassword) {
		this.hasPassword = hasPassword;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public int getCurrentPlayerCount() {
		return currentPlayerCount;
	}

	public void setCurrentPlayerCount(int currentPlayerCount) {
		this.currentPlayerCount = currentPlayerCount;
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

	public void setMaxPlayers(int maxPlayers) {
		this.maxPlayers = maxPlayers;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public static class PlayRoomListContentProvider implements IStructuredContentProvider {
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

	public static class PlayRoomLabelProvider implements ITableLabelProvider {
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
				result = room.address;
				break;
			case 1:
				result = room.masterName;
				break;
			case 2:
				result = room.title;
				break;
			case 3:
				result = room.currentPlayerCount + " / " + room.maxPlayers;
				break;
			case 4:
				result = room.hasPassword ? "有" : "";
				break;
			case 5:
				result = room.description;
				break;
			}

			return result;
		}
	}
}
