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
package pspnetparty.client.swt;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.jnetpcap.Pcap;
import org.jnetpcap.PcapIf;
import org.jnetpcap.nio.JMemory;
import org.jnetpcap.packet.PcapPacket;
import org.jnetpcap.protocol.lan.Ethernet;

import pspnetparty.lib.AsyncTcpClient;
import pspnetparty.lib.AsyncUdpClient;
import pspnetparty.lib.IAsyncClientHandler;
import pspnetparty.lib.IRoomMasterHandler;
import pspnetparty.lib.ISocketConnection;
import pspnetparty.lib.IniParser;
import pspnetparty.lib.PacketData;
import pspnetparty.lib.RoomEngine;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniConstants;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.constants.ProtocolConstants.Search;

public class PlayClient {

	private interface CommandHandler {
		public void process(String argument);
	}

	private static final int CAPTURE_BUFFER_SIZE = 2000;
	private static final int MAX_SERVER_HISTORY = 10;
	private static final int DEFAULT_MAX_PLAYERS = 4;

	enum OperationMode {
		Offline, RoomMaster, ConnectingToRoomServer, RoomParticipant, ConnectingToProxyServer, ProxyRoomMaster
	};

	private IniParser iniParser;
	private IniParser.Section iniSettingSection;

	private Display display;
	private Shell shell;

	private OperationMode currentOperationMode;
	private String loginUserName;
	private String roomMasterAuthCode;

	private RoomEngine roomEngine;

	private AsyncTcpClient tcpClient = new AsyncTcpClient();
	private AsyncUdpClient udpClient = new AsyncUdpClient();

	private RoomClientHandler roomClientHandler = new RoomClientHandler();
	private ISocketConnection roomConnection;

	private TunnelHandler tunnelHandler = new TunnelHandler();
	private ISocketConnection tunnelConnection;

	private MasterSearchHandler masterSearchHandler = new MasterSearchHandler();
	private ISocketConnection masterSearchConnection;

	private ParticipantSearchHandler participantSearchHandler = new ParticipantSearchHandler();
	private ArrayList<PlayRoom> searchResultRooms = new ArrayList<PlayRoom>();

	private String roomMasterName;
	private String roomServerAddressPort;

	private boolean tunnelIsLinked = false;
	private boolean isPacketCapturing = false;
	private boolean isRoomInfoUpdating = false;

	private ByteBuffer bufferForCapturing = ByteBuffer.allocate(CAPTURE_BUFFER_SIZE);
	private ArrayList<PcapIf> wlanAdaptorList = new ArrayList<PcapIf>();
	private HashMap<PcapIf, String> wlanAdaptorMacAddressMap = new HashMap<PcapIf, String>();
	private Pcap currentPcapDevice;

	private HashMap<String, Player> roomPlayerMap = new LinkedHashMap<String, Player>();
	private HashMap<String, TraficStatistics> traficStatsMap = new HashMap<String, TraficStatistics>();

	private int roomServerListCount = 0;
	private LinkedList<String> roomServerHistory = new LinkedList<String>();

	private Thread packetMonitorThread;
	private Thread packetCaptureThread;
	private Thread pingThread;
	private Thread natTableMaintainingThread;

	private Window window;

	public PlayClient(IniParser iniParser) {
		this.iniParser = iniParser;
		this.iniSettingSection = iniParser.getSection(IniConstants.SECTION_SETTINGS);

		roomEngine = new RoomEngine(new RoomServerHandler());

		this.window = new Window();
		initializeComponentListeners();

		goTo(OperationMode.Offline);

		refreshLanAdapterList();

		window.configUserNameText.setText(iniSettingSection.get(IniConstants.CLIENT_LOGIN_NAME, ""));

		String[] serverList = iniSettingSection.get(IniConstants.CLIENT_SERVER_LIST, "").split(",");
		for (String s : serverList) {
			if (Utility.isEmpty(s))
				continue;
			window.roomFormClientModeAddressCombo.add(s);
			roomServerListCount++;
		}

		window.roomFormClientModeAddressCombo.add("----------履歴----------");

		serverList = iniSettingSection.get(IniConstants.CLIENT_SERVER_HISTORY, "").split(",");
		for (String s : serverList) {
			if (Utility.isEmpty(s))
				continue;
			window.roomFormClientModeAddressCombo.add(s);
			roomServerHistory.add(s);
			if (roomServerHistory.size() == MAX_SERVER_HISTORY)
				break;
		}

		String software = String.format("%s プレイクライアント バージョン: %s", AppConstants.APP_NAME, AppConstants.VERSION);
		appendLogTo(window.roomChatLogText, software, window.colorAppInfo);
		appendLogTo(window.roomChatLogText, "プロトコル: " + ProtocolConstants.PROTOCOL_NUMBER, window.colorAppInfo);

		initializeBackgroundThreads();
	}

	private class Window {
		private TabFolder mainTabFolder;
		private TabItem searchTab;
		private Composite searchContainer;
		private Label searchServerAddressLabel;
		private Combo searchServerAddressCombo;
		private Button searchFormIncludeFullRoom;
		private Label searchFormTitleLabel;
		private Combo searchFormTitleCombo;
		private Label searchFormMasterNameLabel;
		private Combo searchFormMasterNameCombo;
		private Label searchFormServerNameLabel;
		private Combo searchFormServerNameCombo;
		private Button searchServerSubmitButton;
		private TableViewer searchResultRoomsTable;
		private TabItem playRoomTab;
		private SashForm roomMainSashForm;
		private Composite roomFormContainer;
		private Composite roomFormGridContainer;
		private Label roomFormServerAddressPortLabel;
		private Composite roomFormModeSwitchContainer;
		private StackLayout roomModeStackLayout;
		private Combo roomFormModeSelectionCombo;
		private Composite roomFormServerModeContainer;
		private Spinner roomFormServerModePortSpinner;
		private Button roomFormServerModePortButton;
		private Composite roomFormClientModeContainer;
		private Combo roomFormClientModeAddressCombo;
		private Button roomFormClientModeAdderssButton;
		private Composite roomFormProxyModeContainer;
		private Combo roomFormProxyModeAddressCombo;
		private Button roomFormProxyModeAddressButton;
		private Label roomFormMasterLabel;
		private Text roomFormMasterText;
		private Label roomFormTitleLabel;
		private Text roomFormTitleText;
		private Label roomFormPasswordLabel;
		private Text roomFormPasswordText;
		private Label roomFormMaxPlayersLabel;
		private Composite roomFormMaxPlayerContainer;
		private Spinner roomFormMaxPlayersSpiner;
		private Button roomFormEditButton;
		private Label roomFormDescriptionLabel;
		private Text roomFormDescriptionText;
		private Composite roomFormSearchServerContainer;
		private Button roomFormSearchServerButton;
		private Combo roomFormSearchServerCombo;
		private Composite roomChatContainer;
		private Composite wlanAdaptorContainer;
		private Label wlanAdapterListLabel;
		private Combo wlanAdapterListCombo;
		private Button wlanPspCommunicationButton;
		private Button roomChatSubmitButton;
		private Text roomChatText;
		private SashForm roomSubSashForm;
		private StyledText roomChatLogText;
		private SashForm roomInfoSashForm;
		private TableViewer roomPlayerListViewer;
		private TableColumn roomPlayerNameColumn;
		private TableColumn roomPlayerPingColumn;
		private Composite packetMonitorContainer;
		private TableViewer packetMonitorTableViewer;
		private TabItem configTab;
		private Composite configContainer;
		private Label configUserNameLabel;
		private Text configUserNameText;
		private Label configUserNameAlertLabel;
		private Group configRoomServerGroup;
		private Label configRoomServerHostNameLabel;
		private Text configRoomServerHostNameText;
		private Button configRoomServerAllowEmptyMasterNameCheck;
		private TabItem logTab;
		private Text logText;
		private Composite statusBarContainer;
		private Label statusServerAddressLabel;
		private Label statusServerStatusLabel;
		private Label statusTunnelConnectionLabel;
		private Label statusTraficStatusLabel;

		private Color colorWhite, colorBlack, colorRed, colorGreen;
		private Color colorLogInfo, colorLogError, colorRoomInfo, colorAppInfo, colorServerInfo;

		private Menu roomPlayerMenu;
		private MenuItem roomPlayerKickMenuItem;
		private MenuItem roomPlayerMasterTransferMenuItem;
		private Menu statusServerAddressMenu;
		private MenuItem statusServerAddressCopy;

		private Window() {
			initializeComponents();
		}

		private void initializeComponents() {
			display = new Display();
			shell = new Shell(display);

			try {
				ImageData imageData = new ImageData("icon/tb_blue.ico");
				Image image = new Image(display, imageData);
				shell.setImage(image);
			} catch (SWTException e) {
			}

			FormData formData;
			GridLayout gridLayout;
			GridData gridData;
			RowLayout rowLayout;

			colorWhite = new Color(display, 255, 255, 255);
			colorBlack = new Color(display, 0, 0, 0);
			colorRed = new Color(display, 200, 0, 0);
			colorGreen = new Color(display, 0, 128, 0);
			// colorReadOnlyBG = new Color(display, 230, 230, 230);

			colorLogInfo = new Color(display, 128, 128, 128);
			colorRoomInfo = new Color(display, 128, 0, 128);
			colorLogError = new Color(display, 255, 0, 0);
			colorAppInfo = new Color(display, 0, 100, 0);
			colorServerInfo = new Color(display, 0, 0, 255);

			gridLayout = new GridLayout(1, false);
			gridLayout.horizontalSpacing = 0;
			gridLayout.verticalSpacing = 5;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			shell.setLayout(gridLayout);

			mainTabFolder = new TabFolder(shell, SWT.TOP);
			mainTabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			searchTab = new TabItem(mainTabFolder, SWT.NONE);
			searchTab.setText("部屋検索");

			searchContainer = new Composite(mainTabFolder, SWT.NONE);
			searchTab.setControl(searchContainer);
			gridLayout = new GridLayout(7, false);
			gridLayout.horizontalSpacing = 3;
			gridLayout.verticalSpacing = 3;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginTop = 3;
			searchContainer.setLayout(gridLayout);

			searchServerAddressLabel = new Label(searchContainer, SWT.NONE);
			searchServerAddressLabel.setText("検索サーバー");
			searchServerAddressLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			searchServerAddressCombo = new Combo(searchContainer, SWT.BORDER);
			searchServerAddressCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 5, 1));
			searchServerAddressCombo.setText("localhost:40000");

