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

	public static final String PROTOCOL_NUMBER = "4";
	public static final String ERROR_PROTOCOL_MISMATCH = "ERR_PROT_MIS";

	public static final String ARGUMENT_SEPARATOR = "\t";
	public static final String MESSAGE_SEPARATOR = "\f";// "\t";
	public static final int INTEGER_BYTE_SIZE = Integer.SIZE / 8;

	public class Room {
		private Room() {
		}

		public static final int MAX_ROOM_PLAYERS = 16;
		public static final String TUNNEL_DUMMY_PACKET = " ";

		public static final String PROTOCOL_NAME = "PNP_ROOM";

		public static final String COMMAND_LOGIN = "LI";
		public static final String COMMAND_LOGOUT = "LO";
		public static final String COMMAND_CHAT = "CH";
		public static final String COMMAND_ROOM_CREATE = "RC";
		public static final String COMMAND_ROOM_UPDATE = "RU";
		public static final String COMMAND_ROOM_KICK_PLAYER = "RK";
		public static final String COMMAND_ROOM_MASTER_TRANSFER = "RT";

		public static final String COMMAND_ADMIN_NOTIFY = "AN";

		public static final String COMMAND_INFORM_TUNNEL_UDP_PORT = "IU";
		public static final String COMMAND_MAC_ADDRESS_PLAYER = "MAP";

		public static final String COMMAND_PING = "PG";
		public static final String COMMAND_PINGBACK = "PB";
		public static final String COMMAND_INFORM_PING = "IP";

		public static final String NOTIFY_SERVER_STATUS = "SS";
		public static final String NOTIFY_USER_ENTERED = "NUE";
		public static final String NOTIFY_USER_EXITED = "NUX";
		public static final String NOTIFY_USER_LIST = "NUL";
		public static final String NOTIFY_ROOM_DELETED = "NRD";
		public static final String NOTIFY_ROOM_UPDATED = "NRU";
		public static final String NOTIFY_ROOM_PLAYER_KICKED = "NRK";

		public static final String NOTIFY_ROOM_PASSWORD_REQUIRED = "NRPR";

		public static final String ERROR_LOGIN_DUPLICATED_NAME = "ERR_LI_DUP";
		public static final String ERROR_LOGIN_BEYOND_CAPACITY = "ERR_LI_CAP";
		public static final String ERROR_LOGIN_ROOM_NOT_EXIST = "ERR_LI_RNE";

		public static final String ERROR_ROOM_INVALID_DATA_ENTRY = "ERR_RM_IDE";
		public static final String ERROR_ROOM_PASSWORD_NOT_ALLOWED = "ERR_RM_PNA";
		public static final String ERROR_ROOM_CREATE_DUPLICATED_NAME = "ERR_RC_DUP";
		public static final String ERROR_ROOM_CREATE_BEYOND_LIMIT = "ERR_RC_LIM";
		public static final String ERROR_ROOM_ENTER_PASSWORD_FAIL = "ERR_RE_PWF";
		public static final String ERROR_ROOM_ENTER_BEYOND_CAPACITY = "ERR_RE_CAP";
		public static final String ERROR_ROOM_TRANSFER_DUPLICATED_NAME = "ERR_RT_DUP";

		public static final String COMMAND_CONFIRM_AUTH_CODE = "CAC";
		public static final String NOTIFY_ROOM_MASTER_AUTH_CODE = "NRAC";
		public static final String ERROR_CONFIRM_INVALID_AUTH_CODE = "ERR_CAC_NG";
	}

	public class Search {
		private Search() {
		}

		public static final String PROTOCOL_NAME = "PNP_SEARCH";

		public static final String MODE_MASTER = "M";
		public static final String MODE_PARTICIPANT = "P";

		public static final String COMMAND_LOGIN = "I";
		public static final String COMMAND_LOGOUT = "O";

		public static final String COMMAND_ENTRY = "E";
		public static final String COMMAND_UPDATE = "U";
		public static final String COMMAND_UPDATE_PLAYER_COUNT = "C";

		public static final String COMMAND_SEARCH = "S";

		public static final String ERROR_MASTER_TCP_PORT = "ERR_RM_TCP";
		public static final String ERROR_MASTER_UDP_PORT = "ERR_RM_UDP";
		public static final String ERROR_MASTER_INVALID_AUTH_CODE = "ERR_RM_IAC";
		public static final String ERROR_MASTER_DATABASE_ENTRY = "ERR_RM_DBE";
	}
}