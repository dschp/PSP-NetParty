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
import java.util.List;

import org.jnetpcap.Pcap;
import org.jnetpcap.PcapIf;
import org.jnetpcap.nio.JMemory;
import org.jnetpcap.packet.PcapPacket;

public class JnetPcapWlanDevice implements WlanDevice {
	private PcapIf pcapIf;
	private Pcap pcapDevice;
	private PcapPacket pcapPacket;
	
	public JnetPcapWlanDevice(PcapIf pcapIf) {
		this.pcapIf = pcapIf;
	}

	@Override
	public String getName() {
		return pcapIf.getDescription();
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
		pcapDevice = Pcap.openLive(pcapIf.getName(), Wlan.CAPTURE_BUFFER_SIZE, Pcap.MODE_PROMISCUOUS, 1, errbuf);
		if (pcapDevice == null) {
			throw new RuntimeException(errbuf.toString());
		}
		pcapPacket = new PcapPacket(JMemory.POINTER);
	}

	@Override
	public int capturePacket(ByteBuffer buffer) {
		if (pcapDevice == null)
			throw new IllegalStateException();
		
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
			throw new IllegalStateException();
		
		pcapDevice.sendPacket(buffer);
		return true;
	}

	@Override
	public String getSSID() {
		if (pcapDevice == null)
			throw new IllegalStateException();
		
		return "";
	}

	@Override
	public void setSSID(String ssid) {
		if (pcapDevice == null)
			throw new IllegalStateException();
		
	}

	@Override
	public boolean scanNetwork() {
		if (pcapDevice == null)
			throw new IllegalStateException();
		
		return false;
	}

	@Override
	public boolean findNetworks(List<WlanNetwork> networkList) {
		if (pcapDevice == null)
			throw new IllegalStateException();
		
		return false;
	}

	@Override
	public void close() {
		pcapDevice.close();
	}
}
