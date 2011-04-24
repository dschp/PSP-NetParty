package pspnetparty.lib.constants;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;

public class IniPublicServer implements IServerNetwork {
	private static final String FILE_NAME = "PublicServerList";

	private static final String PORTAL_SERVER_LIST = "PortalServers";
	private static final String ROOM_SERVER_LIST = "RoomServers";
	private static final String SEARCH_SERVER_LIST = "SearchServers";
	private static final String LOBBY_SERVER_LIST = "LobbyServers";

	private File file;
	private long lastModified;

	private IniSection section;

	public IniPublicServer() throws IOException {
		file = new File(FILE_NAME);
		lastModified = file.lastModified();

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

	/* (non-Javadoc)
	 * @see pspnetparty.lib.constants.IPublicServer#reload()
	 */
	@Override
	public void reload() {
		if (lastModified >= file.lastModified())
			return;

		try {
			IniFile iniFile = new IniFile(FILE_NAME);
			section = iniFile.getSection(null);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* (non-Javadoc)
	 * @see pspnetparty.lib.constants.IPublicServer#isValidPortalServer(java.net.InetAddress)
	 */
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