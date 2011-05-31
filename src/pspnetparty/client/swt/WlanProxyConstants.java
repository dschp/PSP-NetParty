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
package pspnetparty.client.swt;

import pspnetparty.lib.constants.AppConstants;

public class WlanProxyConstants {
	public static final String PROTOCOL = "PNP_ADHOC_PROXY";

	public static final char COMMAND_SSID_FEATURE_ENABLED = 'F';
	public static final char COMMAND_SSID_FEATURE_DISABLED = 'f';
	public static final char COMMAND_PACKET = 'P';
	public static final char COMMAND_GET_SSID = 'S';
	public static final char COMMAND_SET_SSID = 's';
	public static final char COMMAND_SCAN_NETWORK = 'n';
	public static final char COMMAND_FIND_NETWORK = 'N';

	public static final byte BYTE_COMMAND_PACKET = AppConstants.CHARSET.encode(String.valueOf(WlanProxyConstants.COMMAND_PACKET)).get(0);
}
