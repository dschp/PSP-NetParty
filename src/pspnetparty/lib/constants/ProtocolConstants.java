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
package pspnetparty.lib.constants;

public class ProtocolConstants {
	private ProtocolConstants() {
	}

	public static final int TIMEOUT = 2000;

	public static final String PROTOCOL_ROOM = "PNP_ROOM";
	public static final String PROTOCOL_TUNNEL = "PNP_TUNNEL";
	public static final String PROTOCOL_PORTAL = "PNP_PORTAL";
	public static final String PROTOCOL_SEARCH = "PNP_SEARCH";
	public static final String PROTOCOL_LOBBY = "PNP_LOBBY";

	public static final String PROTOCOL_MY_ROOM_ENTRY = "PNP_MYROOM";
	public static final String PROTOCOL_ROOM_STATUS = "PNP_ROOM_STAT";
	public static final String PROTOCOL_SEARCH_STATUS = "PNP_SEARCH_STAT";
	public static final String PROTOCOL_LOBBY_STATUS = "PNP_LOBBY_STAT";

	public static final String SERVER_STATUS = "SS";

	public class Portal {
		private Portal() {
		}

		public static final String COMMAND_FIND_ROOM_SERVER = "R";
		public static final String COMMAND_LIST_ROOM_SERVERS = "r";
		public static final String COMMAND_FIND_SEARCH_SERVER = "S";
		public static final String COMMAND_LIST_SEARCH_SERVERS = "s";
		public static final String COMMAND_LIST_LOBBY_SERVERS = "L";
	}

	public class Room {
		private Room() {
		}

		public static final int MAX_ROOM_PLAYERS = 16;

		public static final String COMMAND_LOGIN = "I";
		public static final String COMMAND_LOGOUT = "O";
		public static final String COMMAND_CHAT = "C";
		public static final String COMMAND_ROOM_CREATE = "RC";
		public static final String COMMAND_ROOM_UPDATE = "RU";
		public static final String COMMAND_ROOM_KICK_PLAYER = "RK";
		public static final String COMMAND_ROOM_MASTER_TRANSFER = "RT";
		public static final String COMMAND_ROOM_DELETE = "RD";

		public static final String COMMAND_INFORM_TUNNEL_PORT = "IU";
		public static final String COMMAND_MAC_ADDRESS_PLAYER = "MAC";

		public static final String COMMAND_PING = "PG";
		public static final String COMMAND_PINGBACK = "PB";
		public static final String COMMAND_INFORM_PING = "IP";
		public static final String COMMAND_INFORM_SSID = "IS";

		public static final String COMMAND_WHITELIST_ENABLE = "WE";
		public static final String COMMAND_WHITELIST_ADD = "WA";
		public static final String COMMAND_WHITELIST_REMOVE = "WR";
		public static final String COMMAND_BLACKLIST_ENABLE = "BE";
		public static final String COMMAND_BLACKLIST_ADD = "BA";
		public static final String COMMAND_BLACKLIST_REMOVE = "BR";

		public static final String NOTIFY_USER_LIST = "NUL";
		public static final String NOTIFY_USER_ENTERED = "NUE";
		public static final String NOTIFY_USER_EXITED = "NUX";
		public static final String NOTIFY_ROOM_PLAYER_KICKED = "NRK";
		public static final String NOTIFY_ROOM_UPDATED = "NRU";
		public static final String NOTIFY_ROOM_DELETED = "NRD";
		public static final String NOTIFY_FROM_ADMIN = "AN";
		public static final String NOTIFY_SSID_CHANGED = "NSC";

		public static final String NOTIFY_ROOM_PASSWORD_REQUIRED = "NRPR";

		public static final String NOTIFY_ROOM_AGE_OLD = "NRAO";
		public static final String NOTIFY_TUNNEL_COMMUNICATION_IDLE = "NTCI";

		public static final String ERROR_LOGIN_DUPLICATED_NAME = "ERR_LI_DUP";
		public static final String ERROR_LOGIN_BEYOND_CAPACITY = "ERR_LI_CAP";
		public static final String ERROR_LOGIN_ROOM_NOT_EXIST = "ERR_LI_RNE";
		public static final String ERROR_LOGIN_PASSWORD_FAIL = "ERR_LI_PWF";

