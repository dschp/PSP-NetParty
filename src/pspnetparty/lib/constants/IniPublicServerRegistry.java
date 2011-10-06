package pspnetparty.lib.constants;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;

public class IniPublicServerRegistry implements IServerRegistry {
	private static final String FILE_NAME = "PublicServerList";

	private static final String PORTAL_SERVER_LIST = "PortalServers";
	private static final String ROOM_SERVER_LIST = "RoomServers";
	private static final String SEARCH_SERVER_LIST = "SearchServers";
	private static final String LOBBY_SERVER_LIST = "LobbyServers";

	private File file;
	private long lastModified;

	private IniSection section;

	private String[] portalServers;
	private String[] roomServers;
	private String[] searchServers;
	private String[] lobbyServers;

	public IniPublicServerRegistry() throws IOException {
		file = new File(FILE_NAME);
		lastModified = file.lastModified();

		IniFile iniFile = new IniFile(FILE_NAME);
		section = iniFile.getSection(null);
	}

	public String[] getPortalServers() {
		if (portalServers == null) {
			portalServers = section.get(PORTAL_SERVER_LIST, "").split(",");
		}
		return portalServers;
	}

	public String[] getRoomServers() {
		if (roomServers == null) {
			roomServers = section.get(ROOM_SERVER_LIST, "").split(",");
		}
		return roomServers;
	}

	public String[] getSearchServers() {
		if (searchServers == null) {
			searchServers = section.get(SEARCH_SERVER_LIST, "").split(",");
		}
		return searchServers;
	}

	public String[] getLobbyServers() {
		if (lobbyServers == null) {
			lobbyServers = section.get(LOBBY_SERVER_LIST, "").split(",");
		}
		return lobbyServers;
	}

	public Iterator<String> getPortalRotator() {
		return new Iterator<String>() {
			int index;
			{
				String[] list = getPortalServers();
				index = (int) (Math.random() * list.length);
			}

			@Override
			public void remove() {
			}

			@Override
			public String next() {
				String[] list = getPortalServers();
				if (list.length == 0)
					return null;
				if (index >= list.length)
					index = 0;
				String s = list[index];
				index++;
				return s;
			}

			@Override
			public boolean hasNext() {
				String[] list = getPortalServers();
				return list.length > 0;
			}
		};
	}

	@Override
	public void reload() {
		if (lastModified >= file.lastModified())
			return;

		try {
			IniFile iniFile = new IniFile(FILE_NAME);
			section = iniFile.getSection(null);

			portalServers = null;
			roomServers = null;
			searchServers = null;
			lobbyServers = null;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean isValidPortalServer(InetAddress address) {
		for (String server : getPortalServers()) {
			try {
				String[] tokens = server.split(":");
				InetAddress serverAddress = InetAddress.getByName(tokens[0]);
				if (serverAddress.equals(address))
					return true;
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
}