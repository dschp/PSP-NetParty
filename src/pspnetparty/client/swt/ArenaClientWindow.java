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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
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
import pspnetparty.lib.IniParser;
import pspnetparty.lib.PacketData;
import pspnetparty.lib.Utility;

public class ArenaClientWindow {

	private static final int CAPTURE_BUFFER_SIZE = 2000;
	private static final int MAX_SERVER_HISTORY = 10;

	enum OperationMode {
		Offline, ConnectingToServer, Portal, ArenaLobby, PlayRoomMaster, PlayRoomParticipant
	};

	private IniParser iniParser;
	private IniParser.Section iniSettingSection;

	private Display display;
	private Shell shell;

	private OperationMode currentOperationMode;

	private AsyncTcpClient arenaSessionClient;
	private AsyncUdpClient arenaTunnelClient;

	private HashMap<String, PlayRoom> playRoomMap = new LinkedHashMap<String, PlayRoom>();
	private HashMap<String, Player> lobbyPlayerMap = new LinkedHashMap<String, Player>();
	private HashMap<String, Player> roomPlayerMap = new LinkedHashMap<String, Player>();

	private String loginUserName;
	private boolean isArenaEntryExitLogEnabled = true;

	private InetSocketAddress arenaSocketAddress;
	private boolean tunnelIsLinked = false;
	private boolean isPacketCapturing = false;

	private ByteBuffer capturedByteBuffer = ByteBuffer.allocate(CAPTURE_BUFFER_SIZE);
	private ArrayList<PcapIf> wlanAdaptorList = new ArrayList<PcapIf>();
	private HashMap<PcapIf, String> wlanAdaptorMacAddressMap = new HashMap<PcapIf, String>();
	private Pcap currentPcap;

	private HashMap<String, TraficStatistics> traficStatsMap = new HashMap<String, TraficStatistics>();

	private int arenaServerListCount = 0;
	private LinkedList<String> arenaServerHistory = new LinkedList<String>();

	public ArenaClientWindow(IniParser iniParser) {
		this.iniParser = iniParser;
		this.iniSettingSection = iniParser.getSection(Constants.Ini.SECTION_SETTINGS);

		initializeComponents();

		goTo(OperationMode.Offline);

		arenaSessionClient = new AsyncTcpClient(new SessionHandler());
		arenaTunnelClient = new AsyncUdpClient(new TunnelHandler());

		refreshLanAdaptorList();

		configUserNameText.setText(iniSettingSection.get(Constants.Ini.CLIENT_LOGIN_NAME, ""));

		String[] serverList = iniSettingSection.get(Constants.Ini.CLIENT_SERVER_LIST, "").split(",");
		for (String s : serverList) {
			if (Utility.isEmpty(s))
				continue;
			arenaServerAddressCombo.add(s);
			arenaServerListCount++;
		}

		arenaServerAddressCombo.add("----------履歴----------");

		serverList = iniSettingSection.get(Constants.Ini.CLIENT_SERVER_HISTORY, "").split(",");
		for (String s : serverList) {
			if (Utility.isEmpty(s))
				continue;
			arenaServerAddressCombo.add(s);
			arenaServerHistory.add(s);
			if (arenaServerHistory.size() == MAX_SERVER_HISTORY)
				break;
		}
		
		String software = String.format("%s アリーナクライアント バージョン: %s", Constants.App.APP_NAME, Constants.App.VERSION);
		appendLogTo(arenaChatLogText, software, colorAppInfo);
		appendLogTo(arenaChatLogText, "プロトコル: " + Constants.Protocol.PROTOCOL_NUMBER, colorAppInfo);
	}

	private TabFolder mainTabFolder;
	private TabItem arenaLobbyTab;
	private SashForm arenaMainSashForm;
	private Composite arenaInfoContainer;
	private Label arenaServerAddressLabel;
	private Combo arenaServerAddressCombo;
	private Button arenaServerLoginButton;
	private TableViewer playRoomListViewer;
	private TableColumn playRoomMasterNameColumn;
	private TableColumn playRoomKeyColumn;
	private TableColumn playRoomTitleColumn;
	private TableColumn playRoomCapacityColumn;
	private Composite arenaChatContainer;
	private Button arenaChatSubmitButton;
	private Text arenaChatText;
	private SashForm arenaSubSashForm;
	private StyledText arenaChatLogText;
	private TableViewer arenaLobbyPlayerListViewer;
	private TableColumn arenaLobbyPlayerNameColumn;
	private TabItem playRoomTab;
	private SashForm roomMainSashForm;
	private Composite roomInfoContainer;
	private Button roomFormCloseExitButton;
	private Button roomFormCreateEditButton;
	private Composite roomEditFormContainer;
	private Label roomFormMasterLabel;
	private Text roomFormMasterText;
	private Label roomFormTitleLabel;
	private Text roomFormTitleText;
	private Label roomFormPasswordLabel;
	private Text roomFormPasswordText;
	private Label roomFormMaxPlayersLabel;
	private Spinner roomFormMaxPlayersSpiner;
	private Label roomFormDescriptionLabel;
	private Text roomFormDesctiptionText;
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

