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

import java.util.Map;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.graphics.Image;

import pspnetparty.lib.engine.LobbyUserState;

public class LobbyUser {

	private String name;
	private LobbyUserState state;

	public LobbyUser(String name, LobbyUserState state) {
		this.name = name;
		this.state = state;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LobbyUserState getState() {
		return state;
	}

	public void setState(LobbyUserState state) {
		this.state = state;
	}

	public static final IStructuredContentProvider CONTENT_PROVIDER = new IStructuredContentProvider() {
		@Override
		public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
		}

		@Override
		public void dispose() {
		}

		@Override
		public Object[] getElements(Object input) {
			@SuppressWarnings("unchecked")
			Map<String, LobbyUser> map = (Map<String, LobbyUser>) input;
			return map.values().toArray();
		}
	};

	public static final ITableLabelProvider LABEL_PROVIDER = new ITableLabelProvider() {
		@Override
		public void removeListener(ILabelProviderListener arg0) {
		}

		@Override
		public boolean isLabelProperty(Object arg0, String arg1) {
			return false;
		}

		@Override
		public void dispose() {
		}

		@Override
		public void addListener(ILabelProviderListener arg0) {
		}

		@Override
		public Image getColumnImage(Object element, int index) {
			return null;
		}

		@Override
		public String getColumnText(Object element, int index) {
			LobbyUser user = (LobbyUser) element;

			switch (index) {
			case 0:
				return user.name;
			case 1:
				switch (user.state) {
				case LOGIN:
					return "参加中";
				case AFK:
					return "離席中";
				case PLAYING:
					return "プレイ中";
				case INACTIVE:
					return "非アクティブ";
				default:
					return "";
				}
			}

			return "";
		}
	};

	public static final ViewerSorter NAME_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			LobbyUser u1 = (LobbyUser) e1;
			LobbyUser u2 = (LobbyUser) e2;
			return u1.name.compareTo(u2.name);
		}
	};

	public static final ViewerSorter STATE_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			LobbyUser u1 = (LobbyUser) e1;
			LobbyUser u2 = (LobbyUser) e2;
			return u1.state.compareTo(u2.state);
		}
	};
}
