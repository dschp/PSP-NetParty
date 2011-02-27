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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
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
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

import pspnetparty.lib.AsyncTcpClient;
import pspnetparty.lib.AsyncUdpClient;
import pspnetparty.lib.CommandHandler;
import pspnetparty.lib.IAsyncClientHandler;
import pspnetparty.lib.IMyRoomMasterHandler;
import pspnetparty.lib.ISocketConnection;
import pspnetparty.lib.IniParser;
import pspnetparty.lib.MyRoomEngine;
import pspnetparty.lib.PacketData;
import pspnetparty.lib.PlayRoom;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniConstants;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.wlan.Wlan;
import pspnetparty.wlan.WlanDevice;
import pspnetparty.wlan.WlanNetwork;

public class PlayClient {

	private static final int MAX_SERVER_HISTORY = 10;
	private static final int DEFAULT_MAX_PLAYERS = 4;

	enum RoomState {
		Offline, MyRoomMaster, ConnectingAsRoomParticipant, RoomParticipant, ConnectingAsRoomMaster, RoomMaster, Negotiating
	};

	enum PortalState {
		Offline, Connecting, Login
	}

	private IniParser iniParser;
	private IniParser.Section iniSettingSection;

	private Display display;
	private Shell shell;

	private RoomState currentRoomState;
	private PortalState currentPortalState = PortalState.Offline;

	private String loginUserName;
	private String roomMasterAuthCode;
	private String roomMasterName;
	private String roomServerAddressPort;

	private MyRoomEngine myRoomEngine;

	private AsyncTcpClient tcpClient = new AsyncTcpClient(1000000, 0);
	private AsyncUdpClient udpClient = new AsyncUdpClient();

	private RoomClientHandler roomClientHandler = new RoomClientHandler();
	private ISocketConnection roomConnection;

	private TunnelHandler tunnelHandler = new TunnelHandler();
	private ISocketConnection tunnelConnection;

	private MyRoomEntryHandler myRoomEntryHandler = new MyRoomEntryHandler();
	private ISocketConnection myRoomEntryConnection;

	private PortalHandler portalHandler = new PortalHandler();
	private ISocketConnection portalConnection;
	private ArrayList<PlayRoom> roomSearchResultList = new ArrayList<PlayRoom>();
	private HashMap<String, RoomServerData> roomServers = new HashMap<String, RoomServerData>();

	private boolean tunnelIsLinked = false;
	private boolean isPacketCapturing = false;
	private boolean isSSIDScaning = false;
	private boolean isRoomInfoUpdating = false;
	private boolean isExitOnLobbyCkecked = false;
	private boolean isPortalTabSelected = true;
	private boolean isPortalAutoQueryEnabled = true;

	private int scanIntervalMillis = 2000;
	private long nextSsidCheckTime = 0L;

	private ByteBuffer bufferForCapturing = ByteBuffer.allocateDirect(Wlan.CAPTURE_BUFFER_SIZE);
	private ArrayList<WlanDevice> wlanAdaptorList = new ArrayList<WlanDevice>();
	private HashMap<WlanDevice, String> wlanAdaptorMacAddressMap = new HashMap<WlanDevice, String>();
	private WlanDevice currentWlanDevice = Wlan.EMPTY_DEVICE;

	private HashMap<String, Player> roomPlayerMap = new LinkedHashMap<String, Player>();
	private HashMap<String, TraficStatistics> traficStatsMap = new HashMap<String, TraficStatistics>();

	public int actualSentBytes;
	public int actualRecievedBytes;

	private Thread packetMonitorThread;
	private Thread packetCaptureThread;
	private Thread pingThread;
	private Thread natTableMaintainingThread;
	private Thread portalSearchQueryThread;

	private Window window;
	private ComboHistoryManager roomServerHistoryManager;
	private ComboHistoryManager roomAddressHistoryManager;
	private ComboHistoryManager portalServerHistoryManager;
	private ComboHistoryManager myRoomServerEntryHistoryManager;

	private ComboHistoryManager queryRoomTitleHistoryManager;
	private ComboHistoryManager queryRoomTitleNgHistoryManager;
	private ComboHistoryManager queryRoomMasterNameHistoryManager;
	private ComboHistoryManager queryRoomMasterNameNgHistoryManager;
	private ComboHistoryManager queryRoomAddressHistoryManager;
	private ComboHistoryManager queryRoomAddressNgHistoryManager;
	private Thread wlanScannerThread;

	public PlayClient(IniParser iniParser) {
		this.iniParser = iniParser;
		this.iniSettingSection = iniParser.getSection(IniConstants.SECTION_SETTINGS);

		myRoomEngine = new MyRoomEngine(new RoomServerHandler());

		display = new Display();
		shell = new Shell(display);

		window = new Window();
		window.initializeComponents(display, shell);

		window.roomPlayerListTableViewer.setInput(roomPlayerMap);
		window.portalRoomServerTableViewer.setInput(roomServers);
		window.ssidScanIntervalSpinner.setSelection(scanIntervalMillis);

		initializeComponentListeners();

		goTo(RoomState.Offline);

		refreshLanAdapterList();

		window.configUserNameText.setText(iniSettingSection.get(IniConstants.Client.LOGIN_NAME, ""));

		window.roomFormMyRoomModePortSpinner.setSelection(iniSettingSection.get(IniConstants.Client.MY_ROOM_PORT, 30000));
		window.roomFormMyRoomModeHostText.setText(iniSettingSection.get(IniConstants.Client.MY_ROOM_HOST_NAME, ""));
		window.configMyRoomAllowEmptyMasterNameCheck.setSelection(iniSettingSection.get(IniConstants.Client.MY_ROOM_ALLOW_NO_MASTER_NAME,
				true));

		window.configAppCloseConfirmCheck.setSelection(iniSettingSection.get(IniConstants.Client.APP_CLOSE_CONFIRM, true));
		window.configEnableBalloonCheck.setSelection(iniSettingSection.get(IniConstants.Client.ENABLE_BALLOON, true));

		String[] serverList;

		serverList = iniSettingSection.get(IniConstants.Client.PORTAL_SERVER_LIST, "").split(",");
		ComboHistoryManager.addList(window.portalServerAddressCombo, serverList);
		serverList = iniSettingSection.get(IniConstants.Client.PORTAL_SERVER_HISTORY, "").split(",");
		portalServerHistoryManager = new ComboHistoryManager(window.portalServerAddressCombo, serverList, MAX_SERVER_HISTORY);

		serverList = iniSettingSection.get(IniConstants.Client.ROOM_SERVER_LIST, "").split(",");
		ComboHistoryManager.addList(window.roomFormMasterModeAddressCombo, serverList);
		serverList = iniSettingSection.get(IniConstants.Client.ROOM_SERVER_HISTORY, "").split(",");
		roomServerHistoryManager = new ComboHistoryManager(window.roomFormMasterModeAddressCombo, serverList, MAX_SERVER_HISTORY);

		serverList = iniSettingSection.get(IniConstants.Client.ROOM_ADDRESS_LIST, "").split(",");
		ComboHistoryManager.addList(window.roomFormParticipantModeAddressCombo, serverList);
		serverList = iniSettingSection.get(IniConstants.Client.ROOM_ADDRESS_HISTORY, "").split(",");
		roomAddressHistoryManager = new ComboHistoryManager(window.roomFormParticipantModeAddressCombo, serverList, MAX_SERVER_HISTORY);

		serverList = iniSettingSection.get(IniConstants.Client.ROOM_SERVER_LIST, "").split(",");
		ComboHistoryManager.addList(window.roomFormMyRoomModeEntryCombo, serverList);
		serverList = iniSettingSection.get(IniConstants.Client.ROOM_SERVER_HISTORY, "").split(",");
		myRoomServerEntryHistoryManager = new ComboHistoryManager(window.roomFormMyRoomModeEntryCombo, serverList, MAX_SERVER_HISTORY);

		queryRoomTitleHistoryManager = new ComboHistoryManager(window.portalSearchFormTitleCombo, null, 20);
		queryRoomTitleNgHistoryManager = new ComboHistoryManager(window.portalSearchFormTitleNgCombo, null, 20);
		queryRoomMasterNameHistoryManager = new ComboHistoryManager(window.portalSearchFormMasterNameCombo, null, 20);
		queryRoomMasterNameNgHistoryManager = new ComboHistoryManager(window.portalSearchFormMasterNameNgCombo, null, 20);
		queryRoomAddressHistoryManager = new ComboHistoryManager(window.portalSearchFormServerNameCombo, null, 20);
		queryRoomAddressNgHistoryManager = new ComboHistoryManager(window.portalSearchFormServerNameNgCombo, null, 20);

		String software = String.format("%s プレイクライアント バージョン: %s", AppConstants.APP_NAME, AppConstants.VERSION);
		appendLogTo(window.portalLogText, software, window.colorAppInfo, false);
		appendLogTo(window.portalLogText, "プロトコル: " + ProtocolConstants.PROTOCOL_NUMBER, window.colorAppInfo, false);
		appendLogTo(window.portalLogText, "SSID機能: " + (Wlan.isLibraryAvailable ? "On" : "Off"), window.colorAppInfo, false);

		initializeBackgroundThreads();
	}

	private static class Window {
		private TabFolder mainTabFolder;

		private TabItem portalTab;
		private SashForm portalMainSash;
		private TableViewer portalRoomServerTableViewer;
		private StyledText portalLogText;
		private Combo portalServerAddressCombo;
		private Button portalServerLoginButton;
		private Combo portalSearchFormTitleCombo;
		private Combo portalSearchFormTitleNgCombo;
		private Combo portalSearchFormMasterNameCombo;
		private Combo portalSearchFormMasterNameNgCombo;
		private Combo portalSearchFormServerNameCombo;
		private Combo portalSearchFormServerNameNgCombo;
		private Button portalSearchFormHasPassword;
		private Button portalSearchFormAutoQuery;
		private TableViewer portalRoomSearchResultTable;

		private TabItem playRoomTab;
		private Composite roomFormModeSwitchContainer;
		private StackLayout roomModeStackLayout;
		private Combo roomFormModeSelectionCombo;
		private Composite roomFormMasterModeContainer;
		private Combo roomFormMasterModeAddressCombo;
		private Button roomFormMasterModeLoginButton;
		private Text roomFormMasterModeLobbyAddressText;
		private Button roomFormMasterModeEnterLobbyCheck;
		private Composite roomFormParticipantModeContainer;
		private Combo roomFormParticipantModeAddressCombo;
		private Button roomFormParticipantModeLoginButton;
		private Text roomFormParticipantModeLobbyAddressText;
		private Button roomFormParticipantModeEnterLobbyCheck;
		private Composite roomFormMyRoomModeContainer;
		private Text roomFormMyRoomModeHostText;
		private Spinner roomFormMyRoomModePortSpinner;
		private Button roomFormMyRoomModeStartButton;
		private Combo roomFormMyRoomModeEntryCombo;
		private Button roomFormMyRoomModeEntryButton;
		private Text roomFormMasterText;
		private Text roomFormTitleText;
		private Text roomFormPasswordText;
		private Spinner roomFormMaxPlayersSpiner;
		private Button roomFormEditButton;
		private Text roomFormDescriptionText;
		private Combo wlanAdapterListCombo;
		private Button wlanPspCommunicationButton;
		private TableViewer packetMonitorTable;
		private Button ssidStartScan;
		private Text ssidCurrentSsidText;
		private Label ssidMatchLabel;
		private Text ssidMatchText;
		private Spinner ssidScanIntervalSpinner;
		private Label ssidScanIntervalLabel;
		private Button ssidAutoDetectCheck;
		private TableViewer ssidListTableViewer;
		private StyledText roomChatLogText;
		private TableViewer roomPlayerListTableViewer;
		private Text roomChatSubmitText;
		private Button roomChatSubmitButton;

		private TabItem configTab;
		private Text configUserNameText;
		private Label configUserNameAlertLabel;
		private Button configAppCloseConfirmCheck;
		private Button configEnableBalloonCheck;
		private Button configMyRoomAllowEmptyMasterNameCheck;

		private TabItem logTab;
		private Text logText;
		private Composite statusBarContainer;
		private Label statusRoomServerAddressLabel;
		private Label statusTunnelConnectionLabel;
		private Label statusPortalServerLabel;
		private Label statusSearchResultLabel;
		private Label statusTraficStatusLabel;

		private Color colorWhite, colorBlack, colorRed, colorGreen;
		private Color colorLogInfo, colorLogError, colorRoomInfo, colorAppInfo, colorServerInfo;

		private Menu portalRoomServerMenu;
		private MenuItem portalRoomServerSetAddress;
		private MenuItem portalRoomServerSetAddress4MyRoom;
		private Menu roomPlayerMenu;
		private MenuItem roomPlayerChaseSsidMenuItem;
		private MenuItem roomPlayerSetSsidMenuItem;
		private MenuItem roomPlayerCopySsidMenuItem;
		private MenuItem roomPlayerKickMenuItem;
		private MenuItem roomPlayerMasterTransferMenuItem;
		private Menu statusServerAddressMenu;
		private MenuItem statusServerAddressCopy;
		private Menu packetMonitorMenu;
		private MenuItem packetMonitorMenuCopy;
		private MenuItem packetMonitorMenuClear;

		private TrayItem trayItem;
		private ToolTip toolTip;
		private boolean isActive;

		private Clipboard clipboard;
		private TextTransfer[] textTransfers = new TextTransfer[] { TextTransfer.getInstance() };

		private Window() {
		}

		private void initializeComponents(Display display, Shell shell) {
			clipboard = new Clipboard(display);
			try {
				ImageData imageData16 = new ImageData("icon/blue16.png");
				ImageData imageData32 = new ImageData("icon/blue32.png");
				ImageData imageData48 = new ImageData("icon/blue48.png");
				ImageData imageData96 = new ImageData("icon/blue96.png");
				Image image16 = new Image(display, imageData16);
				Image image32 = new Image(display, imageData32);
				Image image48 = new Image(display, imageData48);
				Image image96 = new Image(display, imageData96);
				shell.setImages(new Image[] { image16, image32, image48, image96 });

				Tray systemTray = display.getSystemTray();
				if (systemTray != null) {
					trayItem = new TrayItem(systemTray, SWT.NONE);
					trayItem.setImage(image16);

					toolTip = new ToolTip(shell, SWT.BALLOON | SWT.ICON_INFORMATION);
					trayItem.setToolTip(toolTip);
				}
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

			portalTab = new TabItem(mainTabFolder, SWT.NONE);
			portalTab.setText("ポータル検索");

			portalMainSash = new SashForm(mainTabFolder, SWT.HORIZONTAL | SWT.SMOOTH);
			portalTab.setControl(portalMainSash);

			Composite portalLeftContainer = new Composite(portalMainSash, SWT.NONE);
			gridLayout = new GridLayout(3, false);
			gridLayout.horizontalSpacing = 3;
			gridLayout.verticalSpacing = 3;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginTop = 3;
			portalLeftContainer.setLayout(gridLayout);

			Label portalServerAddressLabel = new Label(portalLeftContainer, SWT.NONE);
			portalServerAddressLabel.setText("ポータルサーバー");
			portalServerAddressLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			portalServerAddressCombo = new Combo(portalLeftContainer, SWT.BORDER);
			portalServerAddressCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			portalServerLoginButton = new Button(portalLeftContainer, SWT.PUSH);
			portalServerLoginButton.setText("ログイン");
			portalServerLoginButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

			SashForm portalLeftSash = new SashForm(portalLeftContainer, SWT.VERTICAL | SWT.SMOOTH);
			portalLeftSash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));

