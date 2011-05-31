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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapIf;
import org.jnetpcap.nio.JMemory;
import org.jnetpcap.packet.PcapPacket;

import pspnetparty.lib.Utility;

public class JnetPcapWlanDevice implements WlanDevice {

	static {
		Pcap.libVersion();
	}

	public static final WlanLibrary LIBRARY = new WlanLibrary() {
		@Override
		public String getName() {
			return "jNetPcap";
		}

		@Override
		public boolean isSSIDEnabled() {
			return false;
		}

		@Override
		public void findDevices(List<WlanDevice> devices) {
			ArrayList<PcapIf> list = new ArrayList<PcapIf>();
			StringBuilder errbuf = new StringBuilder();

			int r = Pcap.findAllDevs(list, errbuf);
			if (r != Pcap.OK) {
				throw new RuntimeException(errbuf.toString());
			}

			for (PcapIf pcapIf : list) {
				devices.add(new JnetPcapWlanDevice(pcapIf));
			}
		}
	};

	private PcapIf pcapIf;
	private Pcap pcapDevice;
	private PcapPacket pcapPacket;

	public JnetPcapWlanDevice(PcapIf pcapIf) {
		this.pcapIf = pcapIf;
	}

	@Override
	public String getName() {
		String name = pcapIf.getDescription();
		if (Utility.isEmpty(name))
			name = pcapIf.getName();
		return name;
	}

	@Override
	public byte[] getHardwareAddress() {
		try {
			return pcapIf.getHardwareAddress();
		} catch (IOException e) {
			return new byte[0];
		}
	}

	@Override
	public void open() {
		StringBuilder errbuf = new StringBuilder();
		pcapDevice = Pcap.openLive(pcapIf.getName(), CAPTURE_BUFFER_SIZE, Pcap.MODE_PROMISCUOUS, 1, errbuf);
		if (pcapDevice == null) {
			throw new RuntimeException(errbuf.toString());
		}

		pcapPacket = new PcapPacket(JMemory.POINTER);
	}

	@Override
	public int capturePacket(ByteBuffer buffer) {
		if (pcapDevice == null)
			return -1;

		int ret = pcapDevice.nextEx(pcapPacket);
		if (ret == Pcap.NEXT_EX_OK) {
			pcapPacket.transferTo(buffer);
			return pcapPacket.size();
		}
		return ret;
	}

	@Override
	public boolean sendPacket(ByteBuffer buffer) {
		if (pcapDevice == null)
			return false;

		pcapDevice.sendPacket(buffer);
		return true;
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
	public void close() {
		pcapDevice.close();
		pcapDevice = null;
	}
}