	private void initializeComponents() {
		display = new Display();
		shell = new Shell(display);

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

		arenaLobbyTab = new TabItem(mainTabFolder, SWT.NONE);
		arenaLobbyTab.setText("アリーナロビー");

		arenaMainSashForm = new SashForm(mainTabFolder, SWT.SMOOTH | SWT.HORIZONTAL);
		arenaLobbyTab.setControl(arenaMainSashForm);

		arenaInfoContainer = new Composite(arenaMainSashForm, SWT.NONE);
		gridLayout = new GridLayout(3, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 5;
		gridLayout.marginHeight = 0;
		gridLayout.marginWidth = 0;
		gridLayout.marginTop = 4;
		gridLayout.marginLeft = 1;
		arenaInfoContainer.setLayout(gridLayout);

		arenaServerAddressLabel = new Label(arenaInfoContainer, SWT.NONE);
		arenaServerAddressLabel.setText("アドレス");
		arenaServerAddressLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

		arenaServerAddressCombo = new Combo(arenaInfoContainer, SWT.NONE);
		// arenaServerAddressCombo.setItems(new String[] { "127.0.0.1:30000",
		// "localhost:30000" });
		arenaServerAddressCombo.setText("localhost:30000");
		gridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		arenaServerAddressCombo.setLayoutData(gridData);

		arenaServerLoginButton = new Button(arenaInfoContainer, SWT.PUSH);
		arenaServerLoginButton.setText("ログイン");
		arenaServerLoginButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		// controlServerLoginButton.setLayoutData(new
		// RowData(makeAppropriateSize(controlServerLoginButton, 10, 0)));
		playRoomListViewer = new TableViewer(arenaInfoContainer, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
		playRoomListViewer.getTable().setHeaderVisible(true);
		gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
		gridData.horizontalSpan = 3;
		playRoomListViewer.getTable().setLayoutData(gridData);

		playRoomMasterNameColumn = new TableColumn(playRoomListViewer.getTable(), SWT.LEFT);
		playRoomMasterNameColumn.setText("部屋主");
		playRoomMasterNameColumn.setWidth(80);
		playRoomKeyColumn = new TableColumn(playRoomListViewer.getTable(), SWT.CENTER);
		playRoomKeyColumn.setText("鍵");
		playRoomKeyColumn.setWidth(30);
		playRoomTitleColumn = new TableColumn(playRoomListViewer.getTable(), SWT.LEFT);
		playRoomTitleColumn.setText("部屋名");
		playRoomTitleColumn.setWidth(130);
		playRoomCapacityColumn = new TableColumn(playRoomListViewer.getTable(), SWT.CENTER);
		playRoomCapacityColumn.setText("定員");
		playRoomCapacityColumn.setWidth(45);

		playRoomListViewer.setContentProvider(new PlayRoom.PlayRoomListContentProvider());
		playRoomListViewer.setLabelProvider(new PlayRoom.PlayRoomLabelProvider());
		playRoomListViewer.setInput(playRoomMap);

		arenaChatContainer = new Composite(arenaMainSashForm, SWT.NONE);
		arenaChatContainer.setLayout(new FormLayout());

		arenaChatText = new Text(arenaChatContainer, SWT.BORDER | SWT.SINGLE);

		arenaChatSubmitButton = new Button(arenaChatContainer, SWT.PUSH);
		arenaChatSubmitButton.setText("発言");
		formData = new FormData(50, SWT.DEFAULT);
		formData.bottom = new FormAttachment(100, -2);
		formData.right = new FormAttachment(100, -1);
		arenaChatSubmitButton.setLayoutData(formData);

		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.bottom = new FormAttachment(100, -2);
		formData.right = new FormAttachment(arenaChatSubmitButton, -3);
		arenaChatText.setLayoutData(formData);
		arenaChatText.setFont(new Font(shell.getDisplay(), "Sans Serif", 12, SWT.NORMAL));

		arenaSubSashForm = new SashForm(arenaChatContainer, SWT.SMOOTH);
		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		formData.top = new FormAttachment(0, 0);
		formData.bottom = new FormAttachment(arenaChatText, -3);
		formData.right = new FormAttachment(100, -1);
		arenaSubSashForm.setLayoutData(formData);

		arenaChatLogText = new StyledText(arenaSubSashForm, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER | SWT.READ_ONLY | SWT.WRAP);
		arenaChatLogText.setMargins(3, 1, 3, 1);

		arenaLobbyPlayerListViewer = new TableViewer(arenaSubSashForm, SWT.SINGLE | SWT.BORDER);
		arenaLobbyPlayerListViewer.getTable().setHeaderVisible(true);

		arenaLobbyPlayerNameColumn = new TableColumn(arenaLobbyPlayerListViewer.getTable(), SWT.LEFT);
		arenaLobbyPlayerNameColumn.setText("名前");
		arenaLobbyPlayerNameColumn.setWidth(100);

		arenaLobbyPlayerListViewer.setContentProvider(new Player.PlayerListContentProvider());
		arenaLobbyPlayerListViewer.setLabelProvider(new Player.LobbyPlayerLabelProvider());
		arenaLobbyPlayerListViewer.setInput(lobbyPlayerMap);

		playRoomTab = new TabItem(mainTabFolder, SWT.NONE);
		playRoomTab.setText("プレイルーム");

		roomMainSashForm = new SashForm(mainTabFolder, SWT.HORIZONTAL | SWT.SMOOTH);
		playRoomTab.setControl(roomMainSashForm);

		roomInfoContainer = new Composite(roomMainSashForm, SWT.NONE);
		// playRoomTab.setControl(playRoomContainer);
		roomInfoContainer.setLayout(new FormLayout());

		roomFormCloseExitButton = new Button(roomInfoContainer, SWT.PUSH);
		roomFormCloseExitButton.setText("部屋を閉じる");
		formData = new FormData(100, SWT.DEFAULT);
		formData.top = new FormAttachment(0, 5);
		roomFormCloseExitButton.setLayoutData(formData);

		roomFormCreateEditButton = new Button(roomInfoContainer, SWT.PUSH);
		roomFormCreateEditButton.setText("部屋を作成");
		formData = new FormData(100, SWT.DEFAULT);
		formData.top = new FormAttachment(0, 5);
		formData.right = new FormAttachment(100, -3);
		roomFormCreateEditButton.setLayoutData(formData);

		roomEditFormContainer = new Composite(roomInfoContainer, SWT.NONE);
		roomEditFormContainer.setLayout(new GridLayout(2, false));
		formData = new FormData();
		formData.top = new FormAttachment(roomFormCloseExitButton, 3);
		formData.left = new FormAttachment(0, 0);
		formData.right = new FormAttachment(100, 0);
		roomEditFormContainer.setLayoutData(formData);

		roomFormMasterLabel = new Label(roomEditFormContainer, SWT.NONE);
		roomFormMasterLabel.setText("部屋主");
		roomFormMasterLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

		roomFormMasterText = new Text(roomEditFormContainer, SWT.SINGLE | SWT.BORDER | SWT.READ_ONLY);
		roomFormMasterText.setBackground(colorWhite);
		roomFormMasterText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		roomFormTitleLabel = new Label(roomEditFormContainer, SWT.NONE);
		roomFormTitleLabel.setText("部屋名");
		roomFormTitleLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

		roomFormTitleText = new Text(roomEditFormContainer, SWT.SINGLE | SWT.BORDER);
		roomFormTitleText.setBackground(colorWhite);
		roomFormTitleText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		roomFormPasswordLabel = new Label(roomEditFormContainer, SWT.NONE);
		roomFormPasswordLabel.setText("パスワード");
		roomFormPasswordLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

		roomFormPasswordText = new Text(roomEditFormContainer, SWT.SINGLE | SWT.BORDER);
		roomFormPasswordText.setBackground(colorWhite);
		roomFormPasswordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		roomFormMaxPlayersLabel = new Label(roomEditFormContainer, SWT.NONE);
		roomFormMaxPlayersLabel.setText("制限人数");

		roomFormMaxPlayersSpiner = new Spinner(roomEditFormContainer, SWT.READ_ONLY | SWT.BORDER);
		roomFormMaxPlayersSpiner.setBackground(colorWhite);
		roomFormMaxPlayersSpiner.setForeground(colorBlack);
		roomFormMaxPlayersSpiner.setMinimum(2);
		roomFormMaxPlayersSpiner.setMaximum(16);
		roomFormMaxPlayersSpiner.setSelection(4);

		roomFormDescriptionLabel = new Label(roomInfoContainer, SWT.NONE);
		roomFormDescriptionLabel.setText("部屋の紹介・備考");
		formData = new FormData();
		formData.top = new FormAttachment(roomEditFormContainer, 8);
		formData.left = new FormAttachment(0, 3);
		roomFormDescriptionLabel.setLayoutData(formData);

		roomFormDesctiptionText = new Text(roomInfoContainer, SWT.MULTI | SWT.V_SCROLL | SWT.BORDER);
		roomFormDesctiptionText.setBackground(colorWhite);
		formData = new FormData();
		formData.top = new FormAttachment(roomFormDescriptionLabel, 4);
		formData.left = new FormAttachment();
		formData.right = new FormAttachment(100, 0);
		formData.bottom = new FormAttachment(100, 0);
		roomFormDesctiptionText.setLayoutData(formData);

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

		statusServerAddressLabel = new Label(statusBarContainer, SWT.BORDER);
		statusServerAddressLabel.setText("サーバーアドレス");
		formData = new FormData();
		formData.left = new FormAttachment(0, 0);
		statusServerAddressLabel.setLayoutData(formData);

		statusTunnelConnectionLabel = new Label(statusBarContainer, SWT.BORDER);
		formData = new FormData();
		formData.left = new FormAttachment(statusServerAddressLabel, 5);
		statusTunnelConnectionLabel.setLayoutData(formData);
		updateTunnelStatus(false);

		statusServerStatusLabel = new Label(statusBarContainer, SWT.BORDER);
		statusServerStatusLabel.setText("サーバーステータス");
		formData = new FormData();
		formData.left = new FormAttachment(statusTunnelConnectionLabel, 5);
		statusServerStatusLabel.setLayoutData(formData);

		statusTraficStatusLabel = new Label(statusBarContainer, SWT.BORDER);
		statusTraficStatusLabel.setText("トラフィック");
		formData = new FormData();
		formData.right = new FormAttachment(100, -20);
		statusTraficStatusLabel.setLayoutData(formData);

		roomInfoSashForm.setWeights(new int[] { 5, 2 });
		roomSubSashForm.setWeights(new int[] { 1, 2 });
		roomMainSashForm.setWeights(new int[] { 3, 7 });
		arenaSubSashForm.setWeights(new int[] { 5, 2 });
		arenaMainSashForm.setWeights(new int[] { 2, 3 });

		initializeComponentListeners();

		roomPlayerMenu = new Menu(shell, SWT.POP_UP);
		roomPlayerKickMenuItem = new MenuItem(roomPlayerMenu, SWT.PUSH);
		roomPlayerKickMenuItem.setText("キック");
		roomPlayerKickMenuItem.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				IStructuredSelection selection = (IStructuredSelection) roomPlayerListViewer.getSelection();
				Player player = (Player) selection.getFirstElement();
				arenaSessionClient.send(Constants.Protocol.COMMAND_ROOM_KICK_PLAYER + " " + player.getName());
			}
			
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		});