			portalRoomServerTableViewer = new TableViewer(portalLeftSash, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
			Table portalRoomServerTable = portalRoomServerTableViewer.getTable();
			portalRoomServerTable.setHeaderVisible(true);

			TableColumn portalRoomServerNameColumn = new TableColumn(portalRoomServerTable, SWT.LEFT);
			portalRoomServerNameColumn.setText("ルームサーバー");
			portalRoomServerNameColumn.setWidth(180);

			TableColumn portalRoomServerCapacityColumn = new TableColumn(portalRoomServerTable, SWT.CENTER);
			portalRoomServerCapacityColumn.setText("部屋数");
			portalRoomServerCapacityColumn.setWidth(75);

			TableColumn portalRoomServerAllowPasswordColumn = new TableColumn(portalRoomServerTable, SWT.CENTER);
			portalRoomServerAllowPasswordColumn.setText("鍵許可");
			portalRoomServerAllowPasswordColumn.setWidth(50);

			TableColumn portalRoomServerMyRoomCountColumn = new TableColumn(portalRoomServerTable, SWT.CENTER);
			portalRoomServerMyRoomCountColumn.setText("マイルーム");
			portalRoomServerMyRoomCountColumn.setWidth(65);

			portalRoomServerTableViewer.setContentProvider(new RoomServerData.ContentProvider());
			portalRoomServerTableViewer.setLabelProvider(new RoomServerData.LabelProvider());

			portalLogText = new StyledText(portalLeftSash, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER | SWT.READ_ONLY | SWT.WRAP);
			portalLogText.setBackground(colorWhite);
			portalLogText.setMargins(3, 1, 3, 1);

			portalLeftSash.setWeights(new int[] { 5, 3 });

			Composite searchRightContainer = new Composite(portalMainSash, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.horizontalSpacing = 3;
			gridLayout.verticalSpacing = 3;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginTop = 3;
			searchRightContainer.setLayout(gridLayout);

			Group searchFormGroup = new Group(searchRightContainer, SWT.DEFAULT);
			searchFormGroup.setText("検索条件");
			gridLayout = new GridLayout(4, false);
			gridLayout.horizontalSpacing = 3;
			gridLayout.verticalSpacing = 3;
			gridLayout.marginWidth = 4;
			gridLayout.marginHeight = 1;
			gridLayout.marginTop = 2;
			searchFormGroup.setLayout(gridLayout);
			searchFormGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label portalSearchFormServerNameLabel = new Label(searchFormGroup, SWT.NONE);
			portalSearchFormServerNameLabel.setText("ルームサーバー");
			portalSearchFormServerNameLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			portalSearchFormServerNameCombo = new Combo(searchFormGroup, SWT.BORDER);
			portalSearchFormServerNameCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label portalSearchFormServerNameNgLabel = new Label(searchFormGroup, SWT.NONE);
			portalSearchFormServerNameNgLabel.setText("除外");
			portalSearchFormServerNameNgLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			portalSearchFormServerNameNgCombo = new Combo(searchFormGroup, SWT.NONE);
			portalSearchFormServerNameNgCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label portalSearchFormMasterNameLabel = new Label(searchFormGroup, SWT.NONE);
			portalSearchFormMasterNameLabel.setText("部屋主");
			portalSearchFormMasterNameLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			portalSearchFormMasterNameCombo = new Combo(searchFormGroup, SWT.BORDER);
			portalSearchFormMasterNameCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label portalSearchFormMasterNameNgLabel = new Label(searchFormGroup, SWT.NONE);
			portalSearchFormMasterNameNgLabel.setText("除外");
			portalSearchFormMasterNameNgLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			portalSearchFormMasterNameNgCombo = new Combo(searchFormGroup, SWT.NONE);
			portalSearchFormMasterNameNgCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label portalSearchFormTitleLabel = new Label(searchFormGroup, SWT.NONE);
			portalSearchFormTitleLabel.setText("部屋名");
			portalSearchFormTitleLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			portalSearchFormTitleCombo = new Combo(searchFormGroup, SWT.BORDER);
			portalSearchFormTitleCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label portalSearchFormTitleNgLabel = new Label(searchFormGroup, SWT.NONE);
			portalSearchFormTitleNgLabel.setText("除外");
			portalSearchFormTitleNgLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			portalSearchFormTitleNgCombo = new Combo(searchFormGroup, SWT.NONE);
			portalSearchFormTitleNgCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			portalSearchFormAutoQuery = new Button(searchFormGroup, SWT.TOGGLE);
			portalSearchFormAutoQuery.setText("自動更新オン");
			portalSearchFormAutoQuery.setSelection(true);
			portalSearchFormAutoQuery.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			portalSearchFormHasPassword = new Button(searchFormGroup, SWT.CHECK | SWT.FLAT);
			portalSearchFormHasPassword.setText("鍵付き");
			portalSearchFormHasPassword.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));

			portalRoomSearchResultTable = new TableViewer(searchRightContainer, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
			portalRoomSearchResultTable.getTable().setHeaderVisible(true);
			portalRoomSearchResultTable.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			portalRoomSearchResultTable.setContentProvider(new ArrayContentProvider());
			portalRoomSearchResultTable.setLabelProvider(new PlayRoomUtils.LabelProvider());

			TableColumn portalSearchResultRoomServerColumn = new TableColumn(portalRoomSearchResultTable.getTable(), SWT.LEFT);
			portalSearchResultRoomServerColumn.setText("ルームサーバー");
			portalSearchResultRoomServerColumn.setWidth(150);
			SwtUtils.installSorter(portalRoomSearchResultTable, portalSearchResultRoomServerColumn, new PlayRoomUtils.AddressSorter());

			TableColumn portalSearchResultMasterNameColumn = new TableColumn(portalRoomSearchResultTable.getTable(), SWT.LEFT);
			portalSearchResultMasterNameColumn.setText("部屋主");
			portalSearchResultMasterNameColumn.setWidth(120);
			SwtUtils.installSorter(portalRoomSearchResultTable, portalSearchResultMasterNameColumn, new PlayRoomUtils.MasterNameSorter());

			TableColumn portalSearchResultTitleColumn = new TableColumn(portalRoomSearchResultTable.getTable(), SWT.LEFT);
			portalSearchResultTitleColumn.setText("部屋名");
			portalSearchResultTitleColumn.setWidth(200);
			SwtUtils.installSorter(portalRoomSearchResultTable, portalSearchResultTitleColumn, new PlayRoomUtils.TitleSorter());

			TableColumn portalSearchResultCapacityColumn = new TableColumn(portalRoomSearchResultTable.getTable(), SWT.CENTER);
			portalSearchResultCapacityColumn.setText("定員");
			portalSearchResultCapacityColumn.setWidth(65);
			SwtUtils.installSorter(portalRoomSearchResultTable, portalSearchResultCapacityColumn, new PlayRoomUtils.CapacitySorter());

			TableColumn portalSearchResultHasPasswordColumn = new TableColumn(portalRoomSearchResultTable.getTable(), SWT.CENTER);
			portalSearchResultHasPasswordColumn.setText("鍵");
			portalSearchResultHasPasswordColumn.setWidth(40);
			SwtUtils.installSorter(portalRoomSearchResultTable, portalSearchResultHasPasswordColumn, new PlayRoomUtils.HasPasswordSorter());

			TableColumn portalSearchResultDescriptionColumn = new TableColumn(portalRoomSearchResultTable.getTable(), SWT.LEFT);
			portalSearchResultDescriptionColumn.setText("詳細・備考");
			portalSearchResultDescriptionColumn.setWidth(250);

			portalMainSash.setWeights(new int[] { 3, 7 });

			playRoomTab = new TabItem(mainTabFolder, SWT.NONE);
			playRoomTab.setText("プレイルーム");

			SashForm roomSashForm = new SashForm(mainTabFolder, SWT.HORIZONTAL);
			playRoomTab.setControl(roomSashForm);

			Composite roomLeftContainer = new Composite(roomSashForm, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.horizontalSpacing = 0;
			gridLayout.verticalSpacing = 4;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginTop = 5;
			roomLeftContainer.setLayout(gridLayout);

			Composite roomFormControlContainer = new Composite(roomLeftContainer, SWT.NONE);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 8;
			gridLayout.verticalSpacing = 3;
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginLeft = 5;
			roomFormControlContainer.setLayout(gridLayout);
			roomFormControlContainer.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false));

			Label roomFormModeSelectionLabel = new Label(roomFormControlContainer, SWT.NONE);
			roomFormModeSelectionLabel.setText("モード");
			roomFormModeSelectionLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			roomFormModeSelectionCombo = new Combo(roomFormControlContainer, SWT.READ_ONLY);
			roomFormModeSelectionCombo.setItems(new String[] { "部屋を作成する", "部屋に参加する", "マイルームをホストする" });
			roomFormModeSelectionCombo.select(0);
			roomFormModeSelectionCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			roomFormModeSwitchContainer = new Composite(roomFormControlContainer, SWT.NONE);
			roomModeStackLayout = new StackLayout();
			roomFormModeSwitchContainer.setLayout(roomModeStackLayout);
			roomFormModeSwitchContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

			roomFormMasterModeContainer = new Composite(roomFormModeSwitchContainer, SWT.NONE);
			gridLayout = new GridLayout(3, false);
			gridLayout.horizontalSpacing = 5;
			gridLayout.verticalSpacing = 4;
			gridLayout.marginWidth = 1;
			gridLayout.marginHeight = 0;
			roomFormMasterModeContainer.setLayout(gridLayout);

			Label roomFormProxyModeAddressLabel = new Label(roomFormMasterModeContainer, SWT.NONE);
			roomFormProxyModeAddressLabel.setText("ルームサーバー");
			roomFormProxyModeAddressLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			roomFormMasterModeAddressCombo = new Combo(roomFormMasterModeContainer, SWT.NONE);
			roomFormMasterModeAddressCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			roomFormMasterModeLoginButton = new Button(roomFormMasterModeContainer, SWT.PUSH);
			roomFormMasterModeLoginButton.setText("作成する");
			roomFormMasterModeLoginButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

			Label roomFormProxyModeLobbyAddressLabel = new Label(roomFormMasterModeContainer, SWT.NONE);
			roomFormProxyModeLobbyAddressLabel.setText("ロビーアドレス");

