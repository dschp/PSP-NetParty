package pspnetparty.wlan;

import java.util.List;

public interface WlanLibrary {
	public String getName();

	public void findDevices(List<WlanDevice> devices);

	public boolean isSSIDEnabled();

	public static final WlanLibrary NULL = new WlanLibrary() {
		@Override
		public String getName() {
			return "";
		}

		@Override
		public boolean isSSIDEnabled() {
			return false;
		}

		@Override
		public void findDevices(List<WlanDevice> devices) {
		}
	};
}
