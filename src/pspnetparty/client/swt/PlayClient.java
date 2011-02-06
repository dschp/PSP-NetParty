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
import java.util.Map.Entry;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
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
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;
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
import pspnetparty.lib.PlayRoom;
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

	enum RoomState {
		Offline, RoomMaster, ConnectingToRoomServer, RoomParticipant, ConnectingToProxyServer, ProxyRoomMaster, Negotiating
	};

	private IniParser iniParser;
	private IniParser.Section iniSettingSection;

	private Display display;
	private Shell shell;

	private RoomState currentRoomState;

	private String loginUserName;
	private String roomMasterAuthCode;
	private String roomMasterName;
	private String roomServerAddressPort;

	private int lastUpdatedMaxPlayers = DEFAULT_MAX_PLAYERS;

	private RoomEngine roomEngine;

	private AsyncTcpClient tcpClient = new AsyncTcpClient(1000000);
	private AsyncUdpClient udpClient = new AsyncUdpClient();

	private RoomClientHandler roomClientHandler = new RoomClientHandler();
	private ISocketConnection roomConnection;

	private TunnelHandler tunnelHandler = new TunnelHandler();
	private ISocketConnection tunnelConnection;

	private SearchEntryHandler searchEntryHandler = new SearchEntryHandler();
	private ISocketConnection searchEntryConnection;

	private SearchQueryHandler searchQueryHandler = new SearchQueryHandler();
	private ArrayList<PlayRoom> searchResultRoomList = new ArrayList<PlayRoom>();

	private boolean tunnelIsLinked = false;
	private boolean isPacketCapturing = false;
	private boolean isRoomInfoUpdating = false;

	private ByteBuffer bufferForCapturing = ByteBuffer.allocate(CAPTURE_BUFFER_SIZE);
	private ArrayList<PcapIf> wlanAdaptorList = new ArrayList<PcapIf>();
	private HashMap<PcapIf, String> wlanAdaptorMacAddressMap = new HashMap<PcapIf, String>();
	private Pcap currentPcapDevice;

	private HashMap<String, Player> roomPlayerMap = new LinkedHashMap<String, Player>();
	private HashMap<String, TraficStatistics> traficStatsMap = new HashMap<String, TraficStatistics>();

	private Thread packetMonitorThread;
	private Thread packetCaptureThread;
	private Thread pingThread;
	private Thread natTableMaintainingThread;

	private Window window;
	private ComboHistoryManager roomServerHistoryManager;
	private ComboHistoryManager proxyServerHistoryManager;
	private ComboHistoryManager entrySearchServerHistoryManager;
	private ComboHistoryManager querySearchServerHistoryManager;

	private ComboHistoryManager queryRoomTitleHistoryManager;
	private ComboHistoryManager queryRoomMasterNameHistoryManager;
	private ComboHistoryManager queryRoomAddressHistoryManager;

	public PlayClient(IniParser iniParser) {
		this.iniParser = iniParser;
		this.iniSettingSection = iniParser.getSection(IniConstants.SECTION_SETTINGS);

		roomEngine = new RoomEngine(new RoomServerHandler());

		display = new Display();
		shell = new Shell(display);

		window = new Window();
		window.initializeComponents(display, shell);
		window.roomPlayerListTable.setInput(roomPlayerMap);
		initializeComponentListeners();

		goTo(RoomState.Offline);

		refreshLanAdapterList();

		window.configUserNameText.setText(iniSettingSection.get(IniConstants.Client.LOGIN_NAME, ""));

		window.roomFormServerModePortSpinner.setSelection(iniSettingSection.get(IniConstants.Client.MY_ROOM_PORT, 30000));
		window.configRoomServerHostNameText.setText(iniSettingSection.get(IniConstants.Client.MY_ROOM_HOST_NAME, ""));
		window.configRoomServerAllowEmptyMasterNameCheck.setSelection(iniSettingSection.get(
				IniConstants.Client.MY_ROOM_ALLOW_NO_MASTER_NAME, true));

		window.configAppCloseConfirmCheck.setSelection(iniSettingSection.get(IniConstants.Client.APP_CLOSE_CONFIRM, true));

		String[] serverList;

		serverList = iniSettingSection.get(IniConstants.Client.ROOM_SERVER_LIST, "").split(",");
		ComboHistoryManager.addList(window.roomFormClientModeAddressCombo, serverList);
		serverList = iniSettingSection.get(IniConstants.Client.ROOM_SERVER_HISTORY, "").split(",");
		roomServerHistoryManager = new ComboHistoryManager(window.roomFormClientModeAddressCombo, serverList, MAX_SERVER_HISTORY);

		serverList = iniSettingSection.get(IniConstants.Client.PROXY_SERVER_LIST, "").split(",");
		ComboHistoryManager.addList(window.roomFormProxyModeAddressCombo, serverList);
		serverList = iniSettingSection.get(IniConstants.Client.PROXY_SERVER_HISTORY, "").split(",");
		proxyServerHistoryManager = new ComboHistoryManager(window.roomFormProxyModeAddressCombo, serverList, MAX_SERVER_HISTORY);

		serverList = iniSettingSection.get(IniConstants.Client.ENTRY_SEARCH_SERVER_LIST, "").split(",");
		ComboHistoryManager.addList(window.roomFormSearchServerCombo, serverList);
		serverList = iniSettingSection.get(IniConstants.Client.ENTRY_SEARCH_SERVER_HISTORY, "").split(",");
		entrySearchServerHistoryManager = new ComboHistoryManager(window.roomFormSearchServerCombo, serverList, MAX_SERVER_HISTORY);

		serverList = iniSettingSection.get(IniConstants.Client.QUERY_SEARCH_SERVER_LIST, "").split(",");
		ComboHistoryManager.addList(window.searchServerAddressCombo, serverList);
		serverList = iniSettingSection.get(IniConstants.Client.QUERY_SEARCH_SERVER_HISTORY, "").split(",");
		querySearchServerHistoryManager = new ComboHistoryManager(window.searchServerAddressCombo, serverList, MAX_SERVER_HISTORY);

		queryRoomTitleHistoryManager = new ComboHistoryManager(window.searchFormTitleCombo, null, 20);
		queryRoomMasterNameHistoryManager = new ComboHistoryManager(window.searchFormMasterNameCombo, null, 20);
		queryRoomAddressHistoryManager = new ComboHistoryManager(window.searchFormServerNameCombo, null, 20);

		String software = String.format("%s プレイクライアント バージョン: %s", AppConstants.APP_NAME, AppConstants.VERSION);
		appendLogTo(window.roomChatLogText, software, window.colorAppInfo, false);
		appendLogTo(window.roomChatLogText, "プロトコル: " + ProtocolConstants.PROTOCOL_NUMBER, window.colorAppInfo, false);

		initializeBackgroundThreads();
	}

	private static class Window {
		private TabFolder mainTabFolder;
		private TabItem searchTab;
		private Composite searchContainer;
		private Label searchServerAddressLabel;
		private Combo searchServerAddressCombo;
		private Button searchFormHasPassword;
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
		private TableViewer roomPlayerListTable;
		private TableColumn roomPlayerNameColumn;
		private TableColumn roomPlayerPingColumn;
		private Composite packetMonitorContainer;
		private TableViewer packetMonitorTable;
		private TabItem configTab;
		private Composite configContainer;
		private Label configUserNameLabel;
		private Text configUserNameText;
		private Label configUserNameAlertLabel;
		private Button configAppCloseConfirmCheck;
		private Group configRoomServerGroup;
		private Label configRoomServerHostNameLabel;
		private Text configRoomServerHostNameText;
		private Button configRoomServerAllowEmptyMasterNameCheck;
		private TabItem logTab;
		private Text logText;
		private Composite statusBarContainer;
		private Label statusServerAddressLabel;
		private Label statusTunnelConnectionLabel;
		private Label statusSearchResultLabel;
		private Label statusTraficStatusLabel;

		private Color colorWhite, colorBlack, colorRed, colorGreen;
		private Color colorLogInfo, colorLogError, colorRoomInfo, colorAppInfo, colorServerInfo;

		private Menu roomPlayerMenu;
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

			searchFormHasPassword = new Button(searchContainer, SWT.CHECK);
			searchFormHasPassword.setText("鍵付き");
			searchFormHasPassword.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

			searchFormServerNameLabel = new Label(searchContainer, SWT.NONE);
			searchFormServerNameLabel.setText("部屋サーバ名");
			searchFormServerNameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			searchFormServerNameCombo = new Combo(searchContainer, SWT.BORDER);
			searchFormServerNameCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			searchFormMasterNameLabel = new Label(searchContainer, SWT.NONE);
			searchFormMasterNameLabel.setText("部屋主名");
			searchFormMasterNameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			searchFormMasterNameCombo = new Combo(searchContainer, SWT.BORDER);
			searchFormMasterNameCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			searchFormTitleLabel = new Label(searchContainer, SWT.NONE);
			searchFormTitleLabel.setText("部屋名");
			searchFormTitleLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			searchFormTitleCombo = new Combo(searchContainer, SWT.BORDER);
			searchFormTitleCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

			searchServerSubmitButton = new Button(searchContainer, SWT.PUSH);
			searchServerSubmitButton.setText("検索する");
			searchServerSubmitButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

			searchResultRoomsTable = new TableViewer(searchContainer, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
			searchResultRoomsTable.getTable().setHeaderVisible(true);
			searchResultRoomsTable.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 7, 1));
			searchResultRoomsTable.setContentProvider(new ArrayContentProvider());
			searchResultRoomsTable.setLabelProvider(new PlayRoomUtils.LabelProvider());

			TableColumn searchResultRoomsAddressColumn = new TableColumn(searchResultRoomsTable.getTable(), SWT.LEFT);
			searchResultRoomsAddressColumn.setText("部屋アドレス");
			searchResultRoomsAddressColumn.setWidth(150);
			SwtUtils.installSorter(searchResultRoomsTable, searchResultRoomsAddressColumn, new PlayRoomUtils.AddressSorter());

			TableColumn searchResultRoomsMasterNameColumn = new TableColumn(searchResultRoomsTable.getTable(), SWT.LEFT);
			searchResultRoomsMasterNameColumn.setText("部屋主");
			searchResultRoomsMasterNameColumn.setWidth(120);
			SwtUtils.installSorter(searchResultRoomsTable, searchResultRoomsMasterNameColumn, new PlayRoomUtils.MasterNameSorter());

			TableColumn searchResultRoomsTitleColumn = new TableColumn(searchResultRoomsTable.getTable(), SWT.LEFT);
			searchResultRoomsTitleColumn.setText("部屋名");
			searchResultRoomsTitleColumn.setWidth(200);
			SwtUtils.installSorter(searchResultRoomsTable, searchResultRoomsTitleColumn, new PlayRoomUtils.TitleSorter());

			TableColumn searchResultRoomsCapacityColumn = new TableColumn(searchResultRoomsTable.getTable(), SWT.CENTER);
			searchResultRoomsCapacityColumn.setText("定員");
			searchResultRoomsCapacityColumn.setWidth(65);
			SwtUtils.installSorter(searchResultRoomsTable, searchResultRoomsCapacityColumn, new PlayRoomUtils.CapacitySorter());

			TableColumn searchResultRoomsHasPasswordColumn = new TableColumn(searchResultRoomsTable.getTable(), SWT.CENTER);
			searchResultRoomsHasPasswordColumn.setText("鍵");
			searchResultRoomsHasPasswordColumn.setWidth(40);
			SwtUtils.installSorter(searchResultRoomsTable, searchResultRoomsHasPasswordColumn, new PlayRoomUtils.HasPasswordSorter());

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

			packetMonitorTable = new TableViewer(packetMonitorContainer, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
			packetMonitorTable.getTable().setHeaderVisible(true);

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

			roomInfoSashForm = new SashForm(roomSubSashForm, SWT.SMOOTH | SWT.HORIZONTAL);

			roomChatLogText = new StyledText(roomInfoSashForm, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER | SWT.READ_ONLY | SWT.WRAP);
			roomChatLogText.setMargins(3, 1, 3, 1);

			roomPlayerListTable = new TableViewer(roomInfoSashForm, SWT.SINGLE | SWT.BORDER);
			roomPlayerListTable.getTable().setHeaderVisible(true);

			roomPlayerNameColumn = new TableColumn(roomPlayerListTable.getTable(), SWT.LEFT);
			roomPlayerNameColumn.setText("名前");
			roomPlayerNameColumn.setWidth(100);
			SwtUtils.installSorter(roomPlayerListTable, roomPlayerNameColumn, new Player.NameSorter());

			roomPlayerPingColumn = new TableColumn(roomPlayerListTable.getTable(), SWT.RIGHT);
			roomPlayerPingColumn.setText("PING");
			roomPlayerPingColumn.setWidth(50);
			SwtUtils.installSorter(roomPlayerListTable, roomPlayerPingColumn, new Player.PingSorter());

			roomPlayerListTable.setContentProvider(new Player.PlayerListContentProvider());
			roomPlayerListTable.setLabelProvider(new Player.RoomPlayerLabelProvider());

			configTab = new TabItem(mainTabFolder, SWT.NONE);
			configTab.setText("設定");

			configContainer = new Composite(mainTabFolder, SWT.NONE);
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

			configUserNameLabel = new Label(configUserNameContainer, SWT.NONE);
			configUserNameLabel.setText("ユーザー名");

			configUserNameText = new Text(configUserNameContainer, SWT.SINGLE | SWT.BORDER);
			configUserNameText.setLayoutData(new RowData(200, SWT.DEFAULT));
			configUserNameText.setTextLimit(100);

			configUserNameAlertLabel = new Label(configUserNameContainer, SWT.NONE);
			configUserNameAlertLabel.setText("ユーザー名を入力してください");
			configUserNameAlertLabel.setForeground(colorLogError);

			configAppCloseConfirmCheck = new Button(configContainer, SWT.CHECK | SWT.FLAT);
			configAppCloseConfirmCheck.setText("アプリケーションを閉じる時に確認する");

			configRoomServerGroup = new Group(configContainer, SWT.SHADOW_IN);
			configRoomServerGroup.setText("部屋サーバー");
			gridData = new GridData(SWT.FILL, SWT.CENTER, false, false);
			configRoomServerGroup.setLayoutData(gridData);
			gridLayout = new GridLayout(2, false);
			gridLayout.horizontalSpacing = 0;
			gridLayout.verticalSpacing = 5;
			gridLayout.marginWidth = 4;
			gridLayout.marginHeight = 5;
			configRoomServerGroup.setLayout(gridLayout);

			configRoomServerHostNameLabel = new Label(configRoomServerGroup, SWT.NONE);
			configRoomServerHostNameLabel.setText("検索サーバーへ登録する際の自ホスト名");

			configRoomServerHostNameText = new Text(configRoomServerGroup, SWT.BORDER);
			configRoomServerHostNameText.setLayoutData(new GridData(300, SWT.DEFAULT));

			configRoomServerAllowEmptyMasterNameCheck = new Button(configRoomServerGroup, SWT.CHECK | SWT.FLAT);
			configRoomServerAllowEmptyMasterNameCheck.setText("アドレスの部屋主名を省略でもログインできるようにする");
			configRoomServerAllowEmptyMasterNameCheck.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));

			logTab = new TabItem(mainTabFolder, SWT.NONE);
			logTab.setText("ログ");

			logText = new Text(mainTabFolder, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL);
			logTab.setControl(logText);
			logText.setBackground(colorWhite);

			statusBarContainer = new Composite(shell, SWT.NONE);
			statusBarContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
			statusBarContainer.setLayout(new FormLayout());

			statusServerAddressLabel = new Label(statusBarContainer, SWT.BORDER);
			formData = new FormData();
			formData.left = new FormAttachment(0, 2);
			statusServerAddressLabel.setLayoutData(formData);

			statusTunnelConnectionLabel = new Label(statusBarContainer, SWT.BORDER);
			formData = new FormData();
			formData.left = new FormAttachment(statusServerAddressLabel, 5);
			statusTunnelConnectionLabel.setLayoutData(formData);

			statusSearchResultLabel = new Label(statusBarContainer, SWT.BORDER);
			formData = new FormData();
			formData.left = new FormAttachment(statusTunnelConnectionLabel, 5);
			statusSearchResultLabel.setLayoutData(formData);

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
				if (!MessageDialog.openConfirm(shell, "PSP NetPartyを終了します", "PSP NetPartyを終了します。よろしいですか？")) {
					e.doit = false;
				}
			}
		});
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

				iniSettingSection.set(IniConstants.Client.LOGIN_NAME, window.configUserNameText.getText());

				Point size = shell.getSize();
				iniSettingSection.set(IniConstants.Client.WINDOW_WIDTH, Integer.toString(size.x));
				iniSettingSection.set(IniConstants.Client.WINDOW_HEIGHT, Integer.toString(size.y));

				iniSettingSection.set(IniConstants.Client.MY_ROOM_HOST_NAME, window.configRoomServerHostNameText.getText());
				iniSettingSection.set(IniConstants.Client.MY_ROOM_PORT,
						Integer.toString(window.roomFormServerModePortSpinner.getSelection()));
				iniSettingSection.set(IniConstants.Client.MY_ROOM_ALLOW_NO_MASTER_NAME,
						window.configRoomServerAllowEmptyMasterNameCheck.getSelection());
				iniSettingSection.set(IniConstants.Client.APP_CLOSE_CONFIRM, window.configAppCloseConfirmCheck.getSelection());

				iniSettingSection.set(IniConstants.Client.ROOM_SERVER_HISTORY, roomServerHistoryManager.makeCSV());
				iniSettingSection.set(IniConstants.Client.PROXY_SERVER_HISTORY, proxyServerHistoryManager.makeCSV());
				iniSettingSection.set(IniConstants.Client.ENTRY_SEARCH_SERVER_HISTORY, entrySearchServerHistoryManager.makeCSV());
				iniSettingSection.set(IniConstants.Client.QUERY_SEARCH_SERVER_HISTORY, querySearchServerHistoryManager.makeCSV());

				int index = window.wlanAdapterListCombo.getSelectionIndex() - 1;
				if (index == -1) {
					iniSettingSection.set(IniConstants.Client.LAST_LAN_ADAPTER, "");
				} else {
					PcapIf device = wlanAdaptorList.get(index);
					iniSettingSection.set(IniConstants.Client.LAST_LAN_ADAPTER, wlanAdaptorMacAddressMap.get(device));
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
				if (currentRoomState == RoomState.Offline) {
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
				if (currentRoomState == RoomState.Offline) {
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
				if (searchEntryConnection != null && searchEntryConnection.isConnected()) {
					searchEntryConnection.send(ProtocolConstants.Search.COMMAND_LOGOUT);
				} else {
					if (window.roomFormEditButton.getEnabled() && !commitRoomEditForm()) {
						window.roomFormSearchServerButton.setSelection(false);
						return;
					}
					if (!connectToSearchServerAsMaster()) {
						window.roomFormSearchServerButton.setSelection(false);
					}
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
				if (currentRoomState != RoomState.Offline)
					return;
				IStructuredSelection sel = (IStructuredSelection) e.getSelection();
				PlayRoom room = (PlayRoom) sel.getFirstElement();
				if (room == null)
					return;

				window.roomFormClientModeAddressCombo.setText(room.getRoomAddress());
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

				if (window.trayItem != null) {
					window.trayItem.setText(shell.getText());
					window.trayItem.setToolTipText(shell.getText());
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
				switch (currentRoomState) {
				case RoomMaster:
					roomServerAddressPort = window.configRoomServerHostNameText.getText() + ":"
							+ window.roomFormServerModePortSpinner.getSelection();
					updateServerAddress();
				}
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
		window.searchFormTitleCombo.addVerifyListener(notAcceptControlCharListener);
		window.searchFormMasterNameCombo.addVerifyListener(notAcceptControlCharListener);

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
		window.roomFormClientModeAddressCombo.addVerifyListener(notAcceptSpaceControlCharListener);
		window.roomFormProxyModeAddressCombo.addVerifyListener(notAcceptSpaceControlCharListener);
		window.roomFormSearchServerCombo.addVerifyListener(notAcceptSpaceControlCharListener);

		window.searchServerAddressCombo.addVerifyListener(notAcceptSpaceControlCharListener);
		window.searchFormServerNameCombo.addVerifyListener(notAcceptSpaceControlCharListener);
		window.configRoomServerHostNameText.addVerifyListener(notAcceptSpaceControlCharListener);

		ModifyListener roomEditFormModifyDetectListener = new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (isRoomInfoUpdating)
					return;

				switch (currentRoomState) {
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
				IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListTable.getSelection();
				Player player = (Player) selection.getFirstElement();
				if (player == null)
					return;

				String kickedName = player.getName();
				switch (currentRoomState) {
				case RoomMaster:
					roomEngine.kickPlayer(kickedName);
					removeKickedPlayer(kickedName);
					break;
				case ProxyRoomMaster:
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
				IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListTable.getSelection();
				Player player = (Player) selection.getFirstElement();
				if (player == null)
					return;

				String newMasterName = player.getName();
				switch (currentRoomState) {
				case ProxyRoomMaster:
					roomConnection.send(ProtocolConstants.Room.COMMAND_ROOM_MASTER_TRANSFER + ProtocolConstants.ARGUMENT_SEPARATOR
							+ newMasterName);
					if (searchEntryConnection != null && searchEntryConnection.isConnected())
						searchEntryConnection.send(ProtocolConstants.Search.COMMAND_LOGOUT);
					break;
				}
			}
		});

		window.roomPlayerListTable.getTable().setMenu(window.roomPlayerMenu);
		window.roomPlayerListTable.getTable().addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				boolean isMasterAndOtherSelected = false;
				switch (currentRoomState) {
				case RoomMaster:
				case ProxyRoomMaster:
					IStructuredSelection selection = (IStructuredSelection) window.roomPlayerListTable.getSelection();
					Player player = (Player) selection.getFirstElement();
					if (player != null && !roomMasterName.equals(player.getName())) {
						isMasterAndOtherSelected = true;
					}
					break;
				}

				window.roomPlayerKickMenuItem.setEnabled(isMasterAndOtherSelected);
				if (currentRoomState == RoomState.ProxyRoomMaster) {
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
			@Override
			public void handleEvent(Event event) {
				String roomAddress = roomServerAddressPort + ":" + roomMasterName;
				window.clipboard.setContents(new Object[] { roomAddress }, window.textTransfers);
			}
		});

		window.statusServerAddressLabel.setMenu(window.statusServerAddressMenu);
		window.statusServerAddressLabel.addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				switch (currentRoomState) {
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
		packetMonitorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				int intervalMillis = 1000;

				Runnable refreshAction = new Runnable() {
					@Override
					public void run() {
						try {
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

				while (!shell.isDisposed()) {
					try {
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
							// case RoomMaster:
							case RoomParticipant:
							case ProxyRoomMaster:
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

	private void startRoomServer() throws IOException {
		int port = window.roomFormServerModePortSpinner.getSelection();

		if (!checkConfigUserName())
			return;

		String title = window.roomFormTitleText.getText();
		if (Utility.isEmpty(title)) {
			appendLogTo(window.roomChatLogText, "部屋名を入力してください", window.colorRoomInfo, false);
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

			lastUpdatedMaxPlayers = window.roomFormMaxPlayersSpiner.getSelection();

			window.roomFormServerModePortSpinner.setEnabled(false);
			window.roomFormServerModePortButton.setEnabled(false);
			window.roomFormModeSelectionCombo.setEnabled(false);
		} catch (BindException e) {
			appendLogTo(window.roomChatLogText, "すでに同じポートが使用されています", window.colorLogError, false);
		} catch (RuntimeException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
		}
	}

	private void connectToRoomServerAsParticipant() {
		if (!checkConfigUserName())
			return;

		String address = window.roomFormClientModeAddressCombo.getText();
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
					window.roomFormClientModeAddressCombo.setText(roomServerAddressPort);
				} else {
					window.roomFormClientModeAddressCombo.setText(roomServerAddressPort + ":" + roomMasterName);
				}
			} else {
				roomServerAddressPort = hostname + ":" + port;
			}
			roomConnection = tcpClient.connect(socketAddress, roomClientHandler);
			goTo(RoomState.ConnectingToRoomServer);
			return;
		} catch (UnresolvedAddressException e) {
			appendLogTo(window.roomChatLogText, "アドレスが解決しません", window.colorRed, false);
		} catch (RuntimeException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
		}
	}

	private void connectToProxyServerAsMaster() {
		if (!checkConfigUserName())
			return;

		String title = window.roomFormTitleText.getText();
		if (Utility.isEmpty(title)) {
			appendLogTo(window.roomChatLogText, "部屋名を入力してください", window.colorRoomInfo, false);
			window.roomFormTitleText.setFocus();
			return;
		}

		String address = window.roomFormProxyModeAddressCombo.getText();
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
				window.roomFormProxyModeAddressCombo.setText(roomServerAddressPort);
			} else {
				roomServerAddressPort = address;
			}
			roomConnection = tcpClient.connect(socketAddress, roomClientHandler);
			roomMasterName = loginUserName;
			goTo(RoomState.ConnectingToProxyServer);
			return;
		} catch (RuntimeException e) {
			appendLogTo(window.logText, Utility.makeStackTrace(e));
		}
	}

	private boolean connectToSearchServerAsMaster() {
		switch (currentRoomState) {
		case RoomMaster:
		case ProxyRoomMaster:
			if (roomPlayerMap.size() == lastUpdatedMaxPlayers) {
				appendLogTo(window.roomChatLogText, "部屋が満員なので検索には登録できません", window.colorLogError, false);
				return false;
			}
			String address = window.roomFormSearchServerCombo.getText();
			if (Utility.isEmpty(address)) {
				appendLogTo(window.roomChatLogText, "検索サーバーアドレスを入力してください", window.colorLogError, false);
				return false;
			}

			String[] tokens = address.split(":");

			switch (tokens.length) {
			case 2:
				break;
			default:
				appendLogTo(window.roomChatLogText, "検索サーバーアドレスが正しくありません", window.colorLogError, false);
				return false;
			}

			int port;
			try {
				port = Integer.parseInt(tokens[1]);
			} catch (NumberFormatException e) {
				appendLogTo(window.roomChatLogText, "検索サーバーアドレスが正しくありません", window.colorLogError, false);
				return false;
			}

			try {
				InetSocketAddress socketAddress = new InetSocketAddress(tokens[0], port);

				searchEntryConnection = tcpClient.connect(socketAddress, searchEntryHandler);

				window.roomFormSearchServerCombo.setEnabled(false);
				window.roomFormSearchServerButton.setEnabled(false);
				return true;
			} catch (RuntimeException e) {
				appendLogTo(window.logText, Utility.makeStackTrace(e));
			}
			break;
		}
		return false;
	}

	private void connectToSearchServerAsParticipant() {
		String address = window.searchServerAddressCombo.getText();
		if (Utility.isEmpty(address)) {
			updateSearchResultStatus("検索サーバーアドレスを入力してください", window.colorLogError);
			return;
		}

		String[] tokens = address.split(":");

		switch (tokens.length) {
		case 2:
			break;
		default:
			updateSearchResultStatus("検索サーバーアドレスが正しくありません", window.colorLogError);
			return;
		}

		int port;
		try {
			port = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			updateSearchResultStatus("検索サーバーアドレスが正しくありません", window.colorLogError);
			return;
		}

		try {
			InetSocketAddress socketAddress = new InetSocketAddress(tokens[0], port);
			ISocketConnection connection = tcpClient.connect(socketAddress, searchQueryHandler);

			window.searchServerSubmitButton.setEnabled(false);
			window.statusSearchResultLabel.setText("");

			searchResultRoomList.clear();
			window.searchResultRoomsTable.refresh();
			window.searchResultRoomsTable.setSorter(null);
			window.searchResultRoomsTable.getTable().setSortDirection(SWT.NONE);
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
		case RoomMaster:
			roomEngine.setTitle(title);
			roomEngine.setMaxPlayers(window.roomFormMaxPlayersSpiner.getSelection());
			roomEngine.setPassword(window.roomFormPasswordText.getText());
			roomEngine.setDescription(window.roomFormDescriptionText.getText());

			roomEngine.updateRoom();

			appendLogTo(window.roomChatLogText, "部屋情報を更新しました", window.colorRoomInfo, false);
			window.roomChatText.setFocus();

			break;
		case ProxyRoomMaster:
			StringBuilder sb = new StringBuilder();
			sb.append(ProtocolConstants.Room.COMMAND_ROOM_UPDATE);
			appendRoomInfo(sb);

			roomConnection.send(sb.toString());

			break;
		}

		lastUpdatedMaxPlayers = window.roomFormMaxPlayersSpiner.getSelection();

		return true;
	}

	private void sendChat() {
		String command = window.roomChatText.getText();
		if (!Utility.isEmpty(command)) {
			switch (currentRoomState) {
			case RoomMaster:
				roomEngine.sendChat(command);
				window.roomChatText.setText("");
				break;
			case RoomParticipant:
			case ProxyRoomMaster:
				roomConnection.send(ProtocolConstants.Room.COMMAND_CHAT + ProtocolConstants.ARGUMENT_SEPARATOR + command);
				window.roomChatText.setText("");
				break;
			default:
				appendLogTo(window.roomChatLogText, "サーバーにログインしていません", window.colorLogInfo, false);
			}
		}
	}

	private void appendLogTo(final StyledText text, final String message, final Color color, final boolean inform) {
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
					if (inform && !window.isActive && window.toolTip != null) {
						window.toolTip.setText(shell.getText());
						window.toolTip.setMessage(message);
						window.toolTip.setVisible(true);
					}
				} catch (SWTException e) {
				}
			}
		};
		asyncExec(run);
	}

	private void appendLogTo(final Text text, final String message) {
		if (Utility.isEmpty(message))
			return;

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
					switch (currentRoomState) {
					case Offline:
						window.statusServerAddressLabel.setText("部屋にログインしていません");
						break;
					default:
						String roomAddress;
						if (roomMasterName.equals("")) {
							roomAddress = roomServerAddressPort;
						} else {
							roomAddress = roomServerAddressPort + ":" + roomMasterName;
						}

						window.statusServerAddressLabel.setText("部屋サーバー  " + roomAddress);
					}
					window.statusBarContainer.layout();
				} catch (SWTException e) {
				}
			}
		});
	}

	private void updateTunnelStatus(boolean isLinked) {
		tunnelIsLinked = isLinked;

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

		if (tunnelIsLinked)
			wakeupThread(packetMonitorThread);
	}

	private void updateSearchResultStatus(final String message, final Color color) {
		asyncExec(new Runnable() {
			@Override
			public void run() {
				window.statusSearchResultLabel.setText(message);
				window.statusSearchResultLabel.setForeground(color);
				window.statusBarContainer.layout();
			}
		});
	}

	private void replacePlayerList(final TableViewer viewer, final String[] players) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				try {
					viewer.getTable().clearAll();
					roomPlayerMap.clear();
					for (String name : players) {
						Player player = new Player(name);
						roomPlayerMap.put(name, player);
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
				appendLogTo(window.roomChatLogText, name + " が入室しました", window.colorLogInfo, true);
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

	private void removeExitingPlayer(String name) {
		appendLogTo(window.roomChatLogText, name + " が退室しました", window.colorLogInfo, true);
		removePlayer(window.roomPlayerListTable, name);
	}

	private void removeKickedPlayer(String name) {
		switch (currentRoomState) {
		case RoomMaster:
		case ProxyRoomMaster:
			appendLogTo(window.roomChatLogText, name + " を部屋から追い出しました", window.colorRoomInfo, true);
			break;
		case RoomParticipant:
			appendLogTo(window.roomChatLogText, name + " は部屋から追い出されました", window.colorRoomInfo, true);
			break;
		}
		removePlayer(window.roomPlayerListTable, name);
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
					window.roomPlayerListTable.refresh(player);
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
							window.roomFormProxyModeAddressCombo.setEnabled(false);
							window.roomFormProxyModeAddressCombo.setText(roomServerAddressPort);
							goTo(RoomState.ProxyRoomMaster);
						} else if (currentRoomState == RoomState.ProxyRoomMaster) {
							window.roomFormClientModeAddressCombo.setEnabled(false);
							window.roomFormClientModeAddressCombo.setText(roomServerAddressPort + ":" + masterName);
							goTo(RoomState.RoomParticipant);
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

	private void goTo(final RoomState state) {
		currentRoomState = state;

		asyncExec(new Runnable() {
			@Override
			public void run() {
				try {
					switch (state) {
					case Offline:
						window.statusServerAddressLabel.setText("部屋にログインしていません");
						window.statusBarContainer.layout();

						roomPlayerMap.clear();
						window.roomPlayerListTable.refresh();

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

						// window.mainTabFolder.setSelection(window.playRoomTab);
						disconnectMasterSearch();

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

						disconnectMasterSearch();
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
		if (searchEntryConnection != null && searchEntryConnection.isConnected()) {
			StringBuilder sb = new StringBuilder();

			sb.append(ProtocolConstants.Search.COMMAND_UPDATE);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(window.roomFormTitleText.getText());
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(window.roomFormMaxPlayersSpiner.getSelection());
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(window.roomFormPasswordText.getText().length() > 0 ? "Y" : "N");
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(window.roomFormDescriptionText.getText());

			searchEntryConnection.send(sb.toString());
		}
	}

	private void updateMasterSearchPlayerCount() {
		if (searchEntryConnection != null && searchEntryConnection.isConnected()) {
			if (roomPlayerMap.size() >= lastUpdatedMaxPlayers) {
				appendLogTo(window.roomChatLogText, "部屋が満員になりましたので、検索登録を解除します", window.colorServerInfo, false);
				searchEntryConnection.send(ProtocolConstants.Search.COMMAND_LOGOUT);
			} else {
				searchEntryConnection.send(ProtocolConstants.Search.COMMAND_UPDATE_PLAYER_COUNT + ProtocolConstants.ARGUMENT_SEPARATOR
						+ roomPlayerMap.size());
			}
		}
	}

	private void disconnectMasterSearch() {
		if (searchEntryConnection != null && searchEntryConnection.isConnected())
			searchEntryConnection.send(ProtocolConstants.Search.COMMAND_LOGOUT);
	}

	private class SearchEntryHandler implements IAsyncClientHandler {
		private HashMap<String, CommandHandler> handlers = new HashMap<String, CommandHandler>();
		private boolean isEntryCompleted = false;

		private SearchEntryHandler() {
			handlers.put(Search.COMMAND_ENTRY, new RoomEntryHandler());
			handlers.put(ProtocolConstants.ERROR_PROTOCOL_MISMATCH, new ErrorProtocolMismatchHandler());
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
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(ProtocolConstants.PROTOCOL_NUMBER);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

					sb.append(ProtocolConstants.Search.COMMAND_LOGIN);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(ProtocolConstants.Search.MODE_MASTER);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

					sb.append(ProtocolConstants.Search.COMMAND_ENTRY);
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

					searchEntryConnection.send(sb.toString());
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
					switch (currentRoomState) {
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
						appendLogTo(window.roomChatLogText, "検索サーバーの登録を解除しました", window.colorRoomInfo, false);
					} else {
						appendLogTo(window.roomChatLogText, "検索サーバーに登録できませんでした", window.colorRoomInfo, false);
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
			public void process(String argument) {
				asyncExec(new Runnable() {
					@Override
					public void run() {
						window.roomFormSearchServerButton.setText("登録解除");
						window.roomFormSearchServerButton.setEnabled(true);
						window.configRoomServerHostNameText.setEnabled(false);

						isEntryCompleted = true;
						entrySearchServerHistoryManager.addCurrentItem();
						appendLogTo(window.roomChatLogText, "検索サーバーに登録しました", window.colorRoomInfo, false);
					}
				});
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
				appendLogTo(window.roomChatLogText, "TCPポートが開放されていません", window.colorLogError, false);
			}
		}

		private class ErrorUdpPortHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "UDPポートが開放されていません", window.colorLogError, false);
			}
		}

		private class ErrorInvalidAuthCodeHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "自分の部屋以外の登録はできません", window.colorLogError, false);
			}
		}

		private class ErrorDatabaseEntryHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				appendLogTo(window.roomChatLogText, "検索サーバーのデータベースエラーが発生しました", window.colorLogError, false);
			}
		}
	}

	private class SearchQueryHandler implements IAsyncClientHandler {
		private HashMap<ISocketConnection, Object> connections = new HashMap<ISocketConnection, Object>();
		private final Object connectSuccess = new Object();
		private final Object searchSuccess = new Object();

		private SearchQueryHandler() {
		}

		@Override
		public void log(ISocketConnection connection, String message) {
			appendLogTo(window.logText, message);
		}

		@Override
		public void connectCallback(final ISocketConnection connection) {
			connections.put(connection, connectSuccess);
			asyncExec(new Runnable() {
				@Override
				public void run() {
					StringBuilder sb = new StringBuilder();

					sb.append(ProtocolConstants.Search.PROTOCOL_NAME);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(ProtocolConstants.PROTOCOL_NUMBER);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

					sb.append(ProtocolConstants.Search.COMMAND_LOGIN);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(ProtocolConstants.Search.MODE_PARTICIPANT);
					sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

					sb.append(ProtocolConstants.Search.COMMAND_SEARCH);
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(window.searchFormTitleCombo.getText());
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(window.searchFormMasterNameCombo.getText());
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(window.searchFormServerNameCombo.getText());
					sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
					sb.append(window.searchFormHasPassword.getSelection() ? "Y" : "N");

					connection.send(sb.toString());
				}
			});
		}

		@Override
		public void disconnectCallback(final ISocketConnection connection) {
			asyncExec(new Runnable() {
				@Override
				public void run() {
					window.searchResultRoomsTable.setInput(searchResultRoomList);
					window.searchServerSubmitButton.setEnabled(true);

					Object result = connections.remove(connection);
					if (result == null) {
						updateSearchResultStatus("検索サーバーに接続できません", window.colorRed);
					} else if (result == connectSuccess) {
						updateSearchResultStatus("検索サーバーではありません", window.colorRed);
					} else if (result == searchSuccess) {
						updateSearchResultStatus("検索結果: " + searchResultRoomList.size() + "件", window.colorBlack);
						querySearchServerHistoryManager.addCurrentItem();

						queryRoomTitleHistoryManager.addCurrentItem();
						queryRoomMasterNameHistoryManager.addCurrentItem();
						queryRoomAddressHistoryManager.addCurrentItem();
					}
				}
			});
		}

		@Override
		public void readCallback(ISocketConnection connection, PacketData data) {
			boolean searchResultReturned = false;
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

				if (command.equals(ProtocolConstants.Search.COMMAND_SEARCH)) {
					processSearchResult(argument, connection.getRemoteAddress());
					searchResultReturned = true;
				} else if (command.equals(ProtocolConstants.ERROR_PROTOCOL_MISMATCH)) {
					String error = String.format("サーバーとのプロトコルナンバーが一致しません サーバー:%s クライアント:%s", argument, ProtocolConstants.PROTOCOL_NUMBER);
					updateSearchResultStatus(error, window.colorLogError);
				}
			}
			if (searchResultReturned) {
				connections.put(connection, searchSuccess);
			}
		}

		private void processSearchResult(String argument, InetSocketAddress remoteAddress) {
			// S address master title currentPlayers maxPlayers hasPassword
			// description
			if (Utility.isEmpty(argument)) {
				return;
			}

			String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
			if (tokens.length != 7)
				return;

			String address = tokens[0];
			if (address.startsWith(":")) {
				address = remoteAddress.getHostName() + address;
			}

			String masterName = tokens[1];
			String title = tokens[2];
			int currentPlayers = Integer.parseInt(tokens[3]);
			int maxPlayers = Integer.parseInt(tokens[4]);
			boolean hasPassword = "Y".equals(tokens[5]);
			String description = tokens[6].replace("\n", " ");

			PlayRoom room = new PlayRoom(address, masterName, title, hasPassword, currentPlayers, maxPlayers);
			room.setDescription(description);
			searchResultRoomList.add(room);
		}
	}

	private class RoomServerHandler implements IRoomMasterHandler {
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
			addPlayer(window.roomPlayerListTable, player);
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
		public void tunnelPacketReceived(ByteBuffer packet, String playerName) {
			processRemotePspPacket(packet, playerName);
		}

		@Override
		public void roomOpened(String authCode) {
			roomMasterAuthCode = authCode;
			goTo(RoomState.RoomMaster);
			updateServerAddress();
			appendLogTo(window.roomChatLogText, "自部屋を起動しました", window.colorRoomInfo, false);
			addPlayer(window.roomPlayerListTable, loginUserName);
		}

		@Override
		public void roomClosed() {
			disconnectMasterSearch();
			goTo(RoomState.Offline);
			appendLogTo(window.roomChatLogText, "自部屋を停止しました", window.colorRoomInfo, false);
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
			handlers.put(ProtocolConstants.Room.NOTIFY_USER_ENTERED, new NotifyUserEnteredHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_USER_EXITED, new NotifyUserExitedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_USER_LIST, new NotifyUserListHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED, new NotifyRoomUpdatedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_KICKED, new NotifyRoomPlayerKickedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_MASTER_AUTH_CODE, new NotifyRoomMasterAuthCodeHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_PASSWORD_REQUIRED, new NotifyRoomPasswordRequiredHandler());
			handlers.put(ProtocolConstants.Room.ERROR_LOGIN_DUPLICATED_NAME, new ErrorLoginDuplicatedNameHandler());
			handlers.put(ProtocolConstants.Room.ERROR_LOGIN_ROOM_NOT_EXIST, new ErrorLoginRoomNotExistHandler());
			handlers.put(ProtocolConstants.Room.ERROR_LOGIN_BEYOND_CAPACITY, new ErrorLoginBeyondCapacityHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_INVALID_DATA_ENTRY, new ErrorRoomInvalidDataEntryHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_PASSWORD_NOT_ALLOWED, new ErrorRoomPasswordNotAllowedHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_ENTER_PASSWORD_FAIL, new ErrorRoomEnterPasswordFailHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_ENTER_BEYOND_CAPACITY, new ErrorRoomEnterBeyondCapacityHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_CREATE_DUPLICATED_NAME, new ErrorRoomCreateDuplicatedNameHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_CREATE_BEYOND_LIMIT, new ErrorRoomCreateBeyondLimitHandler());
			handlers.put(ProtocolConstants.Room.ERROR_ROOM_TRANSFER_DUPLICATED_NAME, new ErrorRoomTransferDuplicatedNameHandler());
		}

		@Override
		public void log(ISocketConnection connection, String message) {
			appendLogTo(window.logText, message);
		}

		@Override
		public void connectCallback(ISocketConnection connection) {
			switch (currentRoomState) {
			case ConnectingToRoomServer:
				appendLogTo(window.roomChatLogText, "サーバーに接続しました", window.colorServerInfo, false);

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Room.PROTOCOL_NAME);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(ProtocolConstants.PROTOCOL_NUMBER);
				sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

				sb.append(ProtocolConstants.Room.COMMAND_LOGIN);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(loginUserName);
				sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
				sb.append(roomMasterName);

				roomConnection.send(sb.toString());

				break;
			case ConnectingToProxyServer:
				asyncExec(new Runnable() {
					@Override
					public void run() {
						try {
							StringBuilder sb = new StringBuilder();
							sb.append(ProtocolConstants.Room.PROTOCOL_NAME);
							sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
							sb.append(ProtocolConstants.PROTOCOL_NUMBER);
							sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

							sb.append(ProtocolConstants.Room.COMMAND_ROOM_CREATE);
							sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
							sb.append(loginUserName);
							appendRoomInfo(sb);

							roomConnection.send(sb.toString());

							lastUpdatedMaxPlayers = window.roomFormMaxPlayersSpiner.getSelection();
						} catch (SWTException e) {
						}
					}
				});

				break;
			}
			currentRoomState = RoomState.Negotiating;
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
			switch (currentRoomState) {
			case ConnectingToRoomServer:
			case ConnectingToProxyServer:
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
				syncExec(new Runnable() {
					@Override
					public void run() {
						goTo(RoomState.RoomParticipant);

						updateRoom(args.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1), true);
						updateServerAddress();

						roomServerHistoryManager.addCurrentItem();
						appendLogTo(window.roomChatLogText, "部屋に入りました  ", window.colorRoomInfo, false);
					}
				});

				prepareSession();
			}
		}

		private class RoomCreateHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				syncExec(new Runnable() {
					@Override
					public void run() {
						goTo(RoomState.ProxyRoomMaster);
						appendLogTo(window.roomChatLogText, "代理サーバーで部屋を作成しました", window.colorRoomInfo, false);

						window.roomFormMasterText.setText(loginUserName);
						addPlayer(window.roomPlayerListTable, loginUserName);
						updateServerAddress();

						proxyServerHistoryManager.addCurrentItem();

						prepareSession();
					}
				});
			}
		}

		private class ChatHandler implements CommandHandler {
			@Override
			public void process(String args) {
				switch (currentRoomState) {
				case RoomMaster:
				case RoomParticipant:
				case ProxyRoomMaster:
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
					case RoomMaster:
					case RoomParticipant:
					case ProxyRoomMaster:
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

		private class NotifyUserEnteredHandler implements CommandHandler {
			@Override
			public void process(String name) {
				switch (currentRoomState) {
				case ProxyRoomMaster:
				case RoomParticipant:
					addPlayer(window.roomPlayerListTable, name);
					break;
				}
			}
		}

		private class NotifyUserExitedHandler implements CommandHandler {
			@Override
			public void process(String name) {
				switch (currentRoomState) {
				case ProxyRoomMaster:
				case RoomParticipant:
					removeExitingPlayer(name);
					break;
				}
			}
		}

		private class NotifyUserListHandler implements CommandHandler {
			@Override
			public void process(String args) {
				String[] players = args.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				switch (currentRoomState) {
				case RoomParticipant:
				case ProxyRoomMaster:
					replacePlayerList(window.roomPlayerListTable, players);
					break;
				}
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
				appendLogTo(window.roomChatLogText, "部屋情報を修正しました", window.colorRoomInfo, false);
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
				switch (currentRoomState) {
				case ConnectingToProxyServer:
					appendLogTo(window.roomChatLogText, "サーバーの最大部屋数を超えたので部屋を作成できません", window.colorLogError, false);
					break;
				default:
					appendLogTo(window.roomChatLogText, "サーバーの最大人数を超えたのでログインできません", window.colorLogError, false);
				}
			}
		}

		private void promptPassword() {
			asyncExec(new Runnable() {
				@Override
				public void run() {
					try {
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
			});
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
				appendLogTo(window.roomChatLogText, "部屋パスワードが違います", window.colorRoomInfo, false);
				promptPassword();
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
				appendLogTo(window.roomChatLogText, "このサーバーではパスワードが禁止されています", window.colorLogError, false);
			}
		}

		private class ErrorRoomCreateDuplicatedNameHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "同名のユーザーで既に部屋が作成されているので作成できません", window.colorLogError, false);
			}
		}

		private class ErrorRoomEnterBeyondCapacityHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(window.roomChatLogText, "部屋が満室なので入れません", window.colorRoomInfo, false);
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

		String lastUsedMacAddress = iniSettingSection.get(IniConstants.Client.LAST_LAN_ADAPTER, "");
		int lastUsedIndex = 0;

		IniParser.Section nicSection = iniParser.getSection(IniConstants.Client.SECTION_LAN_ADAPTERS);

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

		srcStats.lastModified = System.currentTimeMillis();
		srcStats.currentInBytes += packet.limit();
		srcStats.totalInBytes += packet.limit();
		if (!Utility.isEmpty(playerName)) {
			srcStats.playerName = playerName;
		} else if (Utility.isEmpty(srcStats.playerName) && !Utility.isMacBroadCastAddress(srcMac)) {
			roomConnection.send(ProtocolConstants.Room.COMMAND_MAC_ADDRESS_PLAYER + ProtocolConstants.ARGUMENT_SEPARATOR + srcMac);
		}

		destStats.lastModified = srcStats.lastModified;
		destStats.currentInBytes += packet.limit();
		destStats.totalInBytes += packet.limit();
		if (destStats.isMine)
			destStats.playerName = loginUserName;

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

			int packetLength = packet.size();

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

				bufferForCapturing.clear();
				packet.transferTo(bufferForCapturing);
				bufferForCapturing.flip();

				switch (currentRoomState) {
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

		return true;
	}

	public void start() {
		int minWidth = 650, minHeight = 400;

		shell.setMinimumSize(new Point(minWidth, minHeight));

		shell.setSize(iniSettingSection.get(IniConstants.Client.WINDOW_WIDTH, minWidth),
				iniSettingSection.get(IniConstants.Client.WINDOW_HEIGHT, minHeight));
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
