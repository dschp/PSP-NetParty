package pspnetparty.client.swt;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

public class RoomServerInfo {
	private String address;
	private int currentRooms;
	private int maxRooms;

	public RoomServerInfo(String address, int currentRooms, int maxRooms) {
		this.address = address;
		this.currentRooms = currentRooms;
		this.maxRooms = maxRooms;
	}

	public String getAddress() {
		return address;
	}

	public int getCurrentRooms() {
		return currentRooms;
	}

	public int getMaxRooms() {
		return maxRooms;
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
			RoomServerInfo info = (RoomServerInfo) element;

			switch (index) {
			case 0:
				return info.address;
			case 1:
				double useRate = ((double) info.currentRooms) / ((double) info.maxRooms);
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
