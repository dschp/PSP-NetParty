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

public class IniConstants {
	private IniConstants() {
	}

	public static final String SECTION_SETTINGS = "Settings";

	public static class Client {
		private Client() {
		}

		public static final String LOGIN_NAME = "UserName";

		public static final String WINDOW_WIDTH = "WindowWidth";
		public static final String WINDOW_HEIGHT = "WindowHeight";

		public static final String MY_ROOM_HOST_NAME = "MyRoomHostName";
		public static final String MY_ROOM_PORT = "MyRoomPort";
		public static final String MY_ROOM_ALLOW_NO_MASTER_NAME = "MyRoomAllowNoMasterName";

		public static final String ROOM_SERVER_LIST = "RoomServerList";
		public static final String ROOM_SERVER_HISTORY = "RoomServerHistory";
		public static final String PROXY_SERVER_LIST = "ProxyServerList";
		public static final String PROXY_SERVER_HISTORY = "ProxyServerHistory";
		public static final String ENTRY_SEARCH_SERVER_LIST = "EntrySearchServerList";
		public static final String ENTRY_SEARCH_SERVER_HISTORY = "EntrySearchServerHistory";
		public static final String QUERY_SEARCH_SERVER_LIST = "QuerySearchServerList";
		public static final String QUERY_SEARCH_SERVER_HISTORY = "QuerySearchServerHistory";

		public static final String LAST_LAN_ADAPTER = "LastLanAdapter";

		public static final String SECTION_LAN_ADAPTERS = "LanAdapters";
	}

	public static class Server {
		private Server() {
		}

		public static final String PORT = "Port";
		public static final String MAX_USERS = "MaxUsers";
		public static final String MAX_ROOMS = "MaxRooms";

		public static final String DB_DRIVER = "DatabaseDriver";
		public static final String DB_URL = "DatabaseURL";
		public static final String DB_USER = "DatabaseUser";
		public static final String DB_PASSWORD = "DatabasePassword";

		public static final String DB_PING_SQL = "DatabasePingSQL";
	}
}