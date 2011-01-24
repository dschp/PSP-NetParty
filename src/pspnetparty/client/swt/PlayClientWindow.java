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
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
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
import pspnetparty.lib.Constants;
import pspnetparty.lib.IAsyncClient;
import pspnetparty.lib.IAsyncClientHandler;
import pspnetparty.lib.IRoomMasterHandler;
import pspnetparty.lib.IniParser;
import pspnetparty.lib.PacketData;
import pspnetparty.lib.RoomEngine;
import pspnetparty.lib.Utility;

public class PlayClientWindow {

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

	private RoomEngine roomEngine;
	private AsyncTcpClient roomClient;
	private AsyncUdpClient tunnelClient;

	private HashMap<String, Player> roomPlayerMap = new LinkedHashMap<String, Player>();

	private String loginUserName;
	private boolean isArenaEntryExitLogEnabled = true;
	private boolean isRoomInfoUpdating = false;

	private String roomMasterName;
	private String roomServerAddressPort;
	private InetSocketAddress roomServerSocketAddress;
	private boolean tunnelIsLinked = false;
	private boolean isPacketCapturing = false;

	private ByteBuffer bufferForCapturing = ByteBuffer.allocate(CAPTURE_BUFFER_SIZE);
	private ArrayList<PcapIf> wlanAdaptorList = new ArrayList<PcapIf>();
	private HashMap<PcapIf, String> wlanAdaptorMacAddressMap = new HashMap<PcapIf, String>();
	private Pcap currentPcapDevice;

	private HashMap<String, TraficStatistics> traficStatsMap = new HashMap<String, TraficStatistics>();

	private int roomServerListCount = 0;
	private LinkedList<String> roomServerHistory = new LinkedList<String>();

	private Thread packetMonitorThread;
	private Thread packetCaptureThread;
	private Thread pingThread;
	private Thread natTableMaintainingThread;

	public PlayClientWindow(IniParser iniParser) {
		this.iniParser = iniParser;
		this.iniSettingSection = iniParser.getSection(Constants.Ini.SECTION_SETTINGS);

		initializeComponents();
		initializeComponentListeners();

		goTo(OperationMode.Offline);

		roomEngine = new RoomEngine(new RoomServerHandler());
		roomClient = new AsyncTcpClient(new RoomClientHandler());
		tunnelClient = new AsyncUdpClient(new TunnelHandler());

		refreshLanAdaptorList();

		configUserNameText.setText(iniSettingSection.get(Constants.Ini.CLIENT_LOGIN_NAME, ""));

		String[] serverList = iniSettingSection.get(Constants.Ini.CLIENT_SERVER_LIST, "").split(",");
		for (String s : serverList) {
			if (Utility.isEmpty(s))
				continue;
			roomFormClientModeAddressCombo.add(s);
			roomServerListCount++;
		}

		roomFormClientModeAddressCombo.add("----------履歴----------");

		serverList = iniSettingSection.get(Constants.Ini.CLIENT_SERVER_HISTORY, "").split(",");
		for (String s : serverList) {
			if (Utility.isEmpty(s))
				continue;
			roomFormClientModeAddressCombo.add(s);
			roomServerHistory.add(s);
			if (roomServerHistory.size() == MAX_SERVER_HISTORY)
				break;
		}

		String software = String.format("%s プレイクライアント バージョン: %s", Constants.App.APP_NAME, Constants.App.VERSION);
		appendLogTo(roomChatLogText, software, colorAppInfo);
		appendLogTo(roomChatLogText, "プロトコル: " + Constants.Protocol.PROTOCOL_NUMBER, colorAppInfo);

		startBackgroundThreads();
	}

	private TabFolder mainTabFolder;
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
	private Label wlanAdaptorListLabel;
	private Combo wlanAdaptorListCombo;
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
	private Button configLogLobbyEnterExit;
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

	private void initializeComponents() {
		display = new Display();
		shell = new Shell(display);

		// ImageData imageData = new
		// ImageData("F:\\workspace\\PspNetParty\\PNP.ico");
		// Image image = new Image(display, imageData);
		// shell.setImage(image);

		FormData formData;
		GridLayout gridLayout;
		GridData gridData;

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
		roomFormMaxPlayersSpiner.setMaximum(Constants.Protocol.MAX_ROOM_PLAYERS);
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

		wlanAdaptorListLabel = new Label(wlanAdaptorContainer, SWT.NONE);
		wlanAdaptorListLabel.setText("無線LANアダプタ");

		wlanAdaptorListCombo = new Combo(wlanAdaptorContainer, SWT.READ_ONLY);
		wlanAdaptorListCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

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
		configContainer.setLayout(new GridLayout(3, false));
		configTab.setControl(configContainer);

		configUserNameLabel = new Label(configContainer, SWT.NONE);
		configUserNameLabel.setText("ユーザー名");

		configUserNameText = new Text(configContainer, SWT.SINGLE | SWT.BORDER);
		configUserNameText.setLayoutData(new GridData(150, SWT.DEFAULT));
		configUserNameText.setTextLimit(80);

		configUserNameAlertLabel = new Label(configContainer, SWT.NONE);
		configUserNameAlertLabel.setText("ユーザー名を入力してください");
		configUserNameAlertLabel.setForeground(colorLogError);
		// configUserNameAlertLabel.setVisible(false);

		configLogLobbyEnterExit = new Button(configContainer, SWT.CHECK | SWT.FLAT);
		configLogLobbyEnterExit.setText("アリーナロビーのチャットログに入退室メッセージを表示する");
		configLogLobbyEnterExit.setSelection(true);
		gridData = new GridData();
		gridData.horizontalSpan = 3;
		configLogLobbyEnterExit.setLayoutData(gridData);

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
		formData = new FormData();
		formData.left = new FormAttachment(statusServerAddressLabel, 5);
		statusServerStatusLabel.setLayoutData(formData);

		statusTraficStatusLabel = new Label(statusBarContainer, SWT.BORDER);
		statusTraficStatusLabel.setText("トラフィック");
		formData = new FormData();
		formData.right = new FormAttachment(100, -20);
		statusTraficStatusLabel.setLayoutData(formData);

		roomInfoSashForm.setWeights(new int[] { 5, 2 });
		roomSubSashForm.setWeights(new int[] { 1, 2 });
		roomMainSashForm.setWeights(new int[] { 3, 7 });
	}

