package pspnetparty.client.swt;

import pspnetparty.lib.IniSection;

public class IniSettings {
	enum TransportLayer {
		TCP, UDP
	}

	private static final String TCP = "TCP";
	private static final String UDP = "UDP";

	public static final String SECTION = "Settings";

	private static final String USER_NAME = "UserName";

	private static final String APP_CLOSE_CONFIRM = "AppCloseConfirm";
	private static final String LOG_LOBBY_ENTER_EXIT = "LogLobbyEnterExit";
	private static final String BALLOON_NOTIFY_LOBBY = "BalloonNotifyLobby";
	private static final String BALLOON_NOTIFY_ROOM = "BalloonNotifyRoom";

	private static final String TUNNEL_TRANSPORT_LAYER = "TunnelTransportLayer";

	private static final String MY_ROOM_HOST_NAME = "MyRoomHostName";
	private static final String MY_ROOM_PORT = "MyRoomPort";
	private static final String MY_ROOM_ALLOW_NO_MASTER_NAME = "MyRoomAllowNoMasterName";

	private static final String PRIVATE_PORTAL_SERVER_USE = "PrivatePortalServerUse";
	private static final String PRIVATE_PORTAL_SERVER_ADDRESS = "PrivatePortalServerAddress";

	private static final String ROOM_SERVER_LIST = "RoomServerList";
	private static final String ROOM_ADDRESS_LIST = "RoomAddressList";

	private IniSection section;

	public IniSettings(IniSection section) {
		this.section = section;

		userName = section.get(USER_NAME, "");
		needAppCloseConfirm = section.get(APP_CLOSE_CONFIRM, true);
		logLobbyEnterExit = section.get(LOG_LOBBY_ENTER_EXIT, true);
		ballonNotifyLobby = section.get(BALLOON_NOTIFY_LOBBY, true);
		ballonNotifyRoom = section.get(BALLOON_NOTIFY_ROOM, true);

		tunnelTransportLayer = UDP.equals(section.get(TUNNEL_TRANSPORT_LAYER, TCP)) ? TransportLayer.UDP : TransportLayer.TCP;

		myRoomHostName = section.get(MY_ROOM_HOST_NAME, "");
		myRoomPort = section.get(MY_ROOM_PORT, 30000);
		myRoomAllowNoMasterName = section.get(MY_ROOM_ALLOW_NO_MASTER_NAME, true);

		privatePortalServerUse = section.get(PRIVATE_PORTAL_SERVER_USE, false);
		privatePortalServerAddress = section.get(PRIVATE_PORTAL_SERVER_ADDRESS, "");

		roomServerList = section.get(ROOM_SERVER_LIST, "").split(",");
		roomAddressList = section.get(ROOM_ADDRESS_LIST, "").split(",");
	}

	private String userName;
	private boolean needAppCloseConfirm;
	private boolean logLobbyEnterExit;
	private boolean ballonNotifyLobby;
	private boolean ballonNotifyRoom;
	private TransportLayer tunnelTransportLayer;
	private String myRoomHostName;
	private int myRoomPort;
	private boolean myRoomAllowNoMasterName;
	private boolean privatePortalServerUse;
	private String privatePortalServerAddress;
	private String[] roomServerList;
	private String[] roomAddressList;

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
		section.set(USER_NAME, userName);
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