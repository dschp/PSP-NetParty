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
package pspnetparty.client.swt.config;

import pspnetparty.client.swt.WlanProxyLibrary;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.socket.TransportLayer;
import pspnetparty.wlan.JnetPcapWlanDevice;
import pspnetparty.wlan.NativeWlanDevice;
import pspnetparty.wlan.WlanLibrary;

public class IniSettings {
	private static final String TCP = "TCP";
	private static final String UDP = "UDP";

	public static final String SECTION = "Settings";

	private static final String STARTUP_WINDOW = "StartupWindow";
	private static final String APP_CLOSE_CONFIRM = "AppCloseConfirm";
	private static final String LOG_LOBBY_ENTER_EXIT = "LogLobbyEnterExit";
	private static final String BALLOON_NOTIFY_LOBBY = "BalloonNotifyLobby";
	private static final String BALLOON_NOTIFY_ROOM = "BalloonNotifyRoom";
	private static final String ARENA_AUTO_LOGIN_ROOM_LIST = "ArenaAutoLoginSearch";
	private static final String ARENA_AUTO_LOGIN_LOBBY = "ArenaAutoLoginLobby";

	private static final String WLAN_LIBRARY = "WlanLibrary";
	private static final String SSID_AUTO_SCAN = "SSIDAutoScan";
	private static final String TUNNEL_TRANSPORT_LAYER = "TunnelTransportLayer";

	private static final String MY_ROOM_HOST_NAME = "MyRoomHostName";
	private static final String MY_ROOM_PORT = "MyRoomPort";
	private static final String MY_ROOM_ALLOW_NO_MASTER_NAME = "MyRoomAllowNoMasterName";

	private static final String PRIVATE_PORTAL_SERVER_USE = "PrivatePortalServerUse";
	private static final String PRIVATE_PORTAL_SERVER_ADDRESS = "PrivatePortalServerAddress";

	private static final String ROOM_SERVER_LIST = "RoomServerList";
	private static final String ROOM_ADDRESS_LIST = "RoomAddressList";

	private static final String SHOW_CHAT_PRESET_BUTTONS = "ShowChatPresetButtons";
	private static final String CHAT_PRESET_BOTTON_MAX_LENGTH = "ChatPresetButtonMaxLength";
	private static final String CHAT_PRESET_ENABLE_KEY_INPUT = "ChatPresetEnableKeyInput";

	private IniSection section;
	private WlanProxyLibrary wlanProxyLibrary;

	public IniSettings(IniSection section, WlanProxyLibrary proxyLibrary) {
		this.section = section;
		wlanProxyLibrary = proxyLibrary;

		startupWindowIsArena = !"Room".equals(section.get(STARTUP_WINDOW, "Arena"));
		needAppCloseConfirm = section.get(APP_CLOSE_CONFIRM, true);
		logLobbyEnterExit = section.get(LOG_LOBBY_ENTER_EXIT, true);
		ballonNotifyLobby = section.get(BALLOON_NOTIFY_LOBBY, true);
		ballonNotifyRoom = section.get(BALLOON_NOTIFY_ROOM, true);
		arenaAutoLoginRoomList = section.get(ARENA_AUTO_LOGIN_ROOM_LIST, true);
		arenaAutoLoginLobby = section.get(ARENA_AUTO_LOGIN_LOBBY, true);

		String library = section.get(WLAN_LIBRARY, JnetPcapWlanDevice.LIBRARY_NAME);
		if (NativeWlanDevice.LIBRARY_NAME.equals(library)) {
			wlanLibrary = NativeWlanDevice.LIBRARY;
		} else if (WlanProxyLibrary.LIBRARY_NAME.equals(library)) {
			wlanLibrary = wlanProxyLibrary;
		} else {
			wlanLibrary = JnetPcapWlanDevice.LIBRARY;
		}

		section.set(WLAN_LIBRARY, wlanLibrary.getName());
		ssidAutoScan = section.get(SSID_AUTO_SCAN, false);
		tunnelTransportLayer = UDP.equals(section.get(TUNNEL_TRANSPORT_LAYER, TCP)) ? TransportLayer.UDP : TransportLayer.TCP;

		myRoomHostName = section.get(MY_ROOM_HOST_NAME, "");
		myRoomPort = section.get(MY_ROOM_PORT, 30000);
		myRoomAllowNoMasterName = section.get(MY_ROOM_ALLOW_NO_MASTER_NAME, true);

		privatePortalServerUse = section.get(PRIVATE_PORTAL_SERVER_USE, false);
		privatePortalServerAddress = section.get(PRIVATE_PORTAL_SERVER_ADDRESS, "");

		roomServerList = section.get(ROOM_SERVER_LIST, "").split(",");
		roomAddressList = section.get(ROOM_ADDRESS_LIST, "").split(",");

		showChatPresetButtons = section.get(SHOW_CHAT_PRESET_BUTTONS, false);
		chatPresetButtonMaxLength = section.get(CHAT_PRESET_BOTTON_MAX_LENGTH, 5);
		chatPresetEnableKeyInput = section.get(CHAT_PRESET_ENABLE_KEY_INPUT, false);
	}

