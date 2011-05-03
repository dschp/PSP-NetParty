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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Date;

import org.jnetpcap.protocol.lan.Ethernet;

import pspnetparty.lib.constants.AppConstants;

public class Utility {

	private Utility() {
	}

	public static String decode(ByteBuffer buffer) {
		return AppConstants.CHARSET.decode(buffer).toString();
	}

	public static String stackTraceToString(Throwable exception) {
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

	public static String trim(String string, int maxLength) {
		if (string == null)
			return "";
		return string.substring(0, Math.min(maxLength, string.length()));
	}

	public static String validateUserName(String name) {
		return name.replaceAll("(^[\\s　]+|[\\s　]+$)", "").replaceAll("[\\s　]{2,}", " ");
	}

	public static boolean isValidUserName(String name) {
		if (name == null || name.length() == 0 || name.length() > AppConstants.LOGIN_NAME_LIMIT)
			return false;
		return !name.matches("(^[\\s　]+.*|.*[\\s　]{2,}.*|.*[\\s　]+$)");
	}

	public static boolean isPspPacket(ByteBuffer packet) {
		return packet.limit() > 14 && packet.get(12) == -120 && packet.get(13) == -56;
	}

	public static boolean isPspPacket(Ethernet ethernet) {
		return ethernet.type() == 35016;// 0x88c8;
	}

	public static InetSocketAddress parseSocketAddress(String address) {
		String[] tokens = address.split(":");

		if (tokens.length != 2)
			return null;

		return parseSocketAddress(tokens[0], tokens[1]);
	}

	public static InetSocketAddress parseSocketAddress(String hostname, String port) {
		int portNum;
		try {
			portNum = Integer.parseInt(port);
			if (portNum > 0 && portNum < 65536)
				return new InetSocketAddress(hostname, portNum);
		} catch (NumberFormatException e) {
		}
		return null;
	}

	public static String socketAddressToStringByHostName(InetSocketAddress address) {
		if (address == null)
			return "";
		return address.getHostName() + ":" + address.getPort();
	}

	public static String socketAddressToStringByIP(InetSocketAddress address) {
		if (address == null)
			return "";
		return address.getAddress().getHostAddress() + ":" + address.getPort();
	}

	public static void appendSocketAddressByHostName(StringBuilder sb, InetSocketAddress address) {
		if (address == null)
			return;
		sb.append(address.getHostName()).append(':').append(address.getPort());
	}

	public static void appendSocketAddressByIP(StringBuilder sb, InetSocketAddress address) {
		if (address == null)
			return;
		sb.append(address.getAddress().getHostAddress()).append(':').append(address.getPort());
	}

	public static String macAddressToString(ByteBuffer packet, int offset, boolean needHyphen) {
		if (packet == null)
			return "";

		if (packet.limit() < offset + 6)
			throw new IllegalArgumentException();
		String format = needHyphen ? "%02X-%02X-%02X-%02X-%02X-%02X" : "%02X%02X%02X%02X%02X%02X";
		return String.format(format, packet.get(offset), packet.get(offset + 1), packet.get(offset + 2), packet.get(offset + 3),
				packet.get(offset + 4), packet.get(offset + 5));
	}

	public static String macAddressToString(byte[] packet, int offset, boolean needHyphen) {
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
		long val = (long) (Math.random() * 1000000000);
		return Long.toString(val);
	}

	public static String getFileContent(File file) {
		if (file == null || !file.isFile())
			return "";
		try {
			RandomAccessFile raf = new RandomAccessFile(file, "r");
			FileChannel fc = raf.getChannel();
			ByteBuffer buffer = ByteBuffer.allocate((int) fc.size());
			fc.read(buffer);
			raf.close();
			return new String(buffer.array(), AppConstants.CHARSET);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}

	public static String makePingLog(String transport, InetSocketAddress local, InetSocketAddress remote, long time) {
		StringBuilder sb = new StringBuilder();

		sb.append(transport);
		sb.append(" Ping受信: ");

		sb.append(socketAddressToStringByIP(remote));
		sb.append(" -> ");
		sb.append(socketAddressToStringByIP(local));
		sb.append(" [");
		sb.append(ILogger.DATE_FORMAT.format(new Date(time)));
		sb.append("]");

		return sb.toString();
	}

	public static String makePingDisconnectLog(String transport, InetSocketAddress address, long deadline, long lastPingTime) {
		Date date = new Date();

		StringBuilder sb = new StringBuilder();
		sb.append(transport);
		sb.append(" PING切断: ");

		date.setTime(deadline);
		sb.append("Deadline[");
		sb.append(ILogger.DATE_FORMAT.format(date));

		date.setTime(lastPingTime);
		sb.append("] LastPingTime[");
		sb.append(ILogger.DATE_FORMAT.format(date));
		sb.append("] @ ");

		appendSocketAddressByIP(sb, address);

		return sb.toString();
	}

	public static void main(String[] args) {
		byte[] data = new byte[] { (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255 };
		ByteBuffer mac = ByteBuffer.wrap(data);

		System.out.println(macAddressToString(mac, 0, true));
		System.out.println(macAddressToString(mac, 0, false));

		System.out.println(macAddressToString(data, 0, true));
		System.out.println(macAddressToString(data, 0, false));

		System.out.println(mac);

		System.out.println(ByteBuffer.allocateDirect(100));

		System.out.println(trim("EAG", 10));
		System.out.println(trim("EAGkljhklglkgWGQWW", 10));
	}
}
