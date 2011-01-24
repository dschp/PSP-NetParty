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

import java.nio.charset.Charset;

public class Constants {

	private Constants() {
	}

	public static final Charset CHARSET = Charset.forName("UTF-8");
	public static final String NEW_LINE = System.getProperty("line.separator");

	public class App {
		private App() {
		}

		public static final String APP_NAME = "PSP NetParty";
		public static final String VERSION = "0.3";
	}

	public class Ini {
		private Ini() {
		}

		public static final String SECTION_SETTINGS = "Settings";

		public static final String CLIENT_LOGIN_NAME = "UserName";
		// public static final String CLIENT_SERVER_ADDRESS = "ServerAddress";

		public static final String CLIENT_WINDOW_WIDTH = "WindowWidth";
		public static final String CLIENT_WINDOW_HEIGHT = "WindowHeight";

		public static final String CLIENT_SERVER_LIST = "ServerList";
		public static final String CLIENT_SERVER_HISTORY = "ServerHistory";

		public static final String CLIENT_LAST_LAN_ADAPTOR = "LastLanAdaptor";

		public static final String SECTION_LAN_ADAPTORS = "LanAdaptors";

		public static final String SERVER_PORT = "Port";
		public static final String SERVER_MAX_USERS = "MaxUsers";
		public static final String SERVER_MAX_ROOMS = "MaxRooms";
	}

	public class Protocol {
		private Protocol() {
		}

		public static final String MESSAGE_SEPARATOR = "\t";

		public static final int INTEGER_BYTE_SIZE = Integer.SIZE / 8;
		public static final int MAX_ROOM_PLAYERS = 16;

		public static final String SERVER_ROOM = "SERVER_ROOM";
		public static final String SERVER_ARENA = "SERVER_ARENA";
		public static final String SERVER_PORTAL = "SERVER_PORTAL";
		public static final String COMMAND_VERSION = "VERSION";
		public static final String PROTOCOL_NUMBER = "4";

		public static final String COMMAND_LOGIN = "LI";
		public static final String COMMAND_LOGOUT = "LO";
		public static final String COMMAND_CHAT = "CH";
		public static final String COMMAND_ROOM_CREATE = "RC";
		public static final String COMMAND_ROOM_UPDATE = "RU";
		public static final String COMMAND_ROOM_DELETE = "RD";
		public static final String COMMAND_ROOM_ENTER = "RE";
		public static final String COMMAND_ROOM_EXIT = "RX";
		public static final String COMMAND_ROOM_KICK_PLAYER = "RK";
		public static final String COMMAND_ROOM_MASTER_TRANSFER = "RT";
		
		public static final String COMMAND_ADMIN_NOTIFY = "AN";

		public static final String COMMAND_INFORM_TUNNEL_UDP_PORT = "IU";

		public static final String COMMAND_PING = "PG";
		public static final String COMMAND_PINGBACK = "PB";
		public static final String COMMAND_INFORM_PING = "IP";

		public static final String NOTIFY_SERVER_STATUS = "SS";
		public static final String NOTIFY_USER_ENTERED = "NUE";
		public static final String NOTIFY_USER_EXITED = "NUX";
		public static final String NOTIFY_USER_LIST = "NUL";
		public static final String NOTIFY_ROOM_CREATED = "NRC";
		public static final String NOTIFY_ROOM_DELETED = "NRD";
		public static final String NOTIFY_ROOM_LIST = "NRL";
		public static final String NOTIFY_ROOM_UPDATED = "NRU";
		public static final String NOTIFY_ROOM_PLAYER_KICKED = "NRK";

		public static final String NOTIFY_ROOM_PASSWORD_REQUIRED = "NRPR";

		public static final String ERROR_VERSION_MISMATCH = "ERR_VER_MIS";

		public static final String ERROR_LOGIN_DUPLICATED_NAME = "ERR_LI_DUP";
		public static final String ERROR_LOGIN_BEYOND_CAPACITY = "ERR_LI_CAP";

		public static final String ERROR_ROOM_CREATE_BEYOND_LIMIT = "ERR_RC_LIM";
		public static final String ERROR_ROOM_ENTER_PASSWORD_FAIL = "ERR_RE_PWF";
		public static final String ERROR_ROOM_ENTER_BEYOND_CAPACITY = "ERR_RE_CAP";
		public static final String ERROR_ROOM_TRANSFER_DUPLICATED_NAME = "ERR_RT_DUP";
	}
}