	private boolean startupWindowIsArena;
	private boolean needAppCloseConfirm;
	private boolean logLobbyEnterExit;
	private boolean ballonNotifyLobby;
	private boolean ballonNotifyRoom;
	private boolean arenaAutoLoginRoomList;
	private boolean arenaAutoLoginLobby;

	private WlanLibrary wlanLibrary;
	private boolean ssidAutoScan;
	private TransportLayer tunnelTransportLayer;

	private String myRoomHostName;
	private int myRoomPort;
	private boolean myRoomAllowNoMasterName;
	private boolean privatePortalServerUse;
	private String privatePortalServerAddress;
	private String[] roomServerList;
	private String[] roomAddressList;
	private boolean showChatPresetButtons;
	private int chatPresetButtonMaxLength;
	private boolean chatPresetEnableKeyInput;

	public boolean isStartupWindowArena() {
		return startupWindowIsArena;
	}

	public void setStartupWindowArena(boolean startupWindowIsArena) {
		this.startupWindowIsArena = startupWindowIsArena;
		section.set(STARTUP_WINDOW, startupWindowIsArena ? "Arena" : "Room");
	}

	public boolean isNeedAppCloseConfirm() {
		return needAppCloseConfirm;
	}

	public void setNeedAppCloseConfirm(boolean needAppCloseConfirm) {
		this.needAppCloseConfirm = needAppCloseConfirm;
		section.set(APP_CLOSE_CONFIRM, needAppCloseConfirm);
	}

	public boolean isLogLobbyEnterExit() {
		return logLobbyEnterExit;
	}

	public void setLogLobbyEnterExit(boolean logLobbyEnterExit) {
		this.logLobbyEnterExit = logLobbyEnterExit;
		section.set(LOG_LOBBY_ENTER_EXIT, logLobbyEnterExit);
	}

	public boolean isBallonNotifyLobby() {
		return ballonNotifyLobby;
	}

	public void setBallonNotifyLobby(boolean ballonNotifyLobby) {
		this.ballonNotifyLobby = ballonNotifyLobby;
		section.set(BALLOON_NOTIFY_LOBBY, ballonNotifyLobby);
	}

	public boolean isBallonNotifyRoom() {
		return ballonNotifyRoom;
	}

	public void setBallonNotifyRoom(boolean ballonNotifyRoom) {
		this.ballonNotifyRoom = ballonNotifyRoom;
		section.set(BALLOON_NOTIFY_ROOM, ballonNotifyRoom);
	}

	public boolean isArenaAutoLoginRoomList() {
		return arenaAutoLoginRoomList;
	}

	public void setArenaAutoLoginSearch(boolean arenaAutoLoginSearch) {
		this.arenaAutoLoginRoomList = arenaAutoLoginSearch;
		section.set(ARENA_AUTO_LOGIN_ROOM_LIST, arenaAutoLoginSearch);
	}

	public boolean isArenaAutoLoginLobby() {
		return arenaAutoLoginLobby;
	}

	public void setArenaAutoLoginLobby(boolean arenaAutoLoginLobby) {
		this.arenaAutoLoginLobby = arenaAutoLoginLobby;
		section.set(ARENA_AUTO_LOGIN_LOBBY, arenaAutoLoginLobby);
	}

