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

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

public class LobbyServerInfo {
	private String address;
	private int currentUsers;
	private String title;

	public LobbyServerInfo(String address, int currentUsers, String title) {
		this.address = address;
		this.currentUsers = currentUsers;
		this.title = title;
	}

	public String getAddress() {
		return address;
	}

	public int getCurrentUsers() {
		return currentUsers;
	}

	public void setCurrentUsers(int currentUsers) {
		this.currentUsers = currentUsers;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

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
			LobbyServerInfo info = (LobbyServerInfo) element;

			switch (index) {
			case 0:
				return info.title;
			case 1:
				return Integer.toString(info.currentUsers);
			case 2:
				return info.address;
			}

			return "";
		}
	};
}
