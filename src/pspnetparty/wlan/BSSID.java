package pspnetparty.wlan;

public class BSSID {
	private byte[] bssid;
	private String ssid;
	private int rssi;

	public byte[] getBssid() {
		return bssid;
	}

	public String getSsid() {
		return ssid;
	}

	public int getRssi() {
		return rssi;
	}
}