	public boolean isPrivatePortalServerUse() {
		return privatePortalServerUse;
	}

	public void setPrivatePortalServerUse(boolean privatePortalServerUse) {
		this.privatePortalServerUse = privatePortalServerUse;
		section.set(PRIVATE_PORTAL_SERVER_USE, privatePortalServerUse);
	}

	public String getPrivatePortalServerAddress() {
		return privatePortalServerAddress;
	}

	public void setPrivatePortalServerAddress(String privatePortalServerAddress) {
		this.privatePortalServerAddress = privatePortalServerAddress;
		section.set(PRIVATE_PORTAL_SERVER_ADDRESS, privatePortalServerAddress);
	}

	public String getMyRoomHostName() {
		return myRoomHostName;
	}

	public void setMyRoomHostName(String myRoomHostName) {
		this.myRoomHostName = myRoomHostName;
		section.set(MY_ROOM_HOST_NAME, myRoomHostName);
	}

	public int getMyRoomPort() {
		return myRoomPort;
	}

	public void setMyRoomPort(int myRoomPort) {
		this.myRoomPort = myRoomPort;
		section.set(MY_ROOM_PORT, myRoomPort);
	}

	public boolean isMyRoomAllowNoMasterName() {
		return myRoomAllowNoMasterName;
	}

	public void setMyRoomAllowNoMasterName(boolean myRoomAllowNoMasterName) {
		this.myRoomAllowNoMasterName = myRoomAllowNoMasterName;
		section.set(MY_ROOM_ALLOW_NO_MASTER_NAME, myRoomAllowNoMasterName);
	}

	public WlanLibrary getWlanLibrary() {
		return wlanLibrary;
	}

	public void setWlanLibrary(WlanLibrary library) {
		if (library == null)
			library = wlanProxyLibrary;

		wlanLibrary = library;
		section.set(WLAN_LIBRARY, wlanLibrary.getName());
	}

	public boolean isSsidAutoScan() {
		return ssidAutoScan;
	}

	public void setSsidAutoScan(boolean ssidAutoScan) {
		this.ssidAutoScan = ssidAutoScan;
		section.set(SSID_AUTO_SCAN, ssidAutoScan);
	}

	public TransportLayer getTunnelTransportLayer() {
		return tunnelTransportLayer;
	}

	public void setTunnelTransportLayer(TransportLayer tunnelTransportLayer) {
		this.tunnelTransportLayer = tunnelTransportLayer;
		switch (tunnelTransportLayer) {
		case TCP:
			section.set(TUNNEL_TRANSPORT_LAYER, TCP);
			break;
		case UDP:
			section.set(TUNNEL_TRANSPORT_LAYER, UDP);
			break;
		}
	}

	public boolean isShowChatPresetButtons() {
		return showChatPresetButtons;
	}

	public void setShowChatPresetButtons(boolean showChatPresetButtons) {
		this.showChatPresetButtons = showChatPresetButtons;
		section.set(SHOW_CHAT_PRESET_BUTTONS, showChatPresetButtons);
	}

	public int getChatPresetButtonMaxLength() {
		return chatPresetButtonMaxLength;
	}

	public void setChatPresetButtonMaxLength(int chatPresetButtonMaxLength) {
		if (chatPresetButtonMaxLength < 1)
			return;
		this.chatPresetButtonMaxLength = chatPresetButtonMaxLength;
		section.set(CHAT_PRESET_BOTTON_MAX_LENGTH, chatPresetButtonMaxLength);
	}

	public boolean isChatPresetEnableKeyInput() {
		return chatPresetEnableKeyInput;
	}

	public void setChatPresetEnableKeyInput(boolean chatPresetEnableKeyInput) {
		this.chatPresetEnableKeyInput = chatPresetEnableKeyInput;
		section.set(CHAT_PRESET_ENABLE_KEY_INPUT, chatPresetEnableKeyInput);
	}

	public int getMaxLogCount() {
		return 1000;
	}

	public String[] getRoomServerList() {
		return roomServerList;
	}

	public String[] getRoomAddressList() {
		return roomAddressList;
	}
}