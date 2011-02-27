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

public class NativeWlanDevice implements WlanDevice {
	static {
		initialize();
	}

	private native static void initialize();

	private NativeWlanDevice() {
	}

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
}