		public static final String ERROR_ROOM_CREATE_BEYOND_LIMIT = "ERR_RC_LIM";
		public static final String ERROR_ROOM_CREATE_DUPLICATED_NAME = "ERR_RC_DUP";
		public static final String ERROR_ROOM_CREATE_INVALID_DATA_ENTRY = "ERR_RM_IDE";
		public static final String ERROR_ROOM_TRANSFER_DUPLICATED_NAME = "ERR_RT_DUP";

		public static final String COMMAND_CONFIRM_AUTH_CODE = "CAC";
		public static final String NOTIFY_ROOM_MASTER_AUTH_CODE = "NRAC";
		public static final String ERROR_CONFIRM_INVALID_AUTH_CODE = "ERR_CAC_NG";
	}

	public class Tunnel {
		private Tunnel() {
		}

		public static final String DUMMY_PACKET = " ";
	}

	public class MyRoom {
		private MyRoom() {
		}

		public static final String COMMAND_ENTRY = "E";
		public static final String COMMAND_UPDATE = "U";
		public static final String COMMAND_UPDATE_PLAYER_COUNT = "C";
		public static final String COMMAND_LOGOUT = "O";

		public static final String ERROR_TCP_PORT_NOT_OPEN = "ERR_TCP";
		public static final String ERROR_UDP_PORT_NOT_OPEN = "ERR_UDP";
		public static final String ERROR_INVALID_AUTH_CODE = "ERR_IAC";
	}

	public class RoomStatus {
		private RoomStatus() {
		}

		public static final String NOTIFY_ROOM_CREATED = "C";
		public static final String NOTIFY_ROOM_UPDATED = "U";
		public static final String NOTIFY_ROOM_DELETED = "D";
		public static final String NOTIFY_ROOM_PLAYER_COUNT_CHANGED = "H";
	}

	public class Search {
		private Search() {
		}

		public static final String COMMAND_LOGIN = "I";
		public static final String COMMAND_LOGOUT = "O";
		public static final String COMMAND_SEARCH = "S";

		public static final String NOTIFY_FROM_ADMIN = "A";

		public static final String ERROR_LOGIN_BEYOND_CAPACITY = "ERR_LI_CAP";
	}

	public class SearchStatus {
		private SearchStatus() {
		}

		public static final String COMMAND_ASK_ROOM_DATA = "A";
		public static final String NOTIFY_ROOM_CREATED = "C";
		public static final String NOTIFY_ROOM_DELETED = "D";
		public static final String NOTIFY_ROOM_UPDATED = "U";
		public static final String NOTIFY_ROOM_PLAYER_COUNT_CHANGED = "H";
		public static final String NOTIFY_ROOM_SERVER_REMOVED = "S";
	}

	public class Lobby {
		private Lobby() {
		}

		public static final String COMMAND_LOGIN = "I";
		public static final String COMMAND_LOGOUT = "O";
		public static final String COMMAND_CHAT = "C";
		public static final String COMMAND_PRIVATE_CHAT = "P";
		public static final String COMMAND_CHANGE_STATE = "S";
		public static final String COMMAND_CHANGE_NAME = "N";

		public static final String NOTIFY_FROM_ADMIN = "AN";
		public static final String NOTIFY_LOBBY_INFO = "LI";

		public static final String NOTIFY_USER_LIST = "UL";
		public static final String NOTIFY_USER_INFO = "UI";
		public static final String NOTIFY_USER_LOGOUT = "UO";
		public static final String NOTIFY_USER_NAME_CHANGED = "UC";

		public static final String ERROR_LOGIN_USER_BEYOND_CAPACITY = "ERR_LU_CAP";
		public static final String ERROR_LOGIN_USER_DUPLICATED_NAME = "ERR_LU_DUP";
	}

	public class LobbyStatus {
		private LobbyStatus() {
		}

		public static final String NOTIFY_LOBBY_INFO = "L";
	}
}