			roomFormMasterModeLobbyAddressText = new Text(roomFormMasterModeContainer, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
			roomFormMasterModeLobbyAddressText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			roomFormMasterModeLobbyAddressText.setBackground(colorWhite);

			roomFormMasterModeEnterLobbyCheck = new Button(roomFormMasterModeContainer, SWT.CHECK | SWT.FLAT);
			roomFormMasterModeEnterLobbyCheck.setText("退室後に入る");
			roomFormMasterModeEnterLobbyCheck.setLayoutData(new GridData(SWT.LEAD, SWT.CENTER, false, false));

			roomFormParticipantModeContainer = new Composite(roomFormModeSwitchContainer, SWT.NONE);
			gridLayout = new GridLayout(3, false);
			gridLayout.horizontalSpacing = 5;
			gridLayout.verticalSpacing = 4;
			gridLayout.marginWidth = 1;
			gridLayout.marginHeight = 0;
			roomFormParticipantModeContainer.setLayout(gridLayout);

			Label roomFormClientModeAddressLabel = new Label(roomFormParticipantModeContainer, SWT.NONE);
			roomFormClientModeAddressLabel.setText("部屋アドレス");

			roomFormParticipantModeAddressCombo = new Combo(roomFormParticipantModeContainer, SWT.NONE);
			roomFormParticipantModeAddressCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			roomFormParticipantModeLoginButton = new Button(roomFormParticipantModeContainer, SWT.PUSH);
			roomFormParticipantModeLoginButton.setText("入室する");
			roomFormParticipantModeLoginButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

			Label roomFormClientModeLobbyAddressLabel = new Label(roomFormParticipantModeContainer, SWT.NONE);
			roomFormClientModeLobbyAddressLabel.setText("ロビーアドレス");

			roomFormParticipantModeLobbyAddressText = new Text(roomFormParticipantModeContainer, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
			roomFormParticipantModeLobbyAddressText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			roomFormParticipantModeLobbyAddressText.setBackground(colorWhite);

			roomFormParticipantModeEnterLobbyCheck = new Button(roomFormParticipantModeContainer, SWT.CHECK | SWT.FLAT);
			roomFormParticipantModeEnterLobbyCheck.setText("退室後に入る");
			roomFormParticipantModeEnterLobbyCheck.setLayoutData(new GridData(SWT.LEAD, SWT.CENTER, false, false));

			roomFormMyRoomModeContainer = new Composite(roomFormModeSwitchContainer, SWT.NONE);
			gridLayout = new GridLayout(4, false);
			gridLayout.horizontalSpacing = 3;
			gridLayout.verticalSpacing = 3;
			gridLayout.marginWidth = 1;
			gridLayout.marginHeight = 0;
			roomFormMyRoomModeContainer.setLayout(gridLayout);

			Label roomFormServerModeAddressLabel = new Label(roomFormMyRoomModeContainer, SWT.NONE);
			roomFormServerModeAddressLabel.setText("ホスト名:ポート");
			roomFormServerModeAddressLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			roomFormMyRoomModeHostText = new Text(roomFormMyRoomModeContainer, SWT.SINGLE | SWT.BORDER);
			roomFormMyRoomModeHostText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

			roomFormMyRoomModePortSpinner = new Spinner(roomFormMyRoomModeContainer, SWT.BORDER);
			roomFormMyRoomModePortSpinner.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
			roomFormMyRoomModePortSpinner.setForeground(colorBlack);
			roomFormMyRoomModePortSpinner.setBackground(colorWhite);
			roomFormMyRoomModePortSpinner.setMinimum(1);
			roomFormMyRoomModePortSpinner.setMaximum(65535);
			roomFormMyRoomModePortSpinner.setSelection(30000);

			roomFormMyRoomModeStartButton = new Button(roomFormMyRoomModeContainer, SWT.PUSH);
			roomFormMyRoomModeStartButton.setText("起動する");
			roomFormMyRoomModeStartButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			Label roomFormServerModeEntryLabel = new Label(roomFormMyRoomModeContainer, SWT.NONE);
			roomFormServerModeEntryLabel.setText("ルームサーバー");
			roomFormServerModeEntryLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			roomFormMyRoomModeEntryCombo = new Combo(roomFormMyRoomModeContainer, SWT.BORDER);
			roomFormMyRoomModeEntryCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

			roomFormMyRoomModeEntryButton = new Button(roomFormMyRoomModeContainer, SWT.PUSH);
			roomFormMyRoomModeEntryButton.setText("登録");
			roomFormMyRoomModeEntryButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

			roomModeStackLayout.topControl = roomFormMasterModeContainer;

			Group roomFormGroup = new Group(roomLeftContainer, SWT.NONE);
			roomFormGroup.setText("部屋情報");
			roomFormGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			roomFormGroup.setLayout(new FormLayout());

			Composite roomFormGridContainer = new Composite(roomFormGroup, SWT.NONE);
			roomFormGridContainer.setLayout(new GridLayout(2, false));
			formData = new FormData();
			formData.top = new FormAttachment(0, 1);
			formData.left = new FormAttachment(0, 0);
			formData.right = new FormAttachment(100, 0);
			roomFormGridContainer.setLayoutData(formData);

			Label roomFormMasterLabel = new Label(roomFormGridContainer, SWT.NONE);
			roomFormMasterLabel.setText("部屋主");
			roomFormMasterLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			roomFormMasterText = new Text(roomFormGridContainer, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
			roomFormMasterText.setBackground(colorWhite);
			roomFormMasterText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label roomFormTitleLabel = new Label(roomFormGridContainer, SWT.NONE);
			roomFormTitleLabel.setText("部屋名");
			roomFormTitleLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			roomFormTitleText = new Text(roomFormGridContainer, SWT.SINGLE | SWT.BORDER);
			roomFormTitleText.setBackground(colorWhite);
			roomFormTitleText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			roomFormTitleText.setTextLimit(100);

			Label roomFormPasswordLabel = new Label(roomFormGridContainer, SWT.NONE);
			roomFormPasswordLabel.setText("パスワード");
			roomFormPasswordLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			roomFormPasswordText = new Text(roomFormGridContainer, SWT.SINGLE | SWT.BORDER);
			roomFormPasswordText.setBackground(colorWhite);
			roomFormPasswordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			roomFormPasswordText.setTextLimit(30);

			Label roomFormMaxPlayersLabel = new Label(roomFormGridContainer, SWT.NONE);
			roomFormMaxPlayersLabel.setText("定員");
			roomFormMaxPlayersLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			Composite roomFormMaxPlayerContainer = new Composite(roomFormGridContainer, SWT.NONE);
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
			roomFormMaxPlayersSpiner.setLayoutData(new GridData(50, SWT.DEFAULT));

			roomFormEditButton = new Button(roomFormMaxPlayerContainer, SWT.PUSH);
			roomFormEditButton.setText("部屋情報を更新");
			roomFormEditButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));

			Label roomFormDescriptionLabel = new Label(roomFormGroup, SWT.NONE);
			roomFormDescriptionLabel.setText("部屋の紹介・備考");
			formData = new FormData();
			formData.top = new FormAttachment(roomFormGridContainer, 8);
			formData.left = new FormAttachment(0, 6);
			roomFormDescriptionLabel.setLayoutData(formData);

			roomFormDescriptionText = new Text(roomFormGroup, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER | SWT.WRAP);
			roomFormDescriptionText.setBackground(colorWhite);
			roomFormDescriptionText.setTextLimit(1000);

			formData = new FormData();
			formData.top = new FormAttachment(roomFormDescriptionLabel, 4);
			formData.left = new FormAttachment(0, 3);
			formData.right = new FormAttachment(100, -3);
			formData.bottom = new FormAttachment(100, -3);
			roomFormDescriptionText.setLayoutData(formData);

			SashForm roomCenterSashForm = new SashForm(roomSashForm, SWT.VERTICAL);
			roomCenterSashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			Composite roomCenterUpperContainer = new Composite(roomCenterSashForm, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.verticalSpacing = 0;
			gridLayout.horizontalSpacing = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginWidth = 0;
			roomCenterUpperContainer.setLayout(gridLayout);

			Composite wlanAdaptorContainer = new Composite(roomCenterUpperContainer, SWT.NONE);
			gridLayout = new GridLayout(3, false);
			gridLayout.verticalSpacing = 0;
			gridLayout.horizontalSpacing = 3;
			gridLayout.marginHeight = 3;
			gridLayout.marginWidth = 2;
			wlanAdaptorContainer.setLayout(gridLayout);
			wlanAdaptorContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Label wlanAdapterListLabel = new Label(wlanAdaptorContainer, SWT.NONE);
			wlanAdapterListLabel.setText("無線LANアダプタ");

			wlanAdapterListCombo = new Combo(wlanAdaptorContainer, SWT.READ_ONLY);
			wlanAdapterListCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			wlanPspCommunicationButton = new Button(wlanAdaptorContainer, SWT.TOGGLE);
			wlanPspCommunicationButton.setText("PSPと通信開始");

			packetMonitorTable = new TableViewer(roomCenterUpperContainer, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
			packetMonitorTable.getTable().setHeaderVisible(true);
			packetMonitorTable.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			TableColumn packetMonitorIsMineColumn = new TableColumn(packetMonitorTable.getTable(), SWT.CENTER);
			packetMonitorIsMineColumn.setText("");
			packetMonitorIsMineColumn.setWidth(25);
			SwtUtils.installSorter(packetMonitorTable, packetMonitorIsMineColumn, new TraficStatistics.MineSorter());

			TableColumn packetMonitorMacAddressColumn = new TableColumn(packetMonitorTable.getTable(), SWT.LEFT);
			packetMonitorMacAddressColumn.setText("MACアドレス");
			packetMonitorMacAddressColumn.setWidth(100);
			SwtUtils.installSorter(packetMonitorTable, packetMonitorMacAddressColumn, new TraficStatistics.MacAddressSorter());

			TableColumn packetMonitorPlayerNameColumn = new TableColumn(packetMonitorTable.getTable(), SWT.LEFT);
			packetMonitorPlayerNameColumn.setText("ユーザー名");
			packetMonitorPlayerNameColumn.setWidth(100);
			SwtUtils.installSorter(packetMonitorTable, packetMonitorPlayerNameColumn, new TraficStatistics.PlayerNameSorter());

			TableColumn packetMonitorInSpeedColumn = new TableColumn(packetMonitorTable.getTable(), SWT.RIGHT);
			packetMonitorInSpeedColumn.setText("In (Kbps)");
			packetMonitorInSpeedColumn.setWidth(80);
			SwtUtils.installSorter(packetMonitorTable, packetMonitorInSpeedColumn, new TraficStatistics.InSpeedSorter());

			TableColumn packetMonitorOutSpeedColumn = new TableColumn(packetMonitorTable.getTable(), SWT.RIGHT);
			packetMonitorOutSpeedColumn.setText("Out (Kbps)");
			packetMonitorOutSpeedColumn.setWidth(80);
			SwtUtils.installSorter(packetMonitorTable, packetMonitorOutSpeedColumn, new TraficStatistics.OutSpeedSorter());

			TableColumn packetMonitorTotalInBytesColumn = new TableColumn(packetMonitorTable.getTable(), SWT.RIGHT);
			packetMonitorTotalInBytesColumn.setText("In 累積バイト");
			packetMonitorTotalInBytesColumn.setWidth(100);
			SwtUtils.installSorter(packetMonitorTable, packetMonitorTotalInBytesColumn, new TraficStatistics.TotalInSorter());

			TableColumn packetMonitorTotalOutBytesColumn = new TableColumn(packetMonitorTable.getTable(), SWT.RIGHT);
			packetMonitorTotalOutBytesColumn.setText("Out 累積バイト");
			packetMonitorTotalOutBytesColumn.setWidth(100);
			SwtUtils.installSorter(packetMonitorTable, packetMonitorTotalOutBytesColumn, new TraficStatistics.TotalOutSorter());

			packetMonitorTable.setContentProvider(new TraficStatistics.ContentProvider());
			packetMonitorTable.setLabelProvider(new TraficStatistics.LabelProvider());

			Composite roomChatContainer = new Composite(roomCenterSashForm, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.verticalSpacing = 0;
			gridLayout.horizontalSpacing = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginWidth = 0;
			roomChatContainer.setLayout(gridLayout);

			roomChatLogText = new StyledText(roomChatContainer, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER | SWT.READ_ONLY | SWT.WRAP);
			roomChatLogText.setMargins(3, 1, 3, 1);
			roomChatLogText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			Composite roomChatCommandContainer = new Composite(roomChatContainer, SWT.NONE);
			gridLayout = new GridLayout(2, false);
			gridLayout.verticalSpacing = 0;
			gridLayout.horizontalSpacing = 3;
			gridLayout.marginHeight = 0;
			gridLayout.marginWidth = 0;
			gridLayout.marginTop = 3;
			gridLayout.marginBottom = 1;
			gridLayout.marginRight = 1;
			roomChatCommandContainer.setLayout(gridLayout);
			roomChatCommandContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			roomChatSubmitText = new Text(roomChatCommandContainer, SWT.BORDER | SWT.SINGLE);
			roomChatSubmitText.setFont(new Font(shell.getDisplay(), "Sans Serif", 12, SWT.NORMAL));
			roomChatSubmitText.setTextLimit(300);
			roomChatSubmitText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			roomChatSubmitButton = new Button(roomChatCommandContainer, SWT.PUSH);
			roomChatSubmitButton.setText("発言");
			roomChatSubmitButton.setLayoutData(new GridData(50, SWT.DEFAULT));

			SashForm roomRightSashForm = new SashForm(roomSashForm, SWT.VERTICAL);

			Composite ssidContainer = new Composite(roomRightSashForm, SWT.NONE);
			gridLayout = new GridLayout(2, false);
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.horizontalSpacing = 1;
			gridLayout.verticalSpacing = 3;
			gridLayout.marginTop = 4;
			gridLayout.marginRight = 1;
			ssidContainer.setLayout(gridLayout);

			Label ssidCurrentSsidLabel = new Label(ssidContainer, SWT.NONE);
			ssidCurrentSsidLabel.setText("現在のSSID");
			ssidCurrentSsidLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			ssidCurrentSsidText = new Text(ssidContainer, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
			ssidCurrentSsidText.setBackground(colorWhite);
			ssidCurrentSsidText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			ssidMatchLabel = new Label(ssidContainer, SWT.NONE);
			ssidMatchLabel.setText("絞り込み");
			ssidMatchLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			ssidMatchText = new Text(ssidContainer, SWT.SINGLE | SWT.BORDER);
			ssidMatchText.setText("PSP_");
			ssidMatchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			Composite ssidControlContainer = new Composite(ssidContainer, SWT.NONE);
			ssidControlContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
			gridLayout = new GridLayout(4, false);
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.horizontalSpacing = 3;
			gridLayout.verticalSpacing = 0;
			gridLayout.marginLeft = 2;
			gridLayout.marginRight = 1;
			ssidControlContainer.setLayout(gridLayout);

			ssidStartScan = new Button(ssidControlContainer, SWT.TOGGLE);
			ssidStartScan.setText("スキャン開始");
			ssidStartScan.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			ssidStartScan.setEnabled(false);

			ssidScanIntervalSpinner = new Spinner(ssidControlContainer, SWT.BORDER);
			ssidScanIntervalSpinner.setMinimum(500);
			ssidScanIntervalSpinner.setMaximum(9999);

			ssidScanIntervalLabel = new Label(ssidControlContainer, SWT.NONE);
			ssidScanIntervalLabel.setText("ミリ秒");

			ssidAutoDetectCheck = new Button(ssidControlContainer, SWT.CHECK | SWT.FLAT);
			ssidAutoDetectCheck.setText("自動追跡");
			ssidAutoDetectCheck.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));

			ssidListTableViewer = new TableViewer(ssidContainer, SWT.BORDER | SWT.FULL_SELECTION);
			ssidListTableViewer.setContentProvider(new ArrayContentProvider());
			ssidListTableViewer.setLabelProvider(new WlanUtils.LabelProvider());
			Table ssidListTable = ssidListTableViewer.getTable();
			ssidListTable.setHeaderVisible(true);
			ssidListTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));

			TableColumn ssidListTableSsidColumn = new TableColumn(ssidListTable, SWT.LEFT);
			ssidListTableSsidColumn.setWidth(150);
			ssidListTableSsidColumn.setText("SSID");

			TableColumn ssidListTableRssiColumn = new TableColumn(ssidListTable, SWT.RIGHT);
			ssidListTableRssiColumn.setWidth(40);
			ssidListTableRssiColumn.setText("強度");

			roomPlayerListTableViewer = new TableViewer(roomRightSashForm, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
			roomPlayerListTableViewer.getTable().setHeaderVisible(true);

			TableColumn roomPlayerSsidChaseColumn = new TableColumn(roomPlayerListTableViewer.getTable(), SWT.CENTER);
			roomPlayerSsidChaseColumn.setWidth(22);
			SwtUtils.installSorter(roomPlayerListTableViewer, roomPlayerSsidChaseColumn, Player.SSID_CHASE_SORTER);

			TableColumn roomPlayerNameColumn = new TableColumn(roomPlayerListTableViewer.getTable(), SWT.LEFT);
			roomPlayerNameColumn.setText("名前");
			roomPlayerNameColumn.setWidth(100);
			SwtUtils.installSorter(roomPlayerListTableViewer, roomPlayerNameColumn, Player.NANE_SORTER);

			TableColumn roomPlayerSsidColumn = new TableColumn(roomPlayerListTableViewer.getTable(), SWT.LEFT);
			roomPlayerSsidColumn.setText("SSID");
			roomPlayerSsidColumn.setWidth(100);
			SwtUtils.installSorter(roomPlayerListTableViewer, roomPlayerSsidColumn, Player.SSID_SORTER);

			TableColumn roomPlayerPingColumn = new TableColumn(roomPlayerListTableViewer.getTable(), SWT.RIGHT);
			roomPlayerPingColumn.setText("PING");
			roomPlayerPingColumn.setWidth(50);
			SwtUtils.installSorter(roomPlayerListTableViewer, roomPlayerPingColumn, Player.PING_SORTER);

			roomPlayerListTableViewer.setContentProvider(new Player.PlayerListContentProvider());
			roomPlayerListTableViewer.setLabelProvider(new Player.RoomPlayerLabelProvider());

			configTab = new TabItem(mainTabFolder, SWT.NONE);
			configTab.setText("設定");

			Composite configContainer = new Composite(mainTabFolder, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.horizontalSpacing = 0;
			gridLayout.verticalSpacing = 5;
			gridLayout.marginWidth = 2;
			gridLayout.marginHeight = 5;
			gridLayout.marginLeft = 4;
			configContainer.setLayout(gridLayout);
			configTab.setControl(configContainer);

			Composite configUserNameContainer = new Composite(configContainer, SWT.NONE);
			configUserNameContainer.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			rowLayout = new RowLayout();
			rowLayout.center = true;
			rowLayout.marginLeft = 0;
			configUserNameContainer.setLayout(rowLayout);

			Label configUserNameLabel = new Label(configUserNameContainer, SWT.NONE);
			configUserNameLabel.setText("ユーザー名");

			configUserNameText = new Text(configUserNameContainer, SWT.SINGLE | SWT.BORDER);
			configUserNameText.setLayoutData(new RowData(200, SWT.DEFAULT));
			configUserNameText.setTextLimit(100);

			configUserNameAlertLabel = new Label(configUserNameContainer, SWT.NONE);
			configUserNameAlertLabel.setText("ユーザー名を入力してください");
			configUserNameAlertLabel.setForeground(colorLogError);

			configAppCloseConfirmCheck = new Button(configContainer, SWT.CHECK | SWT.FLAT);
			configAppCloseConfirmCheck.setText("アプリケーションを閉じる時に確認する");

			configEnableBalloonCheck = new Button(configContainer, SWT.CHECK | SWT.FLAT);
			configEnableBalloonCheck.setText("部屋のメッセージをタスクトレイからバルーンで通知する");

			Group configMyRoomGroup = new Group(configContainer, SWT.SHADOW_IN);
			configMyRoomGroup.setText("マイルーム");
			gridData = new GridData(SWT.FILL, SWT.CENTER, false, false);
			configMyRoomGroup.setLayoutData(gridData);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 0;
			gridLayout.verticalSpacing = 5;
			gridLayout.marginWidth = 4;
			gridLayout.marginHeight = 5;
			configMyRoomGroup.setLayout(gridLayout);

			configMyRoomAllowEmptyMasterNameCheck = new Button(configMyRoomGroup, SWT.CHECK | SWT.FLAT);
			configMyRoomAllowEmptyMasterNameCheck.setText("アドレスの部屋主名を省略でもログインできるようにする");
			configMyRoomAllowEmptyMasterNameCheck.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));

			logTab = new TabItem(mainTabFolder, SWT.NONE);
			logTab.setText("ログ");

			logText = new Text(mainTabFolder, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL);
			logTab.setControl(logText);
			logText.setBackground(colorWhite);

			statusBarContainer = new Composite(shell, SWT.NONE);
			statusBarContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
			statusBarContainer.setLayout(new FormLayout());

			statusRoomServerAddressLabel = new Label(statusBarContainer, SWT.BORDER);
			formData = new FormData();
			formData.left = new FormAttachment(0, 2);
			statusRoomServerAddressLabel.setLayoutData(formData);

			statusTunnelConnectionLabel = new Label(statusBarContainer, SWT.BORDER);
			statusTunnelConnectionLabel.setForeground(colorRed);
			statusTunnelConnectionLabel.setText(" UDPトンネル未接続 ");
			formData = new FormData();
			formData.left = new FormAttachment(statusRoomServerAddressLabel, 5);
			statusTunnelConnectionLabel.setLayoutData(formData);

			statusPortalServerLabel = new Label(statusBarContainer, SWT.BORDER);
			statusPortalServerLabel.setText("ポータルサーバーにログインしていません");
			formData = new FormData();
			formData.left = new FormAttachment(statusTunnelConnectionLabel, 5);
			statusPortalServerLabel.setLayoutData(formData);

			statusSearchResultLabel = new Label(statusBarContainer, SWT.BORDER);
			statusSearchResultLabel.setText("検索結果: なし");
			formData = new FormData();
			formData.left = new FormAttachment(statusPortalServerLabel, 5);
			statusSearchResultLabel.setLayoutData(formData);

			statusTraficStatusLabel = new Label(statusBarContainer, SWT.BORDER);
			statusTraficStatusLabel.setText("トラフィック");
			formData = new FormData();
			formData.right = new FormAttachment(100, -20);
			statusTraficStatusLabel.setLayoutData(formData);

			roomSashForm.setWeights(new int[] { 4, 7, 4 });
			roomCenterSashForm.setWeights(new int[] { 3, 5 });
			roomRightSashForm.setWeights(new int[] { 2, 3 });
		}
	}

	private void initializeComponentListeners() {
		shell.addShellListener(new ShellListener() {
			@Override
			public void shellActivated(ShellEvent e) {
				window.isActive = true;
			}

			@Override
			public void shellIconified(ShellEvent e) {
				window.isActive = false;
			}

			@Override
			public void shellDeiconified(ShellEvent e) {
				window.isActive = true;
			}

			@Override
			public void shellDeactivated(ShellEvent e) {
				window.isActive = false;
			}

			@Override
			public void shellClosed(ShellEvent e) {
				if (!window.configAppCloseConfirmCheck.getSelection()) {
					return;
				}
				ConfirmDialog dialog = new ConfirmDialog(shell);
				switch (dialog.open()) {
				case IDialogConstants.CANCEL_ID:
					e.doit = false;
					break;
				}
			}
		});
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				myRoomEngine.closeRoom();

				tcpClient.dispose();
				udpClient.dispose();

				isPacketCapturing = false;

				iniSettingSection.set(IniConstants.Client.LOGIN_NAME, window.configUserNameText.getText());

				Point size = shell.getSize();
				iniSettingSection.set(IniConstants.Client.WINDOW_WIDTH, Integer.toString(size.x));
				iniSettingSection.set(IniConstants.Client.WINDOW_HEIGHT, Integer.toString(size.y));

				iniSettingSection.set(IniConstants.Client.MY_ROOM_HOST_NAME, window.roomFormMyRoomModeHostText.getText());
				iniSettingSection.set(IniConstants.Client.MY_ROOM_PORT,
						Integer.toString(window.roomFormMyRoomModePortSpinner.getSelection()));
				iniSettingSection.set(IniConstants.Client.MY_ROOM_ALLOW_NO_MASTER_NAME,
						window.configMyRoomAllowEmptyMasterNameCheck.getSelection());
				iniSettingSection.set(IniConstants.Client.APP_CLOSE_CONFIRM, window.configAppCloseConfirmCheck.getSelection());
				iniSettingSection.set(IniConstants.Client.ENABLE_BALLOON, window.configEnableBalloonCheck.getSelection());

				iniSettingSection.set(IniConstants.Client.PORTAL_SERVER_HISTORY, portalServerHistoryManager.makeCSV());
				iniSettingSection.set(IniConstants.Client.ROOM_ADDRESS_HISTORY, roomAddressHistoryManager.makeCSV());
				iniSettingSection.set(IniConstants.Client.ROOM_SERVER_HISTORY, roomServerHistoryManager.makeCSV());

				int index = window.wlanAdapterListCombo.getSelectionIndex() - 1;
				if (window.wlanAdapterListCombo.getItemCount() < 2 || index == -1) {
					iniSettingSection.set(IniConstants.Client.LAST_LAN_ADAPTER, "");
				} else {
					WlanDevice device = wlanAdaptorList.get(index);
					iniSettingSection.set(IniConstants.Client.LAST_LAN_ADAPTER, wlanAdaptorMacAddressMap.get(device));
				}

				try {
					iniParser.saveToIni();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});

