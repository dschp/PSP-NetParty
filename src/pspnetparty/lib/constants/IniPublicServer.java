package pspnetparty.lib.constants;

import java.io.IOException;

import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;

public class IniPublicServer {
	private static final String FILE_NAME = "PublicServerList";

	private static final String PORTAL_SERVER_LIST = "PortalServers";
	private static final String ROOM_SERVER_LIST = "RoomServers";
	private static final String SEARCH_SERVER_LIST = "SearchServers";
	private static final String LOBBY_SERVER_LIST = "LobbyServers";

	private IniSection section;

	public IniPublicServer() throws IOException {
		IniFile iniFile = new IniFile(FILE_NAME);
		section = iniFile.getSection(null);
	}

	public String[] getPortalServers() {
		return section.get(PORTAL_SERVER_LIST, "").split(",");
	}

	public String[] getRoomServers() {
		return section.get(ROOM_SERVER_LIST, "").split(",");
	}

	public String[] getSearchServers() {
		return section.get(SEARCH_SERVER_LIST, "").split(",");
	}

	public String[] getLobbyServers() {
		return section.get(LOBBY_SERVER_LIST, "").split(",");
	}
}