		roomPlayerListViewer.getTable().setMenu(roomPlayerMenu);
		roomPlayerListViewer.getTable().addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				if (currentOperationMode == OperationMode.PlayRoomMaster) {
					IStructuredSelection selection = (IStructuredSelection) roomPlayerListViewer.getSelection();
					Player player = (Player) selection.getFirstElement();
					if (roomFormMasterText.getText().equals(player.getName())) {
						roomPlayerKickMenuItem.setEnabled(false);
					} else {
						roomPlayerKickMenuItem.setEnabled(true);
					}
				} else {
					roomPlayerKickMenuItem.setEnabled(false);
				}
			}
		});
	}

	private void initializeComponentListeners() {
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				arenaSessionClient.disconnect();
				arenaTunnelClient.disconnect();

				isPacketCapturing = false;

				iniSettingSection.set(Constants.Ini.CLIENT_LOGIN_NAME, configUserNameText.getText());

				Point size = shell.getSize();
				iniSettingSection.set(Constants.Ini.CLIENT_WINDOW_WIDTH, Integer.toString(size.x));
				iniSettingSection.set(Constants.Ini.CLIENT_WINDOW_HEIGHT, Integer.toString(size.y));

				if (arenaServerListCount == 0) {
					iniSettingSection.set(Constants.Ini.CLIENT_SERVER_LIST, "");
				}

				StringBuilder sb = new StringBuilder();
				for (String s : arenaServerHistory) {
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

		arenaServerLoginButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (currentOperationMode == OperationMode.Offline) {
					connectToArenaServer();
				} else {
					arenaSessionClient.send(Constants.Protocol.COMMAND_LOGOUT);
					arenaTunnelClient.disconnect();
				}
			}
		});

		arenaServerAddressCombo.addKeyListener(new KeyListener() {
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
					connectToArenaServer();
					break;
				}
			}
		});
		arenaServerAddressCombo.addListener(SWT.Selection, new Listener() {
			private int lastSelectionIndex = 0;

			@Override
			public void handleEvent(Event event) {
				if (arenaServerAddressCombo.getSelectionIndex() == arenaServerListCount) {
					arenaServerAddressCombo.select(lastSelectionIndex);
				} else {
					lastSelectionIndex = arenaServerAddressCombo.getSelectionIndex();
				}
			}
		});

		arenaChatText.addKeyListener(new KeyListener() {
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
				// arenaChatLogText.append(Integer.toString(e.character));
				// arenaChatLogText.append("\n");
			}
		});
		arenaChatSubmitButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				sendChat();
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
				// arenaChatLogText.append(Integer.toString(e.character));
				// arenaChatLogText.append("\n");
			}
		});
		roomChatSubmitButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				sendChat();
			}
		});

		playRoomListViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent e) {
				IStructuredSelection sel = (IStructuredSelection) e.getSelection();
				PlayRoom room = (PlayRoom) sel.getFirstElement();
				arenaSessionClient.send(Constants.Protocol.COMMAND_ROOM_ENTER + " " + room.getMasterName());
			}
		});

		roomFormCloseExitButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				roomFormCloseExitButton.setEnabled(false);
				switch (currentOperationMode) {
				case PlayRoomMaster:
					arenaSessionClient.send(Constants.Protocol.COMMAND_ROOM_DELETE);
					roomFormCreateEditButton.setEnabled(true);
					break;
				case PlayRoomParticipant:
					arenaSessionClient.send(Constants.Protocol.COMMAND_ROOM_EXIT);
					break;
				}
			}
		});

		VerifyListener notAcceptSpaceListener = new VerifyListener() {
			@Override
			public void verifyText(VerifyEvent e) {
				switch (e.character) {
				case ' ':
					e.doit = false;
					break;
				case '\0':
					e.text = e.text.replace(" ", "").trim();
					break;
				}
			}
		};
		roomFormTitleText.addVerifyListener(notAcceptSpaceListener);
		roomFormPasswordText.addVerifyListener(notAcceptSpaceListener);
		roomFormDesctiptionText.addVerifyListener(notAcceptSpaceListener);

		roomFormCreateEditButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				switch (currentOperationMode) {
				case ArenaLobby:
				case PlayRoomMaster:
					String title = roomFormTitleText.getText();
					if (Utility.isEmpty(title)) {
						appendLogTo(roomChatLogText, "部屋名を入力してください", colorLogError);
						roomFormTitleText.setFocus();
						return;
					}

					StringBuilder sb = new StringBuilder();
					if (currentOperationMode == OperationMode.ArenaLobby) {
						sb.append(Constants.Protocol.COMMAND_ROOM_CREATE);
						roomFormCloseExitButton.setEnabled(true);
					} else {
						sb.append(Constants.Protocol.COMMAND_ROOM_UPDATE);
					}
					sb.append(' ');
					sb.append(roomFormMaxPlayersSpiner.getText());
					sb.append(' ');
					sb.append(title);
					sb.append(" \"");
					sb.append(roomFormDesctiptionText.getText());
					sb.append("\" \"");
					sb.append(roomFormPasswordText.getText());
					sb.append('"');

					arenaSessionClient.send(sb.toString());

					break;
				}
			}
		});

		configUserNameText.addVerifyListener(notAcceptSpaceListener);
		configUserNameText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (Utility.isEmpty(configUserNameText.getText())) {
					configUserNameAlertLabel.setVisible(true);
					shell.setText(Constants.App.APP_NAME + " Client");
				} else {
					configUserNameAlertLabel.setVisible(false);
					shell.setText(configUserNameText.getText() + " - " + Constants.App.APP_NAME + " ArenaClient");
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
	}

	private void connectToArenaServer() {
		String username = configUserNameText.getText();
		if (Utility.isEmpty(username)) {
			mainTabFolder.setSelection(configTab);
			configUserNameText.setFocus();
			configUserNameAlertLabel.setVisible(true);
			return;
		}
		this.loginUserName = username;

		String address = arenaServerAddressCombo.getText();
		if (Utility.isEmpty(address)) {
			appendLogTo(arenaChatLogText, "サーバーアドレスを入力してください", colorLogError);
			return;
		}

		String[] tokens = address.split(":");
		if (tokens.length != 2) {
			appendLogTo(arenaChatLogText, "サーバーアドレスが正しくありません", colorLogError);
			return;
		}

		int port;
		try {
			port = Integer.parseInt(tokens[1]);
		} catch (NumberFormatException e) {
			appendLogTo(arenaChatLogText, "サーバーアドレスが正しくありません", colorLogError);
			return;
		}

		arenaSocketAddress = new InetSocketAddress(tokens[0], port);
		try {
			arenaSessionClient.connect(arenaSocketAddress);
			arenaServerLoginButton.setEnabled(false);
			arenaServerAddressCombo.setEnabled(false);
		} catch (IOException e) {
			logText.append(Utility.makeStackTrace(e));
			return;
		}
	}

	private void sendChat() {
		Text chat = null;
		switch (currentOperationMode) {
		case ArenaLobby:
			chat = arenaChatText;
			break;
		case PlayRoomMaster:
		case PlayRoomParticipant:
			chat = roomChatText;
			break;
		}

		if (chat != null) {
			String command = chat.getText();
			if (!Utility.isEmpty(command)) {
				arenaSessionClient.send(Constants.Protocol.COMMAND_CHAT + " " + command);
				chat.setText("");
			}
		} else {
			appendLogTo(arenaChatLogText, "サーバーにログインしていません", colorRoomInfo);
		}
	}

	private void appendLogTo(final StyledText text, final String message, final Color color) {
		if (Utility.isEmpty(message))
			return;

		Runnable run = new Runnable() {
			@Override
			public void run() {
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
			}
		};
		display.asyncExec(run);
	}

	private void appendLogTo(final Text text, final String message) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				text.append(message);
				text.append("\n");
				text.setTopIndex(text.getLineCount());
			}
		};
		display.asyncExec(run);
	}

	private void updateServerAddress(final String type) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				String text = String.format("%sサーバー  %s", type, arenaServerAddressCombo.getText());
				statusServerAddressLabel.setText(text);
				statusBarContainer.layout();
			}
		};
		display.asyncExec(run);
	}

	private void updateTunnelStatus(final boolean isConnected) {
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				if (isConnected) {
					statusTunnelConnectionLabel.setForeground(colorGreen);
					statusTunnelConnectionLabel.setText(" UDPトンネル接続中 ");
				} else {
					statusTunnelConnectionLabel.setForeground(colorRed);
					statusTunnelConnectionLabel.setText(" UDPトンネル切断 ");
				}
				statusBarContainer.layout();
			}
		});
	}

	private void replacePlayerList(final TableViewer viewer, final String[] players) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
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
			}
		};
		display.asyncExec(run);
	}

	private void addPlayer(final TableViewer viewer, final String name) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				Player player = new Player(name);
				@SuppressWarnings("unchecked")
				HashMap<String, Player> map = (HashMap<String, Player>) viewer.getInput();

				map.put(name, player);
				viewer.add(player);
				viewer.refresh();
			}
		};
		display.asyncExec(run);
	}

	private void removePlayer(final TableViewer viewer, final String name) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				@SuppressWarnings("unchecked")
				HashMap<String, Player> map = (HashMap<String, Player>) viewer.getInput();

				Player player = map.get(name);
				map.remove(name);
				viewer.remove(player);
				viewer.refresh();
			}
		};
		display.asyncExec(run);
	}

	private void updatePlayerPing(final String name, final int ping) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				HashMap<String, Player> map = roomPlayerMap;
				Player player = map.get(name);

				player.setPing(ping);
				roomPlayerListViewer.refresh(player);
			}
		};
		display.asyncExec(run);
	}

	private void addRoom(final String[] tokens) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				String masterName = tokens[0];
				int maxPlayers = Integer.parseInt(tokens[1]);
				int currentPlayers = Integer.parseInt(tokens[2]);
				boolean hasPassword = "Y".equals(tokens[3]);
				String title = tokens[4];

				removePlayer(arenaLobbyPlayerListViewer, masterName);

				PlayRoom room = new PlayRoom(masterName, title, hasPassword, currentPlayers, maxPlayers);
				playRoomMap.put(room.getMasterName(), room);
				playRoomListViewer.add(room);
				playRoomListViewer.refresh();
			}
		};
		display.asyncExec(run);
	}

	private void removeRoom(final String masterName) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				PlayRoom room = playRoomMap.get(masterName);
				playRoomMap.remove(masterName);
				playRoomListViewer.remove(room);
				playRoomListViewer.refresh();
			}
		};
		display.asyncExec(run);
	}

	// private void clearRoomList() {
	// Runnable run = new Runnable() {
	// @Override
	// public void run() {
	// playRoomMap.clear();
	// roomListViewer.getTable().clearAll();
	// }
	// };
	// display.asyncExec(run);
	// }

	private void refreshRoomList(final String[] tokens) {
		if (tokens.length < 2)
			return;

		Runnable run = new Runnable() {
			@Override
			public void run() {
				playRoomMap.clear();
				playRoomListViewer.getTable().clearAll();

				try {
					for (int i = 0; i < tokens.length; i += 5) {
						String masterName = tokens[i];
						int maxPlayers = Integer.parseInt(tokens[i + 1]);
						int currentPlayers = Integer.parseInt(tokens[i + 2]);
						boolean hasPassword = "Y".equals(tokens[i + 3]);
						String title = tokens[i + 4];

						PlayRoom room = new PlayRoom(masterName, title, hasPassword, currentPlayers, maxPlayers);
						playRoomMap.put(room.getMasterName(), room);
						playRoomListViewer.add(room);
					}
				} catch (NumberFormatException e) {
					appendLogTo(logText, Utility.makeStackTrace(e));
				}

				playRoomListViewer.refresh();
			}
		};
		display.asyncExec(run);
	}

	private void updateRoom(final String[] tokens) {
		Runnable run = new Runnable() {
			@Override
			public void run() {
				try {
					String masterName = tokens[0];
					int maxPlayers = Integer.parseInt(tokens[1]);
					int currentPlayers = Integer.parseInt(tokens[2]);
					boolean hasPassword = "Y".equals(tokens[3]);
					String title = tokens[4];

					PlayRoom room = playRoomMap.get(masterName);
					room.setMaxPlayers(maxPlayers);
					room.setCurrentPlayerCount(currentPlayers);
					room.setHasPassword(hasPassword);
					room.setTitle(title);

					if (currentOperationMode == OperationMode.PlayRoomParticipant) {
						if (masterName.equals(roomFormMasterText.getText())) {
							roomFormMaxPlayersSpiner.setSelection(maxPlayers);
							roomFormTitleText.setText(title);
							if (tokens.length == 6)
								roomFormDesctiptionText.setText(tokens[5]);
							roomFormPasswordText.setText("");
						}
					}

					playRoomListViewer.refresh(room);

				} catch (NumberFormatException e) {
					appendLogTo(logText, Utility.makeStackTrace(e));
				}
			}
		};
		display.asyncExec(run);
	}

	private void goTo(final OperationMode mode) {
		currentOperationMode = mode;

		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				switch (mode) {
				case Portal:
					break;
				case ArenaLobby:
					mainTabFolder.setSelection(arenaLobbyTab);

					roomFormCreateEditButton.setText("部屋を作成");
					roomFormCreateEditButton.setEnabled(true);

					roomFormCloseExitButton.setText("部屋を閉じる");
					roomFormCloseExitButton.setEnabled(false);

					roomPlayerMap.clear();
					roomPlayerListViewer.refresh();

					playRoomMap.clear();
					playRoomListViewer.refresh();

					roomFormMasterText.setText(loginUserName);

					setEnableRoomFotmItems(true);

					break;
				case PlayRoomMaster:
					mainTabFolder.setSelection(playRoomTab);

					roomFormCreateEditButton.setText("部屋を修正");
					roomFormCreateEditButton.setEnabled(true);

					roomFormCloseExitButton.setText("部屋を閉じる");
					roomFormCloseExitButton.setEnabled(true);

					setEnableRoomFotmItems(true);

					break;
				case PlayRoomParticipant:
					mainTabFolder.setSelection(playRoomTab);

					roomFormCreateEditButton.setEnabled(false);

					roomFormCloseExitButton.setText("退室する");
					roomFormCloseExitButton.setEnabled(true);

					setEnableRoomFotmItems(false);

					break;
				case Offline:
					arenaServerAddressCombo.setEnabled(true);
					arenaServerLoginButton.setText("ログイン");
					arenaInfoContainer.layout();
					arenaServerLoginButton.setEnabled(true);

					statusServerAddressLabel.setText("サーバーアドレス");
					statusServerStatusLabel.setText("サーバーステータス");
					statusBarContainer.layout();

					lobbyPlayerMap.clear();
					arenaLobbyPlayerListViewer.getTable().clearAll();

					playRoomMap.clear();
					playRoomListViewer.refresh();

					roomPlayerMap.clear();
					roomPlayerListViewer.refresh();

					roomFormCreateEditButton.setEnabled(false);
					roomFormCloseExitButton.setEnabled(false);
					setEnableRoomFotmItems(false);

					configUserNameText.setEnabled(true);

					mainTabFolder.setSelection(arenaLobbyTab);

					break;
				case ConnectingToServer:
					arenaServerLoginButton.setText("ログアウト");
					arenaInfoContainer.layout();
					arenaServerLoginButton.setEnabled(true);

					configUserNameText.setEnabled(false);

					break;
				}
			}
		});
	}

	private void setEnableRoomFotmItems(boolean enabled) {
		roomFormTitleText.setEditable(enabled);
		roomFormPasswordText.setEditable(enabled);
		roomFormMaxPlayersSpiner.setEnabled(enabled);
		roomFormDesctiptionText.setEditable(enabled);
	}

	private interface CommandHandler {
		public void process(String argument);
	}

	private class SessionHandler implements IAsyncClientHandler {
		private HashMap<String, CommandHandler> handlers = new HashMap<String, ArenaClientWindow.CommandHandler>();

		public SessionHandler() {
			handlers.put(Constants.Protocol.SERVER_PORTAL, new ServerPortalHandler());
			handlers.put(Constants.Protocol.SERVER_ARENA, new ServerArenaHandler());
			handlers.put(Constants.Protocol.ERROR_VERSION_MISMATCH, new ErrorVersionMismatchHandler());
			handlers.put(Constants.Protocol.COMMAND_LOGIN, new LoginHandler());
			handlers.put(Constants.Protocol.COMMAND_SERVER_STATUS, new ServerStatusHandler());
			handlers.put(Constants.Protocol.COMMAND_CHAT, new ChatHandler());
			handlers.put(Constants.Protocol.COMMAND_PINGBACK, new PingBackHandler());
			handlers.put(Constants.Protocol.COMMAND_INFORM_PING, new InformPingHandler());
			handlers.put(Constants.Protocol.COMMAND_INFORM_TUNNEL_UDP_PORT, new InformTunnelPortHandler());
			handlers.put(Constants.Protocol.NOTIFY_USER_ENTERED, new NotifyUserEnteredHandler());
			handlers.put(Constants.Protocol.NOTIFY_USER_EXITED, new NotifyUserExitedHandler());
			handlers.put(Constants.Protocol.NOTIFY_USER_LIST, new NotifyUserListHandler());
			handlers.put(Constants.Protocol.NOTIFY_ROOM_CREATED, new NotifyRoomCreatedHandler());
			handlers.put(Constants.Protocol.NOTIFY_ROOM_DELETED, new NotifyRoomDeletedHandler());
			handlers.put(Constants.Protocol.NOTIFY_ROOM_LIST, new NotifyRoomListHandler());
			handlers.put(Constants.Protocol.NOTIFY_ROOM_UPDATED, new NotifyRoomUpdatedHandler());
			handlers.put(Constants.Protocol.COMMAND_ROOM_CREATE, new CommandRoomCreateHandler());
			handlers.put(Constants.Protocol.COMMAND_ROOM_UPDATE, new CommandRoomUpdateHandler());
			handlers.put(Constants.Protocol.COMMAND_ROOM_DELETE, new CommandRoomDeleteHandler());
			handlers.put(Constants.Protocol.COMMAND_ROOM_ENTER, new CommandRoomEnterHandler());
			handlers.put(Constants.Protocol.COMMAND_ROOM_EXIT, new CommandRoomExitHandler());
			handlers.put(Constants.Protocol.COMMAND_ROOM_KICK_PLAYER, new CommandRoomKickPlayerHandler());
			handlers.put(Constants.Protocol.ERROR_LOGIN_DUPLICATED_NAME, new ErrorLoginDuplicatedNameHandler());
			handlers.put(Constants.Protocol.ERROR_LOGIN_BEYOND_CAPACITY, new ErrorLoginBeyondCapacityHandler());
			handlers.put(Constants.Protocol.NOTIFY_ROOM_PASSWORD_REQUIRED, new NotifyRoomPasswordRequiredHandler());
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
			goTo(OperationMode.ConnectingToServer);
			appendLogTo(arenaChatLogText, "サーバーに接続しました", colorServerInfo);

			display.asyncExec(new Runnable() {
				@Override
				public void run() {
					String serverAddress = arenaServerAddressCombo.getText();
					int index = arenaServerHistory.indexOf(serverAddress);
					int historyStartIndex = arenaServerListCount + 1;

					if (index == -1) {
						arenaServerHistory.add(0, serverAddress);
						arenaServerAddressCombo.add(serverAddress, historyStartIndex);
						if (arenaServerHistory.size() > MAX_SERVER_HISTORY) {
							arenaServerHistory.removeLast();
							arenaServerAddressCombo.remove(arenaServerAddressCombo.getItemCount() - 1);
						}
					} else {
						arenaServerHistory.remove(index);
						arenaServerHistory.add(0, serverAddress);

						arenaServerAddressCombo.remove(historyStartIndex + index);
						arenaServerAddressCombo.add(serverAddress, historyStartIndex);
						arenaServerAddressCombo.select(historyStartIndex);
					}
				}
			});

			client.send(Constants.Protocol.COMMAND_VERSION + " " + Constants.Protocol.PROTOCOL_NUMBER);
			client.send(Constants.Protocol.COMMAND_LOGIN + " " + loginUserName);
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
			goTo(OperationMode.Offline);
			appendLogTo(arenaChatLogText, "サーバーと切断しました", colorServerInfo);
		}

		private class ErrorVersionMismatchHandler implements CommandHandler {
			@Override
			public void process(String num) {
				String message = String.format("サーバーとのプロトコルナンバーが一致ないので接続できません サーバー:%s クライアント:%s", num, Constants.Protocol.PROTOCOL_NUMBER);
				appendLogTo(arenaChatLogText, message, colorLogError);
			}
		}

		private class ServerPortalHandler implements CommandHandler {
			@Override
			public void process(String message) {
				// ポータルサーバーです　エラー表示
			}
		}

		private class ServerArenaHandler implements CommandHandler {
			@Override
			public void process(String message) {
				updateServerAddress("アリーナ");
				goTo(OperationMode.ArenaLobby);
			}
		}

		private class LoginHandler implements CommandHandler {
			@Override
			public void process(String args) {
				appendLogTo(arenaChatLogText, "ロビーに入りました", colorRoomInfo);
				try {
					arenaTunnelClient.connect(arenaSocketAddress);
				} catch (IOException e1) {
				}

				Runnable run = new Runnable() {
					@Override
					public void run() {
						try {
							while (arenaSessionClient.isConnected()) {
								if (currentOperationMode == OperationMode.PlayRoomMaster
										|| currentOperationMode == OperationMode.PlayRoomParticipant)
									arenaSessionClient.send(Constants.Protocol.COMMAND_PING + " " + System.currentTimeMillis());
								Thread.sleep(5000);
							}
						} catch (InterruptedException e) {
						}
					}
				};
				Thread pingThread = new Thread(run, "PingThread");
				pingThread.start();
			}
		}

		private class ChatHandler implements CommandHandler {
			@Override
			public void process(String args) {
				switch (currentOperationMode) {
				case ArenaLobby:
					appendLogTo(arenaChatLogText, args, colorBlack);
					break;
				case PlayRoomMaster:
				case PlayRoomParticipant:
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

				arenaSessionClient.send(Constants.Protocol.COMMAND_INFORM_PING + " " + ping);
			}
		}

		private class InformPingHandler implements CommandHandler {
			@Override
			public void process(String args) {
				try {
					switch (currentOperationMode) {
					case PlayRoomMaster:
					case PlayRoomParticipant:
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

		private class ServerStatusHandler implements CommandHandler {
			@Override
			public void process(final String args) {
				Runnable run = new Runnable() {
					@Override
					public void run() {
						String[] v = args.split(" ");
						String text = String.format("参加者数: %s / %s     部屋数: %s / %s", v[0], v[1], v[2], v[3]);
						statusServerStatusLabel.setText(text);
						statusBarContainer.layout();
					}
				};
				display.asyncExec(run);
			}
		}

		private class NotifyUserEnteredHandler implements CommandHandler {
			@Override
			public void process(String name) {
				switch (currentOperationMode) {
				case ArenaLobby:
					addPlayer(arenaLobbyPlayerListViewer, name);
					if (isArenaEntryExitLogEnabled)
						appendLogTo(arenaChatLogText, name + " がロビーに来ました", colorLogInfo);
					break;
				case PlayRoomMaster:
				case PlayRoomParticipant:
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
				case ArenaLobby:
					removePlayer(arenaLobbyPlayerListViewer, name);
					if (isArenaEntryExitLogEnabled)
						appendLogTo(arenaChatLogText, name + " がロビーから出ました", colorLogInfo);
					break;
				case PlayRoomMaster:
				case PlayRoomParticipant:
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
				case ArenaLobby:
					replacePlayerList(arenaLobbyPlayerListViewer, players);
					break;
				case PlayRoomMaster:
				case PlayRoomParticipant:
					replacePlayerList(roomPlayerListViewer, players);
					break;
				}
			}
		}

		private class NotifyRoomCreatedHandler implements CommandHandler {
			@Override
			public void process(String args) {
				addRoom(args.split(" "));
			}
		}

		private class NotifyRoomDeletedHandler implements CommandHandler {
			@Override
			public void process(String masterName) {
				removeRoom(masterName);
			}
		}

		private class NotifyRoomListHandler implements CommandHandler {
			@Override
			public void process(String args) {
				String[] tokens = args.split(" ");
				refreshRoomList(tokens);
			}
		}

		private class NotifyRoomUpdatedHandler implements CommandHandler {
			@Override
			public void process(String args) {
				updateRoom(args.split(" "));
			}
		}

		private class CommandRoomCreateHandler implements CommandHandler {
			@Override
			public void process(String message) {
				replacePlayerList(roomPlayerListViewer, new String[] { loginUserName });
				appendLogTo(roomChatLogText, "部屋を作成しました", colorRoomInfo);
				goTo(OperationMode.PlayRoomMaster);
			}
		}

		private class CommandRoomUpdateHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(roomChatLogText, "部屋情報を修正しました", colorRoomInfo);
			}
		}

		private class CommandRoomDeleteHandler implements CommandHandler {
			@Override
			public void process(String message) {
				switch (currentOperationMode) {
				case PlayRoomMaster:
					appendLogTo(arenaChatLogText, "部屋を閉じました", colorRoomInfo);
					break;
				case PlayRoomParticipant:
					appendLogTo(arenaChatLogText, "部屋が閉じられました", colorRoomInfo);
					break;
				}

				goTo(OperationMode.ArenaLobby);
			}
		}

		private class CommandRoomEnterHandler implements CommandHandler {
			@Override
			public void process(String args) {
				final String[] tokens = args.split(" ");
				final String masterName = tokens[0];

				appendLogTo(roomChatLogText, masterName + " 部屋に入りました", colorRoomInfo);
				goTo(OperationMode.PlayRoomParticipant);

				Runnable run = new Runnable() {
					@Override
					public void run() {
						roomFormMasterText.setText(masterName);
						roomFormMaxPlayersSpiner.setSelection(Integer.parseInt(tokens[1]));
						roomFormTitleText.setText(tokens[2]);
						if (tokens.length == 4)
							roomFormDesctiptionText.setText(tokens[3]);
						roomFormPasswordText.setText("");
					}
				};
				display.asyncExec(run);
			}
		}

		private class CommandRoomExitHandler implements CommandHandler {
			@Override
			public void process(String message) {
				goTo(OperationMode.ArenaLobby);
				appendLogTo(arenaChatLogText, "ロビーに出ました", colorRoomInfo);
			}
		}

		private class CommandRoomKickPlayerHandler implements CommandHandler {
			@Override
			public void process(String kickedPlayer) {
				if (loginUserName.equals(kickedPlayer)) {
					goTo(OperationMode.ArenaLobby);
					appendLogTo(arenaChatLogText, "部屋から追い出されました", colorRoomInfo);
				} else {
					removePlayer(roomPlayerListViewer, kickedPlayer);
					if (currentOperationMode == OperationMode.PlayRoomMaster) {
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
				tunnelIsLinked = true;
				updateTunnelStatus(true);
				appendLogTo(logText, "トンネル通信の接続が開始しました");

				Thread natTableMaintainingThread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							while (arenaTunnelClient.isConnected()) {
								arenaTunnelClient.send(" ");
								Thread.sleep(20000);
							}
						} catch (InterruptedException e) {
						}
					}
				});
				natTableMaintainingThread.setDaemon(true);
				natTableMaintainingThread.setName("NatTableMaintaining");
				natTableMaintainingThread.start();
			}
		}

		private class ErrorLoginDuplicatedNameHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(arenaChatLogText, "同名のユーザーが既にログインしているのでログインできません", colorLogError);
			}
		}

		private class ErrorLoginBeyondCapacityHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(arenaChatLogText, "サーバーの最大人数を超えたのでログインできません", colorLogError);
			}
		}

		private class NotifyRoomPasswordRequiredHandler implements CommandHandler {
			@Override
			public void process(final String masterName) {
				Runnable run = new Runnable() {
					@Override
					public void run() {
						PasswordDialog dialog = new PasswordDialog(shell);
						switch (dialog.open()) {
						case IDialogConstants.OK_ID:
							String password = dialog.getPassword();
							// appendLogTo(arenaChatLogText, password,
							// colorRoomInfo);
							String message = Constants.Protocol.COMMAND_ROOM_ENTER + " " + masterName + " " + password;

							arenaSessionClient.send(message);
							break;
						case IDialogConstants.CANCEL_ID:
							appendLogTo(arenaChatLogText, "入室をキャンセルしました", colorRoomInfo);
							break;
						}
					}
				};
				display.asyncExec(run);
			}
		}

		private class ErrorRoomEnterPasswordFailHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(arenaChatLogText, "部屋パスワードが一致しないので入室できません", colorRoomInfo);
			}
		}

		private class ErrorRoomEnterBeyondCapacityHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(arenaChatLogText, "部屋が満室なので入れません", colorRoomInfo);
			}
		}

		private class ErrorRoomCreateBeyondLimitHandler implements CommandHandler {
			@Override
			public void process(String message) {
				appendLogTo(arenaChatLogText, "部屋数が上限に達しましたので部屋を作成できません", colorRoomInfo);
			}
		}
	}

	private class TunnelHandler implements IAsyncClientHandler {
		@Override
		public void connectCallback(IAsyncClient client) {
			client.send(" ");
		}

		@Override
		public void readCallback(IAsyncClient client, PacketData data) {
			ByteBuffer packet = data.getBuffer();
			// System.out.println(packet.toString());
			if (Utility.isPspPacket(packet)) {
				String destMac = Utility.makeMacAddressString(packet, 0, false);
				String srcMac = Utility.makeMacAddressString(packet, 6, false);

				// System.out.println("src: " + srcMac + " desc: " + destMac);

				TraficStatistics destStats, srcStats;
				synchronized (traficStatsMap) {
					destStats = traficStatsMap.get(destMac);
					srcStats = traficStatsMap.get(srcMac);

					if (srcStats == null) {
						srcStats = new TraficStatistics(false);
						traficStatsMap.put(srcMac, srcStats);
					}

					if (destStats == null) {
						destStats = new TraficStatistics(!"FFFFFFFFFFFF".equals(destMac));
						traficStatsMap.put(destMac, destStats);
					}
				}

				if (isPacketCapturing && currentPcap != null) {
					srcStats.lastModified = System.currentTimeMillis();
					srcStats.currentInBytes += packet.limit();
					srcStats.totalInBytes += packet.limit();

					destStats.lastModified = System.currentTimeMillis();
					destStats.currentInBytes += packet.limit();
					destStats.totalInBytes += packet.limit();

					// send packet
					currentPcap.sendPacket(packet);
				}
			} else {
				try {
					String tunnelPort = data.getMessage();
					int port = Integer.parseInt(tunnelPort);
					// System.out.println("Port: " + port);
					arenaSessionClient.send(Constants.Protocol.COMMAND_INFORM_TUNNEL_UDP_PORT + " " + port);
				} catch (NumberFormatException e) {
				}
			}
		}

		@Override
		public void disconnectCallback(IAsyncClient client) {
			tunnelIsLinked = false;
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

		// for (PcapIf device : wlanAdaptorList) {
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
		if (lastUsedIndex != 0)
			wlanPspCommunicationButton.setEnabled(true);
	}

	private void processCapturedPacket(PcapPacket packet) {
		// if (packet != null) {
		// System.out.println(packet.toHexdump());
		// return;
		// }

		Ethernet ethernet = new Ethernet();
		if (packet.hasHeader(ethernet)) {
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

				switch (currentOperationMode) {
				case PlayRoomMaster:
				case PlayRoomParticipant:
					destStats.lastModified = System.currentTimeMillis();
					destStats.currentOutBytes += packetLength;
					destStats.totalOutBytes += packetLength;

					capturedByteBuffer.clear();
					packet.transferTo(capturedByteBuffer);
					capturedByteBuffer.flip();

					// System.out.printf("%s => %s  [%d]", srcMac, destMac,
					// packetLength);
					// System.out.println(packet.toHexdump());

					arenaTunnelClient.send(capturedByteBuffer);

					break;
				}
			}
		}
	}

	private boolean startPacketCapturing() {
		int index = wlanAdaptorListCombo.getSelectionIndex() - 1;
		PcapIf device = wlanAdaptorList.get(index);

		StringBuilder errbuf = new StringBuilder();
		currentPcap = Pcap.openLive(device.getName(), CAPTURE_BUFFER_SIZE, Pcap.MODE_PROMISCUOUS, 1, errbuf);
		if (currentPcap == null) {
			appendLogTo(logText, errbuf.toString());
			return false;
		}

		Thread packetCaptureThread = new Thread(new Runnable() {
			PcapPacket packet = new PcapPacket(JMemory.POINTER);

			@Override
			public void run() {
				while (isPacketCapturing) {
					switch (currentPcap.nextEx(packet)) {
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
								isPacketCapturing = false;
								wlanPspCommunicationButton.setEnabled(false);
							}
						};
						display.syncExec(run);
						break;
					}
				}

				currentPcap.close();
				currentPcap = null;

				Runnable run = new Runnable() {
					@Override
					public void run() {
						wlanAdaptorListCombo.setEnabled(true);
						wlanPspCommunicationButton.setText("PSPと通信開始");
						wlanPspCommunicationButton.setEnabled(true);
					}
				};
				display.syncExec(run);
			}
		}, "PacketCaptureThread");

		isPacketCapturing = true;
		packetCaptureThread.setDaemon(true);
		packetCaptureThread.start();

		Thread packetMonitorThread = new Thread(new Runnable() {
			@Override
			public void run() {
				int intervalMillis = 1000;

				Runnable run = new Runnable() {
					@Override
					public void run() {
						packetMonitorTableViewer.setInput(traficStatsMap);
						packetMonitorTableViewer.refresh();
					}
				};

				while (isPacketCapturing) {
					try {
						long deadlineTime = System.currentTimeMillis() - 10000;

						synchronized (traficStatsMap) {
							Iterator<Entry<String, TraficStatistics>> iter = traficStatsMap.entrySet().iterator();
							while (iter.hasNext()) {
								Entry<String, TraficStatistics> entry = iter.next();
								TraficStatistics stats = entry.getValue();

								if (stats.lastModified < deadlineTime) {
									iter.remove();
								}

								stats.currentInKbps = ((double) stats.currentInBytes) * 8 / intervalMillis;
								stats.currentOutKbps = ((double) stats.currentOutBytes) * 8 / intervalMillis;

								stats.currentInBytes = 0;
								stats.currentOutBytes = 0;
							}
						}

						display.syncExec(run);

						Thread.sleep(intervalMillis);
					} catch (InterruptedException e) {
						break;
					}
				}

				run = new Runnable() {
					@Override
					public void run() {
						synchronized (traficStatsMap) {
							traficStatsMap.clear();
						}
						packetMonitorTableViewer.setInput(traficStatsMap);
					}
				};
				display.syncExec(run);
			}
		}, "PacketMonitorThread");

		packetMonitorThread.setDaemon(true);
		packetMonitorThread.start();

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
		String iniFileName = "ArenaClient.ini";
		switch (args.length) {
		case 1:
			iniFileName = args[0];
			break;
		}
		IniParser parser = new IniParser(iniFileName);

		try {
			new ArenaClientWindow(parser).start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