	private void initializeComponentListeners() {
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				roomEngine.closeRoom();
				roomClient.disconnect();
				tunnelClient.disconnect();

				isPacketCapturing = false;

				iniSettingSection.set(Constants.Ini.CLIENT_LOGIN_NAME, configUserNameText.getText());

				Point size = shell.getSize();
				iniSettingSection.set(Constants.Ini.CLIENT_WINDOW_WIDTH, Integer.toString(size.x));
				iniSettingSection.set(Constants.Ini.CLIENT_WINDOW_HEIGHT, Integer.toString(size.y));

				if (roomServerListCount == 0) {
					iniSettingSection.set(Constants.Ini.CLIENT_SERVER_LIST, "");
				}

				StringBuilder sb = new StringBuilder();
				for (String s : roomServerHistory) {
					sb.append(s).append(',');
				}
				if (sb.length() > 0) {
					sb.deleteCharAt(sb.length() - 1);
				}
				iniSettingSection.set(Constants.Ini.CLIENT_SERVER_HISTORY, sb.toString());

				int index = wlanAdaptorListCombo.getSelectionIndex() - 1;
				if (index == -1) {
					iniSettingSection.set(Constants.Ini.CLIENT_LAST_LAN_ADAPTOR, "");
				} else {
					PcapIf device = wlanAdaptorList.get(index);
					iniSettingSection.set(Constants.Ini.CLIENT_LAST_LAN_ADAPTOR, wlanAdaptorMacAddressMap.get(device));
				}

				try {
					iniParser.saveToIni();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});

