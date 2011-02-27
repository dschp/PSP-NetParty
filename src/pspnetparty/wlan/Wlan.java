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

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapIf;

public class Wlan {
	static {
		boolean success = false;
		try {
			System.loadLibrary("pnpwlan");
			Class.forName(NativeWlanDevice.class.getName());
			success = true;
		} catch (UnsatisfiedLinkError e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}
		isLibraryAvailable = success;
	}

	public static final boolean isLibraryAvailable;
	public static final int CAPTURE_BUFFER_SIZE = 5000;

	public static void findDevices(List<WlanDevice> devices) {
		if (isLibraryAvailable) {
			NativeWlanDevice.findDevices(devices);
		} else {
			ArrayList<PcapIf> list = new ArrayList<PcapIf>();
			StringBuilder errbuf = new StringBuilder();

			int r = Pcap.findAllDevs(list, errbuf);
			if (r != Pcap.OK || list.isEmpty()) {
				throw new RuntimeException(errbuf.toString());
			}

			for (PcapIf pcapIf : list) {
				devices.add(new JnetPcapWlanDevice(pcapIf));
			}
		}
	}
	
	public static final WlanDevice EMPTY_DEVICE = new WlanDevice() {
		private byte[] emptyAddress = new byte[6];
		@Override
		public void open() throws IOException {
		}
		
		@Override
		public String getName() {
			return "";
		}
		
		@Override
		public byte[] getHardwareAddress() {
			return emptyAddress;
		}
		
		@Override
		public String getSSID() {
			return "";
		}
		
		@Override
		public void setSSID(String ssid) {
		}
		
		@Override
		public boolean scanNetwork() {
			return false;
		}
		
		@Override
		public boolean findNetworks(List<WlanNetwork> networkList) {
			return false;
		}
		
		@Override
		public int capturePacket(ByteBuffer buffer) {
			return -1;
		}
		
		@Override
		public boolean sendPacket(ByteBuffer buffer) {
			return false;
		}
		
		@Override
		public void close() {
		}
	};

	public static void main(String[] args) throws Exception {
		List<WlanDevice> deviceList = new ArrayList<WlanDevice>();

		findDevices(deviceList);

		int i = 0;
		for (WlanDevice dev : deviceList) {
			System.out.println(i + ": " + dev.getName());
			System.out.println("\t" + dev.toString());
			i++;
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
		//device.close();
		//if (true) return;

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

					//System.out.println();
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
