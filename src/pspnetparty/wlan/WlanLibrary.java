package pspnetparty.wlan;

import java.util.List;

public interface WlanLibrary {
	public boolean isReady();

	public String getName();

	public void findDevices(List<WlanDevice> devices);

	public boolean isSSIDEnabled();
}
