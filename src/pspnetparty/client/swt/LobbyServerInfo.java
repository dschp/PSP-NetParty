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
