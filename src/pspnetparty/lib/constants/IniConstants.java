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

	public static final String CLIENT_LOGIN_NAME = "UserName";
	// public static final String CLIENT_SERVER_ADDRESS = "ServerAddress";

	public static final String CLIENT_WINDOW_WIDTH = "WindowWidth";
	public static final String CLIENT_WINDOW_HEIGHT = "WindowHeight";

	public static final String CLIENT_SERVER_LIST = "ServerList";
	public static final String CLIENT_SERVER_HISTORY = "ServerHistory";

	public static final String CLIENT_LAST_LAN_ADAPTER = "LastLanAdapter";

	public static final String SECTION_LAN_ADAPTERS = "LanAdapters";

	public static final String SERVER_PORT = "Port";
	public static final String SERVER_MAX_USERS = "MaxUsers";
	public static final String SERVER_MAX_ROOMS = "MaxRooms";
	
	public static final String SERVER_DB_DRIVER = "DatabaseDriver";
	public static final String SERVER_DB_URL = "DatabaseURL";
	public static final String SERVER_DB_USER = "DatabaseUser";
	public static final String SERVER_DB_PASSWORD = "DatabasePassword";
}