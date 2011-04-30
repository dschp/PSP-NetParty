package pspnetparty.client.swt;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

public class SearchServerInfo {
	private String address;
	private int currentUsers;
	private int maxUsers;

	public SearchServerInfo(String address, int currentUsers, int maxUsers) {
		this.address = address;
		this.currentUsers = currentUsers;
		this.maxUsers = maxUsers;
	}

	public String getAddress() {
		return address;
	}

	public int getCurrentUsers() {
		return currentUsers;
	}

	public int getMaxUsers() {
		return maxUsers;
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
		public String getColumnText(Object element, int index) {
			SearchServerInfo info = (SearchServerInfo) element;

			switch (index) {
			case 0:
				return info.address;
			case 1:
				double useRate = ((double) info.currentUsers) / ((double) info.maxUsers);
				return String.format("%.1f%%", useRate * 100);
			}

			return "";
		}

		@Override
		public Image getColumnImage(Object arg0, int arg1) {
			return null;
		}
	};
}