		roomFormModeSelectionCombo.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				updateRoomModeSelection();
			}
		});

		roomFormServerModePortButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				try {
					if (roomEngine.isStarted()) {
						roomFormServerModePortButton.setEnabled(false);
						roomEngine.closeRoom();
					} else {
						startRoomServer();
					}
				} catch (IOException e) {
					appendLogTo(logText, Utility.makeStackTrace(e));
				}
			}
		});

		roomFormClientModeAddressCombo.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.character) {
				case ' ':
					e.doit = false;
					break;
				case SWT.CR:
				case SWT.LF:
					e.doit = false;
					connectToRoomServer();
					break;
				}
			}
		});
		roomFormClientModeAdderssButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (currentOperationMode == OperationMode.Offline) {
					connectToRoomServer();
				} else {
					roomClient.send(Constants.Protocol.COMMAND_LOGOUT);
					tunnelClient.disconnect();
				}
			}
		});

		roomFormProxyModeAddressCombo.addKeyListener(new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.character) {
				case ' ':
					e.doit = false;
					break;
				case SWT.CR:
				case SWT.LF:
					e.doit = false;
					connectToProxyServer();
					break;
				}
			}
		});
		roomFormProxyModeAddressButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (currentOperationMode == OperationMode.Offline) {
					connectToProxyServer();
				} else {
					roomClient.send(Constants.Protocol.COMMAND_LOGOUT);
					tunnelClient.disconnect();
				}
			}
		});

		roomChatText.addKeyListener(new KeyListener() {
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
		roomChatSubmitButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				sendChat();
			}
		});

		configUserNameText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (Utility.isEmpty(configUserNameText.getText())) {
					configUserNameAlertLabel.setVisible(true);
					shell.setText(Constants.App.APP_NAME);
				} else {
					configUserNameAlertLabel.setVisible(false);
					shell.setText(configUserNameText.getText() + " - " + Constants.App.APP_NAME);
				}
			}
		});

		wlanAdaptorListCombo.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				wlanPspCommunicationButton.setEnabled(wlanAdaptorListCombo.getSelectionIndex() != 0);
			}
		});
		wlanPspCommunicationButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (wlanPspCommunicationButton.getSelection()) {
					if (startPacketCapturing()) {
						wlanPspCommunicationButton.setText("PSPと通信中");
						wlanAdaptorListCombo.setEnabled(false);
					} else {
						wlanPspCommunicationButton.setSelection(false);
					}
				} else {
					wlanPspCommunicationButton.setEnabled(false);
					isPacketCapturing = false;
				}
			}
		});

		configLogLobbyEnterExit.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				isArenaEntryExitLogEnabled = configLogLobbyEnterExit.getSelection();
			}
		});

		VerifyListener notAcceptSpaceListener = new VerifyListener() {
			@Override
			public void verifyText(VerifyEvent e) {
				if (" ".equals(e.text)) {
					e.doit = false;
				} else {
					switch (e.character) {
					case '\0':
						e.text = e.text.replace(" ", "").trim();
						break;
					}
				}
			}
		};
		roomFormTitleText.addVerifyListener(notAcceptSpaceListener);
		roomFormPasswordText.addVerifyListener(notAcceptSpaceListener);
		roomFormDescriptionText.addVerifyListener(notAcceptSpaceListener);

		roomFormClientModeAddressCombo.addVerifyListener(notAcceptSpaceListener);
		roomFormProxyModeAddressCombo.addVerifyListener(notAcceptSpaceListener);

		configUserNameText.addVerifyListener(notAcceptSpaceListener);

		ModifyListener roomEditFormModifyListener = new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (isRoomInfoUpdating)
					return;

				switch (currentOperationMode) {
				case RoomMaster:
				case ProxyRoomMaster:
					roomFormEditButton.setEnabled(true);
					break;
				}
			}
		};
		roomFormTitleText.addModifyListener(roomEditFormModifyListener);
		roomFormPasswordText.addModifyListener(roomEditFormModifyListener);
		roomFormDescriptionText.addModifyListener(roomEditFormModifyListener);
		roomFormMaxPlayersSpiner.addModifyListener(roomEditFormModifyListener);

		roomFormEditButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				String title = roomFormTitleText.getText();
				switch (currentOperationMode) {
				case RoomMaster:
					if (Utility.isEmpty(title)) {
						appendLogTo(roomChatLogText, "部屋名を入力してください", colorLogError);
						roomFormTitleText.setFocus();
						return;
					}

					roomEngine.setTitle(title);
					roomEngine.setMaxPlayers(roomFormMaxPlayersSpiner.getSelection());
					roomEngine.setPassword(roomFormPasswordText.getText());
					roomEngine.setDescription(roomFormDescriptionText.getText());

					roomEngine.updateRoom();

					roomFormEditButton.setEnabled(false);
					appendLogTo(roomChatLogText, "部屋情報を更新しました", colorRoomInfo);
					roomChatText.setFocus();
					break;
				case ProxyRoomMaster:
					if (Utility.isEmpty(title)) {
						appendLogTo(roomChatLogText, "部屋名を入力してください", colorLogError);
						roomFormTitleText.setFocus();
						return;
					}

					StringBuilder sb = new StringBuilder();
					sb.append(Constants.Protocol.COMMAND_ROOM_UPDATE);
					appendRoomInfo(sb);

					roomClient.send(sb.toString());

					break;
				}
			}
		});

		roomPlayerMenu = new Menu(shell, SWT.POP_UP);
		roomPlayerKickMenuItem = new MenuItem(roomPlayerMenu, SWT.PUSH);
		roomPlayerKickMenuItem.setText("キック");
		roomPlayerKickMenuItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				IStructuredSelection selection = (IStructuredSelection) roomPlayerListViewer.getSelection();
				Player player = (Player) selection.getFirstElement();
				if (player == null)
					return;

				String kickedName = player.getName();
				switch (currentOperationMode) {
				case RoomMaster:
					roomEngine.kickPlayer(kickedName);
					removePlayer(roomPlayerListViewer, kickedName);
					appendLogTo(roomChatLogText, kickedName + " を部屋から追い出しました", colorRoomInfo);
					break;
				case ProxyRoomMaster:
					roomClient.send(Constants.Protocol.COMMAND_ROOM_KICK_PLAYER + " " + kickedName);
					break;
				}
			}
		});

		roomPlayerMasterTransferMenuItem = new MenuItem(roomPlayerMenu, SWT.PUSH);
		roomPlayerMasterTransferMenuItem.setText("部屋主を委譲");
		roomPlayerMasterTransferMenuItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				IStructuredSelection selection = (IStructuredSelection) roomPlayerListViewer.getSelection();
				Player player = (Player) selection.getFirstElement();
				if (player == null)
					return;

				String newMasterName = player.getName();
				switch (currentOperationMode) {
				case ProxyRoomMaster:
					roomClient.send(Constants.Protocol.COMMAND_ROOM_MASTER_TRANSFER + " " + newMasterName);
					break;
				}
			}
		});

		roomPlayerListViewer.getTable().setMenu(roomPlayerMenu);
		roomPlayerListViewer.getTable().addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				boolean isMasterAndOtherSelected = false;
				switch (currentOperationMode) {
				case RoomMaster:
				case ProxyRoomMaster:
					IStructuredSelection selection = (IStructuredSelection) roomPlayerListViewer.getSelection();
					Player player = (Player) selection.getFirstElement();
					if (player != null && !roomMasterName.equals(player.getName())) {
						isMasterAndOtherSelected = true;
					}
					break;
				}

				roomPlayerKickMenuItem.setEnabled(isMasterAndOtherSelected);
				if (currentOperationMode == OperationMode.ProxyRoomMaster) {
					roomPlayerMasterTransferMenuItem.setEnabled(isMasterAndOtherSelected);
				} else {
					roomPlayerMasterTransferMenuItem.setEnabled(false);
				}
			}
		});
	}

	private void startBackgroundThreads() {
		packetMonitorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				int intervalMillis = 1000;

				Runnable refreshAction = new Runnable() {
					@Override
					public void run() {
						try {
							packetMonitorTableViewer.setInput(traficStatsMap);
							packetMonitorTableViewer.refresh();
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
							packetMonitorTableViewer.setInput(traficStatsMap);
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
		packetMonitorThread.start();

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
											wlanPspCommunicationButton.setEnabled(false);
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
									wlanAdaptorListCombo.setEnabled(true);
									wlanPspCommunicationButton.setText("PSPと通信開始");
									wlanPspCommunicationButton.setEnabled(true);
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
		packetCaptureThread.start();

		pingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!shell.isDisposed()) {
						synchronized (pingThread) {
							if (!roomClient.isConnected())
								pingThread.wait();
						}

						while (roomClient.isConnected()) {
							switch (currentOperationMode) {
							case RoomMaster:
							case RoomParticipant:
							case ProxyRoomMaster:
								roomClient.send(Constants.Protocol.COMMAND_PING + " " + System.currentTimeMillis());
							}

							Thread.sleep(5000);
						}
					}
				} catch (InterruptedException e) {
				}
			}
		}, "PingThread");
		pingThread.setDaemon(true);
		pingThread.start();

		natTableMaintainingThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!shell.isDisposed()) {
						synchronized (natTableMaintainingThread) {
							if (!tunnelClient.isConnected())
								natTableMaintainingThread.wait();
						}

						Thread.sleep(20000);

						while (tunnelClient.isConnected()) {
							tunnelClient.send(" ");
							Thread.sleep(20000);
						}
					}
				} catch (InterruptedException e) {
				}
			}
		}, "NatTableMaintaining");
		natTableMaintainingThread.setDaemon(true);
		natTableMaintainingThread.start();
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
		switch (roomFormModeSelectionCombo.getSelectionIndex()) {
		case 0:
			roomModeStackLayout.topControl = roomFormServerModeContainer;
			roomFormServerAddressPortLabel.setText("ポート");
			setEnableRoomFormItems(true);
			break;
		case 1:
			roomModeStackLayout.topControl = roomFormClientModeContainer;
			roomFormServerAddressPortLabel.setText("アドレス");
			setEnableRoomFormItems(false);
			break;
		case 2:
			roomModeStackLayout.topControl = roomFormProxyModeContainer;
			roomFormServerAddressPortLabel.setText("アドレス");
			setEnableRoomFormItems(true);
			break;
		}
		roomFormGridContainer.layout(true, true);
		// roomFormModeSwitchContainer.layout();
	}

	private void appendRoomInfo(StringBuilder sb) {
		sb.append(' ');
		sb.append(roomFormMaxPlayersSpiner.getText());
		sb.append(' ');
		sb.append(roomFormTitleText.getText());
		sb.append(" \"");
		sb.append(roomFormPasswordText.getText());
		sb.append("\" \"");
		sb.append(roomFormDescriptionText.getText());
		sb.append('"');
	}

	private void startRoomServer() throws IOException {
		int port = roomFormServerModePortSpinner.getSelection();

		loginUserName = configUserNameText.getText();
		if (Utility.isEmpty(loginUserName)) {
			mainTabFolder.setSelection(configTab);
			configUserNameText.setFocus();
			configUserNameAlertLabel.setVisible(true);
			return;
		}
		String title = roomFormTitleText.getText();
		if (Utility.isEmpty(title)) {
			appendLogTo(roomChatLogText, "部屋名を入力してください", colorRoomInfo);
			roomFormTitleText.setFocus();
			return;
		}

		roomEngine.setTitle(title);
		roomEngine.setMaxPlayers(roomFormMaxPlayersSpiner.getSelection());
		roomEngine.setPassword(roomFormPasswordText.getText());
		roomEngine.setDescription(roomFormDescriptionText.getText());

		try {
			roomEngine.openRoom(port, loginUserName);

			roomFormMasterText.setText(loginUserName);
			roomMasterName = loginUserName;

			roomFormServerModePortSpinner.setEnabled(false);
			roomFormServerModePortButton.setEnabled(false);
			roomFormModeSelectionCombo.setEnabled(false);
			addPlayer(roomPlayerListViewer, loginUserName);
		} catch (BindException e) {
			appendLogTo(roomChatLogText, "すでに同じポートが使用されています", colorLogError);
		}
	}

	private void connectToRoomServer() {
		String username = configUserNameText.getText();
		if (Utility.isEmpty(username)) {
			mainTabFolder.setSelection(configTab);
			configUserNameText.setFocus();
			configUserNameAlertLabel.setVisible(true);
			return;
		}
		this.loginUserName = username;

		String address = roomFormClientModeAddressCombo.getText();
		if (Utility.isEmpty(address)) {
			appendLogTo(roomChatLogText, "サーバーアドレスを入力してください", colorLogError);
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
			appendLogTo(roomChatLogText, "サーバーアドレスが正しくありません", colorLogError);
			return;
		}

		int port;
		try {
			port = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			appendLogTo(roomChatLogText, "サーバーアドレスが正しくありません", colorLogError);
			return;
		}

		roomServerAddressPort = tokens[0] + ":" + tokens[1];
		roomServerSocketAddress = new InetSocketAddress(tokens[0], port);
		try {
			roomClient.connect(roomServerSocketAddress);
			goTo(OperationMode.ConnectingToRoomServer);
		} catch (UnresolvedAddressException e) {
			appendLogTo(roomChatLogText, "アドレスが解決しません", colorRed);
		} catch (IOException e) {
			appendLogTo(logText, Utility.makeStackTrace(e));
			return;
		}
	}

	private void connectToProxyServer() {
		String username = configUserNameText.getText();
		if (Utility.isEmpty(username)) {
			mainTabFolder.setSelection(configTab);
			configUserNameText.setFocus();
			configUserNameAlertLabel.setVisible(true);
			return;
		}
		this.loginUserName = username;

		String title = roomFormTitleText.getText();
		if (Utility.isEmpty(title)) {
			appendLogTo(roomChatLogText, "部屋名を入力してください", colorRoomInfo);
			roomFormTitleText.setFocus();
			return;
		}

		String address = roomFormProxyModeAddressCombo.getText();
		if (Utility.isEmpty(address)) {
			appendLogTo(roomChatLogText, "サーバーアドレスを入力してください", colorLogError);
			return;
		}

		String[] tokens = address.split(":");

		switch (tokens.length) {
		case 2:
			break;
		default:
			appendLogTo(roomChatLogText, "サーバーアドレスが正しくありません", colorLogError);
			return;
		}

		int port;
		try {
			port = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			appendLogTo(roomChatLogText, "サーバーアドレスが正しくありません", colorLogError);
			return;
		}

		roomServerAddressPort = address;
		roomServerSocketAddress = new InetSocketAddress(tokens[0], port);
		try {
			roomClient.connect(roomServerSocketAddress);
			roomMasterName = loginUserName;
			goTo(OperationMode.ConnectingToProxyServer);
		} catch (IOException e) {
			appendLogTo(logText, Utility.makeStackTrace(e));
			return;
		}
	}

	private void sendChat() {
		String command = roomChatText.getText();
		if (!Utility.isEmpty(command)) {
			switch (currentOperationMode) {
			case RoomMaster:
				roomEngine.sendChat(command);
				roomChatText.setText("");
				break;
			case RoomParticipant:
			case ProxyRoomMaster:
				roomClient.send(Constants.Protocol.COMMAND_CHAT + " " + command);
				roomChatText.setText("");
				break;
			default:
				appendLogTo(roomChatLogText, "サーバーにログインしていません", colorRoomInfo);
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

	private void updateServerAddress(final String type) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				try {
					String text = String.format("%sサーバー  %s", type, roomFormModeSelectionCombo.getText());
					statusServerAddressLabel.setText(text);
					statusBarContainer.layout();
				} catch (SWTException e) {
				}
			}
		};
		asyncExec(run);
	}

	private void updateTunnelStatus(boolean isConnected) {
		tunnelIsLinked = isConnected;

		asyncExec(new Runnable() {
			@Override
			public void run() {
				try {
					if (tunnelIsLinked) {
						statusTunnelConnectionLabel.setForeground(colorGreen);
						statusTunnelConnectionLabel.setText(" UDPトンネル接続中 ");
					} else {
						statusTunnelConnectionLabel.setForeground(colorRed);
						statusTunnelConnectionLabel.setText(" UDPトンネル未接続 ");
					}
					statusBarContainer.layout();
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
					roomPlayerListViewer.refresh(player);
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

					roomFormMasterText.setText(masterName);
					roomFormMaxPlayersSpiner.setSelection(maxPlayers);
					roomFormTitleText.setText(title);
					roomFormPasswordText.setText(password);
					roomFormDescriptionText.setText(description);

					isRoomInfoUpdating = false;
					roomFormEditButton.setEnabled(false);

					if (isInitialUpdate)
						return;

					appendLogTo(roomChatLogText, "部屋情報が更新されました", colorRoomInfo);

					if (!masterName.equals(roomMasterName)) {
						roomMasterName = masterName;
						appendLogTo(roomChatLogText, "部屋主が " + roomMasterName + " に変更されました", colorRoomInfo);

						if (masterName.equals(loginUserName)) {
							roomFormProxyModeAddressCombo.setEnabled(false);
							roomFormProxyModeAddressCombo.setText(roomServerAddressPort);
							goTo(OperationMode.ProxyRoomMaster);
						} else if (currentOperationMode == OperationMode.ProxyRoomMaster) {
							roomFormClientModeAddressCombo.setEnabled(false);
							roomFormClientModeAddressCombo.setText(roomServerAddressPort + ":" + masterName);
							goTo(OperationMode.RoomParticipant);
						} else {
							roomFormClientModeAddressCombo.setText(roomServerAddressPort + ":" + masterName);
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
						statusServerAddressLabel.setText("サーバーアドレス");
						statusServerStatusLabel.setText("サーバーステータス");
						statusBarContainer.layout();

						roomPlayerMap.clear();
						roomPlayerListViewer.refresh();

						roomFormEditButton.setEnabled(false);

						roomFormModeSelectionCombo.setEnabled(true);

						roomFormServerModePortSpinner.setEnabled(true);
						roomFormServerModePortButton.setText("起動する");
						roomFormServerModePortButton.setEnabled(true);

						roomFormClientModeAddressCombo.setEnabled(true);
						roomFormClientModeAdderssButton.setText("ログイン");
						roomFormClientModeAdderssButton.setEnabled(true);
						roomFormClientModeContainer.layout();

						roomFormProxyModeAddressCombo.setEnabled(true);
						roomFormProxyModeAddressButton.setText("作成する");
						roomFormProxyModeAddressButton.setEnabled(true);
						roomFormProxyModeContainer.layout();

						switch (roomFormModeSelectionCombo.getSelectionIndex()) {
						case 0:
							setEnableRoomFormItems(true);
							updateTunnelStatus(false);
							break;
						case 1:
							setEnableRoomFormItems(false);
							roomFormTitleText.setText("");
							roomFormPasswordText.setText("");
							roomFormDescriptionText.setText("");
							roomFormMaxPlayersSpiner.setSelection(DEFAULT_MAX_PLAYERS);
							break;
						case 2:
							setEnableRoomFormItems(true);
							break;
						}

						roomFormMasterText.setText("");

						configUserNameText.setEnabled(true);

						mainTabFolder.setSelection(playRoomTab);

						break;
					case RoomMaster:
						mainTabFolder.setSelection(playRoomTab);

						// roomFormEditButton.setEnabled(true);
						// setEnableRoomFormItems(true);

						roomFormServerModePortButton.setText("停止する");
						roomFormServerModePortButton.setEnabled(true);

						updateTunnelStatus(true);

						configUserNameText.setEnabled(false);

						break;
					case RoomParticipant:
						mainTabFolder.setSelection(playRoomTab);

						roomFormClientModeAdderssButton.setText("ログアウト");
						roomFormClientModeAdderssButton.setEnabled(true);
						roomFormClientModeContainer.layout();

						roomFormModeSelectionCombo.select(1);
						updateRoomModeSelection();

						break;
					case ProxyRoomMaster:
						roomFormProxyModeAddressButton.setText("ログアウト");
						roomFormProxyModeAddressButton.setEnabled(true);
						roomFormProxyModeContainer.layout();

						roomFormModeSelectionCombo.select(2);
						updateRoomModeSelection();
						break;
					case ConnectingToRoomServer:
						roomFormModeSelectionCombo.setEnabled(false);

						roomFormClientModeAdderssButton.setEnabled(false);
						roomFormClientModeAddressCombo.setEnabled(false);

						configUserNameText.setEnabled(false);

						break;
					case ConnectingToProxyServer:
						roomFormModeSelectionCombo.setEnabled(false);

						roomFormProxyModeAddressButton.setEnabled(false);
						roomFormProxyModeAddressCombo.setEnabled(false);

						configUserNameText.setEnabled(false);

						break;
					}
				} catch (SWTException e) {
				}
			}
		});
	}

	private void setEnableRoomFormItems(boolean enabled) {
		roomFormTitleText.setEditable(enabled);
		roomFormPasswordText.setEditable(enabled);
		roomFormMaxPlayersSpiner.setEnabled(enabled);
		roomFormDescriptionText.setEditable(enabled);

		roomFormSearchServerButton.setEnabled(enabled);
		roomFormSearchServerCombo.setEnabled(enabled);
	}

	private class RoomServerHandler implements IRoomMasterHandler {
		@Override
		public void log(String message) {
			appendLogTo(logText, message);
		}

		@Override
		public void chatReceived(String message) {
			appendLogTo(roomChatLogText, message, colorBlack);
		}

		@Override
		public void playerEntered(String player) {
			addPlayer(roomPlayerListViewer, player);
			appendLogTo(roomChatLogText, player + " が入室しました", colorLogInfo);
		}

		@Override
		public void playerExited(String player) {
			removePlayer(roomPlayerListViewer, player);
			appendLogTo(roomChatLogText, player + " が退室しました", colorLogInfo);
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
		public void roomOpened() {
			goTo(OperationMode.RoomMaster);
			appendLogTo(roomChatLogText, "自部屋を起動しました", colorRoomInfo);
		}

		@Override
		public void roomClosed() {
			goTo(OperationMode.Offline);
			appendLogTo(roomChatLogText, "自部屋を停止しました", colorRoomInfo);
		}
	}

	private interface CommandHandler {
		public void process(String argument);
	}

	private class RoomClientHandler implements IAsyncClientHandler {
		private HashMap<String, CommandHandler> handlers = new HashMap<String, PlayClientWindow.CommandHandler>();

		public RoomClientHandler() {
			handlers.put(Constants.Protocol.ERROR_VERSION_MISMATCH, new ErrorVersionMismatchHandler());
			handlers.put(Constants.Protocol.COMMAND_LOGIN, new LoginHandler());
			handlers.put(Constants.Protocol.COMMAND_ROOM_CREATE, new RoomCreateHandler());
			handlers.put(Constants.Protocol.COMMAND_CHAT, new ChatHandler());
			handlers.put(Constants.Protocol.COMMAND_PINGBACK, new PingBackHandler());
			handlers.put(Constants.Protocol.COMMAND_INFORM_PING, new InformPingHandler());
			handlers.put(Constants.Protocol.COMMAND_INFORM_TUNNEL_UDP_PORT, new InformTunnelPortHandler());
			handlers.put(Constants.Protocol.COMMAND_ROOM_UPDATE, new CommandRoomUpdateHandler());
			handlers.put(Constants.Protocol.NOTIFY_SERVER_STATUS, new NotifyServerStatusHandler());
			handlers.put(Constants.Protocol.NOTIFY_USER_ENTERED, new NotifyUserEnteredHandler());
			handlers.put(Constants.Protocol.NOTIFY_USER_EXITED, new NotifyUserExitedHandler());
			handlers.put(Constants.Protocol.NOTIFY_USER_LIST, new NotifyUserListHandler());
			handlers.put(Constants.Protocol.NOTIFY_ROOM_UPDATED, new NotifyRoomUpdatedHandler());
			handlers.put(Constants.Protocol.NOTIFY_ROOM_PLAYER_KICKED, new NotifyRoomPlayerKickedHandler());
			handlers.put(Constants.Protocol.NOTIFY_ROOM_PASSWORD_REQUIRED, new NotifyRoomPasswordRequiredHandler());
			handlers.put(Constants.Protocol.ERROR_LOGIN_DUPLICATED_NAME, new ErrorLoginDuplicatedNameHandler());
			handlers.put(Constants.Protocol.ERROR_LOGIN_BEYOND_CAPACITY, new ErrorLoginBeyondCapacityHandler());
			handlers.put(Constants.Protocol.ERROR_ROOM_ENTER_PASSWORD_FAIL, new ErrorRoomEnterPasswordFailHandler());
			handlers.put(Constants.Protocol.ERROR_ROOM_ENTER_BEYOND_CAPACITY, new ErrorRoomEnterBeyondCapacityHandler());
			handlers.put(Constants.Protocol.ERROR_ROOM_CREATE_BEYOND_LIMIT, new ErrorRoomCreateBeyondLimitHandler());
		}

		@Override
		public void log(IAsyncClient client, String message) {
			appendLogTo(logText, message);
		}

		@Override
		public void connectCallback(IAsyncClient client) {
			// goTo(RoomMode.ConnectingToServer);
			appendLogTo(roomChatLogText, "サーバーに接続しました", colorServerInfo);

			switch (currentOperationMode) {
			case ConnectingToRoomServer:
				asyncExec(new Runnable() {
					@Override
					public void run() {
						try {
							String serverAddress = roomFormClientModeAddressCombo.getText();
							int index = roomServerHistory.indexOf(serverAddress);
							int historyStartIndex = roomServerListCount + 1;

							if (index == -1) {
								roomServerHistory.add(0, serverAddress);
								roomFormClientModeAddressCombo.add(serverAddress, historyStartIndex);
								if (roomServerHistory.size() > MAX_SERVER_HISTORY) {
									roomServerHistory.removeLast();
									roomFormClientModeAddressCombo.remove(roomFormModeSelectionCombo.getItemCount() - 1);
								}
							} else {
								roomServerHistory.remove(index);
								roomServerHistory.add(0, serverAddress);

								roomFormClientModeAddressCombo.remove(historyStartIndex + index);
								roomFormClientModeAddressCombo.add(serverAddress, historyStartIndex);
								roomFormClientModeAddressCombo.select(historyStartIndex);
							}
						} catch (SWTException e) {
						}
					}
				});

				client.send(Constants.Protocol.COMMAND_VERSION + " " + Constants.Protocol.PROTOCOL_NUMBER);
				client.send(Constants.Protocol.COMMAND_LOGIN + " \"" + roomMasterName + "\" " + loginUserName);

				break;
			case ConnectingToProxyServer:
				client.send(Constants.Protocol.COMMAND_VERSION + " " + Constants.Protocol.PROTOCOL_NUMBER);

				syncExec(new Runnable() {
					@Override
					public void run() {
						try {
							StringBuilder sb = new StringBuilder();
							sb.append(Constants.Protocol.COMMAND_ROOM_CREATE);
							sb.append(' ').append(loginUserName);
							appendRoomInfo(sb);

							roomClient.send(sb.toString());
						} catch (SWTException e) {
						}
					}
				});

				break;
			}
		}

		@Override
		public void readCallback(IAsyncClient client, final PacketData data) {
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
				appendLogTo(logText, Utility.makeStackTrace(e));
			}
		}

		@Override
		public void disconnectCallback(IAsyncClient client) {
			switch (currentOperationMode) {
			case ConnectingToRoomServer:
			case ConnectingToProxyServer:
				appendLogTo(roomChatLogText, "サーバーに接続できません", colorLogError);
				break;
			default:
				appendLogTo(roomChatLogText, "サーバーと切断しました", colorServerInfo);
			}
			goTo(OperationMode.Offline);
		}

		private class ErrorVersionMismatchHandler implements CommandHandler {
			@Override
			public void process(String num) {
				String message = String.format("サーバーとのプロトコルナンバーが一致ないので接続できません サーバー:%s クライアント:%s", num, Constants.Protocol.PROTOCOL_NUMBER);
				appendLogTo(roomChatLogText, message, colorLogError);
			}
		}

		private void prepareSession() {
			synchronized (pingThread) {
				pingThread.notify();
			}

			try {
				tunnelClient.connect(roomServerSocketAddress);
			} catch (IOException ioe) {
			}
		}

		private class LoginHandler implements CommandHandler {
			@Override
			public void process(String args) {
				goTo(OperationMode.RoomParticipant);
				appendLogTo(roomChatLogText, "部屋に入りました  ", colorRoomInfo);

				updateRoom(args.split(" "), true);

				prepareSession();
			}
		}

		private class RoomCreateHandler implements CommandHandler {
			@Override
			public void process(String argument) {
				goTo(OperationMode.ProxyRoomMaster);

				StringBuilder sb = new StringBuilder();
				sb.append("代理サーバーで部屋を作成しました  ");
				sb.append(roomServerAddressPort);
				sb.append(':').append(loginUserName);
				appendLogTo(roomChatLogText, sb.toString(), colorRoomInfo);

				asyncExec(new Runnable() {
					@Override
					public void run() {
						roomFormMasterText.setText(loginUserName);
						addPlayer(roomPlayerListViewer, loginUserName);
					}
				});

				prepareSession();
			}
		}

		private class ChatHandler implements CommandHandler {
			@Override
			public void process(String args) {
				switch (currentOperationMode) {
				case RoomMaster:
				case RoomParticipant:
				case ProxyRoomMaster:
					appendLogTo(roomChatLogText, args, colorBlack);
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

				roomClient.send(Constants.Protocol.COMMAND_INFORM_PING + " " + ping);
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
							statusServerStatusLabel.setText(text);
							statusBarContainer.layout();
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
				case RoomMaster:
				case RoomParticipant:
				case ProxyRoomMaster:
					addPlayer(roomPlayerListViewer, name);
					appendLogTo(roomChatLogText, name + " が入室しました", colorLogInfo);
					break;
				}
			}
		}

		private class NotifyUserExitedHandler implements CommandHandler {
			@Override
			public void process(String name) {
				switch (currentOperationMode) {
				case RoomMaster:
				case RoomParticipant:
				case ProxyRoomMaster:
					removePlayer(roomPlayerListViewer, name);
					appendLogTo(roomChatLogText, name + " が退室しました", colorLogInfo);
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
					replacePlayerList(roomPlayerListViewer, players);
					break;
				}
			}
		}

		private class NotifyRoomUpdatedHandler implements CommandHandler {
			@Override
			public void process(String args) {
				String oldMasterName = roomMasterName;
				updateRoom(args.split(" "), false);
			}
		}

		private class CommandRoomUpdateHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(roomChatLogText, "部屋情報を修正しました", colorRoomInfo);

				asyncExec(new Runnable() {
					@Override
					public void run() {
						roomFormEditButton.setEnabled(false);
					}
				});
			}
		}

		private class NotifyRoomPlayerKickedHandler implements CommandHandler {
			@Override
			public void process(String kickedPlayer) {
				if (loginUserName.equals(kickedPlayer)) {
					goTo(OperationMode.Offline);
					appendLogTo(roomChatLogText, "部屋から追い出されました", colorRoomInfo);
				} else {
					removePlayer(roomPlayerListViewer, kickedPlayer);
					if (currentOperationMode == OperationMode.RoomMaster) {
						appendLogTo(roomChatLogText, kickedPlayer + " を部屋から追い出しました", colorRoomInfo);
					} else {
						appendLogTo(roomChatLogText, kickedPlayer + " は部屋から追い出されました", colorRoomInfo);
					}
				}
			}
		}

		private class InformTunnelPortHandler implements CommandHandler {
			@Override
			public void process(String message) {
				updateTunnelStatus(true);
				appendLogTo(logText, "トンネル通信の接続が開始しました");

				synchronized (packetMonitorThread) {
					packetMonitorThread.notify();
				}
			}
		}

		private class ErrorLoginDuplicatedNameHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(roomChatLogText, "同名のユーザーが既にログインしているのでログインできません", colorLogError);
			}
		}

		private class ErrorLoginBeyondCapacityHandler implements CommandHandler {
			@Override
			public void process(String message) {
				switch (currentOperationMode) {
				case ConnectingToProxyServer:
					appendLogTo(roomChatLogText, "サーバーの最大部屋数を超えたので部屋を作成できません", colorLogError);
					break;
				default:
					appendLogTo(roomChatLogText, "サーバーの最大人数を超えたのでログインできません", colorLogError);
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
							String message = Constants.Protocol.COMMAND_LOGIN + " \"" + roomMasterName + "\" " + loginUserName + " "
									+ password;

							roomClient.send(message);
							break;
						case IDialogConstants.CANCEL_ID:
							appendLogTo(roomChatLogText, "入室をキャンセルしました", colorRoomInfo);
							roomClient.send(Constants.Protocol.COMMAND_LOGOUT);
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
				appendLogTo(roomChatLogText, "部屋パスワードが違います", colorRoomInfo);
				promptPassword();
			}
		}

		private class ErrorRoomEnterBeyondCapacityHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(roomChatLogText, "部屋が満室なので入れません", colorRoomInfo);
			}
		}

		private class ErrorRoomCreateBeyondLimitHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(roomChatLogText, "部屋数が上限に達しましたので部屋を作成できません", colorRoomInfo);
			}
		}
	}

	private class TunnelHandler implements IAsyncClientHandler {
		@Override
		public void connectCallback(IAsyncClient client) {
			client.send(" ");
			synchronized (natTableMaintainingThread) {
				natTableMaintainingThread.notify();
			}
		}

		@Override
		public void readCallback(IAsyncClient client, PacketData data) {
			ByteBuffer packet = data.getBuffer();
			// System.out.println(packet.toString());
			if (Utility.isPspPacket(packet)) {
				processRemotePspPacket(packet);
			} else {
				try {
					String tunnelPort = data.getMessage();
					int port = Integer.parseInt(tunnelPort);
					// System.out.println("Port: " + port);
					roomClient.send(Constants.Protocol.COMMAND_INFORM_TUNNEL_UDP_PORT + " " + port);
				} catch (NumberFormatException e) {
				}
			}
		}

		@Override
		public void disconnectCallback(IAsyncClient client) {
			updateTunnelStatus(false);
			appendLogTo(logText, "トンネル通信の接続が終了しました");
		}

		@Override
		public void log(IAsyncClient client, String message) {
		}
	}

	private void refreshLanAdaptorList() {
		wlanAdaptorListCombo.removeAll();
		wlanAdaptorListCombo.add("選択されていません");
		wlanAdaptorListCombo.select(0);
		wlanPspCommunicationButton.setEnabled(false);

		StringBuilder errBuf = new StringBuilder();
		wlanAdaptorList.clear();
		int r = Pcap.findAllDevs(wlanAdaptorList, errBuf);
		if (r == Pcap.NOT_OK || wlanAdaptorList.isEmpty()) {
			appendLogTo(logText, errBuf.toString());
			return;
		}

		String lastUsedMacAddress = iniSettingSection.get(Constants.Ini.CLIENT_LAST_LAN_ADAPTOR, "");
		int lastUsedIndex = 0;

		IniParser.Section nicSection = iniParser.getSection(Constants.Ini.SECTION_LAN_ADAPTORS);

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
				wlanAdaptorListCombo.add(description);

				wlanAdaptorMacAddressMap.put(device, macAddress);

			} catch (IOException e) {
				appendLogTo(logText, Utility.makeStackTrace(e));
			}
		}

		wlanAdaptorListCombo.select(lastUsedIndex);
		wlanPspCommunicationButton.setEnabled(lastUsedIndex != 0);
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
					tunnelClient.send(bufferForCapturing);
					break;
				}
			}
		}
	}

	private boolean startPacketCapturing() {
		int index = wlanAdaptorListCombo.getSelectionIndex() - 1;
		PcapIf device = wlanAdaptorList.get(index);

		StringBuilder errbuf = new StringBuilder();
		currentPcapDevice = Pcap.openLive(device.getName(), CAPTURE_BUFFER_SIZE, Pcap.MODE_PROMISCUOUS, 1, errbuf);
		if (currentPcapDevice == null) {
			appendLogTo(logText, errbuf.toString());
			return false;
		}

		isPacketCapturing = true;
		synchronized (packetCaptureThread) {
			packetCaptureThread.notify();
		}
		synchronized (packetMonitorThread) {
			packetMonitorThread.notify();
		}

		return true;
	}

	public void start() {
		int minWidth = 650, minHeight = 400;

		shell.setMinimumSize(new Point(minWidth, minHeight));

		shell.setSize(iniSettingSection.get(Constants.Ini.CLIENT_WINDOW_WIDTH, minWidth),
				iniSettingSection.get(Constants.Ini.CLIENT_WINDOW_HEIGHT, minHeight));
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
			new PlayClientWindow(parser).start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
