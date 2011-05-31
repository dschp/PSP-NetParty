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
package pspnetparty.wlan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class NativeWlanDevice implements WlanDevice {

	static {
		System.loadLibrary("pnpwlan");
		initialize();
	}

	public static final WlanLibrary LIBRARY = new WlanLibrary() {
		@Override
		public String getName() {
			return "PNPWLAN";
		}

		@Override
		public boolean isSSIDEnabled() {
			return true;
		}

		@Override
		public void findDevices(List<WlanDevice> devices) {
			NativeWlanDevice.findDevices(devices);
		}
	};

	private NativeWlanDevice() {
	}

	private native static void initialize();

	native static void findDevices(List<WlanDevice> devices);

	public native void open() throws IOException;

	private Object handle;
	private String name;
	private byte[] hardwareAddress = new byte[6];

	@Override
	public String toString() {
		return handle != null ? handle.toString() : super.toString();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public byte[] getHardwareAddress() {
		return hardwareAddress;
	}

	@Override
	public native int capturePacket(ByteBuffer buffer);

	@Override
	public native boolean sendPacket(ByteBuffer buffer);

	@Override
	public native String getSSID();

	@Override
	public native void setSSID(String ssid);

	@Override
	public native boolean scanNetwork();

	@Override
	public native boolean findNetworks(List<WlanNetwork> networkList);

	@Override
	public native void close();

	public static void main(String[] args) throws Exception {
		List<WlanDevice> deviceList = new ArrayList<WlanDevice>();

		LIBRARY.findDevices(deviceList);

		int i = 0;
		for (WlanDevice dev : deviceList) {
			System.out.println(i + ": " + dev.getName());
			System.out.println("\t" + dev.toString());
			i++;
		}

		if (i == 0) {
			System.out.println("No adapter");
			return;
		}

		System.out.print("Select adapter: ");

		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String line = br.readLine();

		int index = Integer.parseInt(line);
		if (index < 0 || index >= deviceList.size()) {
			System.out.println("invalid range");
			return;
		}

		WlanDevice device = deviceList.get(index);
		if (device == null) {
			System.out.println("wlan is null.");
			return;
		}

		device.open();
		// device.close();
		// if (true) return;

		boolean connected = false;
		List<WlanNetwork> networkList = new ArrayList<WlanNetwork>();
		do {
			for (i = 0; i < 10; i++) {
				device.scanNetwork();

				System.out.println("Current SSID: " + device.getSSID());

				Thread.sleep(2000);

				device.findNetworks(networkList);

				for (WlanNetwork bssid : networkList) {
					String ssid = bssid.getSsid();
					System.out.print('\t');
					System.out.print("Network: ");
					System.out.print(ssid);
					System.out.print('\t');
					System.out.print(bssid.getRssi());
					System.out.println();
					if (!ssid.equals(device.getSSID()) && ssid.startsWith("PSP_")) {
						System.out.print("\t\tSSID set to: ");
						System.out.println(ssid);
						device.setSSID(ssid);

						connected = true;
						break;
					}

					// System.out.println();
				}

				if (connected)
					break;
				networkList.clear();
			}

			if (connected)
				break;
			System.out.print("Continue?");

			line = br.readLine();
		} while (line != null && !line.equals("no"));

		System.out.println("end");

		if (connected) {
			Thread.sleep(5000);
			System.out.println("Current SSID: " + device.getSSID());
		}

		device.close();
	}

}
