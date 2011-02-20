package pspnetparty.client.swt;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

import pspnetparty.wlan.BSSID;

public class WlanUtils {

	public static class LabelProvider implements ITableLabelProvider {

		@Override
		public void addListener(ILabelProviderListener listener) {
		}

		@Override
		public void dispose() {
		}

		@Override
		public boolean isLabelProperty(Object element, String arg1) {
			return false;
		}

		@Override
		public void removeListener(ILabelProviderListener listener) {
		}

		@Override
		public Image getColumnImage(Object element, int index) {
			return null;
		}

		@Override
		public String getColumnText(Object element, int index) {
			BSSID bssid = (BSSID) element;

			switch (index) {
			case 0:
				return bssid.getSsid();
			case 1:
				return Integer.toString(Math.abs(bssid.getRssi()));
			}

			return "";
		}
	}
}
