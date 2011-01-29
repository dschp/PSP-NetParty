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
package pspnetparty.lib;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;

import org.jnetpcap.protocol.lan.Ethernet;

import pspnetparty.lib.constants.AppConstants;

public class Utility {

	private Utility() {
	}
	
	public static String decode(ByteBuffer buffer) {
		return AppConstants.CHARSET.decode(buffer).toString();
	}

	public static String makeStackTrace(Throwable exception) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		exception.printStackTrace(pw);
		return sw.toString();
	}

	public static boolean isEmpty(String str) {
		if (str == null)
			return true;
		return "".equals(str);
	}

	public static boolean equals(String str1, String str2) {
		if (str1 == str2)
			return true;

		if (isEmpty(str1))
			return isEmpty(str2);

		return str1.equals(str2);
	}

	public static String removeQuotations(String string) {
		if (string == null || string.length() < 3)
			return "";
		else
			return string.substring(1, string.length() - 1);
	}
	 
	public static String trim(String string, int maxLength) {
		if (string == null)
			return "";
		return string.substring(0, Math.min(maxLength, string.length()));
	}

	public static boolean isPspPacket(ByteBuffer packet) {
		return packet.limit() > 14 && packet.get(12) == -120 && packet.get(13) == -56;
	}

	public static boolean isPspPacket(Ethernet ethernet) {
		return ethernet.type() == 35016;// 0x88c8;
	}

	public static String makeMacAddressString(ByteBuffer packet, int offset, boolean needHyphen) {
		if (packet == null)
			return "";

		if (packet.limit() < offset + 6)
			throw new IllegalArgumentException();
		String format = needHyphen ? "%02X-%02X-%02X-%02X-%02X-%02X" : "%02X%02X%02X%02X%02X%02X";
		return String.format(format, packet.get(offset), packet.get(offset + 1), packet.get(offset + 2), packet.get(offset + 3),
				packet.get(offset + 4), packet.get(offset + 5));
	}

	public static String makeMacAddressString(byte[] packet, int offset, boolean needHyphen) {
		if (packet == null)
			return "";

		if (packet.length < offset + 6)
			throw new IllegalArgumentException();
		String format = needHyphen ? "%02X-%02X-%02X-%02X-%02X-%02X" : "%02X%02X%02X%02X%02X%02X";
		return String.format(format, packet[offset], packet[offset + 1], packet[offset + 2], packet[offset + 3], packet[offset + 4],
				packet[offset + 5]);
	}

	public static boolean isMacBroadCastAddress(String macAddress) {
		return "FFFFFFFFFFFF".equals(macAddress);
	}
	
	public static String makeAuthCode() {
		return Long.toString(System.currentTimeMillis());
	}

	public static void main(String[] args) {
		byte[] data = new byte[] { (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255 };
		ByteBuffer mac = ByteBuffer.wrap(data);

		System.out.println(makeMacAddressString(mac, 0, true));
		System.out.println(makeMacAddressString(mac, 0, false));

		System.out.println(makeMacAddressString(data, 0, true));
		System.out.println(makeMacAddressString(data, 0, false));

		System.out.println(mac);

		System.out.println(ByteBuffer.allocateDirect(100));
		
		System.out.println(trim("EAG", 10));
		System.out.println(trim("EAGkljhklglkgWGQWW", 10));
	}
}
