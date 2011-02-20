package pspnetparty.wlan;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapIf;

import pspnetparty.lib.Utility;

public class Wlan {
	static {
		boolean success = false;
		try {
			System.loadLibrary("pnpwlan");
			try {
				Class.forName(BSSID.class.getName());
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException(e);
			}
			initIDs();
			success = true;
		} catch (UnsatisfiedLinkError e) {
		}
		isSupported = success;
	}

	public static final boolean isSupported;

	private native static void initIDs();

	public native static Wlan open(String deviceName);

	private long handle;

	public native String getSSID();

	public native void setSSID(String ssid);

	public native void scanBSSID();

	public native int findBSSIDs(List<BSSID> bssidList);

	public native void close();

	public static void main(String[] args) throws Exception {
		List<PcapIf> pcapIfList = new ArrayList<PcapIf>();
		StringBuilder errbuf = new StringBuilder();

		Pcap.findAllDevs(pcapIfList, errbuf);

		int i = 0;
		for (PcapIf pif : pcapIfList) {
			System.out.println(i + ": " + pif.getName());
			System.out.println("\t" + pif.getDescription());
			i++;
		}

		System.out.println("Select adapter: ");

		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line = br.readLine();

		int index = Integer.parseInt(line);
		if (index < 0 || index >= i) {
			System.out.println("invalid range");
			return;
		}

		Wlan wlan = open(pcapIfList.get(index).getName());
		if (wlan == null) {
			System.out.println("wlan is null.");
			return;
		}

		List<BSSID> bssidList = new ArrayList<BSSID>();
		do {
			for (i = 0; i < 10; i++) {
				wlan.scanBSSID();

				System.out.println("SSID: " + wlan.getSSID());

				// wlan.findBSSIDs(bssidList);

				for (BSSID bssid : bssidList) {
					String ssid = bssid.getSsid();
					System.out.print('\t');
					System.out.println(ssid);
					System.out.print('\t');
					System.out.println(bssid.getRssi());
					System.out.print('\t');
					System.out.println(Utility.makeMacAddressString(bssid.getBssid(), 0, true));

					if (!ssid.equals(wlan.getSSID()) && ssid.startsWith("PSP_")) {
						System.out.print("\t\tSSID set to: ");
						System.out.println(ssid);
						wlan.setSSID(ssid);
					}

					System.out.println();
				}
				bssidList.clear();

				Thread.sleep(1000);
			}

			System.out.print("Start monitor?");

			line = br.readLine();
		} while (line != null && !line.equals("end"));

		System.out.println("end");
		wlan.close();
	}
}
