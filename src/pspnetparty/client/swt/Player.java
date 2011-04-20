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
import java.util.Map;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.graphics.Image;

public class Player {

	private String name;
	private int ping = -1;
	private boolean isSsidChased = false;
	private String ssid = "";

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

	public String getSsid() {
		return ssid;
	}

	public void setSsid(String ssid) {
		this.ssid = ssid;
	}

	public boolean isSSIDChased() {
		return isSsidChased;
	}

	public void setSSIDChased(boolean isSsidchased) {
		this.isSsidChased = isSsidchased;
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
			Map<String, Player> players = (HashMap<String, Player>) input;
			return players.values().toArray();
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
		public Image getColumnImage(Object element, int index) {
			return null;
		}

		@Override
		public String getColumnText(Object element, int index) {
			Player player = (Player) element;

			switch (index) {
			case 0:
				return player.isSsidChased ? "追" : "";
			case 1:
				return player.name;
			case 2:
				return player.ssid;
			case 3:
				if (player.ping >= 0)
					return Integer.toString(player.ping);
			}

			return "";
		}
	}

	public static final ViewerSorter NANE_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			Player p1 = (Player) e1;
			Player p2 = (Player) e2;
			return p1.name.compareTo(p2.name);
		}
	};

	public static final ViewerSorter PING_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			Player p1 = (Player) e1;
			Player p2 = (Player) e2;
			return Integer.valueOf(p1.ping).compareTo(p2.ping);
		}
	};

	public static final ViewerSorter SSID_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			Player p1 = (Player) e1;
			Player p2 = (Player) e2;
			return p1.ssid.compareTo(p2.ssid);
		}
	};

	public static final ViewerSorter SSID_CHASE_SORTER = new ViewerSorter() {
		public int compare(Viewer viewer, Object e1, Object e2) {
			Player p1 = (Player) e1;
			Player p2 = (Player) e2;
			return Boolean.valueOf(p1.isSsidChased).compareTo(p2.isSsidChased);
		};
	};
}