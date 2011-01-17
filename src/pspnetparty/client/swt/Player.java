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

public class Player {

	private String name;
	private int ping = -1;

	public Player(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public int getPing() {
		return ping;
	}

	public void setPing(int ping) {
		this.ping = ping;
	}

	public static class PlayerListContentProvider implements IStructuredContentProvider {
		@Override
		public void dispose() {
		}

		@Override
		public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
		}

		@Override
		public Object[] getElements(Object input) {
			@SuppressWarnings("unchecked")
			HashMap<String, Player> players = (HashMap<String, Player>) input;
			return players.values().toArray();
		}
	}

	public static class LobbyPlayerLabelProvider implements ITableLabelProvider {
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
		public Image getColumnImage(Object arg0, int arg1) {
			return null;
		}

		@Override
		public String getColumnText(Object element, int columnIndex) {
			Player player = (Player) element;

			String result = "";
			switch (columnIndex) {
			case 0:
				result = player.name;
				break;
			}

			return result;
		}
	}

	public static class RoomPlayerLabelProvider implements ITableLabelProvider {
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
		public Image getColumnImage(Object arg0, int arg1) {
			return null;
		}

		@Override
		public String getColumnText(Object element, int columnIndex) {
			Player player = (Player) element;

			String result = "";
			switch (columnIndex) {
			case 0:
				result = player.name;
				break;
			case 1:
				if (player.ping >= 0)
					result = Integer.toString(player.ping);
				break;
			}

			return result;
		}
	}
}