		window.mainTabFolder.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				TabItem item = window.mainTabFolder.getItem(window.mainTabFolder.getSelectionIndex());
				isPortalTabSelected = item == window.portalTab;
			}
		});

		window.portalServerLoginButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (portalConnection == null || !portalConnection.isConnected()) {
					connectToPortalServer();
				} else {
					portalConnection.send(ProtocolConstants.Portal.COMMAND_LOGOUT);
				}
			}
		});

		window.portalSearchFormAutoQuery.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				isPortalAutoQueryEnabled = window.portalSearchFormAutoQuery.getSelection();
				window.portalSearchFormAutoQuery.setText(isPortalAutoQueryEnabled ? "自動更新オン" : "自動更新オフ");
			}
		});

		window.portalRoomSearchResultTable.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent e) {
				if (currentRoomState != RoomState.Offline)
					return;
				IStructuredSelection sel = (IStructuredSelection) e.getSelection();
				PlayRoom room = (PlayRoom) sel.getFirstElement();
				if (room == null)
					return;

				window.roomFormParticipantModeAddressCombo.setText(room.getRoomAddress());
				connectToRoomServerAsParticipant();
			}
		});

		window.roomFormModeSelectionCombo.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				updateRoomModeSelection();
			}
		});

		window.roomFormMyRoomModeStartButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				try {
					if (myRoomEngine.isStarted()) {
						window.roomFormMyRoomModeStartButton.setEnabled(false);
						myRoomEngine.closeRoom();
					} else {
						startMyRoomServer();
					}
				} catch (IOException e) {
					appendLogTo(window.logText, Utility.makeStackTrace(e));
				}
			}
		});
		window.roomFormParticipantModeAddressCombo.addKeyListener(new KeyListener() {
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
		window.roomFormParticipantModeLoginButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (currentRoomState == RoomState.Offline) {
					connectToRoomServerAsParticipant();
				} else {
					roomConnection.send(ProtocolConstants.Room.COMMAND_LOGOUT);
					tunnelConnection.disconnect();
				}
			}
		});

		window.roomFormMasterModeAddressCombo.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.character) {
				case SWT.CR:
				case SWT.LF:
					e.doit = false;
					connectToRoomServerAsMaster();
					break;
				}
			}
		});
		window.roomFormMasterModeLoginButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (currentRoomState == RoomState.Offline) {
					connectToRoomServerAsMaster();
				} else {
					roomConnection.send(ProtocolConstants.Room.COMMAND_LOGOUT);
					tunnelConnection.disconnect();
				}
			}
		});

		window.roomFormMasterModeEnterLobbyCheck.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				isExitOnLobbyCkecked = window.roomFormMasterModeEnterLobbyCheck.getSelection();
				window.roomFormParticipantModeEnterLobbyCheck.setSelection(isExitOnLobbyCkecked);
			}
		});
		window.roomFormParticipantModeEnterLobbyCheck.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				isExitOnLobbyCkecked = window.roomFormParticipantModeEnterLobbyCheck.getSelection();
				window.roomFormMasterModeEnterLobbyCheck.setSelection(isExitOnLobbyCkecked);
			}
		});

		window.roomChatSubmitText.addKeyListener(new KeyListener() {
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
				int index = window.wlanAdapterListCombo.getSelectionIndex();
				int separatorIndex = wlanAdaptorList.size() + 1;
				int refreshIndex = separatorIndex + 1;
				if (index == 0) {
					window.wlanPspCommunicationButton.setEnabled(false);
				} else if (index < separatorIndex) {
					window.wlanPspCommunicationButton.setEnabled(true);
				} else if (index == separatorIndex) {
					window.wlanAdapterListCombo.select(0);
					window.wlanPspCommunicationButton.setEnabled(false);
				} else if (index == refreshIndex) {
					refreshLanAdapterList();
				}
			}
		});
		window.wlanPspCommunicationButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (window.wlanPspCommunicationButton.getSelection()) {
					if (startPacketCapturing()) {
						window.wlanPspCommunicationButton.setText("PSPと通信中");
						window.wlanAdapterListCombo.setEnabled(false);

						if (Wlan.isLibraryAvailable) {
							window.ssidStartScan.setEnabled(true);
						}
					} else {
						window.wlanPspCommunicationButton.setSelection(false);
					}
				} else {
					window.wlanPspCommunicationButton.setEnabled(false);
					isPacketCapturing = false;

					if (Wlan.isLibraryAvailable) {
						updateSsidStartScan(false);
						window.ssidStartScan.setEnabled(false);

						setAndSendInformNewSSID("");
						nextSsidCheckTime = 0L;
					}
				}
			}
		});

		window.ssidScanIntervalSpinner.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				scanIntervalMillis = window.ssidScanIntervalSpinner.getSelection();
			}
		});

		window.ssidStartScan.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (!Wlan.isLibraryAvailable || currentWlanDevice == null) {
					updateSsidStartScan(false);
				} else {
					updateSsidStartScan(window.ssidStartScan.getSelection());
				}
			}
		});

		window.ssidListTableViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent e) {
				if (!Wlan.isLibraryAvailable || currentWlanDevice == null) {
					return;
				}
				IStructuredSelection sel = (IStructuredSelection) e.getSelection();
				WlanNetwork network = (WlanNetwork) sel.getFirstElement();
				if (network == null)
					return;

				String selectedSSID = network.getSsid();
				String currentSSID = window.ssidCurrentSsidText.getText();
				if (currentSSID.equals(selectedSSID))
					return;

				changeSSID(selectedSSID);
			}
		});

		window.roomFormEditButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				commitRoomEditForm();
			}
		});

		window.roomFormMyRoomModeEntryButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (myRoomEntryConnection != null && myRoomEntryConnection.isConnected()) {
					myRoomEntryConnection.send(ProtocolConstants.MyRoom.COMMAND_LOGOUT);
				} else {
					connectToRoomServerAsMyRoom();
				}
			}
		});
		window.roomFormMyRoomModeEntryCombo.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.character) {
				case SWT.CR:
				case SWT.LF:
					e.doit = false;
					connectToRoomServerAsMyRoom();
					break;
				}
			}
		});

		window.roomFormMyRoomModeHostText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				switch (currentRoomState) {
				case MyRoomMaster:
					roomServerAddressPort = window.roomFormMyRoomModeHostText.getText() + ":"
							+ window.roomFormMyRoomModePortSpinner.getSelection();
					updateServerAddress();
				}
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

				if (window.trayItem != null) {
					window.trayItem.setText(shell.getText());
					window.trayItem.setToolTipText(shell.getText());
				}
			}
		});

		window.configMyRoomAllowEmptyMasterNameCheck.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				myRoomEngine.setAllowEmptyMasterNameLogin(window.configMyRoomAllowEmptyMasterNameCheck.getSelection());
			}
		});

		VerifyListener notAcceptControlCharListener = new VerifyListener() {
			@Override
			public void verifyText(VerifyEvent e) {
				switch (e.character) {
				case '\t':
					e.doit = false;
					break;
				case '\0':
					e.text = e.text.replace("\t", "").trim();
					break;
				}
			}
		};
		window.roomFormTitleText.addVerifyListener(notAcceptControlCharListener);
		window.roomFormPasswordText.addVerifyListener(notAcceptControlCharListener);
		window.roomFormDescriptionText.addVerifyListener(notAcceptControlCharListener);

		window.configUserNameText.addVerifyListener(notAcceptControlCharListener);
		window.portalSearchFormTitleCombo.addVerifyListener(notAcceptControlCharListener);
		window.portalSearchFormMasterNameCombo.addVerifyListener(notAcceptControlCharListener);

		VerifyListener notAcceptSpaceControlCharListener = new VerifyListener() {
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
						e.text = e.text.replace("\t", "").trim();
						break;
					}
				}
			}
		};
		window.roomFormParticipantModeAddressCombo.addVerifyListener(notAcceptSpaceControlCharListener);
		window.roomFormMasterModeAddressCombo.addVerifyListener(notAcceptSpaceControlCharListener);
		window.roomFormMyRoomModeHostText.addVerifyListener(notAcceptSpaceControlCharListener);

		window.portalServerAddressCombo.addVerifyListener(notAcceptSpaceControlCharListener);
		window.portalSearchFormServerNameCombo.addVerifyListener(notAcceptSpaceControlCharListener);

		ModifyListener roomEditFormModifyDetectListener = new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (isRoomInfoUpdating)
					return;

				switch (currentRoomState) {
				case MyRoomMaster:
				case RoomMaster:
					window.roomFormEditButton.setEnabled(true);
					break;
				}
			}
		};
		window.roomFormTitleText.addModifyListener(roomEditFormModifyDetectListener);
		window.roomFormPasswordText.addModifyListener(roomEditFormModifyDetectListener);
		window.roomFormDescriptionText.addModifyListener(roomEditFormModifyDetectListener);
		window.roomFormMaxPlayersSpiner.addModifyListener(roomEditFormModifyDetectListener);

		window.portalRoomServerMenu = new Menu(shell, SWT.POP_UP);

		window.portalRoomServerSetAddress = new MenuItem(window.portalRoomServerMenu, SWT.PUSH);
		window.portalRoomServerSetAddress.setText("このサーバーで部屋を作成");
		window.portalRoomServerSetAddress.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (currentRoomState != RoomState.Offline) {
					return;
				}
				IStructuredSelection selection = (IStructuredSelection) window.portalRoomServerTableViewer.getSelection();
				RoomServerData server = (RoomServerData) selection.getFirstElement();
				if (server == null)
					return;

				window.roomFormMasterModeAddressCombo.setText(server.address);
				window.roomFormModeSelectionCombo.select(0);
				updateRoomModeSelection();
				window.mainTabFolder.setSelection(window.playRoomTab);
			}
		});

		window.portalRoomServerSetAddress4MyRoom = new MenuItem(window.portalRoomServerMenu, SWT.PUSH);
		window.portalRoomServerSetAddress4MyRoom.setText("マイルームを登録するサーバーに設定");
		window.portalRoomServerSetAddress4MyRoom.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				IStructuredSelection selection = (IStructuredSelection) window.portalRoomServerTableViewer.getSelection();
				RoomServerData server = (RoomServerData) selection.getFirstElement();
				if (server == null)
					return;

				if (currentRoomState == RoomState.Offline || currentRoomState == RoomState.MyRoomMaster) {
					window.roomFormMyRoomModeEntryCombo.setText(server.address);
					window.roomFormModeSelectionCombo.select(2);
					updateRoomModeSelection();
					window.mainTabFolder.setSelection(window.playRoomTab);
				} else if (currentRoomState == RoomState.MyRoomMaster) {
					window.roomFormMyRoomModeEntryCombo.setText(server.address);
					window.mainTabFolder.setSelection(window.playRoomTab);
				}
			}
		});

		window.portalRoomServerTableViewer.getTable().setMenu(window.portalRoomServerMenu);
		window.portalRoomServerTableViewer.getTable().addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				IStructuredSelection selection = (IStructuredSelection) window.portalRoomServerTableViewer.getSelection();
				RoomServerData server = (RoomServerData) selection.getFirstElement();

				switch (currentRoomState) {
				case Offline:
					window.portalRoomServerSetAddress.setEnabled(server != null);
					window.portalRoomServerSetAddress4MyRoom.setEnabled(server != null);
					break;
				case MyRoomMaster:
					window.portalRoomServerSetAddress.setEnabled(false);
					if (myRoomEntryConnection != null && myRoomEntryConnection.isConnected()) {
						window.portalRoomServerSetAddress4MyRoom.setEnabled(false);
					} else {
						window.portalRoomServerSetAddress4MyRoom.setEnabled(server != null);
					}
					break;
				default:
					window.portalRoomServerSetAddress.setEnabled(false);
					window.portalRoomServerSetAddress4MyRoom.setEnabled(false);
				}
			}
		});

		window.roomPlayerMenu = new Menu(shell, SWT.POP_UP);

		window.roomPlayerChaseSsidMenuItem = new MenuItem(window.roomPlayerMenu, SWT.CHECK);
		window.roomPlayerChaseSsidMenuItem.setText("このプレイヤーのSSIDを追跡");
		window.roomPlayerChaseSsidMenuItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListTableViewer.getSelection();
				Player player = (Player) selection.getFirstElement();
				if (player == null)
					return;

				player.setSSIDChased(window.roomPlayerChaseSsidMenuItem.getSelection());
				window.roomPlayerListTableViewer.refresh(player);
			}
		});

		window.roomPlayerSetSsidMenuItem = new MenuItem(window.roomPlayerMenu, SWT.PUSH);
		window.roomPlayerSetSsidMenuItem.setText("このSSIDに設定");
		window.roomPlayerSetSsidMenuItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListTableViewer.getSelection();
				Player player = (Player) selection.getFirstElement();
				if (player == null)
					return;

				changeSSID(player.getSsid());
			}
		});

		window.roomPlayerCopySsidMenuItem = new MenuItem(window.roomPlayerMenu, SWT.PUSH);
		window.roomPlayerCopySsidMenuItem.setText("このSSIDをコピー");
		window.roomPlayerCopySsidMenuItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListTableViewer.getSelection();
				Player player = (Player) selection.getFirstElement();
				if (player == null)
					return;

				window.clipboard.setContents(new Object[] { player.getSsid() }, window.textTransfers);
			}
		});

		new MenuItem(window.roomPlayerMenu, SWT.SEPARATOR);

		window.roomPlayerKickMenuItem = new MenuItem(window.roomPlayerMenu, SWT.PUSH);
		window.roomPlayerKickMenuItem.setText("キック");
		window.roomPlayerKickMenuItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListTableViewer.getSelection();
				Player player = (Player) selection.getFirstElement();
				if (player == null || loginUserName.equals(player.getName()))
					return;

				String kickedName = player.getName();
				switch (currentRoomState) {
				case MyRoomMaster:
					myRoomEngine.kickPlayer(kickedName);
					removeKickedPlayer(kickedName);
					break;
				case RoomMaster:
					roomConnection
							.send(ProtocolConstants.Room.COMMAND_ROOM_KICK_PLAYER + ProtocolConstants.ARGUMENT_SEPARATOR + kickedName);
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
				IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListTableViewer.getSelection();
				Player player = (Player) selection.getFirstElement();
				if (player == null)
					return;

				String newMasterName = player.getName();
				switch (currentRoomState) {
				case RoomMaster:
					roomConnection.send(ProtocolConstants.Room.COMMAND_ROOM_MASTER_TRANSFER + ProtocolConstants.ARGUMENT_SEPARATOR
							+ newMasterName);
					if (myRoomEntryConnection != null && myRoomEntryConnection.isConnected())
						myRoomEntryConnection.send(ProtocolConstants.Portal.COMMAND_LOGOUT);
					break;
				}
			}
		});

		window.roomPlayerListTableViewer.getTable().setMenu(window.roomPlayerMenu);
		window.roomPlayerListTableViewer.getTable().addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListTableViewer.getSelection();
				Player player = (Player) selection.getFirstElement();

				if (player == null) {
					window.roomPlayerKickMenuItem.setEnabled(false);
					window.roomPlayerMasterTransferMenuItem.setEnabled(false);

					window.roomPlayerChaseSsidMenuItem.setSelection(false);
					window.roomPlayerChaseSsidMenuItem.setEnabled(false);
					window.roomPlayerSetSsidMenuItem.setEnabled(false);
					window.roomPlayerCopySsidMenuItem.setEnabled(false);
					return;
				}

				boolean isMasterAndOtherSelected = false;
				switch (currentRoomState) {
				case MyRoomMaster:
				case RoomMaster:
					if (!roomMasterName.equals(player.getName())) {
						isMasterAndOtherSelected = true;
					}
					break;
				}
				window.roomPlayerKickMenuItem.setEnabled(isMasterAndOtherSelected);
				if (currentRoomState == RoomState.RoomMaster) {
					window.roomPlayerMasterTransferMenuItem.setEnabled(isMasterAndOtherSelected);
				} else {
					window.roomPlayerMasterTransferMenuItem.setEnabled(false);
				}

				boolean isSelfSelected = Utility.equals(loginUserName, player.getName());

				if (isSelfSelected || !isPacketCapturing) {
					window.roomPlayerChaseSsidMenuItem.setEnabled(false);
					window.roomPlayerChaseSsidMenuItem.setSelection(false);
				} else {
					window.roomPlayerChaseSsidMenuItem.setEnabled(Wlan.isLibraryAvailable);
					window.roomPlayerChaseSsidMenuItem.setSelection(player.isSSIDChased());
				}

				if (Utility.isEmpty(player.getSsid())) {
					window.roomPlayerSetSsidMenuItem.setEnabled(false);
					window.roomPlayerCopySsidMenuItem.setEnabled(false);
				} else {
					window.roomPlayerSetSsidMenuItem.setEnabled(Wlan.isLibraryAvailable && !isSelfSelected && isPacketCapturing);
					window.roomPlayerCopySsidMenuItem.setEnabled(true);
				}
			}
		});

		window.statusServerAddressMenu = new Menu(shell, SWT.POP_UP);

		window.statusServerAddressCopy = new MenuItem(window.statusServerAddressMenu, SWT.PUSH);
		window.statusServerAddressCopy.setText("アドレスをコピー");
		window.statusServerAddressCopy.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				String roomAddress = roomServerAddressPort + ":" + roomMasterName;
				window.clipboard.setContents(new Object[] { roomAddress }, window.textTransfers);
			}
		});

		window.statusRoomServerAddressLabel.setMenu(window.statusServerAddressMenu);
		window.statusRoomServerAddressLabel.addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				switch (currentRoomState) {
				case MyRoomMaster:
				case RoomParticipant:
				case RoomMaster:
					window.statusServerAddressCopy.setEnabled(true);
					break;
				default:
					window.statusServerAddressCopy.setEnabled(false);
				}
			}
		});

		window.packetMonitorMenu = new Menu(shell, SWT.POP_UP);

		window.packetMonitorMenuCopy = new MenuItem(window.packetMonitorMenu, SWT.PUSH);
		window.packetMonitorMenuCopy.setText("MACアドレスとユーザー名をコピー");
		window.packetMonitorMenuCopy.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				IStructuredSelection selection = (IStructuredSelection) window.packetMonitorTable.getSelection();
				TraficStatistics stats = (TraficStatistics) selection.getFirstElement();
				if (stats == null)
					return;

				window.clipboard.setContents(new Object[] { stats.macAddress + "\t" + stats.playerName }, window.textTransfers);
			}
		});

		window.packetMonitorMenuClear = new MenuItem(window.packetMonitorMenu, SWT.PUSH);
		window.packetMonitorMenuClear.setText("累積バイトをクリア");
		window.packetMonitorMenuClear.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				synchronized (traficStatsMap) {
					Iterator<Entry<String, TraficStatistics>> iter = traficStatsMap.entrySet().iterator();
					while (iter.hasNext()) {
						Entry<String, TraficStatistics> entry = iter.next();
						TraficStatistics stats = entry.getValue();

						stats.totalInBytes = 0;
						stats.totalOutBytes = 0;
					}
				}
				window.packetMonitorTable.refresh();
			}
		});

		window.packetMonitorTable.getTable().setMenu(window.packetMonitorMenu);
		window.packetMonitorTable.getTable().addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				window.packetMonitorMenuClear.setEnabled(traficStatsMap.size() > 0);

				IStructuredSelection selection = (IStructuredSelection) window.packetMonitorTable.getSelection();
				TraficStatistics stats = (TraficStatistics) selection.getFirstElement();
				window.packetMonitorMenuCopy.setEnabled(stats != null);
			}
		});

		if (window.trayItem != null)
			window.trayItem.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					shell.setActive();
				}
			});
	}

	private void initializeBackgroundThreads() {
		packetCaptureThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Runnable prepareCaptureEndAction = new Runnable() {
					@Override
					public void run() {
						try {
							isPacketCapturing = false;
							window.wlanPspCommunicationButton.setEnabled(false);
						} catch (SWTException e) {
						}
					}
				};
				Runnable captureEndAction = new Runnable() {
					@Override
					public void run() {
						try {
							window.wlanAdapterListCombo.setEnabled(true);
							window.wlanPspCommunicationButton.setText("PSPと通信開始");
							window.wlanPspCommunicationButton.setEnabled(true);

							if (Wlan.isLibraryAvailable)
								updateSsidStartScan(false);
						} catch (SWTException e) {
						}
					}
				};

				try {
					while (!shell.isDisposed()) {
						synchronized (packetCaptureThread) {
							if (!isPacketCapturing)
								packetCaptureThread.wait();
						}

						try {
							while (isPacketCapturing) {
								bufferForCapturing.clear();
								int ret = currentWlanDevice.capturePacket(bufferForCapturing);
								if (ret > 0) {
									// bufferForCapturing.position(ret);
									bufferForCapturing.flip();
									processCapturedPacket();
								} else if (ret == 0) {
								} else {
									display.syncExec(prepareCaptureEndAction);
									break;
								}
							}
						} catch (Exception e) {
							appendLogTo(window.logText, Utility.makeStackTrace(e));
							isPacketCapturing = false;
						}

						currentWlanDevice.close();
						currentWlanDevice = Wlan.EMPTY_DEVICE;

						display.syncExec(captureEndAction);
					}
				} catch (SWTException e) {
				} catch (Exception e) {
					appendLogTo(window.logText, Utility.makeStackTrace(e));
				}
			}
		}, "PacketCaptureThread");
		packetCaptureThread.setDaemon(true);

		wlanScannerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				final List<WlanNetwork> networkList = new ArrayList<WlanNetwork>();

				Runnable refreshAction = new Runnable() {
					@Override
					public void run() {
						try {
							if (window.ssidAutoDetectCheck.getSelection()) {
								String currentSSID = window.ssidCurrentSsidText.getText();
								String match = window.ssidMatchText.getText();
								for (WlanNetwork bssid : networkList) {
									String ssid = bssid.getSsid();

									if (!ssid.equals(currentSSID) && ssid.startsWith(match)) {
										changeSSID(ssid);
										break;
									}
								}
							} else {
								checkSsidChange();
							}
							window.ssidListTableViewer.setInput(networkList);
							window.ssidListTableViewer.refresh();
						} catch (SWTException e) {
						}
					}
				};
				Runnable clearAction = new Runnable() {
					@Override
					public void run() {
						try {
							networkList.clear();
							window.ssidListTableViewer.setInput(networkList);
							window.ssidListTableViewer.refresh();
						} catch (SWTException e) {
						}
					}
				};

				try {
					while (!shell.isDisposed()) {
						synchronized (wlanScannerThread) {
							while (!isSSIDScaning)
								wlanScannerThread.wait();
						}

						while (isSSIDScaning) {
							long nextIteration = System.currentTimeMillis() + scanIntervalMillis;

							networkList.clear();
							currentWlanDevice.findNetworks(networkList);
							display.syncExec(refreshAction);

							currentWlanDevice.scanNetwork();

							long diff = nextIteration - System.currentTimeMillis();
							if (diff > 0)
								Thread.sleep(diff);
						}

						display.asyncExec(clearAction);
					}
				} catch (SWTException e) {
				} catch (InterruptedException e) {
				}
			}
		}, "WlanScannerThread");
		wlanScannerThread.setDaemon(true);

		packetMonitorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				int intervalMillis = 1000;

				Runnable refreshAction = new Runnable() {
					@Override
					public void run() {
						try {
							checkSsidChange();

							window.packetMonitorTable.setInput(traficStatsMap);
							window.packetMonitorTable.refresh();
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
							window.packetMonitorTable.setInput(traficStatsMap);
						} catch (SWTException e) {
						}
					}
				};

				try {
					while (!shell.isDisposed()) {
						synchronized (packetMonitorThread) {
							if (!isPacketCapturing && !tunnelIsLinked)
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

								String text;
								if (actualSentBytes == 0 && actualRecievedBytes == 0) {
									text = " トラフィックはありません ";
								} else {
									double totalInKbps = ((double) actualRecievedBytes) * 8 / intervalMillis;
									double totalOutKbps = ((double) actualSentBytes) * 8 / intervalMillis;
									text = String.format(" In: %.1f Kbps   Out: %.1f Kbps ", totalInKbps, totalOutKbps);

									actualSentBytes = 0;
									actualRecievedBytes = 0;
								}
								updateTraficStatus(text);
							}

							display.syncExec(refreshAction);

							Thread.sleep(intervalMillis);
						}

						display.syncExec(clearAction);
					}
				} catch (SWTException e) {
				} catch (InterruptedException e) {
				}
			}
		}, "PacketMonitorThread");
		packetMonitorThread.setDaemon(true);

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
							switch (currentRoomState) {
							case RoomMaster:
							case RoomParticipant:
								roomConnection.send(ProtocolConstants.Room.COMMAND_PING + ProtocolConstants.ARGUMENT_SEPARATOR
										+ System.currentTimeMillis());
							}

							Thread.sleep(5000);
						}
					}
				} catch (InterruptedException e) {
				}
			}
		}, "PingThread");
		pingThread.setDaemon(true);

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
							tunnelConnection.send(ProtocolConstants.Room.TUNNEL_DUMMY_PACKET);
							Thread.sleep(20000);
						}
					}
				} catch (InterruptedException e) {
				}
			}
		}, "NatTableMaintaining");
		natTableMaintainingThread.setDaemon(true);

		portalSearchQueryThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Runnable sendAction = new Runnable() {
					StringBuilder sb = new StringBuilder();

					@Override
					public void run() {
						if (portalConnection == null)
							return;

						try {
							roomSearchResultList.clear();
							sb.delete(0, sb.length());

							sb.append(ProtocolConstants.Portal.COMMAND_SEARCH);
							sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
							sb.append(window.portalSearchFormTitleCombo.getText());
							sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
							sb.append(window.portalSearchFormMasterNameCombo.getText());
							sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
							sb.append(window.portalSearchFormServerNameCombo.getText());
							sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
							sb.append(window.portalSearchFormHasPassword.getSelection() ? "Y" : "N");
							sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
							sb.append(window.portalSearchFormTitleNgCombo.getText());
							sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
							sb.append(window.portalSearchFormMasterNameNgCombo.getText());
							sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
							sb.append(window.portalSearchFormServerNameNgCombo.getText());

							portalConnection.send(sb.toString());
						} catch (SWTException e) {
						}
					}
				};
				Runnable refreshSearchResultAction = new Runnable() {
					SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
					Date now = new Date();

					@Override
					public void run() {
						try {
							window.portalRoomSearchResultTable.setInput(roomSearchResultList);
							window.portalRoomSearchResultTable.refresh();

							now.setTime(System.currentTimeMillis());

							String message = "検索結果: " + roomSearchResultList.size() + "件 (" + dateFormat.format(now) + ")";
							window.statusSearchResultLabel.setText(message);
							window.statusSearchResultLabel.getParent().layout();

							portalServerHistoryManager.addCurrentItem();

							queryRoomTitleHistoryManager.addCurrentItem();
							queryRoomTitleNgHistoryManager.addCurrentItem();
							queryRoomMasterNameHistoryManager.addCurrentItem();
							queryRoomMasterNameNgHistoryManager.addCurrentItem();
							queryRoomAddressHistoryManager.addCurrentItem();
							queryRoomAddressNgHistoryManager.addCurrentItem();
						} catch (SWTException e) {
						}
					}
				};
				try {
					while (!shell.isDisposed()) {
						synchronized (portalSearchQueryThread) {
							if (portalConnection == null || !portalConnection.isConnected()) {
								portalSearchQueryThread.wait();
							}
						}

						if (isPortalTabSelected && isPortalAutoQueryEnabled) {
							display.syncExec(sendAction);

							synchronized (roomSearchResultList) {
								roomSearchResultList.wait();
							}

							display.syncExec(refreshSearchResultAction);
						}

						Thread.sleep(3000);
					}
				} catch (InterruptedException e) {
				} catch (SWTException e) {
				}
			}
		}, "PortalSearchQueryThread");
		portalSearchQueryThread.setDaemon(true);
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

	private boolean isNotSwtUIThread() {
		return Thread.currentThread() != display.getThread();
	}

	private void checkSsidChange() {
		if (System.currentTimeMillis() < nextSsidCheckTime)
			return;
		String latestSSID = currentWlanDevice.getSSID();
		if (latestSSID == null)
			latestSSID = "";

		String currentSSID = window.ssidCurrentSsidText.getText();
		if (!latestSSID.equals(currentSSID))
			setAndSendInformNewSSID(latestSSID);

		nextSsidCheckTime = System.currentTimeMillis() + 3000;
	}

	private void changeSSID(String newSSID) {
		if (!Utility.isEmpty(newSSID))
			currentWlanDevice.setSSID(newSSID);
		setAndSendInformNewSSID(newSSID);
		updateSsidStartScan(false);

		nextSsidCheckTime += System.currentTimeMillis() + 10000;
	}

	private void setAndSendInformNewSSID(String latestSSID) {
		window.ssidCurrentSsidText.setText(latestSSID);
		updatePlayerSSID(loginUserName, latestSSID);

		switch (currentRoomState) {
		case MyRoomMaster:
			myRoomEngine.informSSID(latestSSID);
			break;
		case RoomMaster:
		case RoomParticipant:
			if (roomConnection != null)
				roomConnection.send(ProtocolConstants.Room.COMMAND_INFORM_SSID + ProtocolConstants.ARGUMENT_SEPARATOR + latestSSID);
			break;
		}
	}

	private void updateRoomModeSelection() {
		switch (window.roomFormModeSelectionCombo.getSelectionIndex()) {
		case 0:
			window.roomModeStackLayout.topControl = window.roomFormMasterModeContainer;
			setEnableRoomFormItems(true);
			break;
		case 1:
			window.roomModeStackLayout.topControl = window.roomFormParticipantModeContainer;
			setEnableRoomFormItems(false);
			break;
		case 2:
			window.roomModeStackLayout.topControl = window.roomFormMyRoomModeContainer;
			setEnableRoomFormItems(true);
			break;
		}
		// window.roomFormControlGroup.layout(true, true);
		window.roomFormModeSwitchContainer.layout();
	}

	private void appendRoomInfo(StringBuilder sb) {
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(window.roomFormMaxPlayersSpiner.getText());
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(window.roomFormTitleText.getText());
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(window.roomFormPasswordText.getText());
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(window.roomFormDescriptionText.getText());
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

	private void startMyRoomServer() throws IOException {
		int port = window.roomFormMyRoomModePortSpinner.getSelection();

		if (!checkConfigUserName())
			return;

		String title = window.roomFormTitleText.getText();
		if (Utility.isEmpty(title)) {
			appendLogTo(window.roomChatLogText, "部屋名を入力してください", window.colorRoomInfo, false);
			window.roomFormTitleText.setFocus();
			return;
		}

		myRoomEngine.setTitle(title);
		myRoomEngine.setMaxPlayers(window.roomFormMaxPlayersSpiner.getSelection());
		myRoomEngine.setPassword(window.roomFormPasswordText.getText());
		myRoomEngine.setDescription(window.roomFormDescriptionText.getText());

		try {
			myRoomEngine.openRoom(port, loginUserName);

			window.roomFormMasterText.setText(loginUserName);
			roomMasterName = loginUserName;
			roomServerAddressPort = window.roomFormMyRoomModeHostText.getText() + ":" + port;

			window.roomFormModeSelectionCombo.setEnabled(false);
			window.roomFormMyRoomModePortSpinner.setEnabled(false);
			window.roomFormMyRoomModeStartButton.setEnabled(false);
			return;
		} catch (BindException e) {
			appendLogTo(window.roomChatLogText, "すでに同じポートが使用されています", window.colorLogError, false);
		} catch (RuntimeException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
		}
	}

	private void connectToRoomServerAsMaster() {
		if (!checkConfigUserName())
			return;

		String title = window.roomFormTitleText.getText();
		if (Utility.isEmpty(title)) {
			appendLogTo(window.roomChatLogText, "部屋名を入力してください", window.colorRoomInfo, false);
			window.roomFormTitleText.setFocus();
			return;
		}

		String address = window.roomFormMasterModeAddressCombo.getText();
		if (Utility.isEmpty(address)) {
			appendLogTo(window.roomChatLogText, "サーバーアドレスを入力してください", window.colorLogError, false);
			return;
		}

		String[] tokens = address.split(":");

		switch (tokens.length) {
		case 2:
			break;
		default:
			appendLogTo(window.roomChatLogText, "サーバーアドレスが正しくありません", window.colorLogError, false);
			return;
		}

		int port;
		try {
			port = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			appendLogTo(window.roomChatLogText, "サーバーアドレスが正しくありません", window.colorLogError, false);
			return;
		}

		try {
			String hostname = tokens[0];
			InetSocketAddress socketAddress = new InetSocketAddress(hostname, port);
			if (socketAddress.getAddress().isLoopbackAddress()) {
				hostname = "";
				roomServerAddressPort = ":" + port;
				window.roomFormMasterModeAddressCombo.setText(roomServerAddressPort);
			} else {
				roomServerAddressPort = address;
			}
			roomConnection = tcpClient.connect(socketAddress, roomClientHandler);
			roomMasterName = loginUserName;
			goTo(RoomState.ConnectingAsRoomMaster);
			return;
		} catch (UnresolvedAddressException e) {
			appendLogTo(window.roomChatLogText, "アドレスが解決しません", window.colorRed, false);
		} catch (RuntimeException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
		}
	}

	private void connectToRoomServerAsParticipant() {
		if (!checkConfigUserName())
			return;

		String address = window.roomFormParticipantModeAddressCombo.getText();
		if (Utility.isEmpty(address)) {
			appendLogTo(window.roomChatLogText, "サーバーアドレスを入力してください", window.colorLogError, false);
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
			appendLogTo(window.roomChatLogText, "サーバーアドレスが正しくありません", window.colorLogError, false);
			return;
		}

		int port;
		try {
			port = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			appendLogTo(window.roomChatLogText, "サーバーアドレスが正しくありません", window.colorLogError, false);
			return;
		}

		try {
			String hostname = tokens[0];
			InetSocketAddress socketAddress = new InetSocketAddress(hostname, port);
			if (socketAddress.getAddress().isLoopbackAddress()) {
				roomServerAddressPort = ":" + port;
				if (roomMasterName.equals("")) {
					window.roomFormParticipantModeAddressCombo.setText(roomServerAddressPort);
				} else {
					window.roomFormParticipantModeAddressCombo.setText(roomServerAddressPort + ":" + roomMasterName);
				}
			} else {
				roomServerAddressPort = hostname + ":" + port;
			}
			roomConnection = tcpClient.connect(socketAddress, roomClientHandler);
			goTo(RoomState.ConnectingAsRoomParticipant);
			return;
		} catch (UnresolvedAddressException e) {
			appendLogTo(window.roomChatLogText, "アドレスが解決しません", window.colorRed, false);
		} catch (RuntimeException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
		}
	}

	private void connectToPortalServer() {
		String address = window.portalServerAddressCombo.getText();
		if (Utility.isEmpty(address)) {
			appendLogTo(window.portalLogText, "サーバーアドレスを入力してください", window.colorLogError, false);
			return;
		}

		String[] tokens = address.split(":");

		switch (tokens.length) {
		case 2:
			break;
		default:
			appendLogTo(window.portalLogText, "サーバーアドレスが正しくありません", window.colorLogError, false);
			return;
		}

		int port;
		try {
			port = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			appendLogTo(window.portalLogText, "サーバーアドレスが正しくありません", window.colorLogError, false);
			return;
		}

		try {
			InetSocketAddress socketAddress = new InetSocketAddress(tokens[0], port);
			portalConnection = tcpClient.connect(socketAddress, portalHandler);

			currentPortalState = PortalState.Connecting;

			window.portalServerLoginButton.setEnabled(false);
			window.portalServerAddressCombo.setEnabled(false);

			roomSearchResultList.clear();
			window.portalRoomSearchResultTable.refresh();
			window.portalRoomSearchResultTable.setSorter(null);
			window.portalRoomSearchResultTable.getTable().setSortDirection(SWT.NONE);
			return;
		} catch (UnresolvedAddressException e) {
			appendLogTo(window.portalLogText, "アドレスが解決しません", window.colorRed, false);
		} catch (RuntimeException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
		}
	}

	private void connectToRoomServerAsMyRoom() {
		if (currentRoomState != RoomState.MyRoomMaster) {
			return;
		}

		if (window.roomFormEditButton.getEnabled()) {
			if (!commitRoomEditForm()) {
				window.roomFormMyRoomModeStartButton.setSelection(false);
				return;
			}
		}
		String address = window.roomFormMyRoomModeEntryCombo.getText();
		if (Utility.isEmpty(address)) {
			appendLogTo(window.roomChatLogText, "サーバーアドレスを入力してください", window.colorLogError, false);
			return;
		}

		String[] tokens = address.split(":");

		switch (tokens.length) {
		case 2:
			break;
		default:
			appendLogTo(window.roomChatLogText, "サーバーアドレスが正しくありません", window.colorLogError, false);
			return;
		}

		int port;
		try {
			port = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			appendLogTo(window.roomChatLogText, "サーバーアドレスが正しくありません", window.colorLogError, false);
			return;
		}

		try {
			InetSocketAddress socketAddress = new InetSocketAddress(tokens[0], port);
			myRoomEntryConnection = tcpClient.connect(socketAddress, myRoomEntryHandler);

			window.roomFormMyRoomModeEntryCombo.setEnabled(false);
			window.roomFormMyRoomModeEntryButton.setEnabled(false);
			window.roomFormMyRoomModeEntryButton.setSelection(true);
			return;
		} catch (UnresolvedAddressException e) {
			appendLogTo(window.roomChatLogText, "アドレスが解決しません", window.colorRed, false);
		} catch (RuntimeException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
		}
	}

	private boolean commitRoomEditForm() {
		String title = window.roomFormTitleText.getText();
		if (Utility.isEmpty(title)) {
			appendLogTo(window.roomChatLogText, "部屋名を入力してください", window.colorLogError, false);
			window.roomFormTitleText.setFocus();
			return false;
		}
		window.roomFormEditButton.setEnabled(false);

		switch (currentRoomState) {
		case MyRoomMaster:
			myRoomEngine.setTitle(title);
			myRoomEngine.setMaxPlayers(window.roomFormMaxPlayersSpiner.getSelection());
			myRoomEngine.setPassword(window.roomFormPasswordText.getText());
			myRoomEngine.setDescription(window.roomFormDescriptionText.getText());

			myRoomEngine.updateRoom();

			appendLogTo(window.roomChatLogText, "部屋情報を更新しました", window.colorRoomInfo, false);
			window.roomChatSubmitText.setFocus();

			sendMyRoomUpdate();
			break;
		case RoomMaster:
			StringBuilder sb = new StringBuilder();
			sb.append(ProtocolConstants.Room.COMMAND_ROOM_UPDATE);
			appendRoomInfo(sb);

			roomConnection.send(sb.toString());

			break;
		}

		return true;
	}

	private void sendChat() {
		String command = window.roomChatSubmitText.getText();
		if (!Utility.isEmpty(command)) {
			switch (currentRoomState) {
			case MyRoomMaster:
				myRoomEngine.sendChat(command);
				window.roomChatSubmitText.setText("");
				break;
			case RoomMaster:
			case RoomParticipant:
				roomConnection.send(ProtocolConstants.Room.COMMAND_CHAT + ProtocolConstants.ARGUMENT_SEPARATOR + command);
				window.roomChatSubmitText.setText("");
				break;
			default:
				appendLogTo(window.roomChatLogText, "サーバーにログインしていません", window.colorLogInfo, false);
			}
		}
	}

	private void appendLogTo(final StyledText text, final String message, final Color color, final boolean inform) {
		if (Utility.isEmpty(message))
			return;

		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						appendLogTo(text, message, color, inform);
					}
				});
				return;
			}

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
			if (window.configEnableBalloonCheck.getSelection() && inform && !window.isActive && window.toolTip != null) {
				window.toolTip.setText(shell.getText());
				window.toolTip.setMessage(message);
				window.toolTip.setVisible(true);
			}
		} catch (SWTException e) {
		}
	}

	private void appendLogTo(final Text text, final String message) {
		if (Utility.isEmpty(message))
			return;

		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						appendLogTo(text, message);
					}
				});
				return;
			}

			text.append(message);
			text.append("\n");
			text.setTopIndex(text.getLineCount());
		} catch (SWTException e) {
		}
	}

	private void updateSsidStartScan(boolean startScan) {
		if (!Wlan.isLibraryAvailable)
			return;
		isSSIDScaning = startScan;
		window.ssidStartScan.setSelection(isSSIDScaning);
		window.ssidStartScan.setText(isSSIDScaning ? "スキャン中" : "スキャン開始");
		if (isSSIDScaning)
			wakeupThread(wlanScannerThread);
	}

	private void updateServerAddress() {
		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateServerAddress();
					}
				});
				return;
			}

			switch (currentRoomState) {
			case Offline:
				window.statusRoomServerAddressLabel.setText("部屋にログインしていません");
				break;
			default:
				String roomAddress;
				if (roomMasterName.equals("")) {
					roomAddress = roomServerAddressPort;
				} else {
					roomAddress = roomServerAddressPort + ":" + roomMasterName;
				}

				window.statusRoomServerAddressLabel.setText("部屋アドレス  " + roomAddress);
			}
			window.statusBarContainer.layout();
		} catch (SWTException e) {
		}
	}

	private void updateTunnelStatus(final boolean isLinked) {
		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateTunnelStatus(isLinked);
					}
				});
				return;
			}

			tunnelIsLinked = isLinked;
			if (tunnelIsLinked)
				wakeupThread(packetMonitorThread);

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

	private void updateTraficStatus(final String text) {
		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateTraficStatus(text);
					}
				});
				return;
			}

			window.statusTraficStatusLabel.setText(text);
			window.statusBarContainer.layout();
		} catch (SWTException e) {
		}
	}

	private void replacePlayerList(final TableViewer viewer, final String[] playerInfoList) {
		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						replacePlayerList(viewer, playerInfoList);
					}
				});
				return;
			}

			viewer.getTable().clearAll();
			roomPlayerMap.clear();
			for (int i = 0; i < playerInfoList.length - 1; i++) {
				String name = playerInfoList[i];
				String ssid = playerInfoList[++i];

				if (Utility.isEmpty(name))
					continue;

				Player player = new Player(name);
				player.setSsid(ssid);

				roomPlayerMap.put(name, player);
				viewer.add(player);
			}
			viewer.refresh();
		} catch (SWTException e) {
		}
	}

	private void addPlayer(final TableViewer viewer, final String name) {
		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						addPlayer(viewer, name);
					}
				});
				return;
			}

			appendLogTo(window.roomChatLogText, name + " が入室しました", window.colorLogInfo, true);

			Player player = new Player(name);
			@SuppressWarnings("unchecked")
			HashMap<String, Player> map = (HashMap<String, Player>) viewer.getInput();

			map.put(name, player);
			viewer.add(player);
			viewer.refresh();

			sendMyRoomPlayerCountChange();
		} catch (SWTException e) {
		}
	}

	private void removePlayer(final TableViewer viewer, final String name) {
		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						removePlayer(viewer, name);
					}
				});
				return;
			}

			@SuppressWarnings("unchecked")
			HashMap<String, Player> map = (HashMap<String, Player>) viewer.getInput();

			Player player = map.remove(name);
			if (player == null)
				return;

			viewer.remove(player);
			viewer.refresh();

			sendMyRoomPlayerCountChange();
		} catch (SWTException e) {
		}
	}

	private void removeExitingPlayer(String name) {
		appendLogTo(window.roomChatLogText, name + " が退室しました", window.colorLogInfo, true);
		removePlayer(window.roomPlayerListTableViewer, name);
	}

	private void removeKickedPlayer(String name) {
		switch (currentRoomState) {
		case MyRoomMaster:
		case RoomMaster:
			appendLogTo(window.roomChatLogText, name + " を部屋から追い出しました", window.colorRoomInfo, true);
			break;
		case RoomParticipant:
			appendLogTo(window.roomChatLogText, name + " は部屋から追い出されました", window.colorRoomInfo, true);
			break;
		}
		removePlayer(window.roomPlayerListTableViewer, name);
	}

	private void updatePlayerPing(final String name, final int ping) {
		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						updatePlayerPing(name, ping);
					}
				});
				return;
			}

			HashMap<String, Player> map = roomPlayerMap;
			Player player = map.get(name);
			if (player == null)
				return;

			player.setPing(ping);
			window.roomPlayerListTableViewer.refresh(player);
		} catch (SWTException e) {
		}
	}

	private void updatePlayerSSID(final String name, final String ssid) {
		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						updatePlayerSSID(name, ssid);
					}
				});
				return;
			}

			HashMap<String, Player> map = roomPlayerMap;
			Player player = map.get(name);
			if (player == null)
				return;

			player.setSsid(ssid);
			window.roomPlayerListTableViewer.refresh(player);

			if (player.isSSIDChased() && isPacketCapturing) {
				changeSSID(ssid);
			}
		} catch (SWTException e) {
		}
	}

	private void updateRoom(final String[] tokens, final boolean isInitialUpdate) {
		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateRoom(tokens, isInitialUpdate);
					}
				});
				return;
			}

			String masterName = tokens[0];
			int maxPlayers = Integer.parseInt(tokens[1]);
			String title = tokens[2];
			String password = tokens[3];
			String description = tokens[4];

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

			appendLogTo(window.roomChatLogText, "部屋情報が更新されました", window.colorRoomInfo, false);

			if (!masterName.equals(roomMasterName)) {
				roomMasterName = masterName;
				appendLogTo(window.roomChatLogText, "部屋主が " + roomMasterName + " に変更されました", window.colorRoomInfo, true);
				updateServerAddress();

				if (masterName.equals(loginUserName)) {
					window.roomFormMasterModeAddressCombo.setEnabled(false);
					window.roomFormMasterModeAddressCombo.setText(roomServerAddressPort);
					goTo(RoomState.RoomMaster);
				} else if (currentRoomState == RoomState.RoomMaster) {
					window.roomFormParticipantModeAddressCombo.setEnabled(false);
					window.roomFormParticipantModeAddressCombo.setText(roomServerAddressPort + ":" + masterName);
					goTo(RoomState.RoomParticipant);
				} else {
					window.roomFormParticipantModeAddressCombo.setText(roomServerAddressPort + ":" + masterName);
				}
			}
		} catch (NumberFormatException e) {
		} catch (SWTException e) {
		}
	}

	private void goTo(final RoomState state) {
		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						goTo(state);
					}
				});
				return;
			}

			currentRoomState = state;

			switch (state) {
			case Offline:
				window.statusRoomServerAddressLabel.setText("部屋にログインしていません");
				window.statusBarContainer.layout();

				roomPlayerMap.clear();
				window.roomPlayerListTableViewer.refresh();

				window.roomFormEditButton.setEnabled(false);

				window.roomFormModeSelectionCombo.setEnabled(true);

				window.roomFormMyRoomModePortSpinner.setEnabled(true);
				window.roomFormMyRoomModeStartButton.setText("起動する");
				window.roomFormMyRoomModeStartButton.setEnabled(true);

				window.roomFormParticipantModeAddressCombo.setEnabled(true);
				window.roomFormParticipantModeLoginButton.setText("入室する");
				window.roomFormParticipantModeLoginButton.setEnabled(true);
				window.roomFormParticipantModeContainer.layout();

				window.roomFormMasterModeAddressCombo.setEnabled(true);
				window.roomFormMasterModeLoginButton.setText("作成する");
				window.roomFormMasterModeLoginButton.setEnabled(true);
				window.roomFormMasterModeContainer.layout();

				switch (window.roomFormModeSelectionCombo.getSelectionIndex()) {
				case 0:
					setEnableRoomFormItems(true);
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
					updateTunnelStatus(false);
					break;
				}

				window.roomFormMasterText.setText("");
				window.roomFormMaxPlayersSpiner.setMaximum(ProtocolConstants.Room.MAX_ROOM_PLAYERS);

				window.roomFormMasterModeEnterLobbyCheck.setEnabled(true);
				window.roomFormParticipantModeEnterLobbyCheck.setEnabled(true);

				window.roomFormMyRoomModeEntryButton.setEnabled(false);

				window.configUserNameText.setEnabled(true);

				// window.mainTabFolder.setSelection(window.playRoomTab);
				disconnectMasterSearch();

				String lobbyAddress = window.roomFormParticipantModeLobbyAddressText.getText();
				window.roomFormMasterModeLobbyAddressText.setText("");
				window.roomFormParticipantModeLobbyAddressText.setText("");
				if (isExitOnLobbyCkecked && !Utility.isEmpty(lobbyAddress)) {
					window.roomFormParticipantModeAddressCombo.setText(lobbyAddress);
					connectToRoomServerAsParticipant();
				}

				break;
			case MyRoomMaster:
				window.mainTabFolder.setSelection(window.playRoomTab);

				window.roomFormMyRoomModeStartButton.setText("停止する");
				window.roomFormMyRoomModeStartButton.setEnabled(true);

				window.roomFormMyRoomModeEntryButton.setEnabled(true);

				updateTunnelStatus(true);

				window.roomFormMaxPlayersSpiner.setMaximum(ProtocolConstants.Room.MAX_ROOM_PLAYERS);

				window.configUserNameText.setEnabled(false);

				window.roomChatSubmitText.setFocus();
				break;
			case ConnectingAsRoomMaster:
				window.roomFormModeSelectionCombo.setEnabled(false);

				window.roomFormMasterModeLoginButton.setEnabled(false);
				window.roomFormMasterModeAddressCombo.setEnabled(false);

				window.configUserNameText.setEnabled(false);

				window.roomFormMasterModeEnterLobbyCheck.setEnabled(false);
				window.roomFormParticipantModeEnterLobbyCheck.setEnabled(false);

				break;
			case RoomMaster:
				window.roomFormMasterModeLoginButton.setText("ログアウト");
				window.roomFormMasterModeLoginButton.setEnabled(true);
				window.roomFormMasterModeContainer.layout();

				window.roomFormModeSelectionCombo.select(0);
				updateRoomModeSelection();

				window.roomFormMaxPlayersSpiner.setMaximum(ProtocolConstants.Room.MAX_ROOM_PLAYERS);

				window.roomChatSubmitText.setFocus();
				break;
			case ConnectingAsRoomParticipant:
				window.roomFormModeSelectionCombo.setEnabled(false);

				window.roomFormParticipantModeLoginButton.setEnabled(false);
				window.roomFormParticipantModeAddressCombo.setEnabled(false);

				window.configUserNameText.setEnabled(false);

				window.roomFormMasterModeEnterLobbyCheck.setEnabled(false);
				window.roomFormParticipantModeEnterLobbyCheck.setEnabled(false);

				break;
			case RoomParticipant:
				window.mainTabFolder.setSelection(window.playRoomTab);

				window.roomFormParticipantModeLoginButton.setText("退室する");
				window.roomFormParticipantModeLoginButton.setEnabled(true);
				window.roomFormParticipantModeContainer.layout();

				window.roomFormModeSelectionCombo.select(1);
				updateRoomModeSelection();

				window.roomFormMaxPlayersSpiner.setMaximum(Integer.MAX_VALUE);

				disconnectMasterSearch();
				window.roomChatSubmitText.setFocus();
				break;
			}
		} catch (SWTException e) {
		}
	}

	private void setEnableRoomFormItems(boolean enabled) {
		window.roomFormTitleText.setEditable(enabled);
		window.roomFormPasswordText.setEditable(enabled);
		window.roomFormMaxPlayersSpiner.setEnabled(enabled);
		window.roomFormDescriptionText.setEditable(enabled);
	}

	private void sendMyRoomUpdate() {
		if (myRoomEntryConnection == null || !myRoomEntryConnection.isConnected()) {
			return;
		}
		try {
			if (isNotSwtUIThread()) {
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						sendMyRoomUpdate();
					}
				});
				return;
			}
			StringBuilder sb = new StringBuilder();

			sb.append(ProtocolConstants.MyRoom.COMMAND_UPDATE);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(window.roomFormTitleText.getText());
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(window.roomFormMaxPlayersSpiner.getSelection());
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(window.roomFormPasswordText.getText().length() > 0 ? "Y" : "N");
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(window.roomFormDescriptionText.getText());

			myRoomEntryConnection.send(sb.toString());
		} catch (SWTException e) {
		}
	}

	private void sendMyRoomPlayerCountChange() {
		if (myRoomEntryConnection != null && myRoomEntryConnection.isConnected()) {
			myRoomEntryConnection.send(ProtocolConstants.MyRoom.COMMAND_UPDATE_PLAYER_COUNT + ProtocolConstants.ARGUMENT_SEPARATOR
					+ roomPlayerMap.size());
		}
	}

	private void disconnectMasterSearch() {
		if (myRoomEntryConnection != null && myRoomEntryConnection.isConnected())
			myRoomEntryConnection.send(ProtocolConstants.Portal.COMMAND_LOGOUT);
	}

	private class MyRoomEntryHandler implements IAsyncClientHandler {
		private HashMap<String, CommandHandler> handlers = new HashMap<String, CommandHandler>();
		private boolean isEntryCompleted = false;

		private MyRoomEntryHandler() {
			handlers.put(ProtocolConstants.MyRoom.COMMAND_ENTRY, new RoomEntryHandler());
			handlers.put(ProtocolConstants.ERROR_PROTOCOL_MISMATCH, new ErrorProtocolMismatchHandler());
			handlers.put(ProtocolConstants.MyRoom.ERROR_TCP_PORT_NOT_OPEN, new ErrorTcpPortHandler());
			handlers.put(ProtocolConstants.MyRoom.ERROR_UDP_PORT_NOT_OPEN, new ErrorUdpPortHandler());
			handlers.put(ProtocolConstants.MyRoom.ERROR_INVALID_AUTH_CODE, new ErrorInvalidAuthCodeHandler());
		}

		@Override
		public void connectCallback(final ISocketConnection connection) {
			try {
				if (isNotSwtUIThread()) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							connectCallback(connection);
						}
					});
					return;
				}

				StringBuilder sb = new StringBuilder();

				sb.append(ProtocolConstants.PROTOCOL_MY_ROOM);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(ProtocolConstants.PROTOCOL_NUMBER);
				sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

				sb.append(ProtocolConstants.MyRoom.COMMAND_ENTRY);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(roomMasterAuthCode);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(roomServerAddressPort);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(loginUserName);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(window.roomFormTitleText.getText());
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(roomPlayerMap.size());
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(window.roomFormMaxPlayersSpiner.getSelection());
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(window.roomFormPasswordText.getText().length() > 0 ? "Y" : "N");
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(window.roomFormDescriptionText.getText());

				myRoomEntryConnection.send(sb.toString());
			} catch (SWTException e) {
			}
		}

		@Override
		public void disconnectCallback(final ISocketConnection connection) {
			try {
				if (isNotSwtUIThread()) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							disconnectCallback(connection);
						}
					});
					return;
				}

				window.roomFormMyRoomModeEntryButton.setText("登録");
				window.roomFormMyRoomModeEntryButton.setSelection(false);
				window.roomFormMyRoomModeEntryButton.setEnabled(currentRoomState != RoomState.Offline);
				window.roomFormMyRoomModeEntryCombo.setEnabled(true);
				window.roomFormMyRoomModeHostText.setEnabled(true);

				if (isEntryCompleted) {
					isEntryCompleted = false;
					myRoomServerEntryHistoryManager.addCurrentItem();
					window.roomFormMasterModeAddressCombo.setText(window.roomFormMyRoomModeEntryCombo.getText());
					roomServerHistoryManager.addCurrentItem();
					appendLogTo(window.roomChatLogText, "マイルームの登録を解除しました", window.colorRoomInfo, false);
				} else {
					appendLogTo(window.roomChatLogText, "マイルームを登録できませんでした", window.colorLogError, false);
				}
			} catch (SWTException e) {
			}
		}

		@Override
		public void log(ISocketConnection connection, String message) {
		}

		@Override
		public void readCallback(ISocketConnection connection, PacketData data) {
			try {
				for (String message : data.getMessages()) {
					int commandEndIndex = message.indexOf(ProtocolConstants.ARGUMENT_SEPARATOR);
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

		private class RoomEntryHandler implements CommandHandler {
			@Override
			public void process(final String argument) {
				try {
					if (isNotSwtUIThread()) {
						display.asyncExec(new Runnable() {
							@Override
							public void run() {
								process(argument);
							}
						});
						return;
					}

					window.roomFormMyRoomModeEntryButton.setText("解除");
					window.roomFormMyRoomModeEntryButton.setSelection(true);
					window.roomFormMyRoomModeEntryButton.setEnabled(true);
					window.roomFormMyRoomModeHostText.setEnabled(false);

					isEntryCompleted = true;
					myRoomServerEntryHistoryManager.addCurrentItem();
					appendLogTo(window.roomChatLogText, "マイルームを登録しました", window.colorRoomInfo, false);
				} catch (SWTException e) {
				}
			}
		}

		private class ErrorProtocolMismatchHandler implements CommandHandler {
			@Override
			public void process(String num) {
				String message = String.format("サーバーとのプロトコルナンバーが一致ないので接続できません サーバー:%s クライアント:%s", num, ProtocolConstants.PROTOCOL_NUMBER);
				appendLogTo(window.roomChatLogText, message, window.colorLogError, false);
			}
		}

		private class ErrorTcpPortHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "マイルームのTCPポートが開放されていません", window.colorLogError, false);
			}
		}

		private class ErrorUdpPortHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "マイルームのUDPポートが開放されていません", window.colorLogError, false);
			}
		}

		private class ErrorInvalidAuthCodeHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "マイルーム以外の登録はできません", window.colorLogError, false);
			}
		}
	}

	private class PortalHandler implements IAsyncClientHandler {
		private HashMap<String, CommandHandler> handlers = new HashMap<String, CommandHandler>();

		private PortalHandler() {
			handlers.put(ProtocolConstants.ERROR_PROTOCOL_MISMATCH, new ErrorProtocolMismatchHandler());
			handlers.put(ProtocolConstants.Portal.COMMAND_LOGIN, new CommandLoginHandler());
			handlers.put(ProtocolConstants.Portal.COMMAND_SEARCH, new CommandSearchHandler());
			handlers.put(ProtocolConstants.Portal.SERVER_STATUS, new ServerStatusHandler());
			handlers.put(ProtocolConstants.Portal.ROOM_SERVER_STATUS, new RoomServerStatusHandler());
			handlers.put(ProtocolConstants.Portal.NOTIFY_ROOM_SERVER_REMOVED, new NotifyRoomServerRemovedHandler());
			handlers.put(ProtocolConstants.Portal.NOTIFY_FROM_ADMIN, new NotifyFromAdminHandler());
			handlers.put(ProtocolConstants.Portal.ERROR_LOGIN_BEYOND_CAPACITY, new ErrorLoginBeyondCapacityHandler());
		}

		@Override
		public void log(ISocketConnection connection, String message) {
			appendLogTo(window.logText, message);
		}

		@Override
		public void connectCallback(ISocketConnection connection) {
			StringBuilder sb = new StringBuilder();

			sb.append(ProtocolConstants.PROTOCOL_PORTAL);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(ProtocolConstants.PROTOCOL_NUMBER);
			sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

			sb.append(ProtocolConstants.Portal.COMMAND_LOGIN);
			sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

			connection.send(sb.toString());

			updateLoginButton();

			wakeupThread(portalSearchQueryThread);
		}

		private void updateLoginButton() {
			try {
				if (isNotSwtUIThread()) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							updateLoginButton();
						}
					});
					return;
				}

				window.portalServerLoginButton.setText("ログアウト");
				window.portalServerLoginButton.setEnabled(true);
				window.portalServerLoginButton.getParent().layout();

				appendLogTo(window.portalLogText, "ポータルサーバーにログインしました", window.colorRoomInfo, false);
			} catch (SWTException e) {
			}
		}

		@Override
		public void disconnectCallback(final ISocketConnection connection) {
			try {
				if (isNotSwtUIThread()) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							disconnectCallback(connection);
						}
					});
					return;
				}

				synchronized (roomSearchResultList) {
					roomSearchResultList.clear();
					window.portalRoomSearchResultTable.refresh();

					roomSearchResultList.notify();
				}

				window.statusPortalServerLabel.setText("ポータルサーバーにログインしていません");
				window.statusSearchResultLabel.setText("検索結果: なし");
				window.statusBarContainer.layout();

				roomServers.clear();
				window.portalRoomServerTableViewer.refresh();

				window.portalServerLoginButton.setText("ログイン");
				window.portalServerLoginButton.setEnabled(true);
				window.portalServerAddressCombo.setEnabled(true);

				switch (currentPortalState) {
				case Connecting:
					appendLogTo(window.portalLogText, "ポータルサーバーに接続できません", window.colorLogError, false);
					break;
				case Login:
					appendLogTo(window.portalLogText, "ポータルサーバーからログアウトしました", window.colorRoomInfo, true);
					break;
				}
			} catch (SWTException e) {
			}
		}

		@Override
		public void readCallback(ISocketConnection connection, PacketData data) {
			for (String message : data.getMessages()) {
				int commandEndIndex = message.indexOf(ProtocolConstants.ARGUMENT_SEPARATOR);
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
				}
			}
		}

		private class CommandLoginHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				currentPortalState = PortalState.Login;
			}
		}

		private class CommandSearchHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				// S address master title currentPlayers maxPlayers hasPassword
				// description
				if (Utility.isEmpty(argument)) {
					synchronized (roomSearchResultList) {
						roomSearchResultList.notify();
					}
					return;
				}

				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 7)
					return;

				String address = tokens[0];
				if (address.startsWith(":")) {
					// address = remoteAddress.getHostName() + address;
				}

				String masterName = tokens[1];
				String title = tokens[2];
				int currentPlayers = Integer.parseInt(tokens[3]);
				int maxPlayers = Integer.parseInt(tokens[4]);
				boolean hasPassword = "Y".equals(tokens[5]);
				String description = tokens[6].replace("\n", " ");

				PlayRoom room = new PlayRoom(address, masterName, title, hasPassword, currentPlayers, maxPlayers);
				room.setDescription(description);
				roomSearchResultList.add(room);
			}
		}

		private class ServerStatusHandler implements CommandHandler {
			@Override
			public void process(final String argument) {
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR);
				if (tokens.length != 2)
					return;

				updatePortalServerStatus(tokens);
			}
		}

		private void updatePortalServerStatus(final String[] tokens) {
			try {
				if (isNotSwtUIThread()) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							updatePortalServerStatus(tokens);
						}
					});
					return;
				}

				window.statusPortalServerLabel.setText("ポータルサーバー: " + tokens[0] + " / " + tokens[1] + " 人");
				window.statusPortalServerLabel.getParent().layout();
			} catch (SWTException e) {
			}
		}

		private class RoomServerStatusHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR);
				if (tokens.length != 5)
					return;

				updateRoomServerStatus(tokens);
			}
		}

		private void updateRoomServerStatus(final String[] tokens) {
			try {
				if (isNotSwtUIThread()) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							updateRoomServerStatus(tokens);
						}
					});
					return;
				}

				String address = tokens[0];
				int roomCount = Integer.parseInt(tokens[1]);
				int maxRooms = Integer.parseInt(tokens[2]);
				boolean passwordAllowed = "Y".equals(tokens[3]);
				int myRoomCount = Integer.parseInt(tokens[4]);

				RoomServerData server = new RoomServerData();
				server.address = address;
				server.roomCount = roomCount;
				server.maxRooms = maxRooms;
				server.isPasswordAllowed = passwordAllowed;
				server.myRoomCount = myRoomCount;

				roomServers.put(address, server);
				window.portalRoomServerTableViewer.refresh();
			} catch (SWTException e) {
			}
		}

		private class NotifyRoomServerRemovedHandler implements CommandHandler {
			@Override
			public void process(final String argument) {
				try {
					if (isNotSwtUIThread()) {
						display.asyncExec(new Runnable() {
							@Override
							public void run() {
								process(argument);
							}
						});
						return;
					}

					String address = argument;
					roomServers.remove(address);
					window.portalRoomServerTableViewer.refresh();
				} catch (SWTException e) {
				}
			}
		}

		private class NotifyFromAdminHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.portalLogText, argument, window.colorServerInfo, false);
			}
		}

		private class ErrorProtocolMismatchHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				String error = String.format("サーバーとのプロトコルナンバーが一致しません サーバー:%s クライアント:%s", argument, ProtocolConstants.PROTOCOL_NUMBER);
				appendLogTo(window.portalLogText, error, window.colorLogError, false);
			}
		}

		private class ErrorLoginBeyondCapacityHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.portalLogText, "サーバーのログイン上限人数に達したのでログインできません", window.colorLogError, false);
			}
		}
	}

	private class RoomServerHandler implements IMyRoomMasterHandler {
		@Override
		public void log(String message) {
			appendLogTo(window.logText, message);
		}

		@Override
		public void chatReceived(String message) {
			appendLogTo(window.roomChatLogText, message, window.colorBlack, true);
		}

		@Override
		public void playerEntered(String player) {
			addPlayer(window.roomPlayerListTableViewer, player);
		}

		@Override
		public void playerExited(String player) {
			removeExitingPlayer(player);
		}

		@Override
		public void pingInformed(String player, int ping) {
			updatePlayerPing(player, ping);
		}

		@Override
		public void ssidInformed(String player, String ssid) {
			updatePlayerSSID(player, ssid);
		}

		@Override
		public void tunnelPacketReceived(ByteBuffer packet, String playerName) {
			processRemotePspPacket(packet, playerName);
		}

		@Override
		public void roomOpened(String authCode) {
			try {
				if (isNotSwtUIThread()) {
					roomMasterAuthCode = authCode;

					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							roomOpened(null);
						}
					});
					return;
				}

				goTo(RoomState.MyRoomMaster);
				updateServerAddress();
				appendLogTo(window.roomChatLogText, "マイルームを起動しました", window.colorRoomInfo, false);
				addPlayer(window.roomPlayerListTableViewer, loginUserName);

				String ssid = window.ssidCurrentSsidText.getText();
				updatePlayerSSID(loginUserName, ssid);
				myRoomEngine.informSSID(ssid);
			} catch (SWTException e) {
			}
		}

		@Override
		public void roomClosed() {
			disconnectMasterSearch();
			goTo(RoomState.Offline);
			appendLogTo(window.roomChatLogText, "マイルームを停止しました", window.colorRoomInfo, false);
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
			handlers.put(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER, new MacAddressPlayerHandler());
			handlers.put(ProtocolConstants.Room.COMMAND_ROOM_UPDATE, new CommandRoomUpdateHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_LOBBY_ADDRESS, new NotifyLobbyAddressHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_USER_LIST, new NotifyUserListHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_USER_ENTERED, new NotifyUserEnteredHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_USER_EXITED, new NotifyUserExitedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED, new NotifyRoomUpdatedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED, new NotifyRoomPlayerKickedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_MASTER_AUTH_CODE, new NotifyRoomMasterAuthCodeHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_PASSWORD_REQUIRED, new NotifyRoomPasswordRequiredHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_FROM_ADMIN, new NotifyFromAdminHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_DELETED, new NotifyRoomDeletedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_SSID_CHANGED, new NotifySSIDHandler());
			handlers.put(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME, new ErrorLoginDuplicatedNameHandler());
			handlers.put(ProtocolConstants.Room.ERROR_LOGIN_ROOM_NOT_EXIST, new ErrorLoginRoomNotExistHandler());
			handlers.put(ProtocolConstants.Room.ERROR_LOGIN_BEYOND_CAPACITY, new ErrorLoginBeyondCapacityHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_CREATE_INVALID_DATA_ENTRY, new ErrorRoomInvalidDataEntryHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_CREATE_PASSWORD_NOT_ALLOWED, new ErrorRoomPasswordNotAllowedHandler());
			handlers.put(ProtocolConstants.Room.ERROR_LOGIN_PASSWORD_FAIL, new ErrorLoginPasswordFailHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_CREATE_DUPLICATED_NAME, new ErrorRoomCreateDuplicatedNameHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_CREATE_BEYOND_LIMIT, new ErrorRoomCreateBeyondLimitHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_TRANSFER_DUPLICATED_NAME, new ErrorRoomTransferDuplicatedNameHandler());
		}

		@Override
		public void log(ISocketConnection connection, String message) {
			appendLogTo(window.logText, message);
		}

		@Override
		public void connectCallback(final ISocketConnection connection) {
			try {
				if (isNotSwtUIThread()) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							connectCallback(connection);
						}
					});
					return;
				}

				StringBuilder sb;
				switch (currentRoomState) {
				case ConnectingAsRoomParticipant:
					appendLogTo(window.roomChatLogText, "サーバーに接続しました", window.colorServerInfo, false);

					sb = new StringBuilder();
					sb.append(ProtocolConstants.PROTOCOL_ROOM);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(ProtocolConstants.PROTOCOL_NUMBER);

					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

					sb.append(ProtocolConstants.Room.COMMAND_LOGIN);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(loginUserName);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(roomMasterName);

					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
					sb.append(ProtocolConstants.Room.COMMAND_INFORM_SSID);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(window.ssidCurrentSsidText.getText());

					roomConnection.send(sb.toString());

					currentRoomState = RoomState.Negotiating;

					break;
				case ConnectingAsRoomMaster:

					appendLogTo(window.roomChatLogText, "サーバーに接続しました", window.colorServerInfo, false);

					sb = new StringBuilder();
					sb.append(ProtocolConstants.PROTOCOL_ROOM);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(ProtocolConstants.PROTOCOL_NUMBER);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

					sb.append(ProtocolConstants.Room.COMMAND_ROOM_CREATE);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(loginUserName);
					appendRoomInfo(sb);

					roomConnection.send(sb.toString());

					currentRoomState = RoomState.Negotiating;
					break;
				}
			} catch (SWTException e) {
			}
		}

		@Override
		public void readCallback(ISocketConnection connection, final PacketData data) {
			try {
				for (String message : data.getMessages()) {
					int commandEndIndex = message.indexOf(ProtocolConstants.ARGUMENT_SEPARATOR);
					String command, argument;
					if (commandEndIndex > 0) {
						command = message.substring(0, commandEndIndex);
						argument = message.substring(commandEndIndex + 1);
					} else {
						command = message;
						argument = "";
					}
					// appendLogTo(window.logText, message);

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
			switch (currentRoomState) {
			case ConnectingAsRoomParticipant:
			case ConnectingAsRoomMaster:
				appendLogTo(window.roomChatLogText, "サーバーに接続できません", window.colorLogError, false);
				break;
			default:
				appendLogTo(window.roomChatLogText, "サーバーと切断しました", window.colorServerInfo, false);
			}
			goTo(RoomState.Offline);
		}

		private class ErrorProtocolMismatchHandler implements CommandHandler {
			@Override
			public void process(String num) {
				String message = String.format("サーバーとのプロトコルナンバーが一致ないので接続できません サーバー:%s クライアント:%s", num, ProtocolConstants.PROTOCOL_NUMBER);
				appendLogTo(window.roomChatLogText, message, window.colorLogError, false);
			}
		}

		private void prepareSession() {
			wakeupThread(pingThread);
			tunnelConnection = udpClient.connect(roomConnection.getRemoteAddress(), tunnelHandler);
		}

		private class LoginHandler implements CommandHandler {
			@Override
			public void process(final String args) {
				try {
					if (isNotSwtUIThread()) {
						display.syncExec(new Runnable() {
							@Override
							public void run() {
								process(args);
							}
						});

						prepareSession();
						return;
					}

					goTo(RoomState.RoomParticipant);

					updateRoom(args.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1), true);
					updateServerAddress();

					roomAddressHistoryManager.addCurrentItem();
					appendLogTo(window.roomChatLogText, "部屋に入りました  ", window.colorRoomInfo, false);
				} catch (SWTException e) {
				}
			}
		}

		private class RoomCreateHandler implements CommandHandler {
			@Override
			public void process(final String argument) {
				try {
					if (isNotSwtUIThread()) {
						display.syncExec(new Runnable() {
							@Override
							public void run() {
								process(argument);
							}
						});

						prepareSession();
						return;
					}

					goTo(RoomState.RoomMaster);
					appendLogTo(window.roomChatLogText, "ルームサーバーで部屋を作成しました", window.colorRoomInfo, false);

					window.roomFormMasterText.setText(loginUserName);
					addPlayer(window.roomPlayerListTableViewer, loginUserName);
					updateServerAddress();

					roomServerHistoryManager.addCurrentItem();
				} catch (SWTException e) {
				}
			}
		}

		private class ChatHandler implements CommandHandler {
			@Override
			public void process(String args) {
				switch (currentRoomState) {
				case MyRoomMaster:
				case RoomParticipant:
				case RoomMaster:
					appendLogTo(window.roomChatLogText, args, window.colorBlack, true);
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

				roomConnection.send(ProtocolConstants.Room.COMMAND_INFORM_PING + ProtocolConstants.ARGUMENT_SEPARATOR + ping);
			}
		}

		private class InformPingHandler implements CommandHandler {
			@Override
			public void process(String args) {
				try {
					switch (currentRoomState) {
					case MyRoomMaster:
					case RoomParticipant:
					case RoomMaster:
						String[] values = args.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
						if (values.length == 2) {
							int ping = Integer.parseInt(values[1]);
							updatePlayerPing(values[0], ping);
						}
					}
				} catch (NumberFormatException e) {
				}
			}
		}

		private class NotifyLobbyAddressHandler implements CommandHandler {
			@Override
			public void process(final String argument) {
				try {
					if (isNotSwtUIThread()) {
						display.syncExec(new Runnable() {
							@Override
							public void run() {
								process(argument);
							}
						});
						return;
					}

					String address = Utility.isEmpty(argument) ? roomServerAddressPort : argument;
					window.roomFormMasterModeLobbyAddressText.setText(address);
					window.roomFormParticipantModeLobbyAddressText.setText(address);

					window.roomFormMasterModeEnterLobbyCheck.setEnabled(true);
					window.roomFormParticipantModeEnterLobbyCheck.setEnabled(true);
				} catch (SWTException e) {
				}
			}
		}

		private class NotifyUserListHandler implements CommandHandler {
			@Override
			public void process(String args) {
				// System.out.println(args);
				String[] playerInfoList = args.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				replacePlayerList(window.roomPlayerListTableViewer, playerInfoList);
			}
		}

		private class NotifyUserEnteredHandler implements CommandHandler {
			@Override
			public void process(String name) {
				addPlayer(window.roomPlayerListTableViewer, name);
			}
		}

		private class NotifyUserExitedHandler implements CommandHandler {
			@Override
			public void process(String name) {
				removeExitingPlayer(name);
			}
		}

		private class NotifyRoomUpdatedHandler implements CommandHandler {
			@Override
			public void process(String args) {
				updateRoom(args.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1), false);
			}
		}

		private class CommandRoomUpdateHandler implements CommandHandler {
			@Override
			public void process(String message) {
				try {
					if (isNotSwtUIThread()) {
						display.asyncExec(new Runnable() {
							@Override
							public void run() {
								process(null);
							}
						});
						return;
					}

					appendLogTo(window.roomChatLogText, "部屋情報を修正しました", window.colorRoomInfo, false);
					window.roomChatSubmitText.setFocus();
				} catch (SWTException e) {
				}
			}
		}

		private class NotifyRoomPlayerKickedHandler implements CommandHandler {
			@Override
			public void process(String kickedPlayer) {
				if (loginUserName.equals(kickedPlayer)) {
					goTo(RoomState.Offline);
					appendLogTo(window.roomChatLogText, "部屋から追い出されました", window.colorRoomInfo, true);
				} else {
					removeKickedPlayer(kickedPlayer);
				}
			}
		}

		private class NotifyRoomMasterAuthCodeHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				roomMasterAuthCode = argument;
			}
		}

		private class NotifySSIDHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				String[] values = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (values.length == 2) {
					updatePlayerSSID(values[0], values[1]);
				}
			}
		}

		private class InformTunnelPortHandler implements CommandHandler {
			@Override
			public void process(String message) {
				updateTunnelStatus(true);
				appendLogTo(window.logText, "トンネル通信の接続が開始しました");
			}
		}

		private class MacAddressPlayerHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR);
				if (tokens.length != 2)
					return;

				TraficStatistics stats = traficStatsMap.get(tokens[0]);
				stats.playerName = tokens[1];
			}
		}

		private class NotifyFromAdminHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, message, window.colorServerInfo, true);
			}
		}

		private class NotifyRoomDeletedHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "部屋が削除されました", window.colorRoomInfo, true);
			}
		}

		private class NotifyRoomPasswordRequiredHandler implements CommandHandler {
			@Override
			public void process(final String masterName) {
				promptPassword();
			}
		}

		private class ErrorLoginDuplicatedNameHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "同名のユーザーが既にログインしているのでログインできません", window.colorLogError, false);
			}
		}

		private class ErrorLoginRoomNotExistHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "ログインしようとしている部屋は存在しません", window.colorLogError, false);
			}
		}

		private class ErrorLoginBeyondCapacityHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "部屋が満室なので入れません", window.colorLogError, false);
			}
		}

		private class ErrorLoginPasswordFailHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "部屋パスワードが違います", window.colorRoomInfo, false);
				promptPassword();
			}
		}

		private void promptPassword() {
			try {
				if (isNotSwtUIThread()) {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							promptPassword();
						}
					});
					return;
				}

				PasswordDialog dialog = new PasswordDialog(shell);
				switch (dialog.open()) {
				case IDialogConstants.OK_ID:
					String password = dialog.getPassword();

					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.Room.COMMAND_LOGIN);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(loginUserName);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(roomMasterName);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(password);

					roomConnection.send(sb.toString());
					break;
				case IDialogConstants.CANCEL_ID:
					appendLogTo(window.roomChatLogText, "入室をキャンセルしました", window.colorRoomInfo, false);
					roomConnection.send(ProtocolConstants.Room.COMMAND_LOGOUT);
					break;
				}
			} catch (SWTException e) {
			}
		}

		private class ErrorRoomInvalidDataEntryHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "部屋の情報に不正な値があります", window.colorLogError, false);
			}
		}

		private class ErrorRoomPasswordNotAllowedHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				try {
					display.asyncExec(new Runnable() {
						@Override
						public void run() {
							appendLogTo(window.roomChatLogText, "このサーバーではパスワードが禁止されています", window.colorLogError, false);
							try {
								window.roomFormEditButton.setEnabled(true);
								window.roomFormPasswordText.setFocus();
							} catch (SWTException e) {
							}
						}
					});
				} catch (SWTException e) {
				}
			}
		}

		private class ErrorRoomCreateDuplicatedNameHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "同名のユーザーで既に部屋が作成されているので作成できません", window.colorLogError, false);
			}
		}

		private class ErrorRoomCreateBeyondLimitHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "部屋数が上限に達しましたので部屋を作成できません", window.colorRoomInfo, false);
			}
		}

		private class ErrorRoomTransferDuplicatedNameHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "同名のユーザーで既に部屋が作成されているので委譲できません", window.colorLogError, false);
			}
		}
	}

	private class TunnelHandler implements IAsyncClientHandler {
		@Override
		public void connectCallback(ISocketConnection connection) {
			connection.send(ProtocolConstants.Room.TUNNEL_DUMMY_PACKET);
			wakeupThread(natTableMaintainingThread);
		}

		@Override
		public void readCallback(ISocketConnection connection, PacketData data) {
			ByteBuffer packet = data.getBuffer();
			// System.out.println(packet.toString());
			if (Utility.isPspPacket(packet)) {
				processRemotePspPacket(packet, null);
			} else {
				try {
					String tunnelPort = data.getMessage();
					int port = Integer.parseInt(tunnelPort);
					roomConnection
							.send(ProtocolConstants.Room.COMMAND_INFORM_TUNNEL_UDP_PORT + ProtocolConstants.ARGUMENT_SEPARATOR + port);
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

		wlanAdaptorList.clear();

		try {
			Wlan.findDevices(wlanAdaptorList);
		} catch (RuntimeException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
			return;
		} catch (UnsatisfiedLinkError e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
			return;
		}

		String lastUsedMacAddress = iniSettingSection.get(IniConstants.Client.LAST_LAN_ADAPTER, "");
		int lastUsedIndex = 0;

		IniParser.Section nicSection = iniParser.getSection(IniConstants.Client.SECTION_LAN_ADAPTERS);

		int maxNameLength = 5;
		int i = 1;
		for (Iterator<WlanDevice> iter = wlanAdaptorList.iterator(); iter.hasNext(); i++) {
			WlanDevice device = iter.next();
			String macAddress = Utility.makeMacAddressString(device.getHardwareAddress(), 0, true);
			if (lastUsedMacAddress.equals(macAddress)) {
				lastUsedIndex = i;
			}

			String description = nicSection.get(macAddress, null);

			if (description == null) {
				description = device.getName();
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

			maxNameLength = Math.max(description.length(), maxNameLength);
		}

		StringBuilder sb = new StringBuilder(maxNameLength);
		for (i = 0; i < maxNameLength; i++)
			sb.append('-');

		window.wlanAdapterListCombo.add(sb.toString());
		window.wlanAdapterListCombo.add("アダプターリストを再読み込み");

		window.wlanAdapterListCombo.select(lastUsedIndex);
		window.wlanPspCommunicationButton.setEnabled(lastUsedIndex != 0);
	}

	private void processRemotePspPacket(ByteBuffer packet, String playerName) {
		String destMac = Utility.makeMacAddressString(packet, 0, false);
		String srcMac = Utility.makeMacAddressString(packet, 6, false);

		// System.out.println("src: " + srcMac + " dest: " + destMac);

		TraficStatistics destStats, srcStats;
		synchronized (traficStatsMap) {
			destStats = traficStatsMap.get(destMac);
			srcStats = traficStatsMap.get(srcMac);

			if (srcStats == null) {
				srcStats = new TraficStatistics(srcMac, false);
				traficStatsMap.put(srcMac, srcStats);
			}

			if (destStats == null) {
				destStats = new TraficStatistics(destMac, !Utility.isMacBroadCastAddress(destMac));
				traficStatsMap.put(destMac, destStats);
			}
		}

		int packetLength = packet.limit();

		srcStats.lastModified = System.currentTimeMillis();
		srcStats.currentInBytes += packetLength;
		srcStats.totalInBytes += packetLength;
		if (!Utility.isEmpty(playerName)) {
			srcStats.playerName = playerName;
		} else if (Utility.isEmpty(srcStats.playerName) && !Utility.isMacBroadCastAddress(srcMac)) {
			roomConnection.send(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER + ProtocolConstants.ARGUMENT_SEPARATOR + srcMac);
		}

		destStats.lastModified = srcStats.lastModified;
		destStats.currentInBytes += packetLength;
		destStats.totalInBytes += packetLength;
		if (destStats.isMine)
			destStats.playerName = loginUserName;

		if (isPacketCapturing && currentWlanDevice != null) {
			// send packet
			currentWlanDevice.sendPacket(packet);
		}
		actualRecievedBytes += packetLength;
	}

	private void processCapturedPacket() {
		// if (packet != null) {
		// System.out.println(packet.toHexdump());
		// return;
		// }

		// System.out.println(bufferForCapturing);
		if (Utility.isPspPacket(bufferForCapturing)) {
			String srcMac = Utility.makeMacAddressString(bufferForCapturing, 6, false);
			String destMac = Utility.makeMacAddressString(bufferForCapturing, 0, false);

			TraficStatistics srcStats, destStats;
			synchronized (traficStatsMap) {
				srcStats = traficStatsMap.get(srcMac);
				destStats = traficStatsMap.get(destMac);

				if (srcStats == null) {
					srcStats = new TraficStatistics(srcMac, true);
					traficStatsMap.put(srcMac, srcStats);
				} else if (!srcStats.isMine) {
					// サーバーから送られてきた他PSPからのパケットの再キャプチャなのでスルー
					return;
				}

				if (destStats == null) {
					destStats = new TraficStatistics(destMac, false);
					traficStatsMap.put(destMac, destStats);

				} else if (destStats.isMine) {
					// 手元のPSP同士の通信なのでスルー
					return;
				}
			}

			int packetLength = bufferForCapturing.limit();

			srcStats.lastModified = System.currentTimeMillis();
			srcStats.currentOutBytes += packetLength;
			srcStats.totalOutBytes += packetLength;
			srcStats.playerName = loginUserName;

			destStats.lastModified = srcStats.lastModified;
			destStats.currentOutBytes += packetLength;
			destStats.totalOutBytes += packetLength;
			if (Utility.isEmpty(destStats.playerName) && !Utility.isMacBroadCastAddress(destMac))
				roomConnection.send(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER + ProtocolConstants.ARGUMENT_SEPARATOR + destMac);

			if (tunnelIsLinked) {
				// System.out.printf("%s => %s  [%d]", srcMac, destMac,
				// packetLength);
				// System.out.println(packet.toHexdump());

				switch (currentRoomState) {
				case MyRoomMaster:
					myRoomEngine.sendTunnelPacketToParticipants(bufferForCapturing, srcMac, destMac);
					break;
				case RoomParticipant:
				case RoomMaster:
					tunnelConnection.send(bufferForCapturing);
					break;
				}
				actualSentBytes += packetLength;
			}
		}
	}

	private boolean startPacketCapturing() {
		int index = window.wlanAdapterListCombo.getSelectionIndex() - 1;
		WlanDevice device = wlanAdaptorList.get(index);

		try {
			device.open();
		} catch (RuntimeException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
			return false;
		} catch (Exception e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
			return false;
		}

		currentWlanDevice = device;

		checkSsidChange();

		isPacketCapturing = true;
		wakeupThread(packetCaptureThread);
		wakeupThread(packetMonitorThread);

		return true;
	}

	public void start() {
		int minWidth = 650, minHeight = 400;

		shell.setMinimumSize(new Point(minWidth, minHeight));

		shell.setSize(iniSettingSection.get(IniConstants.Client.WINDOW_WIDTH, 800),
				iniSettingSection.get(IniConstants.Client.WINDOW_HEIGHT, 600));
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