			searchFormIncludeFullRoom = new Button(searchContainer, SWT.CHECK);
			searchFormIncludeFullRoom.setText("満室含む");
			searchFormIncludeFullRoom.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			searchFormTitleLabel = new Label(searchContainer, SWT.NONE);
			searchFormTitleLabel.setText("部屋名");
			searchFormTitleLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			searchFormTitleCombo = new Combo(searchContainer, SWT.BORDER);
			searchFormTitleCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			searchFormMasterNameLabel = new Label(searchContainer, SWT.NONE);
			searchFormMasterNameLabel.setText("部屋主名");
			searchFormMasterNameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			searchFormMasterNameCombo = new Combo(searchContainer, SWT.BORDER);
			searchFormMasterNameCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			searchFormServerNameLabel = new Label(searchContainer, SWT.NONE);
			searchFormServerNameLabel.setText("部屋サーバー名");
			searchFormServerNameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			searchFormServerNameCombo = new Combo(searchContainer, SWT.BORDER);
			searchFormServerNameCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			searchServerSubmitButton = new Button(searchContainer, SWT.PUSH);
			searchServerSubmitButton.setText("検索する");
			searchServerSubmitButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			searchResultRoomsTable = new TableViewer(searchContainer, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
			searchResultRoomsTable.getTable().setHeaderVisible(true);
			searchResultRoomsTable.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 7, 1));
			searchResultRoomsTable.setContentProvider(new ArrayContentProvider());
			searchResultRoomsTable.setLabelProvider(new PlayRoom.PlayRoomLabelProvider());

			TableColumn searchResultRoomsAddressColumn = new TableColumn(searchResultRoomsTable.getTable(), SWT.LEFT);
			searchResultRoomsAddressColumn.setText("部屋アドレス");
			searchResultRoomsAddressColumn.setWidth(150);

			TableColumn searchResultRoomsMasterNameColumn = new TableColumn(searchResultRoomsTable.getTable(), SWT.LEFT);
			searchResultRoomsMasterNameColumn.setText("部屋主");
			searchResultRoomsMasterNameColumn.setWidth(120);

			TableColumn searchResultRoomsTitleColumn = new TableColumn(searchResultRoomsTable.getTable(), SWT.LEFT);
			searchResultRoomsTitleColumn.setText("部屋名");
			searchResultRoomsTitleColumn.setWidth(200);

			TableColumn searchResultRoomsCapacityColumn = new TableColumn(searchResultRoomsTable.getTable(), SWT.CENTER);
			searchResultRoomsCapacityColumn.setText("定員");
			searchResultRoomsCapacityColumn.setWidth(65);

			TableColumn searchResultRoomsHasPasswordColumn = new TableColumn(searchResultRoomsTable.getTable(), SWT.CENTER);
			searchResultRoomsHasPasswordColumn.setText("鍵");
			searchResultRoomsHasPasswordColumn.setWidth(40);
			
			TableColumn searchResultRoomsDescriptionColumn = new TableColumn(searchResultRoomsTable.getTable(), SWT.LEFT);
			searchResultRoomsDescriptionColumn.setText("詳細・備考");
			searchResultRoomsDescriptionColumn.setWidth(250);

			playRoomTab = new TabItem(mainTabFolder, SWT.NONE);
			playRoomTab.setText("プレイルーム");

			roomMainSashForm = new SashForm(mainTabFolder, SWT.HORIZONTAL | SWT.SMOOTH);
			playRoomTab.setControl(roomMainSashForm);

			roomFormContainer = new Composite(roomMainSashForm, SWT.NONE);
			roomFormContainer.setLayout(new FormLayout());

			roomFormGridContainer = new Composite(roomFormContainer, SWT.NONE);
			roomFormGridContainer.setLayout(new GridLayout(2, false));
			formData = new FormData();
			formData.top = new FormAttachment(0, 1);
			formData.left = new FormAttachment(0, 0);
			formData.right = new FormAttachment(100, 0);
			roomFormGridContainer.setLayoutData(formData);

			roomFormModeSelectionCombo = new Combo(roomFormGridContainer, SWT.READ_ONLY);
			roomFormModeSelectionCombo.setItems(new String[] { "部屋サーバーを立ち上げる", "部屋に参加する", "代理サーバーで部屋を作成" });
			roomFormModeSelectionCombo.select(0);
			gridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
			roomFormModeSelectionCombo.setLayoutData(gridData);

			roomFormServerAddressPortLabel = new Label(roomFormGridContainer, SWT.NONE);
			roomFormServerAddressPortLabel.setText("ポート");
			roomFormServerAddressPortLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			roomFormModeSwitchContainer = new Composite(roomFormGridContainer, SWT.NONE);
			roomModeStackLayout = new StackLayout();
			roomFormModeSwitchContainer.setLayout(roomModeStackLayout);
			roomFormModeSwitchContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			roomFormServerModeContainer = new Composite(roomFormModeSwitchContainer, SWT.NONE);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 10;
			gridLayout.verticalSpacing = 0;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			roomFormServerModeContainer.setLayout(gridLayout);

			roomFormServerModePortSpinner = new Spinner(roomFormServerModeContainer, SWT.BORDER);
			roomFormServerModePortSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			roomFormServerModePortSpinner.setForeground(colorBlack);
			roomFormServerModePortSpinner.setBackground(colorWhite);
			roomFormServerModePortSpinner.setMinimum(1);
			roomFormServerModePortSpinner.setMaximum(65535);
			roomFormServerModePortSpinner.setSelection(30000);

			roomFormServerModePortButton = new Button(roomFormServerModeContainer, SWT.PUSH);
			roomFormServerModePortButton.setText("起動する");
			roomFormServerModePortButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			roomFormClientModeContainer = new Composite(roomFormModeSwitchContainer, SWT.NONE);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 5;
			gridLayout.verticalSpacing = 0;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			roomFormClientModeContainer.setLayout(gridLayout);

			roomFormClientModeAddressCombo = new Combo(roomFormClientModeContainer, SWT.NONE);
			roomFormClientModeAddressCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			roomFormClientModeAddressCombo.setText("localhost:30000");

			roomFormClientModeAdderssButton = new Button(roomFormClientModeContainer, SWT.PUSH);
			roomFormClientModeAdderssButton.setText("ログイン");
			roomFormClientModeAdderssButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			roomFormProxyModeContainer = new Composite(roomFormModeSwitchContainer, SWT.NONE);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 5;
			gridLayout.verticalSpacing = 0;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			roomFormProxyModeContainer.setLayout(gridLayout);

			roomFormProxyModeAddressCombo = new Combo(roomFormProxyModeContainer, SWT.NONE);
			roomFormProxyModeAddressCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			roomFormProxyModeAddressCombo.setText("localhost:30000");

			roomFormProxyModeAddressButton = new Button(roomFormProxyModeContainer, SWT.PUSH);
			roomFormProxyModeAddressButton.setText("作成する");
			roomFormProxyModeAddressButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			roomModeStackLayout.topControl = roomFormServerModeContainer;

			roomFormMasterLabel = new Label(roomFormGridContainer, SWT.NONE);
			roomFormMasterLabel.setText("部屋主");
			roomFormMasterLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			roomFormMasterText = new Text(roomFormGridContainer, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
			roomFormMasterText.setBackground(colorWhite);
			roomFormMasterText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			roomFormTitleLabel = new Label(roomFormGridContainer, SWT.NONE);
			roomFormTitleLabel.setText("部屋名");
			roomFormTitleLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			roomFormTitleText = new Text(roomFormGridContainer, SWT.SINGLE | SWT.BORDER);
			roomFormTitleText.setBackground(colorWhite);
			roomFormTitleText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			roomFormTitleText.setTextLimit(100);

			roomFormPasswordLabel = new Label(roomFormGridContainer, SWT.NONE);
			roomFormPasswordLabel.setText("パスワード");
			roomFormPasswordLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			roomFormPasswordText = new Text(roomFormGridContainer, SWT.SINGLE | SWT.BORDER);
			roomFormPasswordText.setBackground(colorWhite);
			roomFormPasswordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			roomFormPasswordText.setTextLimit(30);

			roomFormMaxPlayersLabel = new Label(roomFormGridContainer, SWT.NONE);
			roomFormMaxPlayersLabel.setText("制限人数");

			roomFormMaxPlayerContainer = new Composite(roomFormGridContainer, SWT.NONE);
			roomFormMaxPlayerContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 0;
			gridLayout.verticalSpacing = 0;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			roomFormMaxPlayerContainer.setLayout(gridLayout);

			roomFormMaxPlayersSpiner = new Spinner(roomFormMaxPlayerContainer, SWT.READ_ONLY | SWT.BORDER);
			roomFormMaxPlayersSpiner.setBackground(colorWhite);
			roomFormMaxPlayersSpiner.setForeground(colorBlack);
			roomFormMaxPlayersSpiner.setMinimum(2);
			roomFormMaxPlayersSpiner.setMaximum(ProtocolConstants.Room.MAX_ROOM_PLAYERS);
			roomFormMaxPlayersSpiner.setSelection(DEFAULT_MAX_PLAYERS);
			roomFormMaxPlayersSpiner.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

			roomFormEditButton = new Button(roomFormMaxPlayerContainer, SWT.PUSH);
			roomFormEditButton.setText("部屋情報を更新");
			roomFormEditButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));

			roomFormDescriptionLabel = new Label(roomFormContainer, SWT.NONE);
			roomFormDescriptionLabel.setText("部屋の紹介・備考");
			formData = new FormData();
			formData.top = new FormAttachment(roomFormGridContainer, 8);
			formData.left = new FormAttachment(0, 3);
			roomFormDescriptionLabel.setLayoutData(formData);

			roomFormDescriptionText = new Text(roomFormContainer, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER | SWT.WRAP);
			roomFormDescriptionText.setBackground(colorWhite);
			roomFormDescriptionText.setTextLimit(1000);

			roomFormSearchServerContainer = new Composite(roomFormContainer, SWT.NONE);

			formData = new FormData();
			formData.top = new FormAttachment(roomFormDescriptionLabel, 4);
			formData.left = new FormAttachment();
			formData.right = new FormAttachment(100, 0);
			formData.bottom = new FormAttachment(roomFormSearchServerContainer, -3);
			roomFormDescriptionText.setLayoutData(formData);

			formData = new FormData();
			formData.left = new FormAttachment();
			formData.right = new FormAttachment(100, 0);
			formData.bottom = new FormAttachment(100, -1);
			roomFormSearchServerContainer.setLayoutData(formData);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 4;
			gridLayout.verticalSpacing = 0;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			roomFormSearchServerContainer.setLayout(gridLayout);

			roomFormSearchServerButton = new Button(roomFormSearchServerContainer, SWT.TOGGLE);
			roomFormSearchServerButton.setText("検索登録する");
			roomFormSearchServerButton.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

			roomFormSearchServerCombo = new Combo(roomFormSearchServerContainer, SWT.BORDER);
			roomFormSearchServerCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			roomFormSearchServerCombo.setText("localhost:40000");

			roomChatContainer = new Composite(roomMainSashForm, SWT.NONE);
			roomChatContainer.setLayout(new FormLayout());

			wlanAdaptorContainer = new Composite(roomChatContainer, SWT.NONE);
			gridLayout = new GridLayout(3, false);
			gridLayout.verticalSpacing = 0;
			gridLayout.horizontalSpacing = 5;
			gridLayout.marginHeight = 1;
			gridLayout.marginWidth = 2;
			gridLayout.marginTop = 3;
			wlanAdaptorContainer.setLayout(gridLayout);
			formData = new FormData();
			formData.top = new FormAttachment(0, 0);
			formData.left = new FormAttachment(0, 0);
			formData.right = new FormAttachment(100, 0);
			wlanAdaptorContainer.setLayoutData(formData);

			wlanAdapterListLabel = new Label(wlanAdaptorContainer, SWT.NONE);
			wlanAdapterListLabel.setText("無線LANアダプタ");

			wlanAdapterListCombo = new Combo(wlanAdaptorContainer, SWT.READ_ONLY);
			wlanAdapterListCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			wlanPspCommunicationButton = new Button(wlanAdaptorContainer, SWT.TOGGLE);
			wlanPspCommunicationButton.setText("PSPと通信開始");

			roomChatText = new Text(roomChatContainer, SWT.BORDER | SWT.SINGLE);

			roomChatSubmitButton = new Button(roomChatContainer, SWT.PUSH);
			roomChatSubmitButton.setText("発言");
			formData = new FormData(50, SWT.DEFAULT);
			formData.bottom = new FormAttachment(100, -2);
			formData.right = new FormAttachment(100, -1);
			roomChatSubmitButton.setLayoutData(formData);

			formData = new FormData();
			formData.left = new FormAttachment(0, 0);
			formData.bottom = new FormAttachment(100, -2);
			formData.right = new FormAttachment(roomChatSubmitButton, -3);
			roomChatText.setLayoutData(formData);
			roomChatText.setFont(new Font(shell.getDisplay(), "Sans Serif", 12, SWT.NORMAL));
			roomChatText.setTextLimit(300);

			roomSubSashForm = new SashForm(roomChatContainer, SWT.SMOOTH | SWT.VERTICAL);
			formData = new FormData();
			formData.left = new FormAttachment(0, 0);
			formData.top = new FormAttachment(wlanAdaptorContainer, 3);
			formData.bottom = new FormAttachment(roomChatText, -3);
			formData.right = new FormAttachment(100, -1);
			roomSubSashForm.setLayoutData(formData);

			packetMonitorContainer = new Composite(roomSubSashForm, SWT.NONE);
			packetMonitorContainer.setLayout(new FillLayout());

			packetMonitorTableViewer = new TableViewer(packetMonitorContainer, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
			packetMonitorTableViewer.getTable().setHeaderVisible(true);

			TableColumn packetMonitorIsMineColumn = new TableColumn(packetMonitorTableViewer.getTable(), SWT.CENTER);
			packetMonitorIsMineColumn.setText("");
			packetMonitorIsMineColumn.setWidth(25);

			TableColumn packetMonitorMacAddressColumn = new TableColumn(packetMonitorTableViewer.getTable(), SWT.LEFT);
			packetMonitorMacAddressColumn.setText("MACアドレス");
			packetMonitorMacAddressColumn.setWidth(100);

			TableColumn packetMonitorInSpeedColumn = new TableColumn(packetMonitorTableViewer.getTable(), SWT.RIGHT);
			packetMonitorInSpeedColumn.setText("In (Kbps)");
			packetMonitorInSpeedColumn.setWidth(80);

			TableColumn packetMonitorOutSpeedColumn = new TableColumn(packetMonitorTableViewer.getTable(), SWT.RIGHT);
			packetMonitorOutSpeedColumn.setText("Out (Kbps)");
			packetMonitorOutSpeedColumn.setWidth(80);

			TableColumn packetMonitorTotalInBytesColumn = new TableColumn(packetMonitorTableViewer.getTable(), SWT.RIGHT);
			packetMonitorTotalInBytesColumn.setText("In 累積バイト");
			packetMonitorTotalInBytesColumn.setWidth(100);

			TableColumn packetMonitorTotalOutBytesColumn = new TableColumn(packetMonitorTableViewer.getTable(), SWT.RIGHT);
			packetMonitorTotalOutBytesColumn.setText("Out 累積バイト");
			packetMonitorTotalOutBytesColumn.setWidth(100);

			packetMonitorTableViewer.setContentProvider(new TraficStatistics.ContentProvider());
			packetMonitorTableViewer.setLabelProvider(new TraficStatistics.LabelProvider());

			roomInfoSashForm = new SashForm(roomSubSashForm, SWT.SMOOTH | SWT.HORIZONTAL);

			roomChatLogText = new StyledText(roomInfoSashForm, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER | SWT.READ_ONLY | SWT.WRAP);
			roomChatLogText.setMargins(3, 1, 3, 1);

			roomPlayerListViewer = new TableViewer(roomInfoSashForm, SWT.SINGLE | SWT.BORDER);
			roomPlayerListViewer.getTable().setHeaderVisible(true);

			roomPlayerNameColumn = new TableColumn(roomPlayerListViewer.getTable(), SWT.LEFT);
			roomPlayerNameColumn.setText("名前");
			roomPlayerNameColumn.setWidth(100);

			roomPlayerPingColumn = new TableColumn(roomPlayerListViewer.getTable(), SWT.RIGHT);
			roomPlayerPingColumn.setText("PING");
			roomPlayerPingColumn.setWidth(50);

			roomPlayerListViewer.setContentProvider(new Player.PlayerListContentProvider());
			roomPlayerListViewer.setLabelProvider(new Player.RoomPlayerLabelProvider());
			roomPlayerListViewer.setInput(roomPlayerMap);

			configTab = new TabItem(mainTabFolder, SWT.NONE);
			configTab.setText("設定");

			configContainer = new Composite(mainTabFolder, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.horizontalSpacing = 0;
			gridLayout.verticalSpacing = 5;
			gridLayout.marginWidth = 2;
			gridLayout.marginHeight = 5;
			configContainer.setLayout(gridLayout);
			configTab.setControl(configContainer);

			Composite configUserNameContainer = new Composite(configContainer, SWT.NONE);
			configUserNameContainer.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			rowLayout = new RowLayout();
			rowLayout.center = true;
			configUserNameContainer.setLayout(rowLayout);

			configUserNameLabel = new Label(configUserNameContainer, SWT.NONE);
			configUserNameLabel.setText("ユーザー名");

			configUserNameText = new Text(configUserNameContainer, SWT.SINGLE | SWT.BORDER);
			// configUserNameText.setLayoutData(new GridData(150, SWT.DEFAULT));
			configUserNameText.setLayoutData(new RowData(200, SWT.DEFAULT));
			configUserNameText.setTextLimit(100);

			configUserNameAlertLabel = new Label(configUserNameContainer, SWT.NONE);
			configUserNameAlertLabel.setText("ユーザー名を入力してください");
			configUserNameAlertLabel.setForeground(colorLogError);
			// configUserNameAlertLabel.setVisible(false);

			configRoomServerGroup = new Group(configContainer, SWT.SHADOW_IN);
			configRoomServerGroup.setText("部屋サーバー");
			gridData = new GridData(SWT.FILL, SWT.CENTER, false, false);
			configRoomServerGroup.setLayoutData(gridData);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 3;
			gridLayout.verticalSpacing = 5;
			gridLayout.marginWidth = 4;
			gridLayout.marginHeight = 5;
			configRoomServerGroup.setLayout(gridLayout);

			configRoomServerHostNameLabel = new Label(configRoomServerGroup, SWT.NONE);
			configRoomServerHostNameLabel.setText("検索サーバーへ登録する際の自ホスト名");

			configRoomServerHostNameText = new Text(configRoomServerGroup, SWT.BORDER);
			configRoomServerHostNameText.setLayoutData(new GridData(300, SWT.DEFAULT));

			configRoomServerAllowEmptyMasterNameCheck = new Button(configRoomServerGroup, SWT.CHECK | SWT.FLAT);
			configRoomServerAllowEmptyMasterNameCheck.setText("アドレスの部屋主名を省略でもログインできるようにする (ホスト名:ポート)");
			configRoomServerAllowEmptyMasterNameCheck.setSelection(roomEngine.isAllowEmptyMasterNameLogin());
			configRoomServerAllowEmptyMasterNameCheck.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));

			logTab = new TabItem(mainTabFolder, SWT.NONE);
			logTab.setText("ログ");

			logText = new Text(mainTabFolder, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL);
			logTab.setControl(logText);
			logText.setBackground(colorWhite);

			statusBarContainer = new Composite(shell, SWT.NONE);
			statusBarContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
			statusBarContainer.setLayout(new FormLayout());

			statusTunnelConnectionLabel = new Label(statusBarContainer, SWT.BORDER);
			formData = new FormData();
			formData.left = new FormAttachment(0, 0);
			statusTunnelConnectionLabel.setLayoutData(formData);

			statusServerAddressLabel = new Label(statusBarContainer, SWT.BORDER);
			statusServerAddressLabel.setText("サーバーアドレス");
			formData = new FormData();
			formData.left = new FormAttachment(statusTunnelConnectionLabel, 5);
			statusServerAddressLabel.setLayoutData(formData);

			statusServerStatusLabel = new Label(statusBarContainer, SWT.BORDER);
			statusServerStatusLabel.setText("サーバーステータス");
			statusServerStatusLabel.setVisible(false);
			formData = new FormData();
			formData.left = new FormAttachment(statusServerAddressLabel, 5);
			statusServerStatusLabel.setLayoutData(formData);

			statusTraficStatusLabel = new Label(statusBarContainer, SWT.BORDER);
			statusTraficStatusLabel.setText("トラフィック");
			statusTraficStatusLabel.setVisible(false);
			formData = new FormData();
			formData.right = new FormAttachment(100, -20);
			statusTraficStatusLabel.setLayoutData(formData);

			roomInfoSashForm.setWeights(new int[] { 5, 2 });
			roomSubSashForm.setWeights(new int[] { 1, 2 });
			roomMainSashForm.setWeights(new int[] { 3, 7 });
		}
	}

	private void initializeComponentListeners() {
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				roomEngine.closeRoom();

				if (roomConnection != null)
					roomConnection.disconnect();
				if (tunnelConnection != null)
					tunnelConnection.disconnect();

				tcpClient.dispose();
				udpClient.dispose();

				isPacketCapturing = false;

				iniSettingSection.set(IniConstants.CLIENT_LOGIN_NAME, window.configUserNameText.getText());

				Point size = shell.getSize();
				iniSettingSection.set(IniConstants.CLIENT_WINDOW_WIDTH, Integer.toString(size.x));
				iniSettingSection.set(IniConstants.CLIENT_WINDOW_HEIGHT, Integer.toString(size.y));

				if (roomServerListCount == 0) {
					iniSettingSection.set(IniConstants.CLIENT_SERVER_LIST, "");
				}

				StringBuilder sb = new StringBuilder();
				for (String s : roomServerHistory) {
					sb.append(s).append(',');
				}
				if (sb.length() > 0) {
					sb.deleteCharAt(sb.length() - 1);
				}
				iniSettingSection.set(IniConstants.CLIENT_SERVER_HISTORY, sb.toString());

				int index = window.wlanAdapterListCombo.getSelectionIndex() - 1;
				if (index == -1) {
					iniSettingSection.set(IniConstants.CLIENT_LAST_LAN_ADAPTER, "");
				} else {
					PcapIf device = wlanAdaptorList.get(index);
					iniSettingSection.set(IniConstants.CLIENT_LAST_LAN_ADAPTER, wlanAdaptorMacAddressMap.get(device));
				}

				try {
					iniParser.saveToIni();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});

		window.roomFormModeSelectionCombo.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				updateRoomModeSelection();
			}
		});

		window.roomFormServerModePortButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				try {
					if (roomEngine.isStarted()) {
						window.roomFormServerModePortButton.setEnabled(false);
						roomEngine.closeRoom();
					} else {
						startRoomServer();
					}
				} catch (IOException e) {
					appendLogTo(window.logText, Utility.makeStackTrace(e));
				}
			}
		});
		window.roomFormClientModeAddressCombo.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.character) {
				case SWT.CR:
				case SWT.LF:
					e.doit = false;
					connectToRoomServerAsParticipant();
					break;
				}
			}
		});
		window.roomFormClientModeAdderssButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (currentOperationMode == OperationMode.Offline) {
					connectToRoomServerAsParticipant();
				} else {
					roomConnection.send(ProtocolConstants.Room.COMMAND_LOGOUT);
					tunnelConnection.disconnect();
				}
			}
		});

		window.roomFormProxyModeAddressCombo.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.character) {
				case SWT.CR:
				case SWT.LF:
					e.doit = false;
					connectToProxyServerAsMaster();
					break;
				}
			}
		});
		window.roomFormProxyModeAddressButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (currentOperationMode == OperationMode.Offline) {
					connectToProxyServerAsMaster();
				} else {
					roomConnection.send(ProtocolConstants.Room.COMMAND_LOGOUT);
					tunnelConnection.disconnect();
				}
			}
		});

		window.roomChatText.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.character) {
				case SWT.CR:
				case SWT.LF:
					e.doit = false;
					sendChat();
				}
			}
		});
		window.roomChatSubmitButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				sendChat();
			}
		});

		window.wlanAdapterListCombo.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				window.wlanPspCommunicationButton.setEnabled(window.wlanAdapterListCombo.getSelectionIndex() != 0);
			}
		});
		window.wlanPspCommunicationButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (window.wlanPspCommunicationButton.getSelection()) {
					if (startPacketCapturing()) {
						window.wlanPspCommunicationButton.setText("PSPと通信中");
						window.wlanAdapterListCombo.setEnabled(false);
					} else {
						window.wlanPspCommunicationButton.setSelection(false);
					}
				} else {
					window.wlanPspCommunicationButton.setEnabled(false);
					isPacketCapturing = false;
				}
			}
		});

		window.roomFormEditButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (commitRoomEditForm()) {
					updateMasterSearchRoomInfo();
				}
			}
		});

		window.roomFormSearchServerButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (masterSearchConnection != null && masterSearchConnection.isConnected()) {
					masterSearchConnection.send(ProtocolConstants.Search.COMMAND_LOGOUT);
				} else {
					if (window.roomFormEditButton.getEnabled() && !commitRoomEditForm()) {
						window.roomFormSearchServerButton.setSelection(false);
						return;
					}
					connectToSearchServerAsMaster();
				}
			}
		});
		window.roomFormSearchServerCombo.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.character) {
				case SWT.CR:
				case SWT.LF:
					e.doit = false;
					connectToSearchServerAsMaster();
					break;
				}
			}
		});

		window.searchServerSubmitButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				connectToSearchServerAsParticipant();
			}
		});

		window.searchResultRoomsTable.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent e) {
				if (currentOperationMode != OperationMode.Offline)
					return;
				IStructuredSelection sel = (IStructuredSelection) e.getSelection();
				PlayRoom room = (PlayRoom) sel.getFirstElement();
				if (room == null)
					return;

				window.roomFormClientModeAddressCombo.setText(room.getAddress());
				connectToRoomServerAsParticipant();
			}
		});

		window.configUserNameText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (Utility.isEmpty(window.configUserNameText.getText())) {
					window.configUserNameAlertLabel.setVisible(true);
					shell.setText(AppConstants.APP_NAME);
				} else {
					window.configUserNameAlertLabel.setVisible(false);
					shell.setText(window.configUserNameText.getText() + " - " + AppConstants.APP_NAME);
				}
			}
		});

		window.configRoomServerAllowEmptyMasterNameCheck.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				roomEngine.setAllowEmptyMasterNameLogin(window.configRoomServerAllowEmptyMasterNameCheck.getSelection());
			}
		});

		window.configRoomServerHostNameText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				switch (currentOperationMode) {
				case RoomMaster:
					roomServerAddressPort = window.configRoomServerHostNameText.getText() + ":"
							+ window.roomFormServerModePortSpinner.getSelection();
					updateServerAddress();
				}
			}
		});

		VerifyListener notAcceptSpaceTabListener = new VerifyListener() {
			@Override
			public void verifyText(VerifyEvent e) {
				if (" ".equals(e.text)) {
					e.doit = false;
				} else {
					switch (e.character) {
					case '\t':
						e.doit = false;
						break;
					case '\0':
						e.text = e.text.replace(" ", "").replace("\t", "").trim();
						break;
					}
				}
			}
		};
		window.roomFormTitleText.addVerifyListener(notAcceptSpaceTabListener);
		window.roomFormPasswordText.addVerifyListener(notAcceptSpaceTabListener);
		window.roomFormDescriptionText.addVerifyListener(notAcceptSpaceTabListener);

		window.roomFormClientModeAddressCombo.addVerifyListener(notAcceptSpaceTabListener);
		window.roomFormProxyModeAddressCombo.addVerifyListener(notAcceptSpaceTabListener);
		window.roomFormSearchServerCombo.addVerifyListener(notAcceptSpaceTabListener);

		window.searchServerAddressCombo.addVerifyListener(notAcceptSpaceTabListener);
		window.searchFormTitleCombo.addVerifyListener(notAcceptSpaceTabListener);
		window.searchFormMasterNameCombo.addVerifyListener(notAcceptSpaceTabListener);
		window.searchFormServerNameCombo.addVerifyListener(notAcceptSpaceTabListener);

		window.configUserNameText.addVerifyListener(notAcceptSpaceTabListener);
		window.configRoomServerHostNameText.addVerifyListener(notAcceptSpaceTabListener);

		ModifyListener roomEditFormModifyDetectListener = new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (isRoomInfoUpdating)
					return;

				switch (currentOperationMode) {
				case RoomMaster:
				case ProxyRoomMaster:
					window.roomFormEditButton.setEnabled(true);
					break;
				}
			}
		};
		window.roomFormTitleText.addModifyListener(roomEditFormModifyDetectListener);
		window.roomFormPasswordText.addModifyListener(roomEditFormModifyDetectListener);
		window.roomFormDescriptionText.addModifyListener(roomEditFormModifyDetectListener);
		window.roomFormMaxPlayersSpiner.addModifyListener(roomEditFormModifyDetectListener);

		window.roomPlayerMenu = new Menu(shell, SWT.POP_UP);

		window.roomPlayerKickMenuItem = new MenuItem(window.roomPlayerMenu, SWT.PUSH);
		window.roomPlayerKickMenuItem.setText("キック");
		window.roomPlayerKickMenuItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListViewer.getSelection();
				Player player = (Player) selection.getFirstElement();
				if (player == null)
					return;

				String kickedName = player.getName();
				switch (currentOperationMode) {
				case RoomMaster:
					roomEngine.kickPlayer(kickedName);
					removePlayer(window.roomPlayerListViewer, kickedName);
					appendLogTo(window.roomChatLogText, kickedName + " を部屋から追い出しました", window.colorRoomInfo);
					break;
				case ProxyRoomMaster:
					roomConnection.send(ProtocolConstants.Room.COMMAND_ROOM_KICK_PLAYER + " " + kickedName);
					break;
				}
			}
		});

		new MenuItem(window.roomPlayerMenu, SWT.SEPARATOR);

		window.roomPlayerMasterTransferMenuItem = new MenuItem(window.roomPlayerMenu, SWT.PUSH);
		window.roomPlayerMasterTransferMenuItem.setText("部屋主を委譲");
		window.roomPlayerMasterTransferMenuItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListViewer.getSelection();
				Player player = (Player) selection.getFirstElement();
				if (player == null)
					return;

				String newMasterName = player.getName();
				switch (currentOperationMode) {
				case ProxyRoomMaster:
					roomConnection.send(ProtocolConstants.Room.COMMAND_ROOM_MASTER_TRANSFER + " " + newMasterName);
					if (masterSearchConnection != null && masterSearchConnection.isConnected())
						masterSearchConnection.send(ProtocolConstants.Search.COMMAND_LOGOUT);
					break;
				}
			}
		});

		window.roomPlayerListViewer.getTable().setMenu(window.roomPlayerMenu);
		window.roomPlayerListViewer.getTable().addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				boolean isMasterAndOtherSelected = false;
				switch (currentOperationMode) {
				case RoomMaster:
				case ProxyRoomMaster:
					IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListViewer.getSelection();
					Player player = (Player) selection.getFirstElement();
					if (player != null && !roomMasterName.equals(player.getName())) {
						isMasterAndOtherSelected = true;
					}
					break;
				}

				window.roomPlayerKickMenuItem.setEnabled(isMasterAndOtherSelected);
				if (currentOperationMode == OperationMode.ProxyRoomMaster) {
					window.roomPlayerMasterTransferMenuItem.setEnabled(isMasterAndOtherSelected);
				} else {
					window.roomPlayerMasterTransferMenuItem.setEnabled(false);
				}
			}
		});

		window.statusServerAddressMenu = new Menu(shell, SWT.POP_UP);

		window.statusServerAddressCopy = new MenuItem(window.statusServerAddressMenu, SWT.PUSH);
		window.statusServerAddressCopy.setText("アドレスをコピー");
		window.statusServerAddressCopy.addListener(SWT.Selection, new Listener() {
			private Clipboard clipboard = new Clipboard(display);
			private TextTransfer[] transfers = new TextTransfer[] { TextTransfer.getInstance() };

			@Override
			public void handleEvent(Event event) {
				String roomAddress = roomServerAddressPort + ":" + roomMasterName;
				clipboard.setContents(new Object[] { roomAddress }, transfers);
			}
		});

		window.statusServerAddressLabel.setMenu(window.statusServerAddressMenu);
		window.statusServerAddressLabel.addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				switch (currentOperationMode) {
				case RoomMaster:
				case RoomParticipant:
				case ProxyRoomMaster:
					window.statusServerAddressCopy.setEnabled(true);
					break;
				default:
					window.statusServerAddressCopy.setEnabled(false);
				}
			}
		});
	}

	private void initializeBackgroundThreads() {
		packetMonitorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				int intervalMillis = 1000;

				Runnable refreshAction = new Runnable() {
					@Override
					public void run() {
						try {
							window.packetMonitorTableViewer.setInput(traficStatsMap);
							window.packetMonitorTableViewer.refresh();
						} catch (SWTException e) {
						}
					}
				};
				Runnable clearAction = new Runnable() {
					@Override
					public void run() {
						try {
							synchronized (traficStatsMap) {
								traficStatsMap.clear();
							}
							window.packetMonitorTableViewer.setInput(traficStatsMap);
						} catch (SWTException e) {
						}
					}
				};

				while (!shell.isDisposed()) {
					try {
						synchronized (packetMonitorThread) {
							packetMonitorThread.wait();
						}

						while (isPacketCapturing || tunnelIsLinked) {
							long deadlineTime = System.currentTimeMillis() - 10000;

							synchronized (traficStatsMap) {
								Iterator<Entry<String, TraficStatistics>> iter = traficStatsMap.entrySet().iterator();
								while (iter.hasNext()) {
									Entry<String, TraficStatistics> entry = iter.next();
									TraficStatistics stats = entry.getValue();

									if (stats.lastModified < deadlineTime) {
										iter.remove();
										continue;
									}

									stats.currentInKbps = ((double) stats.currentInBytes) * 8 / intervalMillis;
									stats.currentOutKbps = ((double) stats.currentOutBytes) * 8 / intervalMillis;

									stats.currentInBytes = 0;
									stats.currentOutBytes = 0;
								}
							}

							syncExec(refreshAction);

							Thread.sleep(intervalMillis);
						}

						syncExec(clearAction);
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		}, "PacketMonitorThread");
		packetMonitorThread.setDaemon(true);
		// packetMonitorThread.start();

		packetCaptureThread = new Thread(new Runnable() {
			@Override
			public void run() {
				PcapPacket packet;
				while (!shell.isDisposed()) {
					try {
						synchronized (packetCaptureThread) {
							if (!isPacketCapturing)
								packetCaptureThread.wait();
						}

						packet = new PcapPacket(JMemory.POINTER);
						while (isPacketCapturing) {
							switch (currentPcapDevice.nextEx(packet)) {
							case 1:
								processCapturedPacket(packet);
								break;
							case 0:
								break;
							case -1:
							case -2:
								Runnable run = new Runnable() {
									@Override
									public void run() {
										try {
											isPacketCapturing = false;
											window.wlanPspCommunicationButton.setEnabled(false);
										} catch (SWTException e) {
										}
									}
								};
								syncExec(run);
								break;
							}
						}

						currentPcapDevice.close();
						currentPcapDevice = null;

						syncExec(new Runnable() {
							@Override
							public void run() {
								try {
									window.wlanAdapterListCombo.setEnabled(true);
									window.wlanPspCommunicationButton.setText("PSPと通信開始");
									window.wlanPspCommunicationButton.setEnabled(true);
								} catch (SWTException e) {
								}
							}
						});
					} catch (InterruptedException e) {
					}
				}
			}
		}, "PacketCaptureThread");
		packetCaptureThread.setDaemon(true);
		// packetCaptureThread.start();

		pingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!shell.isDisposed()) {
						synchronized (pingThread) {
							if (roomConnection == null || !roomConnection.isConnected())
								pingThread.wait();
						}

						while (roomConnection.isConnected()) {
							switch (currentOperationMode) {
							// case RoomMaster:
							case RoomParticipant:
							case ProxyRoomMaster:
								roomConnection.send(ProtocolConstants.Room.COMMAND_PING + " " + System.currentTimeMillis());
							}

							Thread.sleep(5000);
						}
					}
				} catch (InterruptedException e) {
				}
			}
		}, "PingThread");
		pingThread.setDaemon(true);
		// pingThread.start();

		natTableMaintainingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!shell.isDisposed()) {
						synchronized (natTableMaintainingThread) {
							if (tunnelConnection == null || !tunnelConnection.isConnected())
								natTableMaintainingThread.wait();
						}

						Thread.sleep(20000);

						while (tunnelConnection.isConnected()) {
							tunnelConnection.send(" ");
							Thread.sleep(20000);
						}
					}
				} catch (InterruptedException e) {
				}
			}
		}, "NatTableMaintaining");
		natTableMaintainingThread.setDaemon(true);
		// natTableMaintainingThread.start();
	}

	private void wakeupThread(Thread thread) {
		synchronized (thread) {
			if (thread.isAlive()) {
				thread.notify();
				return;
			}
		}
		thread.start();
	}

	private void asyncExec(Runnable action) {
		if (display.isDisposed())
			return;
		try {
			if (Thread.currentThread() == display.getThread()) {
				action.run();
			} else {
				display.asyncExec(action);
			}
		} catch (SWTException e) {
		}
	}

	private void syncExec(Runnable action) {
		if (display.isDisposed())
			return;
		try {
			if (Thread.currentThread() == display.getThread()) {
				action.run();
			} else {
				display.syncExec(action);
			}
		} catch (SWTException e) {
		}
	}

	private void updateRoomModeSelection() {
		switch (window.roomFormModeSelectionCombo.getSelectionIndex()) {
		case 0:
			window.roomModeStackLayout.topControl = window.roomFormServerModeContainer;
			window.roomFormServerAddressPortLabel.setText("ポート");
			setEnableRoomFormItems(true);
			break;
		case 1:
			window.roomModeStackLayout.topControl = window.roomFormClientModeContainer;
			window.roomFormServerAddressPortLabel.setText("アドレス");
			setEnableRoomFormItems(false);
			break;
		case 2:
			window.roomModeStackLayout.topControl = window.roomFormProxyModeContainer;
			window.roomFormServerAddressPortLabel.setText("アドレス");
			setEnableRoomFormItems(true);
			break;
		}
		window.roomFormGridContainer.layout(true, true);
		// roomFormModeSwitchContainer.layout();
	}

	private void appendRoomInfo(StringBuilder sb) {
		sb.append(' ');
		sb.append(window.roomFormMaxPlayersSpiner.getText());
		sb.append(' ');
		sb.append(window.roomFormTitleText.getText());
		sb.append(" \"");
		sb.append(window.roomFormPasswordText.getText());
		sb.append("\" \"");
		sb.append(window.roomFormDescriptionText.getText());
		sb.append('"');
	}

	private boolean checkConfigUserName() {
		String name = window.configUserNameText.getText();
		if (Utility.isEmpty(name)) {
			window.mainTabFolder.setSelection(window.configTab);
			window.configUserNameText.setFocus();
			window.configUserNameAlertLabel.setVisible(true);
			return false;
		} else {
			loginUserName = name;
			return true;
		}
	}

	private void startRoomServer() throws IOException {
		int port = window.roomFormServerModePortSpinner.getSelection();

		if (!checkConfigUserName())
			return;

		String title = window.roomFormTitleText.getText();
		if (Utility.isEmpty(title)) {
			appendLogTo(window.roomChatLogText, "部屋名を入力してください", window.colorRoomInfo);
			window.roomFormTitleText.setFocus();
			return;
		}

		roomEngine.setTitle(title);
		roomEngine.setMaxPlayers(window.roomFormMaxPlayersSpiner.getSelection());
		roomEngine.setPassword(window.roomFormPasswordText.getText());
		roomEngine.setDescription(window.roomFormDescriptionText.getText());

		try {
			roomEngine.openRoom(port, loginUserName);

			window.roomFormMasterText.setText(loginUserName);
			roomMasterName = loginUserName;
			roomServerAddressPort = window.configRoomServerHostNameText.getText() + ":" + port;

			window.roomFormServerModePortSpinner.setEnabled(false);
			window.roomFormServerModePortButton.setEnabled(false);
			window.roomFormModeSelectionCombo.setEnabled(false);
			addPlayer(window.roomPlayerListViewer, loginUserName);
		} catch (BindException e) {
			appendLogTo(window.roomChatLogText, "すでに同じポートが使用されています", window.colorLogError);
		}
	}

	private void connectToRoomServerAsParticipant() {
		if (!checkConfigUserName())
			return;

		String address = window.roomFormClientModeAddressCombo.getText();
		if (Utility.isEmpty(address)) {
			appendLogTo(window.roomChatLogText, "サーバーアドレスを入力してください", window.colorLogError);
			return;
		}

		String[] tokens = address.split(":", 3);

		roomMasterName = null;
		switch (tokens.length) {
		case 2:
			roomMasterName = "";
			break;
		case 3:
			roomMasterName = tokens[2];
			break;
		default:
			appendLogTo(window.roomChatLogText, "サーバーアドレスが正しくありません", window.colorLogError);
			return;
		}

		int port;
		try {
			port = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			appendLogTo(window.roomChatLogText, "サーバーアドレスが正しくありません", window.colorLogError);
			return;
		}

		roomServerAddressPort = tokens[0] + ":" + tokens[1];
		InetSocketAddress socketAddress = new InetSocketAddress(tokens[0], port);
		try {
			roomConnection = tcpClient.connect(socketAddress, roomClientHandler);
			goTo(OperationMode.ConnectingToRoomServer);
		} catch (UnresolvedAddressException e) {
			appendLogTo(window.roomChatLogText, "アドレスが解決しません", window.colorRed);
		} catch (IOException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
			return;
		}
	}

	private void connectToProxyServerAsMaster() {
		if (!checkConfigUserName())
			return;

		String title = window.roomFormTitleText.getText();
		if (Utility.isEmpty(title)) {
			appendLogTo(window.roomChatLogText, "部屋名を入力してください", window.colorRoomInfo);
			window.roomFormTitleText.setFocus();
			return;
		}

		String address = window.roomFormProxyModeAddressCombo.getText();
		if (Utility.isEmpty(address)) {
			appendLogTo(window.roomChatLogText, "サーバーアドレスを入力してください", window.colorLogError);
			return;
		}

		String[] tokens = address.split(":");

		switch (tokens.length) {
		case 2:
			break;
		default:
			appendLogTo(window.roomChatLogText, "サーバーアドレスが正しくありません", window.colorLogError);
			return;
		}

		int port;
		try {
			port = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			appendLogTo(window.roomChatLogText, "サーバーアドレスが正しくありません", window.colorLogError);
			return;
		}

		roomServerAddressPort = address;
		InetSocketAddress socketAddress = new InetSocketAddress(tokens[0], port);
		try {
			roomConnection = tcpClient.connect(socketAddress, roomClientHandler);
			roomMasterName = loginUserName;
			goTo(OperationMode.ConnectingToProxyServer);
		} catch (IOException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
			return;
		}
	}

	private void connectToSearchServerAsMaster() {
		switch (currentOperationMode) {
		case RoomMaster:
		case ProxyRoomMaster:
			String address = window.roomFormSearchServerCombo.getText();
			if (Utility.isEmpty(address)) {
				appendLogTo(window.roomChatLogText, "検索サーバーアドレスを入力してください", window.colorLogError);
				return;
			}

			String[] tokens = address.split(":");

			switch (tokens.length) {
			case 2:
				break;
			default:
				appendLogTo(window.roomChatLogText, "検索サーバーアドレスが正しくありません", window.colorLogError);
				return;
			}

			int port;
			try {
				port = Integer.parseInt(tokens[1]);
			} catch (NumberFormatException e) {
				appendLogTo(window.roomChatLogText, "検索サーバーアドレスが正しくありません", window.colorLogError);
				return;
			}

			try {
				window.roomFormSearchServerCombo.setEnabled(false);
				window.roomFormSearchServerButton.setEnabled(false);

				InetSocketAddress socketAddress = new InetSocketAddress(tokens[0], port);
				masterSearchConnection = tcpClient.connect(socketAddress, masterSearchHandler);
			} catch (IOException e) {
				window.roomFormSearchServerCombo.setEnabled(true);
				window.roomFormSearchServerButton.setEnabled(true);

				appendLogTo(window.logText, Utility.makeStackTrace(e));
			}
			break;
		default:
		}
	}

	private void connectToSearchServerAsParticipant() {
		String address = window.searchServerAddressCombo.getText();
		if (Utility.isEmpty(address)) {
			// appendLogTo(window.roomChatLogText, "検索サーバーアドレスを入力してください",
			// window.colorLogError);
			return;
		}

		String[] tokens = address.split(":");

		switch (tokens.length) {
		case 2:
			break;
		default:
			// appendLogTo(window.roomChatLogText, "検索サーバーアドレスが正しくありません",
			// window.colorLogError);
			return;
		}

		int port;
		try {
			port = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			// appendLogTo(window.roomChatLogText, "検索サーバーアドレスが正しくありません",
			// window.colorLogError);
			return;
		}

		try {
			window.searchServerSubmitButton.setEnabled(false);
			searchResultRooms.clear();
			window.searchResultRoomsTable.refresh();

			InetSocketAddress socketAddress = new InetSocketAddress(tokens[0], port);
			ISocketConnection connection = tcpClient.connect(socketAddress, participantSearchHandler);
		} catch (IOException e) {
			window.searchServerSubmitButton.setEnabled(true);
			appendLogTo(window.logText, Utility.makeStackTrace(e));
		}
	}

	private boolean commitRoomEditForm() {
		String title = window.roomFormTitleText.getText();
		if (Utility.isEmpty(title)) {
			appendLogTo(window.roomChatLogText, "部屋名を入力してください", window.colorLogError);
			window.roomFormTitleText.setFocus();
			return false;
		}
		window.roomFormEditButton.setEnabled(false);

		switch (currentOperationMode) {
		case RoomMaster:
			roomEngine.setTitle(title);
			roomEngine.setMaxPlayers(window.roomFormMaxPlayersSpiner.getSelection());
			roomEngine.setPassword(window.roomFormPasswordText.getText());
			roomEngine.setDescription(window.roomFormDescriptionText.getText());

			roomEngine.updateRoom();

			appendLogTo(window.roomChatLogText, "部屋情報を更新しました", window.colorRoomInfo);
			window.roomChatText.setFocus();

			break;
		case ProxyRoomMaster:
			StringBuilder sb = new StringBuilder();
			sb.append(ProtocolConstants.Room.COMMAND_ROOM_UPDATE);
			appendRoomInfo(sb);

			roomConnection.send(sb.toString());

			break;
		}

		return true;
	}

	private void sendChat() {
		String command = window.roomChatText.getText();
		if (!Utility.isEmpty(command)) {
			switch (currentOperationMode) {
			case RoomMaster:
				roomEngine.sendChat(command);
				window.roomChatText.setText("");
				break;
			case RoomParticipant:
			case ProxyRoomMaster:
				roomConnection.send(ProtocolConstants.Room.COMMAND_CHAT + " " + command);
				window.roomChatText.setText("");
				break;
			default:
				appendLogTo(window.roomChatLogText, "サーバーにログインしていません", window.colorLogInfo);
			}
		}
	}

	private void appendLogTo(final StyledText text, final String message, final Color color) {
		if (Utility.isEmpty(message))
			return;

		Runnable run = new Runnable() {
			@Override
			public void run() {
				try {
					if (color == null) {
						text.append(message);
						text.append("\n");

						text.setTopIndex(text.getLineCount());
					} else {
						StyleRange range = new StyleRange();
						range.start = text.getCharCount();
						range.length = message.length();
						range.foreground = color;

						text.append(message);
						text.append("\n");

						text.setStyleRange(range);
						text.setTopIndex(text.getLineCount());
					}
				} catch (SWTException e) {
				}
			}
		};
		asyncExec(run);
	}

	private void appendLogTo(final Text text, final String message) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				try {
					text.append(message);
					text.append("\n");
					text.setTopIndex(text.getLineCount());
				} catch (SWTException e) {
				}
			}
		};
		asyncExec(run);
	}

	private void updateServerAddress() {
		asyncExec(new Runnable() {
			@Override
			public void run() {
				try {
					switch (currentOperationMode) {
					case Offline:
						window.statusServerAddressLabel.setText("サーバーにログインしていません");
						break;
					default:
						String roomAddress = roomServerAddressPort + ":" + roomMasterName;

						String text = String.format("サーバーアドレス  %s", roomAddress);
						window.statusServerAddressLabel.setText(text);
					}
					window.statusBarContainer.layout();
				} catch (SWTException e) {
				}
			}
		});
	}

	private void updateTunnelStatus(boolean isConnected) {
		tunnelIsLinked = isConnected;

		asyncExec(new Runnable() {
			@Override
			public void run() {
				try {
					if (tunnelIsLinked) {
						window.statusTunnelConnectionLabel.setForeground(window.colorGreen);
						window.statusTunnelConnectionLabel.setText(" UDPトンネル接続中 ");
					} else {
						window.statusTunnelConnectionLabel.setForeground(window.colorRed);
						window.statusTunnelConnectionLabel.setText(" UDPトンネル未接続 ");
					}
					window.statusBarContainer.layout();
				} catch (SWTException e) {
				}
			}
		});
	}

	private void replacePlayerList(final TableViewer viewer, final String[] players) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				try {
					viewer.getTable().clearAll();
					@SuppressWarnings("unchecked")
					HashMap<String, Player> map = (HashMap<String, Player>) viewer.getInput();
					map.clear();
					for (String name : players) {
						// System.out.println(name);
						Player player = new Player(name);
						map.put(name, player);
						viewer.add(player);
					}
					viewer.refresh();
				} catch (SWTException e) {
				}
			}
		};
		asyncExec(run);
	}

	private void addPlayer(final TableViewer viewer, final String name) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				try {
					Player player = new Player(name);
					@SuppressWarnings("unchecked")
					HashMap<String, Player> map = (HashMap<String, Player>) viewer.getInput();

					map.put(name, player);
					viewer.add(player);
					viewer.refresh();

					updateMasterSearchPlayerCount();
				} catch (SWTException e) {
				}
			}
		};
		asyncExec(run);
	}

	private void removePlayer(final TableViewer viewer, final String name) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				try {
					@SuppressWarnings("unchecked")
					HashMap<String, Player> map = (HashMap<String, Player>) viewer.getInput();

					Player player = map.remove(name);
					if (player == null)
						return;

					viewer.remove(player);
					viewer.refresh();

					updateMasterSearchPlayerCount();
				} catch (SWTException e) {
				}
			}
		};
		asyncExec(run);
	}

	private void updatePlayerPing(final String name, final int ping) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				try {
					HashMap<String, Player> map = roomPlayerMap;
					Player player = map.get(name);
					if (player == null)
						return;

					player.setPing(ping);
					window.roomPlayerListViewer.refresh(player);
				} catch (SWTException e) {
				}
			}
		};
		asyncExec(run);
	}

	private void updateRoom(final String[] tokens, final boolean isInitialUpdate) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				try {
					String masterName = tokens[0];
					int maxPlayers = Integer.parseInt(tokens[1]);
					String title = tokens[2];
					String password = Utility.removeQuotations(tokens[3]);
					String description = Utility.removeQuotations(tokens[4]);

					isRoomInfoUpdating = true;

					window.roomFormMasterText.setText(masterName);
					window.roomFormMaxPlayersSpiner.setSelection(maxPlayers);
					window.roomFormTitleText.setText(title);
					window.roomFormPasswordText.setText(password);
					window.roomFormDescriptionText.setText(description);

					isRoomInfoUpdating = false;
					window.roomFormEditButton.setEnabled(false);

					if (isInitialUpdate)
						return;

					appendLogTo(window.roomChatLogText, "部屋情報が更新されました", window.colorRoomInfo);

					if (!masterName.equals(roomMasterName)) {
						roomMasterName = masterName;
						appendLogTo(window.roomChatLogText, "部屋主が " + roomMasterName + " に変更されました", window.colorRoomInfo);
						updateServerAddress();

						if (masterName.equals(loginUserName)) {
							window.roomFormProxyModeAddressCombo.setEnabled(false);
							window.roomFormProxyModeAddressCombo.setText(roomServerAddressPort);
							goTo(OperationMode.ProxyRoomMaster);
						} else if (currentOperationMode == OperationMode.ProxyRoomMaster) {
							window.roomFormClientModeAddressCombo.setEnabled(false);
							window.roomFormClientModeAddressCombo.setText(roomServerAddressPort + ":" + masterName);
							goTo(OperationMode.RoomParticipant);
						} else {
							window.roomFormClientModeAddressCombo.setText(roomServerAddressPort + ":" + masterName);
						}
					}
				} catch (NumberFormatException e) {
				} catch (SWTException e) {
				}
			}
		};
		asyncExec(run);
	}

	private void goTo(final OperationMode mode) {
		currentOperationMode = mode;

		asyncExec(new Runnable() {
			@Override
			public void run() {
				try {
					switch (mode) {
					case Offline:
						window.statusServerAddressLabel.setText("サーバーに接続していません");
						window.statusServerStatusLabel.setText("サーバーステータス");
						window.statusBarContainer.layout();

						roomPlayerMap.clear();
						window.roomPlayerListViewer.refresh();

						window.roomFormEditButton.setEnabled(false);

						window.roomFormModeSelectionCombo.setEnabled(true);

						window.roomFormServerModePortSpinner.setEnabled(true);
						window.roomFormServerModePortButton.setText("起動する");
						window.roomFormServerModePortButton.setEnabled(true);

						window.roomFormClientModeAddressCombo.setEnabled(true);
						window.roomFormClientModeAdderssButton.setText("ログイン");
						window.roomFormClientModeAdderssButton.setEnabled(true);
						window.roomFormClientModeContainer.layout();

						window.roomFormProxyModeAddressCombo.setEnabled(true);
						window.roomFormProxyModeAddressButton.setText("作成する");
						window.roomFormProxyModeAddressButton.setEnabled(true);
						window.roomFormProxyModeContainer.layout();

						switch (window.roomFormModeSelectionCombo.getSelectionIndex()) {
						case 0:
							setEnableRoomFormItems(true);
							updateTunnelStatus(false);
							break;
						case 1:
							setEnableRoomFormItems(false);
							window.roomFormTitleText.setText("");
							window.roomFormPasswordText.setText("");
							window.roomFormDescriptionText.setText("");
							window.roomFormMaxPlayersSpiner.setSelection(DEFAULT_MAX_PLAYERS);
							break;
						case 2:
							setEnableRoomFormItems(true);
							break;
						}

						window.roomFormMasterText.setText("");

						window.roomFormSearchServerButton.setEnabled(false);
						window.roomFormSearchServerCombo.setEnabled(false);

						window.configUserNameText.setEnabled(true);

						//window.mainTabFolder.setSelection(window.playRoomTab);

						break;
					case RoomMaster:
						window.mainTabFolder.setSelection(window.playRoomTab);

						window.roomFormServerModePortButton.setText("停止する");
						window.roomFormServerModePortButton.setEnabled(true);

						updateTunnelStatus(true);

						window.roomFormSearchServerButton.setEnabled(true);
						window.roomFormSearchServerCombo.setEnabled(true);

						window.configUserNameText.setEnabled(false);

						break;
					case RoomParticipant:
						window.mainTabFolder.setSelection(window.playRoomTab);

						window.roomFormClientModeAdderssButton.setText("ログアウト");
						window.roomFormClientModeAdderssButton.setEnabled(true);
						window.roomFormClientModeContainer.layout();

						window.roomFormModeSelectionCombo.select(1);
						updateRoomModeSelection();

						window.roomFormSearchServerButton.setEnabled(false);
						window.roomFormSearchServerCombo.setEnabled(false);

						break;
					case ProxyRoomMaster:
						window.roomFormProxyModeAddressButton.setText("ログアウト");
						window.roomFormProxyModeAddressButton.setEnabled(true);
						window.roomFormProxyModeContainer.layout();

						window.roomFormModeSelectionCombo.select(2);
						updateRoomModeSelection();

						window.roomFormSearchServerButton.setEnabled(true);
						window.roomFormSearchServerCombo.setEnabled(true);

						break;
					case ConnectingToRoomServer:
						window.roomFormModeSelectionCombo.setEnabled(false);

						window.roomFormClientModeAdderssButton.setEnabled(false);
						window.roomFormClientModeAddressCombo.setEnabled(false);

						window.configUserNameText.setEnabled(false);

						break;
					case ConnectingToProxyServer:
						window.roomFormModeSelectionCombo.setEnabled(false);

						window.roomFormProxyModeAddressButton.setEnabled(false);
						window.roomFormProxyModeAddressCombo.setEnabled(false);

						window.configUserNameText.setEnabled(false);

						break;
					}
				} catch (SWTException e) {
				}
			}
		});
	}

	private void setEnableRoomFormItems(boolean enabled) {
		window.roomFormTitleText.setEditable(enabled);
		window.roomFormPasswordText.setEditable(enabled);
		window.roomFormMaxPlayersSpiner.setEnabled(enabled);
		window.roomFormDescriptionText.setEditable(enabled);
	}

	private void updateMasterSearchRoomInfo() {
		if (masterSearchConnection != null && masterSearchConnection.isConnected()) {
			StringBuilder sb = new StringBuilder();

			sb.append(ProtocolConstants.Search.COMMAND_UPDATE);
			sb.append(' ').append(window.roomFormTitleText.getText());
			sb.append(' ').append(window.roomFormMaxPlayersSpiner.getSelection());
			sb.append(' ').append(window.roomFormPasswordText.getText().length() > 0 ? "Y" : "N");
			sb.append(" \"").append(window.roomFormDescriptionText.getText()).append('"');

			masterSearchConnection.send(sb.toString());
		}
	}

	private void updateMasterSearchPlayerCount() {
		if (masterSearchConnection != null && masterSearchConnection.isConnected()) {
			masterSearchConnection.send(ProtocolConstants.Search.COMMAND_UPDATE_PLAYER_COUNT + " " + roomPlayerMap.size());
		}
	}

	private void disconnectMasterSearch() {
		if (masterSearchConnection != null && masterSearchConnection.isConnected())
			masterSearchConnection.send(ProtocolConstants.Search.COMMAND_LOGOUT);
	}

	private class MasterSearchHandler implements IAsyncClientHandler {
		private HashMap<String, CommandHandler> handlers = new HashMap<String, CommandHandler>();
		private boolean isEntryCompleted = false;

		private MasterSearchHandler() {
			handlers.put(Search.COMMAND_ENTRY, new RoomRegisterHandler());
			handlers.put(Search.ERROR_MASTER_TCP_PORT, new ErrorTcpPortHandler());
			handlers.put(Search.ERROR_MASTER_UDP_PORT, new ErrorUdpPortHandler());
			handlers.put(Search.ERROR_MASTER_INVALID_AUTH_CODE, new ErrorInvalidAuthCodeHandler());
			handlers.put(Search.ERROR_MASTER_DATABASE_ENTRY, new ErrorDatabaseEntryHandler());
		}

		@Override
		public void connectCallback(ISocketConnection connection) {
			asyncExec(new Runnable() {
				@Override
				public void run() {
					StringBuilder sb = new StringBuilder();

					sb.append(ProtocolConstants.Search.PROTOCOL_NAME);
					sb.append(' ').append(ProtocolConstants.PROTOCOL_NUMBER);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

					sb.append(ProtocolConstants.Search.COMMAND_LOGIN);
					sb.append(' ').append(ProtocolConstants.Search.MODE_MASTER);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

					sb.append(ProtocolConstants.Search.COMMAND_ENTRY);
					sb.append(' ').append(roomMasterAuthCode);
					sb.append(' ').append(roomServerAddressPort);
					sb.append(' ').append(loginUserName);
					sb.append(' ').append(window.roomFormTitleText.getText());
					sb.append(' ').append(roomPlayerMap.size());
					sb.append(' ').append(window.roomFormMaxPlayersSpiner.getSelection());
					sb.append(' ').append(window.roomFormPasswordText.getText().length() > 0 ? "Y" : "N");
					sb.append(" \"").append(window.roomFormDescriptionText.getText()).append('"');

					masterSearchConnection.send(sb.toString());
				}
			});
		}

		@Override
		public void disconnectCallback(ISocketConnection connection) {
			asyncExec(new Runnable() {
				@Override
				public void run() {
					window.roomFormSearchServerButton.setText("検索登録する");
					window.roomFormSearchServerButton.setSelection(false);
					window.configRoomServerHostNameText.setEnabled(true);
					switch (currentOperationMode) {
					case RoomMaster:
					case ProxyRoomMaster:
						window.roomFormSearchServerButton.setEnabled(true);
						window.roomFormSearchServerCombo.setEnabled(true);
						break;
					default:
						window.roomFormSearchServerButton.setEnabled(false);
						window.roomFormSearchServerCombo.setEnabled(false);
					}

					if (isEntryCompleted) {
						isEntryCompleted = false;
						appendLogTo(window.roomChatLogText, "検索サーバーの登録を解除しました", window.colorRoomInfo);
					} else {
						appendLogTo(window.roomChatLogText, "検索サーバーに登録できませんでした", window.colorRoomInfo);
					}
				}
			});
		}

		@Override
		public void log(ISocketConnection connection, String message) {
		}

		@Override
		public void readCallback(ISocketConnection connection, PacketData data) {
			try {
				for (String message : data.getMessages()) {
					int commandEndIndex = message.indexOf(' ');
					String command, argument;
					if (commandEndIndex > 0) {
						command = message.substring(0, commandEndIndex);
						argument = message.substring(commandEndIndex + 1);
					} else {
						command = message;
						argument = "";
					}

					if (handlers.containsKey(command)) {
						CommandHandler handler = handlers.get(command);
						handler.process(argument);
					} else {
					}
				}
			} catch (Exception e) {
				appendLogTo(window.logText, Utility.makeStackTrace(e));
			}
		}

		private class RoomRegisterHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				asyncExec(new Runnable() {
					@Override
					public void run() {
						window.roomFormSearchServerButton.setText("登録解除");
						window.roomFormSearchServerButton.setEnabled(true);
						window.configRoomServerHostNameText.setEnabled(false);

						isEntryCompleted = true;
						appendLogTo(window.roomChatLogText, "検索サーバーに登録しました", window.colorRoomInfo);
					}
				});
			}
		}

		private class ErrorTcpPortHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "TCPポートが開放されていません", window.colorLogError);
			}
		}

		private class ErrorUdpPortHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "UDPポートが開放されていません", window.colorLogError);
			}
		}

		private class ErrorInvalidAuthCodeHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "自分の部屋以外の登録はできません", window.colorLogError);
			}
		}

		private class ErrorDatabaseEntryHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "検索サーバーのデータベースエラーが発生しました", window.colorLogError);
			}
		}
	}

	private class ParticipantSearchHandler implements IAsyncClientHandler {
		private HashMap<String, CommandHandler> handlers = new HashMap<String, CommandHandler>();

		private ParticipantSearchHandler() {
			handlers.put(ProtocolConstants.Search.COMMAND_SEARCH, new SearchHandler());
		}

		@Override
		public void log(ISocketConnection connection, String message) {
			appendLogTo(window.logText, message);
		}

		@Override
		public void connectCallback(final ISocketConnection connection) {
			asyncExec(new Runnable() {
				@Override
				public void run() {
					StringBuilder sb = new StringBuilder();

					sb.append(ProtocolConstants.Search.PROTOCOL_NAME);
					sb.append(' ').append(ProtocolConstants.PROTOCOL_NUMBER);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

					sb.append(ProtocolConstants.Search.COMMAND_LOGIN);
					sb.append(' ').append(ProtocolConstants.Search.MODE_PARTICIPANT);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

					sb.append(ProtocolConstants.Search.COMMAND_SEARCH);
					sb.append(' ').append(window.searchFormTitleCombo.getText());
					sb.append(' ').append(window.searchFormMasterNameCombo.getText());
					sb.append(' ').append(window.searchFormServerNameCombo.getText());
					sb.append(' ').append(window.searchFormIncludeFullRoom.getSelection() ? "Y" : "N");

					connection.send(sb.toString());
				}
			});
		}

		@Override
		public void disconnectCallback(ISocketConnection connection) {
			asyncExec(new Runnable() {
				@Override
				public void run() {
					window.searchResultRoomsTable.setInput(searchResultRooms);
					window.searchServerSubmitButton.setEnabled(true);
				}
			});
		}

		@Override
		public void readCallback(ISocketConnection connection, PacketData data) {
			try {
				for (String message : data.getMessages()) {
					int commandEndIndex = message.indexOf(' ');
					String command, argument;
					if (commandEndIndex > 0) {
						command = message.substring(0, commandEndIndex);
						argument = message.substring(commandEndIndex + 1);
					} else {
						command = message;
						argument = "";
					}

					if (handlers.containsKey(command)) {
						CommandHandler handler = handlers.get(command);
						handler.process(argument);
					} else {
					}
				}
			} catch (Exception e) {
				appendLogTo(window.logText, Utility.makeStackTrace(e));
			}
		}

		private class SearchHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				// S address master title currentPlayers maxPlayers hasPassword "description"
				if (Utility.isEmpty(argument)) {
					return;
				}

				String[] tokens = argument.split(" ");
				if (tokens.length != 7)
					return;

				String address = tokens[0];
				String masterName = tokens[1];
				String title = tokens[2];
				int currentPlayers = Integer.parseInt(tokens[3]);
				int maxPlayers = Integer.parseInt(tokens[4]);
				boolean hasPassword = "Y".equals(tokens[5]);
				String description = Utility.removeQuotations(tokens[6]).replace("\n", " ");

				PlayRoom room = new PlayRoom(address, masterName, title, hasPassword, currentPlayers, maxPlayers);
				room.setDescription(description);
				searchResultRooms.add(room);
			}
		}
	}

	private class RoomServerHandler implements IRoomMasterHandler {
		@Override
		public void log(String message) {
			appendLogTo(window.logText, message);
		}

		@Override
		public void chatReceived(String message) {
			appendLogTo(window.roomChatLogText, message, window.colorBlack);
		}

		@Override
		public void playerEntered(String player) {
			addPlayer(window.roomPlayerListViewer, player);
			appendLogTo(window.roomChatLogText, player + " が入室しました", window.colorLogInfo);
		}

		@Override
		public void playerExited(String player) {
			removePlayer(window.roomPlayerListViewer, player);
			appendLogTo(window.roomChatLogText, player + " が退室しました", window.colorLogInfo);
		}

		@Override
		public void pingInformed(String player, int ping) {
			updatePlayerPing(player, ping);
		}

		@Override
		public void tunnelPacketReceived(ByteBuffer packet) {
			processRemotePspPacket(packet);
		}

		@Override
		public void roomOpened(String authCode) {
			roomMasterAuthCode = authCode;
			goTo(OperationMode.RoomMaster);
			updateServerAddress();
			appendLogTo(window.roomChatLogText, "自部屋を起動しました", window.colorRoomInfo);
		}

		@Override
		public void roomClosed() {
			disconnectMasterSearch();
			goTo(OperationMode.Offline);
			appendLogTo(window.roomChatLogText, "自部屋を停止しました", window.colorRoomInfo);
		}
	}

	private class RoomClientHandler implements IAsyncClientHandler {
		private HashMap<String, CommandHandler> handlers = new HashMap<String, CommandHandler>();

		RoomClientHandler() {
			handlers.put(ProtocolConstants.ERROR_PROTOCOL_MISMATCH, new ErrorProtocolMismatchHandler());
			handlers.put(ProtocolConstants.Room.COMMAND_LOGIN, new LoginHandler());
			handlers.put(ProtocolConstants.Room.COMMAND_ROOM_CREATE, new RoomCreateHandler());
			handlers.put(ProtocolConstants.Room.COMMAND_CHAT, new ChatHandler());
			handlers.put(ProtocolConstants.Room.COMMAND_PINGBACK, new PingBackHandler());
			handlers.put(ProtocolConstants.Room.COMMAND_INFORM_PING, new InformPingHandler());
			handlers.put(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_UDP_PORT, new InformTunnelPortHandler());
			handlers.put(ProtocolConstants.Room.COMMAND_ROOM_UPDATE, new CommandRoomUpdateHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_SERVER_STATUS, new NotifyServerStatusHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_USER_ENTERED, new NotifyUserEnteredHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_USER_EXITED, new NotifyUserExitedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_USER_LIST, new NotifyUserListHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED, new NotifyRoomUpdatedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED, new NotifyRoomPlayerKickedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_MASTER_AUTH_CODE, new NotifyRoomMasterAuthCodeHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_PASSWORD_REQUIRED, new NotifyRoomPasswordRequiredHandler());
			handlers.put(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME, new ErrorLoginDuplicatedNameHandler());
			handlers.put(ProtocolConstants.Room.ERROR_LOGIN_BEYOND_CAPACITY, new ErrorLoginBeyondCapacityHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_ENTER_PASSWORD_FAIL, new ErrorRoomEnterPasswordFailHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_ENTER_BEYOND_CAPACITY, new ErrorRoomEnterBeyondCapacityHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_CREATE_BEYOND_LIMIT, new ErrorRoomCreateBeyondLimitHandler());
		}

		@Override
		public void log(ISocketConnection connection, String message) {
			appendLogTo(window.logText, message);
		}

		@Override
		public void connectCallback(ISocketConnection connection) {
			switch (currentOperationMode) {
			case ConnectingToRoomServer:
				asyncExec(new Runnable() {
					@Override
					public void run() {
						appendLogTo(window.roomChatLogText, "サーバーに接続しました", window.colorServerInfo);

						try {
							String serverAddress = window.roomFormClientModeAddressCombo.getText();
							int index = roomServerHistory.indexOf(serverAddress);
							int historyStartIndex = roomServerListCount + 1;

							if (index == -1) {
								roomServerHistory.add(0, serverAddress);
								window.roomFormClientModeAddressCombo.add(serverAddress, historyStartIndex);
								if (roomServerHistory.size() > MAX_SERVER_HISTORY) {
									roomServerHistory.removeLast();
									window.roomFormClientModeAddressCombo.remove(window.roomFormModeSelectionCombo.getItemCount() - 1);
								}
							} else {
								roomServerHistory.remove(index);
								roomServerHistory.add(0, serverAddress);

								window.roomFormClientModeAddressCombo.remove(historyStartIndex + index);
								window.roomFormClientModeAddressCombo.add(serverAddress, historyStartIndex);
								window.roomFormClientModeAddressCombo.select(historyStartIndex);
							}
						} catch (SWTException e) {
						}
					}
				});

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.PROTOCOL_NAME);
				sb.append(' ').append(ProtocolConstants.PROTOCOL_NUMBER);
				sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
				sb.append(ProtocolConstants.Room.COMMAND_LOGIN);
				sb.append(' ').append(loginUserName);
				sb.append(" \"").append(roomMasterName).append('"');

				roomConnection.send(sb.toString());

				break;
			case ConnectingToProxyServer:
				syncExec(new Runnable() {
					@Override
					public void run() {
						try {
							StringBuilder sb = new StringBuilder();
							sb.append(ProtocolConstants.Room.PROTOCOL_NAME);
							sb.append(' ').append(ProtocolConstants.PROTOCOL_NUMBER);
							sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
							sb.append(ProtocolConstants.Room.COMMAND_ROOM_CREATE);
							sb.append(' ').append(loginUserName);
							appendRoomInfo(sb);

							roomConnection.send(sb.toString());
						} catch (SWTException e) {
						}
					}
				});

				break;
			}
		}

		@Override
		public void readCallback(ISocketConnection connection, final PacketData data) {
			try {
				for (String message : data.getMessages()) {
					int commandEndIndex = message.indexOf(' ');
					String command, argument;
					if (commandEndIndex > 0) {
						command = message.substring(0, commandEndIndex);
						argument = message.substring(commandEndIndex + 1);
					} else {
						command = message;
						argument = "";
					}
					// appendToArenaChatLog(message, null);

					if (handlers.containsKey(command)) {
						CommandHandler handler = handlers.get(command);
						handler.process(argument);
					} else {
						// appendToLogText(message, null);
					}
				}
			} catch (Exception e) {
				appendLogTo(window.logText, Utility.makeStackTrace(e));
			}
		}

		@Override
		public void disconnectCallback(ISocketConnection connection) {
			switch (currentOperationMode) {
			case ConnectingToRoomServer:
			case ConnectingToProxyServer:
				appendLogTo(window.roomChatLogText, "サーバーに接続できません", window.colorLogError);
				break;
			default:
				disconnectMasterSearch();
				appendLogTo(window.roomChatLogText, "サーバーと切断しました", window.colorServerInfo);
			}
			goTo(OperationMode.Offline);
		}

		private class ErrorProtocolMismatchHandler implements CommandHandler {
			@Override
			public void process(String num) {
				String message = String.format("サーバーとのプロトコルナンバーが一致ないので接続できません サーバー:%s クライアント:%s", num, ProtocolConstants.PROTOCOL_NUMBER);
				appendLogTo(window.roomChatLogText, message, window.colorLogError);
			}
		}

		private void prepareSession() {
			wakeupThread(pingThread);
			// synchronized (pingThread) {
			// pingThread.notify();
			// }

			try {
				tunnelConnection = udpClient.connect(roomConnection.getRemoteAddress(), tunnelHandler);
			} catch (IOException ioe) {
			}
		}

		private class LoginHandler implements CommandHandler {
			@Override
			public void process(String args) {
				goTo(OperationMode.RoomParticipant);

				updateRoom(args.split(" "), true);
				updateServerAddress();

				appendLogTo(window.roomChatLogText, "部屋に入りました  ", window.colorRoomInfo);

				prepareSession();
			}
		}

		private class RoomCreateHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				asyncExec(new Runnable() {
					@Override
					public void run() {
						goTo(OperationMode.ProxyRoomMaster);

						window.roomFormMasterText.setText(loginUserName);
						addPlayer(window.roomPlayerListViewer, loginUserName);
						updateServerAddress();

						appendLogTo(window.roomChatLogText, "代理サーバーで部屋を作成しました", window.colorRoomInfo);

						prepareSession();
					}
				});
			}
		}

		private class ChatHandler implements CommandHandler {
			@Override
			public void process(String args) {
				switch (currentOperationMode) {
				case RoomMaster:
				case RoomParticipant:
				case ProxyRoomMaster:
					appendLogTo(window.roomChatLogText, args, window.colorBlack);
					break;
				}
			}
		}

		private class PingBackHandler implements CommandHandler {
			@Override
			public void process(String message) {
				long time = Long.parseLong(message);
				int ping = (int) (System.currentTimeMillis() - time);

				updatePlayerPing(loginUserName, ping);

				roomConnection.send(ProtocolConstants.Room.COMMAND_INFORM_PING + " " + ping);
			}
		}

		private class InformPingHandler implements CommandHandler {
			@Override
			public void process(String args) {
				try {
					switch (currentOperationMode) {
					case RoomMaster:
					case RoomParticipant:
					case ProxyRoomMaster:
						String[] values = args.split(" ");
						if (values.length == 2) {
							int ping = Integer.parseInt(values[1]);
							updatePlayerPing(values[0], ping);
						}
					}
				} catch (NumberFormatException e) {
				}
			}
		}

		private class NotifyServerStatusHandler implements CommandHandler {
			@Override
			public void process(final String args) {
				Runnable run = new Runnable() {
					@Override
					public void run() {
						try {
							String[] v = args.split(" ");
							String text = String.format("参加者数: %s / %s     部屋数: %s / %s", v[0], v[1], v[2], v[3]);
							window.statusServerStatusLabel.setText(text);
							window.statusBarContainer.layout();
						} catch (SWTException e) {
						}
					}
				};
				asyncExec(run);
			}
		}

		private class NotifyUserEnteredHandler implements CommandHandler {
			@Override
			public void process(String name) {
				switch (currentOperationMode) {
				case ProxyRoomMaster:
				case RoomParticipant:
					addPlayer(window.roomPlayerListViewer, name);
					appendLogTo(window.roomChatLogText, name + " が入室しました", window.colorLogInfo);
					break;
				}
			}
		}

		private class NotifyUserExitedHandler implements CommandHandler {
			@Override
			public void process(String name) {
				switch (currentOperationMode) {
				case ProxyRoomMaster:
				case RoomParticipant:
					removePlayer(window.roomPlayerListViewer, name);
					appendLogTo(window.roomChatLogText, name + " が退室しました", window.colorLogInfo);
					break;
				}
			}
		}

		private class NotifyUserListHandler implements CommandHandler {
			@Override
			public void process(String args) {
				String[] players = args.split(" ");
				switch (currentOperationMode) {
				case RoomMaster:
				case RoomParticipant:
				case ProxyRoomMaster:
					replacePlayerList(window.roomPlayerListViewer, players);
					break;
				}
			}
		}

		private class NotifyRoomUpdatedHandler implements CommandHandler {
			@Override
			public void process(String args) {
				updateRoom(args.split(" "), false);
			}
		}

		private class CommandRoomUpdateHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "部屋情報を修正しました", window.colorRoomInfo);

				asyncExec(new Runnable() {
					@Override
					public void run() {
					}
				});
			}
		}

		private class NotifyRoomPlayerKickedHandler implements CommandHandler {
			@Override
			public void process(String kickedPlayer) {
				if (loginUserName.equals(kickedPlayer)) {
					goTo(OperationMode.Offline);
					appendLogTo(window.roomChatLogText, "部屋から追い出されました", window.colorRoomInfo);
				} else {
					removePlayer(window.roomPlayerListViewer, kickedPlayer);
					if (currentOperationMode == OperationMode.RoomMaster) {
						appendLogTo(window.roomChatLogText, kickedPlayer + " を部屋から追い出しました", window.colorRoomInfo);
					} else {
						appendLogTo(window.roomChatLogText, kickedPlayer + " は部屋から追い出されました", window.colorRoomInfo);
					}
				}
			}
		}

		private class NotifyRoomMasterAuthCodeHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				roomMasterAuthCode = argument;
			}
		}

		private class InformTunnelPortHandler implements CommandHandler {
			@Override
			public void process(String message) {
				updateTunnelStatus(true);
				appendLogTo(window.logText, "トンネル通信の接続が開始しました");

				wakeupThread(packetMonitorThread);
				// synchronized (packetMonitorThread) {
				// packetMonitorThread.notify();
				// }
			}
		}

		private class ErrorLoginDuplicatedNameHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "同名のユーザーが既にログインしているのでログインできません", window.colorLogError);
			}
		}

		private class ErrorLoginBeyondCapacityHandler implements CommandHandler {
			@Override
			public void process(String message) {
				switch (currentOperationMode) {
				case ConnectingToProxyServer:
					appendLogTo(window.roomChatLogText, "サーバーの最大部屋数を超えたので部屋を作成できません", window.colorLogError);
					break;
				default:
					appendLogTo(window.roomChatLogText, "サーバーの最大人数を超えたのでログインできません", window.colorLogError);
				}
			}
		}

		private void promptPassword() {
			Runnable run = new Runnable() {
				@Override
				public void run() {
					try {
						PasswordDialog dialog = new PasswordDialog(shell);
						switch (dialog.open()) {
						case IDialogConstants.OK_ID:
							String password = dialog.getPassword();
							// appendLogTo(arenaChatLogText, password,
							// colorRoomInfo);
							String message = ProtocolConstants.Room.COMMAND_LOGIN + " " + loginUserName + " \"" + roomMasterName + "\" "
									+ password;

							roomConnection.send(message);
							break;
						case IDialogConstants.CANCEL_ID:
							appendLogTo(window.roomChatLogText, "入室をキャンセルしました", window.colorRoomInfo);
							roomConnection.send(ProtocolConstants.Room.COMMAND_LOGOUT);
							break;
						}
					} catch (SWTException e) {
					}
				}
			};
			asyncExec(run);
		}

		private class NotifyRoomPasswordRequiredHandler implements CommandHandler {
			@Override
			public void process(final String masterName) {
				promptPassword();
			}
		}

		private class ErrorRoomEnterPasswordFailHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "部屋パスワードが違います", window.colorRoomInfo);
				promptPassword();
			}
		}

		private class ErrorRoomEnterBeyondCapacityHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "部屋が満室なので入れません", window.colorRoomInfo);
			}
		}

		private class ErrorRoomCreateBeyondLimitHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "部屋数が上限に達しましたので部屋を作成できません", window.colorRoomInfo);
			}
		}
	}

	private class TunnelHandler implements IAsyncClientHandler {
		@Override
		public void connectCallback(ISocketConnection connection) {
			connection.send(" ");
			wakeupThread(natTableMaintainingThread);
			// synchronized (natTableMaintainingThread) {
			// natTableMaintainingThread.notify();
			// }
		}

		@Override
		public void readCallback(ISocketConnection connection, PacketData data) {
			ByteBuffer packet = data.getBuffer();
			// System.out.println(packet.toString());
			if (Utility.isPspPacket(packet)) {
				processRemotePspPacket(packet);
			} else {
				try {
					String tunnelPort = data.getMessage();
					int port = Integer.parseInt(tunnelPort);
					// System.out.println("Port: " + port);
					roomConnection.send(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_UDP_PORT + " " + port);
				} catch (NumberFormatException e) {
				}
			}
		}

		@Override
		public void disconnectCallback(ISocketConnection connection) {
			updateTunnelStatus(false);
			appendLogTo(window.logText, "トンネル通信の接続が終了しました");
		}

		@Override
		public void log(ISocketConnection connection, String message) {
		}
	}

	private void refreshLanAdapterList() {
		window.wlanAdapterListCombo.removeAll();
		window.wlanAdapterListCombo.add("選択されていません");
		window.wlanAdapterListCombo.select(0);
		window.wlanPspCommunicationButton.setEnabled(false);

		StringBuilder errBuf = new StringBuilder();
		wlanAdaptorList.clear();
		try {
			int r = Pcap.findAllDevs(wlanAdaptorList, errBuf);
			if (r == Pcap.NOT_OK || wlanAdaptorList.isEmpty()) {
				appendLogTo(window.logText, errBuf.toString());
				return;
			}
		} catch (UnsatisfiedLinkError e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
			return;
		}

		String lastUsedMacAddress = iniSettingSection.get(IniConstants.CLIENT_LAST_LAN_ADAPTER, "");
		int lastUsedIndex = 0;

		IniParser.Section nicSection = iniParser.getSection(IniConstants.SECTION_LAN_ADAPTERS);

		int i = 1;
		for (Iterator<PcapIf> iter = wlanAdaptorList.iterator(); iter.hasNext(); i++) {
			PcapIf device = iter.next();
			try {
				String macAddress = Utility.makeMacAddressString(device.getHardwareAddress(), 0, true);
				if (lastUsedMacAddress.equals(macAddress)) {
					lastUsedIndex = i;
				}

				String description = nicSection.get(macAddress, null);

				if (description == null) {
					description = device.getDescription();
					if (description == null) {
						description = device.getName();
					}
					description = description.replace("(Microsoft's Packet Scheduler)", "");
					description = description.replaceAll(" {2,}", " ").trim();

					nicSection.set(macAddress, description);
				} else if (description.equals("")) {
					iter.remove();
					continue;
				}

				description += " [" + macAddress + "]";
				window.wlanAdapterListCombo.add(description);

				wlanAdaptorMacAddressMap.put(device, macAddress);

			} catch (IOException e) {
				appendLogTo(window.logText, Utility.makeStackTrace(e));
			}
		}

		window.wlanAdapterListCombo.select(lastUsedIndex);
		window.wlanPspCommunicationButton.setEnabled(lastUsedIndex != 0);
	}

	private void processRemotePspPacket(ByteBuffer packet) {
		String destMac = Utility.makeMacAddressString(packet, 0, false);
		String srcMac = Utility.makeMacAddressString(packet, 6, false);

		// System.out.println("src: " + srcMac + " dest: " + destMac);

		TraficStatistics destStats, srcStats;
		synchronized (traficStatsMap) {
			destStats = traficStatsMap.get(destMac);
			srcStats = traficStatsMap.get(srcMac);

			if (srcStats == null) {
				srcStats = new TraficStatistics(false);
				traficStatsMap.put(srcMac, srcStats);
			}

			if (destStats == null) {
				destStats = new TraficStatistics(!Utility.isMacBroadCastAddress(destMac));
				traficStatsMap.put(destMac, destStats);
			}
		}

		srcStats.lastModified = System.currentTimeMillis();
		srcStats.currentInBytes += packet.limit();
		srcStats.totalInBytes += packet.limit();

		destStats.lastModified = System.currentTimeMillis();
		destStats.currentInBytes += packet.limit();
		destStats.totalInBytes += packet.limit();

		if (isPacketCapturing && currentPcapDevice != null) {
			// send packet
			currentPcapDevice.sendPacket(packet);
		}
	}

	private void processCapturedPacket(PcapPacket packet) {
		// if (packet != null) {
		// System.out.println(packet.toHexdump());
		// return;
		// }

		Ethernet ethernet = new Ethernet();
		if (packet.hasHeader(ethernet) && Utility.isPspPacket(ethernet)) {
			String srcMac = Utility.makeMacAddressString(ethernet.source(), 0, false);
			String destMac = Utility.makeMacAddressString(ethernet.destination(), 0, false);

			TraficStatistics srcStats, destStats;
			synchronized (traficStatsMap) {
				srcStats = traficStatsMap.get(srcMac);
				destStats = traficStatsMap.get(destMac);

				if (srcStats == null) {
					srcStats = new TraficStatistics(true);
					traficStatsMap.put(srcMac, srcStats);
				} else if (!srcStats.isMine) {
					// サーバーから送られてきた他PSPからのパケットの再キャプチャなのでスルー
					return;
				}

				if (destStats == null) {
					destStats = new TraficStatistics(false);
					traficStatsMap.put(destMac, destStats);
				} else if (destStats.isMine) {
					// 手元のPSP同士の通信なのでスルー
					return;
				}
			}

			int packetLength = packet.size();

			srcStats.lastModified = System.currentTimeMillis();
			srcStats.currentOutBytes += packetLength;
			srcStats.totalOutBytes += packetLength;

			if (tunnelIsLinked) {
				destStats.lastModified = System.currentTimeMillis();
				destStats.currentOutBytes += packetLength;
				destStats.totalOutBytes += packetLength;

				// System.out.printf("%s => %s  [%d]", srcMac, destMac,
				// packetLength);
				// System.out.println(packet.toHexdump());

				bufferForCapturing.clear();
				packet.transferTo(bufferForCapturing);
				bufferForCapturing.flip();

				switch (currentOperationMode) {
				case RoomMaster:
					roomEngine.sendTunnelPacketToParticipants(bufferForCapturing, srcMac, destMac);
					break;
				case RoomParticipant:
				case ProxyRoomMaster:
					tunnelConnection.send(bufferForCapturing);
					break;
				}
			}
		}
	}

	private boolean startPacketCapturing() {
		int index = window.wlanAdapterListCombo.getSelectionIndex() - 1;
		PcapIf device = wlanAdaptorList.get(index);

		StringBuilder errbuf = new StringBuilder();
		currentPcapDevice = Pcap.openLive(device.getName(), CAPTURE_BUFFER_SIZE, Pcap.MODE_PROMISCUOUS, 1, errbuf);
		if (currentPcapDevice == null) {
			appendLogTo(window.logText, errbuf.toString());
			return false;
		}

		isPacketCapturing = true;
		wakeupThread(packetCaptureThread);
		wakeupThread(packetMonitorThread);
		// synchronized (packetCaptureThread) {
		// packetCaptureThread.notify();
		// }
		// synchronized (packetMonitorThread) {
		// packetMonitorThread.notify();
		// }

		return true;
	}

	public void start() {
		int minWidth = 650, minHeight = 400;

		shell.setMinimumSize(new Point(minWidth, minHeight));

		shell.setSize(iniSettingSection.get(IniConstants.CLIENT_WINDOW_WIDTH, minWidth),
				iniSettingSection.get(IniConstants.CLIENT_WINDOW_HEIGHT, minHeight));
		shell.open();

		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		try {
			display.dispose();
		} catch (RuntimeException e) {
		}
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		String iniFileName = "PlayClient.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		IniParser parser = new IniParser(iniFileName);

		try {
			new PlayClient(parser).start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
