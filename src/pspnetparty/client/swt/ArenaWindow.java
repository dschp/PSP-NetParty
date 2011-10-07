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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import pspnetparty.client.swt.PlayClient.PortalQuery;
import pspnetparty.client.swt.config.IniAppData;
import pspnetparty.client.swt.config.IniSettings;
import pspnetparty.client.swt.config.IniUserProfile;
import pspnetparty.client.swt.config.UserProfilePage;
import pspnetparty.client.swt.message.AdminNotify;
import pspnetparty.client.swt.message.Chat;
import pspnetparty.client.swt.message.ErrorLog;
import pspnetparty.client.swt.message.InfoLog;
import pspnetparty.client.swt.message.LogViewer;
import pspnetparty.client.swt.message.PrivateChat;
import pspnetparty.client.swt.message.ServerLog;
import pspnetparty.lib.LobbyUser;
import pspnetparty.lib.LobbyUserState;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.engine.PlayRoom;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.IProtocolMessageHandler;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.TextProtocolDriver;

public class ArenaWindow implements IAppWindow {
	enum RoomListSessionState {
		OFFLINE, CONNECTING, LOGIN,
	}

	enum LobbySessionState {
		OFFLINE, CONNECTING, LOGIN,
	}

	private static final String[] LOBBY_USER_STATES = new String[] { "参加中", "離席中", "プレイ中", "非アクティブ" };

	private final long lobbyInactivityInterval = 30 * 60 * 1000;

	private boolean isActive;

	private RoomListProtocol roomListProtocol = new RoomListProtocol();
	private ISocketConnection roomListConnection = ISocketConnection.NULL;
	private HashMap<String, Set<PlayRoom>> roomListServerMap = new HashMap<String, Set<PlayRoom>>();
	private HashMap<String, PlayRoom> roomListMap = new HashMap<String, PlayRoom>();

	private RoomListSessionState roomListSession = RoomListSessionState.OFFLINE;

	private LobbySessionState lobbySession = LobbySessionState.OFFLINE;
	private LobbyUserState lobbyUserState = LobbyUserState.OFFLINE;

	private LobbyProtocol lobbyProtocol = new LobbyProtocol();
	private ISocketConnection lobbyConnection = ISocketConnection.NULL;

	private LobbyUser myself = new LobbyUser(null, LobbyUserState.OFFLINE);
	private long lobbyLastActivity = 0L;

	private HashMap<String, LobbyUser> allLobbyUsers = new HashMap<String, LobbyUser>();

	private PlayClient application;
	private Shell shell;

	private UserProfileWindow profileWindow;

	private SashForm mainSash;
	private Composite toolBarContainer;
	private Button roomListServerLoginButton;
	private Composite roomSearchFormControlContainer;
	private Button roomSearchFormHasPassword;
	private Button roomSearchFormOnlyVacant;
	private Button roomSearchOpenRoomWindow;
	private Label roomListServerAddressLabel;
	private Label roomListServerStatusLabel;
	private Label roomSearchResultLabel;
	private Combo roomSearchFormTitleCombo;
	private Combo roomSearchFormTitleNgCombo;
	private Combo roomSearchFormMasterNameCombo;
	private Combo roomSearchFormMasterNameNgCombo;
	private Button roomSearchFormClear;
	private Button roomSearchFormFilter;
	private TableViewer roomListTableViewer;

	private RoomMasterFilter roomMasterFilter = new RoomMasterFilter(true);
	private RoomMasterFilter roomMasterNgFilter = new RoomMasterFilter(false);
	private RoomTitleFilter roomTitleFilter = new RoomTitleFilter(true);
	private RoomTitleFilter roomTitleNgFilter = new RoomTitleFilter(false);
	private RoomPasswordFilter roomPasswordFilter = new RoomPasswordFilter();
	private RoomVacantFilter roomVacantFilter = new RoomVacantFilter();

	private ComboHistoryManager roomTitleHistoryManager;
	private ComboHistoryManager roomTitleNgHistoryManager;
	private ComboHistoryManager roomMasterNameHistoryManager;
	private ComboHistoryManager roomMasterNameNgHistoryManager;

	private Button lobbyServerLoginButton;
	private Label lobbyServerAddressLabel;
	private Label lobbyUserCountLabel;
	private Label lobbyUserNameLabel;
	private Combo lobbyUserStateCombo;
	private Button lobbyProfileEdit;
	private Button lobbyCircleAdd;
	private TabFolder lobbyCircleTabFolder;

	private LobbyCircleTab lobbyTab;
	private LobbyCircleTab pmTab;
	private TabItem systemLogTab;

	private HashMap<String, LobbyCircleTab> myCircleTabs = new HashMap<String, LobbyCircleTab>();
	private HashMap<String, HashMap<String, LobbyUser>> circleMap = new HashMap<String, HashMap<String, LobbyUser>>();

	private Menu menuLobbyTab;
	private MenuItem menuItemAddCircle;
	private Menu menuCircleTab;
	private MenuItem menuItemCloseCircle;

	private Text systemLogText;

	public ArenaWindow(final PlayClient application) {
		this.application = application;

		ImageRegistry imageRegistry = application.getImageRegistry();
		// ColorRegistry colorRegistry = application.getColorRegistry();

		shell = new Shell(SWT.SHELL_TRIM);
		shell.setText("アリーナ - " + AppConstants.APP_NAME);

		try {
			shell.setImages(application.getShellImages());
		} catch (RuntimeException e) {
			e.printStackTrace();
		}

		GridLayout gridLayout;
		GridData gridData;

		gridLayout = new GridLayout(1, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 0;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		shell.setLayout(gridLayout);

		toolBarContainer = new Composite(shell, SWT.NONE);
		toolBarContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		toolBarContainer.setLayout(new FillLayout());

		application.createToolBar(toolBarContainer, this);

		mainSash = new SashForm(shell, SWT.VERTICAL);
		mainSash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Composite roomListContainer = new Composite(mainSash, SWT.NONE);
		gridLayout = new GridLayout(1, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = 3;
		roomListContainer.setLayout(gridLayout);

		Composite roomSearchFormContainer = new Composite(roomListContainer, SWT.NONE);
		gridLayout = new GridLayout(5, false);
		gridLayout.horizontalSpacing = 3;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 1;
		gridLayout.marginHeight = 1;
		roomSearchFormContainer.setLayout(gridLayout);
		roomSearchFormContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		roomSearchFormControlContainer = new Composite(roomSearchFormContainer, SWT.NONE);
		roomSearchFormControlContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 5, 1));
		gridLayout = new GridLayout(6, false);
		gridLayout.horizontalSpacing = 6;
		gridLayout.verticalSpacing = 0;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		roomSearchFormControlContainer.setLayout(gridLayout);

		roomListServerLoginButton = new Button(roomSearchFormControlContainer, SWT.TOGGLE);
		gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		roomListServerLoginButton.setLayoutData(gridData);
		application.initControl(roomListServerLoginButton);

		Composite roomListStatusBarContainer = new Composite(roomSearchFormControlContainer, SWT.NONE);
		roomListStatusBarContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		gridLayout = new GridLayout(6, false);
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		roomListStatusBarContainer.setLayout(gridLayout);

		roomListServerAddressLabel = new Label(roomListStatusBarContainer, SWT.NONE);
		roomListServerAddressLabel.setText("サーバー: ");

		gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		gridData.heightHint = 15;
		new Label(roomListStatusBarContainer, SWT.SEPARATOR | SWT.VERTICAL).setLayoutData(gridData);

		roomListServerStatusLabel = new Label(roomListStatusBarContainer, SWT.NONE);
		roomListServerStatusLabel.setText("ログインしていません");

		new Label(roomListStatusBarContainer, SWT.SEPARATOR | SWT.VERTICAL).setLayoutData(gridData);

		roomSearchResultLabel = new Label(roomListStatusBarContainer, SWT.NONE);
		roomSearchResultLabel.setText("検索結果: なし");
		roomSearchResultLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));

		Label roomSearchServerAddressLabel = new Label(roomSearchFormControlContainer, SWT.NONE);
		roomSearchServerAddressLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initControl(roomSearchServerAddressLabel);

		roomSearchFormHasPassword = new Button(roomSearchFormControlContainer, SWT.CHECK | SWT.FLAT);
		roomSearchFormHasPassword.setText("鍵付き");
		application.initControl(roomSearchFormHasPassword);

		roomSearchFormOnlyVacant = new Button(roomSearchFormControlContainer, SWT.CHECK | SWT.FLAT);
		roomSearchFormOnlyVacant.setText("満室非表示");
		roomSearchFormOnlyVacant.setSelection(true);
		application.initControl(roomSearchFormOnlyVacant);

		roomSearchOpenRoomWindow = new Button(roomSearchFormControlContainer, SWT.PUSH);
		roomSearchOpenRoomWindow.setText("部屋を作成する");
		application.initControl(roomSearchOpenRoomWindow);

		Label roomSearchFormMasterNameLabel = new Label(roomSearchFormContainer, SWT.NONE);
		roomSearchFormMasterNameLabel.setText("部屋主");
		gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		gridData.horizontalIndent = 4;
		roomSearchFormMasterNameLabel.setLayoutData(gridData);
		application.initControl(roomSearchFormMasterNameLabel);

		roomSearchFormMasterNameCombo = new Combo(roomSearchFormContainer, SWT.BORDER);
		roomSearchFormMasterNameCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initControl(roomSearchFormMasterNameCombo);

		Label roomSeaerchFormMasterNameNgLabel = new Label(roomSearchFormContainer, SWT.NONE);
		roomSeaerchFormMasterNameNgLabel.setText("除外");
		roomSeaerchFormMasterNameNgLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		application.initControl(roomSeaerchFormMasterNameNgLabel);

		roomSearchFormMasterNameNgCombo = new Combo(roomSearchFormContainer, SWT.NONE);
		roomSearchFormMasterNameNgCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initControl(roomSearchFormMasterNameNgCombo);

		roomSearchFormClear = new Button(roomSearchFormContainer, SWT.PUSH);
		roomSearchFormClear.setText("クリア");
		roomSearchFormClear.setEnabled(false);
		roomSearchFormClear.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

		Label roomSearchFormTitleLabel = new Label(roomSearchFormContainer, SWT.NONE);
		roomSearchFormTitleLabel.setText("部屋名");
		gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		gridData.horizontalIndent = 4;
		roomSearchFormTitleLabel.setLayoutData(gridData);
		application.initControl(roomSearchFormTitleLabel);

		roomSearchFormTitleCombo = new Combo(roomSearchFormContainer, SWT.BORDER);
		roomSearchFormTitleCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initControl(roomSearchFormTitleCombo);

		Label roomSearchFormTitleNgLabel = new Label(roomSearchFormContainer, SWT.NONE);
		roomSearchFormTitleNgLabel.setText("除外");
		roomSearchFormTitleNgLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		application.initControl(roomSearchFormTitleNgLabel);

		roomSearchFormTitleNgCombo = new Combo(roomSearchFormContainer, SWT.NONE);
		roomSearchFormTitleNgCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initControl(roomSearchFormTitleNgCombo);

		roomSearchFormFilter = new Button(roomSearchFormContainer, SWT.TOGGLE);
		roomSearchFormFilter.setText("絞り込む");
		roomSearchFormFilter.setEnabled(false);
		roomSearchFormFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

		roomListTableViewer = new TableViewer(roomListContainer, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
		Table roomListTable = roomListTableViewer.getTable();

		roomListTable.setHeaderVisible(true);
		roomListTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		application.initControl(roomListTable);

		TableColumn columnMasterName = new TableColumn(roomListTable, SWT.LEFT);
		columnMasterName.setText("部屋主");
		SwtUtils.installSorter(roomListTableViewer, columnMasterName, PlayRoomUtils.MASTER_NAME_SORTER);

		TableColumn columnTitle = new TableColumn(roomListTable, SWT.LEFT);
		columnTitle.setText("部屋名");
		SwtUtils.installSorter(roomListTableViewer, columnTitle, PlayRoomUtils.TITLE_SORTER);

		TableColumn columnCapacity = new TableColumn(roomListTable, SWT.CENTER);
		columnCapacity.setText("定員");
		SwtUtils.installSorter(roomListTableViewer, columnCapacity, PlayRoomUtils.CAPACITY_SORTER);

		TableColumn columnDescription = new TableColumn(roomListTable, SWT.LEFT);
		columnDescription.setText("部屋の紹介");

		TableColumn columnRoomServer = new TableColumn(roomListTable, SWT.LEFT);
		columnRoomServer.setText("ルームサーバー");
		SwtUtils.installSorter(roomListTableViewer, columnRoomServer, PlayRoomUtils.ADDRESS_SORTER);

		TableColumn columnTimestamp = new TableColumn(roomListTable, SWT.CENTER);
		columnTimestamp.setText("作成日時");
		SwtUtils.installSorter(roomListTableViewer, columnTimestamp, PlayRoomUtils.TIMESTAMP_SORTER);

		SwtUtils.enableColumnDrag(roomListTable);
		roomListTableViewer.setContentProvider(PlayRoomUtils.CONTENT_PROVIDER);
		roomListTableViewer.setLabelProvider(PlayRoomUtils.LABEL_PROVIDER);
		roomListTableViewer.setInput(roomListMap);
		roomListTableViewer.addFilter(roomMasterFilter);
		roomListTableViewer.addFilter(roomMasterNgFilter);
		roomListTableViewer.addFilter(roomTitleFilter);
		roomListTableViewer.addFilter(roomTitleNgFilter);
		roomListTableViewer.addFilter(roomPasswordFilter);
		roomListTableViewer.addFilter(roomVacantFilter);

		Composite lobbyContainer = new Composite(mainSash, SWT.NONE);
		gridLayout = new GridLayout(1, false);
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginBottom = 2;
		gridLayout.verticalSpacing = 6;
		lobbyContainer.setLayout(gridLayout);

		Composite lobbyControlContainer = new Composite(lobbyContainer, SWT.NONE);
		lobbyControlContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(8, false);
		gridLayout.marginWidth = 1;
		gridLayout.marginHeight = 0;
		gridLayout.marginLeft = 1;
		gridLayout.marginTop = 3;
		lobbyControlContainer.setLayout(gridLayout);

		lobbyServerLoginButton = new Button(lobbyControlContainer, SWT.TOGGLE);
		lobbyServerLoginButton.setText("ロビーログイン");
		gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		lobbyServerLoginButton.setLayoutData(gridData);

		lobbyServerAddressLabel = new Label(lobbyControlContainer, SWT.NONE);
		lobbyServerAddressLabel.setText("サーバー: ");

		gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		gridData.heightHint = 15;
		new Label(lobbyControlContainer, SWT.SEPARATOR | SWT.VERTICAL).setLayoutData(gridData);

		lobbyUserCountLabel = new Label(lobbyControlContainer, SWT.NONE);
		lobbyUserCountLabel.setText("ユーザー数: ");

		lobbyUserNameLabel = new Label(lobbyControlContainer, SWT.NONE);
		lobbyUserNameLabel.setText("ユーザー名");
		lobbyUserNameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));

		lobbyUserStateCombo = new Combo(lobbyControlContainer, SWT.BORDER | SWT.READ_ONLY);
		lobbyUserStateCombo.setItems(LOBBY_USER_STATES);
		lobbyUserStateCombo.setEnabled(false);
		application.initControl(lobbyUserStateCombo);

		lobbyProfileEdit = new Button(lobbyControlContainer, SWT.PUSH);
		lobbyProfileEdit.setText("プロフィール編集");

		lobbyCircleAdd = new Button(lobbyControlContainer, SWT.PUSH);
		lobbyCircleAdd.setText("サークル追加");

		lobbyCircleTabFolder = new TabFolder(lobbyContainer, SWT.TOP);
		lobbyCircleTabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		Image lobbyIcon = imageRegistry.get(PlayClient.ICON_TAB_LOBBY);
		Image lobbyIconNotify = imageRegistry.get(PlayClient.ICON_TAB_LOBBY_NOTIFY);
		lobbyTab = new LobbyCircleTab("ロビー全体", allLobbyUsers, lobbyIcon, lobbyIconNotify, true);
		lobbyTab.tabItem.setToolTipText("ログインしている全てのユーザー");

		systemLogTab = new TabItem(lobbyCircleTabFolder, SWT.NONE);
		systemLogTab.setText("ログ");
		systemLogTab.setToolTipText("システムログ");
		systemLogTab.setImage(imageRegistry.get(PlayClient.ICON_TAB_LOG));

		Composite logContainer = new Composite(lobbyCircleTabFolder, SWT.NONE);
		logContainer.setLayout(new FillLayout());
		systemLogTab.setControl(logContainer);

		systemLogText = new Text(logContainer, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		application.initControl(systemLogText);

		Image pmIcon = imageRegistry.get(PlayClient.ICON_TAB_PM);
		Image pmIconNotify = imageRegistry.get(PlayClient.ICON_TAB_PM_NOTIFY);
		pmTab = new LobbyCircleTab("PM", allLobbyUsers, pmIcon, pmIconNotify, false);
		pmTab.tabItem.setToolTipText("プライベートメッセージ");

		initWidgetListeners();

		updateRoomListServerLoginButton(false);

		String[] stringList;

		IniAppData appData = application.getAppData();

		stringList = appData.getSearchHistoryRoomMaster();
		roomMasterNameHistoryManager = new ComboHistoryManager(roomSearchFormMasterNameCombo, stringList, 20, false);
		stringList = appData.getSearchHistoryRoomMasterNG();
		roomMasterNameNgHistoryManager = new ComboHistoryManager(roomSearchFormMasterNameNgCombo, stringList, 20, false);

		stringList = appData.getSearchHistoryTitle();
		roomTitleHistoryManager = new ComboHistoryManager(roomSearchFormTitleCombo, stringList, 20, false);
		stringList = appData.getSearchHistoryTitleNG();
		roomTitleNgHistoryManager = new ComboHistoryManager(roomSearchFormTitleNgCombo, stringList, 20, false);

		mainSash.setWeights(appData.getArenaSashWeights());
		appData.restoreSearchRoomTable(roomListTable);
		appData.restoreArenaWindow(shell);

		for (String circle : application.getUserProfile().getCircles()) {
			requestCircleJoin(circle);
		}
	}

	private void initWidgetListeners() {
		roomListServerLoginButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (roomListConnection.isConnected()) {
					roomListConnection.send(ProtocolConstants.Search.COMMAND_LOGOUT);
				} else {
					connectToRoomListServer();
				}
			}
		});
		roomListServerLoginButton.addMouseListener(new MouseListener() {
			@Override
			public void mouseUp(MouseEvent e) {
				if (roomListSession != RoomListSessionState.OFFLINE)
					return;
				if (e.button != 3)
					return;
				if (e.x < 0 || e.y < 0)
					return;
				Point size = roomListServerLoginButton.getSize();
				if (e.x > size.x || e.y > size.y)
					return;

				showRoomListServerSelectDialog();
			}

			@Override
			public void mouseDown(MouseEvent e) {
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
			}
		});
		roomSearchOpenRoomWindow.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				application.getRoomWindow().show();
			}
		});

		roomListTableViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent e) {
				IStructuredSelection sel = (IStructuredSelection) e.getSelection();
				PlayRoom room = (PlayRoom) sel.getFirstElement();
				if (room == null)
					return;

				ArenaWindow.this.application.getRoomWindow().autoConnectAsParticipant(room);
			}
		});

		SelectionListener formSelectionListener = new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Combo combo = (Combo) e.widget;
				String text = combo.getText();
				combo.setSelection(new Point(text.length(), text.length()));
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}
		};
		roomSearchFormMasterNameCombo.addSelectionListener(formSelectionListener);
		roomSearchFormMasterNameNgCombo.addSelectionListener(formSelectionListener);
		roomSearchFormTitleCombo.addSelectionListener(formSelectionListener);
		roomSearchFormTitleNgCombo.addSelectionListener(formSelectionListener);

		KeyListener roomSearchFormKeyListerner = new KeyListener() {
			@Override
			public void keyReleased(KeyEvent e) {
			}

			@Override
			public void keyPressed(KeyEvent e) {
				if (!roomSearchFormFilter.getEnabled())
					return;

				switch (e.character) {
				case SWT.CR:
				case SWT.LF:
					e.doit = false;
					roomSearchFormFilter.setSelection(true);
					applyRoomSearchFilter();
					break;
				default:
					roomSearchFormFilter.setSelection(false);
				}
			}
		};
		roomSearchFormMasterNameCombo.addKeyListener(roomSearchFormKeyListerner);
		roomSearchFormMasterNameNgCombo.addKeyListener(roomSearchFormKeyListerner);
		roomSearchFormTitleCombo.addKeyListener(roomSearchFormKeyListerner);
		roomSearchFormTitleNgCombo.addKeyListener(roomSearchFormKeyListerner);

		roomSearchFormTitleCombo.addVerifyListener(SwtUtils.NOT_ACCEPT_CONTROL_CHAR_LISTENER);
		roomSearchFormMasterNameCombo.addVerifyListener(SwtUtils.NOT_ACCEPT_CONTROL_CHAR_LISTENER);

		roomSearchFormHasPassword.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				roomPasswordFilter.hasPassword = roomSearchFormHasPassword.getSelection();

				roomListTableViewer.refresh();
				updateRoomSearchResult();
			}
		});
		roomSearchFormOnlyVacant.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				roomVacantFilter.onlyVacant = roomSearchFormOnlyVacant.getSelection();

				roomListTableViewer.refresh();
				updateRoomSearchResult();
			}
		});

		roomSearchFormFilter.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				applyRoomSearchFilter();
			}
		});

		roomSearchFormClear.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				roomSearchFormMasterNameCombo.setText("");
				roomSearchFormMasterNameNgCombo.setText("");
				roomSearchFormTitleCombo.setText("");
				roomSearchFormTitleNgCombo.setText("");

				roomSearchFormFilter.setSelection(false);
				applyRoomSearchFilter();
			}
		});

		lobbyServerLoginButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (lobbyConnection.isConnected()) {
					lobbyConnection.send(ProtocolConstants.Lobby.COMMAND_LOGOUT);
				} else {
					connectToLobbyServer();
				}
			}
		});
		lobbyServerLoginButton.addMouseListener(new MouseListener() {
			@Override
			public void mouseUp(MouseEvent e) {
				if (lobbySession != LobbySessionState.OFFLINE)
					return;
				if (e.button != 3)
					return;
				if (e.x < 0 || e.y < 0)
					return;
				Point size = lobbyServerLoginButton.getSize();
				if (e.x > size.x || e.y > size.y)
					return;

				selectLobbyServer();
			}

			@Override
			public void mouseDown(MouseEvent e) {
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
			}
		});

		lobbyUserStateCombo.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				sendLobbyState();
				lobbyTab.chatText.setFocus();
			}
		});

		lobbyProfileEdit.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (application.openConfigDialog(shell, UserProfilePage.PAGE_ID) && lobbySession == LobbySessionState.LOGIN) {
					IniUserProfile profile = application.getUserProfile();

					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.Lobby.COMMAND_UPDATE_PROFILE);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(profile.getUrl());
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(profile.getIconUrl());
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(profile.getProfile());

					lobbyConnection.send(sb.toString());
				}
				shell.setFocus();
			}
		});
		lobbyCircleAdd.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				openAddCircleDialog();
			}
		});

		menuLobbyTab = new Menu(shell, SWT.POP_UP);
		menuItemAddCircle = new MenuItem(menuLobbyTab, SWT.PUSH);
		menuItemAddCircle.setText("サークル追加");
		menuItemAddCircle.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				openAddCircleDialog();
			}
		});
		menuCircleTab = new Menu(shell, SWT.POP_UP);
		menuItemCloseCircle = new MenuItem(menuCircleTab, SWT.PUSH);
		menuItemCloseCircle.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				String circle = (String) menuItemCloseCircle.getData();
				if (Utility.isEmpty(circle))
					return;
				requestCircleLeave(circle);
			}
		});

		lobbyCircleTabFolder.addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				TabItem item = lobbyCircleTabFolder.getItem(lobbyCircleTabFolder.toControl(new Point(e.x, e.y)));
				if (item == null || lobbyCircleTabFolder.indexOf(item) < 3) {
					lobbyCircleTabFolder.setMenu(menuLobbyTab);
				} else {
					lobbyCircleTabFolder.setMenu(menuCircleTab);
					menuItemCloseCircle.setText("[" + item.getText() + "] を閉じる");
					menuItemCloseCircle.setData(item.getText());
				}
			}
		});
		lobbyCircleTabFolder.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event e) {
				int index = lobbyCircleTabFolder.getSelectionIndex();

				LobbyCircleTab tab;
				switch (index) {
				case 0:
					tab = lobbyTab;
					break;
				case 1:
					Image icon = application.getImageRegistry().get(PlayClient.ICON_TAB_LOG);
					systemLogTab.setImage(icon);
					return;
				case 2:
					tab = pmTab;
					break;
				default:
					TabItem item = lobbyCircleTabFolder.getItem(index);
					tab = myCircleTabs.get(item.getText());
				}

				tab.tabItem.setImage(tab.icon);
				if (tab.chatText != null)
					tab.chatText.setFocus();
			}
		});

		shell.addShellListener(new ShellListener() {
			@Override
			public void shellClosed(ShellEvent e) {
			}

			@Override
			public void shellIconified(ShellEvent e) {
				isActive = false;
			}

			@Override
			public void shellDeiconified(ShellEvent e) {
				isActive = true;
			}

			@Override
			public void shellDeactivated(ShellEvent e) {
				isActive = false;
			}

			@Override
			public void shellActivated(ShellEvent e) {
				isActive = true;
			}
		});
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				if (roomListConnection.isConnected())
					roomListConnection.disconnect();
				if (lobbyConnection.isConnected())
					lobbyConnection.disconnect();
				storeAppData();
			}
		});
	}

	@Override
	public Type getType() {
		return Type.ARENA;
	}

	@Override
	public void settingChanged() {
	}

	private void storeAppData() {
		IniAppData appData = application.getAppData();

		appData.storeArenaWindow(shell.getBounds());

		appData.setArenaSashWeights(mainSash.getWeights());
		appData.storeSearchRoomTable(roomListTableViewer.getTable());

		appData.setArenaLobbySashFormWeights(lobbyTab.mainSash.getWeights());
		appData.storeLobbyUserTable(lobbyTab.userListTableViewer.getTable());

		appData.setSearchHistoryRoomMaster(roomMasterNameHistoryManager.makeCSV());
		appData.setSearchHistoryRoomMasterNG(roomMasterNameNgHistoryManager.makeCSV());
		appData.setSearchHistoryTitle(roomTitleHistoryManager.makeCSV());
		appData.setSearchHistoryTitleNG(roomTitleNgHistoryManager.makeCSV());

		application.getUserProfile().setCircles(myCircleTabs.keySet());
	}

	public void reflectAppearance() {
		lobbyTab.chatLogViewer.applyAppearance();
		pmTab.chatLogViewer.applyAppearance();
		shell.layout(true, true);
	}

	public void show() {
		IniSettings settings = application.getSettings();
		if (settings.isArenaAutoLoginRoomList() && !roomListConnection.isConnected())
			connectToRoomListServer();
		if (settings.isArenaAutoLoginLobby() && !lobbyConnection.isConnected())
			connectToLobbyServer();
		if (shell.getMinimized())
			shell.setMinimized(false);
		shell.open();
	}

	public void hide() {
		shell.setVisible(false);
	}

	public Shell getShell() {
		return shell;
	}

	public void cronJob() {
		if (lobbySession != LobbySessionState.LOGIN || lobbyUserState != LobbyUserState.LOGIN)
			return;
		long deadline = System.currentTimeMillis() - lobbyInactivityInterval;
		if (deadline < lobbyLastActivity)
			return;

		changeLobbyStateTo(LobbyUserState.INACTIVE);
	}

	public void appendLog(final String message, final boolean timestamp) {
		if (Utility.isEmpty(message))
			return;

		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						appendLog(message, timestamp);
					}
				});
				return;
			}

			if (systemLogText.getCharCount() > 0)
				systemLogText.append("\n");

			if (timestamp) {
				Date now = new Date();
				systemLogText.append(SwtUtils.LOG_DATE_FORMAT.format(now));
				systemLogText.append(" - ");
			}

			systemLogText.append(message);
			systemLogText.setTopIndex(systemLogText.getLineCount());

			if (systemLogTab == lobbyCircleTabFolder.getItem(lobbyCircleTabFolder.getSelectionIndex()))
				return;
			Image icon = application.getImageRegistry().get(PlayClient.ICON_TAB_LOG_NOTIFY);
			systemLogTab.setImage(icon);
		} catch (SWTException e) {
		}
	}

	private static abstract class MultipleQueryStringFilter extends ViewerFilter {
		private String[] values;
		private boolean pass;

		private MultipleQueryStringFilter(boolean pass) {
			this.pass = pass;
		}

		public void setValues(String[] values) {
			if (values != null)
				for (int i = 0; i < values.length; i++) {
					values[i] = values[i].toLowerCase();
				}
			this.values = values;
		}

		protected boolean filter(String string) {
			if (values == null)
				return true;

			string = string.toLowerCase();
			for (String val : values) {
				if (string.contains(val))
					return pass;
			}
			return !pass;
		}
	}

	private static class RoomMasterFilter extends MultipleQueryStringFilter {
		private RoomMasterFilter(boolean pass) {
			super(pass);
		}

		@Override
		public boolean select(Viewer viewer, Object parent, Object element) {
			PlayRoom room = (PlayRoom) element;
			return filter(room.getMasterName());
		}
	}

	private static class RoomTitleFilter extends MultipleQueryStringFilter {
		private RoomTitleFilter(boolean pass) {
			super(pass);
		}

		@Override
		public boolean select(Viewer viewer, Object parent, Object element) {
			PlayRoom room = (PlayRoom) element;
			return filter(room.getTitle());
		}
	}

	private static class RoomPasswordFilter extends ViewerFilter {
		private boolean hasPassword = false;

		@Override
		public boolean select(Viewer viewer, Object parent, Object element) {
			PlayRoom room = (PlayRoom) element;
			if (hasPassword)
				return room.hasPassword();
			else
				return !room.hasPassword();
		}
	}

	private static class RoomVacantFilter extends ViewerFilter {
		private boolean onlyVacant = true;

		@Override
		public boolean select(Viewer viewer, Object parent, Object element) {
			PlayRoom room = (PlayRoom) element;
			if (onlyVacant)
				return room.getCurrentPlayers() < room.getMaxPlayers();
			else
				return true;
		}
	}

	private void setFilterValues(MultipleQueryStringFilter filter, Combo combo) {
		String text = combo.getText();
		if (Utility.isEmpty(text))
			return;

		text = Utility.validateNameString(text);
		combo.setText(text);
		filter.setValues(text.split(" "));
	}

	private void setFilterValues(MultipleQueryStringFilter filter, Text control) {
		String text = control.getText();
		if (Utility.isEmpty(text))
			return;

		text = Utility.validateNameString(text);
		control.setText(text);
		filter.setValues(text.split(" "));
	}

	private void applyRoomSearchFilter() {
		if (roomSearchFormFilter.getSelection()) {
			setFilterValues(roomMasterFilter, roomSearchFormMasterNameCombo);
			setFilterValues(roomMasterNgFilter, roomSearchFormMasterNameNgCombo);
			setFilterValues(roomTitleFilter, roomSearchFormTitleCombo);
			setFilterValues(roomTitleNgFilter, roomSearchFormTitleNgCombo);
		} else {
			roomMasterFilter.setValues(null);
			roomMasterNgFilter.setValues(null);
			roomTitleFilter.setValues(null);
			roomTitleNgFilter.setValues(null);
		}

		roomListTableViewer.refresh();
		updateRoomSearchResult();
	}

	private void connectToRoomListServer() {
		InetSocketAddress address = application.getPortalServer();
		if (address == null) {
			appendLog("サーバーリストが設定されていません", true);
			return;
		}

		roomListServerLoginButton.setEnabled(false);

		connectToRoomListServer(address);
	}

	private void showRoomListServerSelectDialog() {
		String[] list = application.getServerRegistry().getPortalServers();

		if (list.length == 0) {
			ErrorLog log = new ErrorLog("サーバーリストが設定されていません");
			appendLog(log.getMessage(), true);

			roomListServerLoginButton.setEnabled(true);
		} else {
			SearchServerSelectDialog dialog = new SearchServerSelectDialog(shell, list);
			switch (dialog.open()) {
			case IDialogConstants.OK_ID:
				String selected = dialog.getSelectedServer();
				InetSocketAddress address = Utility.parseSocketAddress(selected);
				if (address == null) {
					appendLog("選択されたアドレスにエラーがあります", true);
				} else {
					connectToRoomListServer(address);
				}
				break;
			case IDialogConstants.CANCEL_ID:
				roomListServerLoginButton.setEnabled(true);
				break;
			}
		}
	}

	private void connectToRoomListServer(final InetSocketAddress address) {
		roomListSession = RoomListSessionState.CONNECTING;

		roomListServerAddressLabel.setText("サーバー: " + Utility.socketAddressToStringByHostName(address));

		roomListMap.clear();
		roomListTableViewer.refresh();
		roomListTableViewer.setSorter(null);
		roomListTableViewer.getTable().setSortDirection(SWT.NONE);

		Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					application.connectTcp(address, roomListProtocol);
				} catch (IOException e) {
					roomListSession = RoomListSessionState.OFFLINE;
					updateRoomListServerLoginButton(false);
					appendLog(e.getMessage(), true);
				}
			}
		};
		application.execute(task);
	}

	private void updateRoomListServerLoginButton(final boolean loginSuccess) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateRoomListServerLoginButton(loginSuccess);
					}
				});
				return;
			}

			// roomSearchServerLoginButton.setText(loginSuccess ? "検索ログイン中" :
			// "部屋検索開始");
			roomListServerLoginButton.setText(loginSuccess ? "リスト閲覧中" : "部屋リスト閲覧");
			roomListServerLoginButton.setSelection(loginSuccess);
			roomListServerLoginButton.setEnabled(true);
			roomSearchFormControlContainer.layout();

			roomSearchFormFilter.setEnabled(loginSuccess);
			roomSearchFormClear.setEnabled(loginSuccess);
		} catch (SWTException e) {
		}
	}

	private void updateRoomSearchResult() {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					public void run() {
						updateRoomSearchResult();
					}
				});
				return;
			}

			String message = "検索結果: " + roomListTableViewer.getTable().getItemCount() + "件";
			roomSearchResultLabel.setText(message);
			roomSearchFormControlContainer.layout();

			roomTitleHistoryManager.addCurrentItem();
			roomTitleNgHistoryManager.addCurrentItem();
			roomMasterNameHistoryManager.addCurrentItem();
			roomMasterNameNgHistoryManager.addCurrentItem();

			roomListSession = RoomListSessionState.LOGIN;
		} catch (SWTException e) {
		}
	}

	private void addPlayRoom(final PlayRoom room) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						addPlayRoom(room);
					}
				});
				return;
			}

			String source = room.getSourceServer();
			Set<PlayRoom> serverRooms = roomListServerMap.get(source);
			if (serverRooms == null) {
				serverRooms = new HashSet<PlayRoom>();
				roomListServerMap.put(source, serverRooms);
			}
			serverRooms.add(room);

			roomListMap.put(room.getRoomAddress(), room);
			roomListTableViewer.add(room);
			roomListTableViewer.refresh(room);

			updateRoomSearchResult();
		} catch (SWTException e) {
		}
	}

	private void updatePlayRoom(final PlayRoom room) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updatePlayRoom(room);
					}
				});
				return;
			}

			roomListTableViewer.remove(room);
			roomListTableViewer.add(room);
			roomListTableViewer.refresh(room);
		} catch (SWTException e) {
		}
	}

	private void removePlayRoom(final PlayRoom room) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						removePlayRoom(room);
					}
				});
				return;
			}

			String source = room.getSourceServer();
			Set<PlayRoom> serverRooms = roomListServerMap.get(source);
			if (serverRooms == null) {
				return;
			}
			serverRooms.remove(room);

			roomListMap.remove(room.getRoomAddress());
			roomListTableViewer.remove(room);
			roomListTableViewer.refresh(room);

			updateRoomSearchResult();
		} catch (SWTException e) {
		}
	}

	private void removeRoomServersAllRooms(final Set<PlayRoom> rooms) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						removeRoomServersAllRooms(rooms);
					}
				});
				return;
			}

			for (PlayRoom r : rooms) {
				roomListMap.remove(r.getRoomAddress());
				roomListTableViewer.remove(r);
				roomListTableViewer.refresh(r);
			}
			rooms.clear();

			updateRoomSearchResult();
		} catch (SWTException e) {
		}
	}

	private class RoomListProtocol implements IProtocol {
		@Override
		public void log(String message) {
			appendLog(message, true);
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_ROOM_LIST;
		}

		private void updateLoginButton() {
			try {
				if (SwtUtils.isNotUIThread()) {
					SwtUtils.DISPLAY.asyncExec(new Runnable() {
						@Override
						public void run() {
							updateLoginButton();
						}
					});
					return;
				}

				updateRoomListServerLoginButton(true);
				appendLog("部屋リストの閲覧を開始しました", true);
			} catch (SWTException e) {
			}
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			roomListConnection = connection;

			updateLoginButton();
			roomListSession = RoomListSessionState.LOGIN;

			RoomListProtocolDriver driver = new RoomListProtocolDriver(connection);
			return driver;
		}
	}

	private class RoomListProtocolDriver extends TextProtocolDriver {

		public RoomListProtocolDriver(ISocketConnection connection) {
			super(connection, roomListHandlers);
		}

		@Override
		public void log(String message) {
			appendLog(message, true);
		}

		@Override
		public void errorProtocolNumber(String number) {
			String error = String.format("サーバーとのプロトコルナンバーが一致しないので接続できません サーバー:%s クライアント:%s", number, IProtocol.NUMBER);
			appendLog(error, true);
		}

		@Override
		public void connectionDisconnected() {
			try {
				if (SwtUtils.isNotUIThread()) {
					SwtUtils.DISPLAY.asyncExec(new Runnable() {
						@Override
						public void run() {
							connectionDisconnected();
						}
					});
					return;
				}

				switch (roomListSession) {
				case CONNECTING:
					appendLog("部屋リストにアクセスできません", true);
					break;
				case LOGIN:
					appendLog("部屋リストの閲覧を終了しました", true);
					break;
				}

				roomListConnection = ISocketConnection.NULL;
				roomListSession = RoomListSessionState.OFFLINE;

				roomListMap.clear();
				roomListTableViewer.refresh();

				roomListServerAddressLabel.setText("サーバー: ");
				roomListServerStatusLabel.setText("ログインしていません");
				roomSearchResultLabel.setText("検索結果: なし");
				roomSearchFormControlContainer.layout();

				updateRoomListServerLoginButton(false);
			} catch (SWTException e) {
			}
		}
	}

	private HashMap<String, IProtocolMessageHandler> roomListHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		roomListHandlers.put(ProtocolConstants.RoomList.NOTIFY_ROOM_SERVER_REMOVED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String server) {
				Set<PlayRoom> rooms = roomListServerMap.get(server);
				if (rooms == null || rooms.isEmpty())
					return true;

				removeRoomServersAllRooms(rooms);
				return true;
			}
		});
		roomListHandlers.put(ProtocolConstants.RoomList.NOTIFY_ROOM_CREATED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// R hostname:port hostname:port masterName title currentPlayers
				// maxPlayers hasPassword createdTime description
				final String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 9)
					return true;

				String source = tokens[0];
				String server = tokens[1];
				if (Utility.isEmpty(server))
					server = source;
				String masterName = tokens[2];
				String title = tokens[3];

				int currentPlayers = Integer.parseInt(tokens[4]);
				int maxPlayers = Integer.parseInt(tokens[5]);
				boolean hasPassword = "Y".equals(tokens[6]);
				long created = Long.parseLong(tokens[7]);
				String description = Utility.multiLineToSingleLine(tokens[8]);

				PlayRoom room = new PlayRoom(source, server, masterName, title, hasPassword, currentPlayers, maxPlayers, created);
				room.setDescription(description);

				addPlayRoom(room);
				return true;
			}
		});
		roomListHandlers.put(ProtocolConstants.RoomList.NOTIFY_ROOM_UPDATED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// U hostname:port:master title maxPlayers hasPassword
				// description
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 5)
					return true;

				try {
					String address = tokens[0];
					String title = tokens[1];
					int maxPlayers = Integer.parseInt(tokens[2]);
					boolean hasPassword = "Y".equals(tokens[3]);
					String description = Utility.multiLineToSingleLine(tokens[4]);

					PlayRoom room = roomListMap.get(address);
					if (room == null)
						return true;

					room.setTitle(title);
					room.setMaxPlayers(maxPlayers);
					room.setHasPassword(hasPassword);
					room.setDescription(description);

					updatePlayRoom(room);

				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		roomListHandlers.put(ProtocolConstants.RoomList.NOTIFY_ROOM_DELETED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// NRD hostname:port:master
				String address = argument;

				PlayRoom room = roomListMap.remove(address);
				if (room == null)
					return true;

				removePlayRoom(room);
				return true;
			}
		});
		roomListHandlers.put(ProtocolConstants.RoomList.NOTIFY_ROOM_PLAYER_COUNT_CHANGED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// NRPC hostname:port:master playerCount
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return true;

				String address = tokens[0];

				PlayRoom room = roomListMap.get(address);
				if (room == null)
					return true;

				try {
					int playerCount = Integer.parseInt(tokens[1]);
					room.setCurrentPlayers(playerCount);

					updatePlayRoom(room);
				} catch (NumberFormatException e) {
				}
				return true;
			}
		});
		roomListHandlers.put(ProtocolConstants.SERVER_STATUS, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				try {
					int currentUsers = Integer.parseInt(argument);
					updateServerStatus(currentUsers);
				} catch (NumberFormatException e) {
				}

				return true;
			}

			private void updateServerStatus(final int currentUsers) {
				try {
					if (SwtUtils.isNotUIThread()) {
						SwtUtils.DISPLAY.asyncExec(new Runnable() {
							@Override
							public void run() {
								updateServerStatus(currentUsers);
							}
						});
						return;
					}

					String text = String.format("接続ユーザー数: %d", currentUsers);
					roomListServerStatusLabel.setText(text);
					roomSearchFormControlContainer.layout();
				} catch (SWTException e) {
				}
			}
		});
		roomListHandlers.put(ProtocolConstants.Search.NOTIFY_FROM_ADMIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				appendLog(argument, true);
				return true;
			}
		});
		roomListHandlers.put(ProtocolConstants.Search.ERROR_LOGIN_BEYOND_CAPACITY, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				appendLog("サーバーのログイン上限人数に達したのでログインできません", true);
				return true;
			}
		});
	}

	private HashMap<String, LobbyUser> getCircleMemberMap(String circle) {
		HashMap<String, LobbyUser> members = circleMap.get(circle);
		if (members == null) {
			members = new HashMap<String, LobbyUser>();
			circleMap.put(circle, members);
		}
		return members;
	}

	private class LobbyCircleTab {
		private String circleName;
		private Image icon;
		private Image iconNotify;

		private SashForm mainSash;
		private TabItem tabItem;
		private LogViewer chatLogViewer;
		private Text userSearchName;
		private Text userSearchProfile;
		private Combo userSearchStateCombo;
		private Button userSearchClear;
		private Button userSearchFilter;
		private TableViewer userListTableViewer;
		private Text chatText;
		private Button multilineChatButton;

		private MenuItem menuViewProfile;
		private MenuItem menuPrivateChat;

		private HashMap<String, LobbyUser> memberMap;

		private UserNameFilter nameFilter = new UserNameFilter();
		private UserProfileFilter profileFilter = new UserProfileFilter();
		private UserStateFilter stateFilter = new UserStateFilter();

		private LobbyCircleTab(final String tabText, HashMap<String, LobbyUser> memberMap, Image icon, Image iconNotify,
				boolean showChatInput) {
			this.circleName = tabText;
			this.memberMap = memberMap;
			this.icon = icon;
			this.iconNotify = iconNotify;

			IniAppData appData = application.getAppData();
			GridLayout gridLayout;

			tabItem = new TabItem(lobbyCircleTabFolder, SWT.DEFAULT);
			tabItem.setImage(icon);
			tabItem.setText(tabText);

			Composite contents = new Composite(lobbyCircleTabFolder, SWT.NONE);
			gridLayout = new GridLayout(1, false);
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginTop = 2;
			gridLayout.verticalSpacing = 2;
			contents.setLayout(gridLayout);

			mainSash = new SashForm(contents, SWT.HORIZONTAL);
			mainSash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			chatLogViewer = new LogViewer(mainSash, application.getSettings().getMaxLogCount(), application);

			Composite userListContainer = new Composite(mainSash, SWT.NONE);
			gridLayout = new GridLayout(4, false);
			gridLayout.marginWidth = 0;
			gridLayout.marginHeight = 0;
			gridLayout.marginTop = 2;
			gridLayout.marginLeft = 2;
			userListContainer.setLayout(gridLayout);

			Label userSearchNameLabel = new Label(userListContainer, SWT.NONE);
			userSearchNameLabel.setText("ユーザー名");
			userSearchNameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			userSearchName = new Text(userListContainer, SWT.SINGLE | SWT.BORDER);
			userSearchName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
			application.initControl(userSearchName);

			Label userSearchProfileLabel = new Label(userListContainer, SWT.NONE);
			userSearchProfileLabel.setText("プロフィール");
			userSearchProfileLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			userSearchProfile = new Text(userListContainer, SWT.SINGLE | SWT.BORDER);
			userSearchProfile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
			application.initControl(userSearchProfile);

			Label userSearchStateLabel = new Label(userListContainer, SWT.NONE);
			userSearchStateLabel.setText("状態");
			userSearchStateLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

			userSearchStateCombo = new Combo(userListContainer, SWT.BORDER | SWT.READ_ONLY);
			userSearchStateCombo.setItems(LOBBY_USER_STATES);
			userSearchStateCombo.add("指定なし", 0);
			userSearchStateCombo.select(0);
			userSearchStateCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			application.initControl(userSearchStateCombo);

			userSearchClear = new Button(userListContainer, SWT.PUSH);
			userSearchClear.setText("クリア");
			userSearchClear.setEnabled(false);
			userSearchClear.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

			userSearchFilter = new Button(userListContainer, SWT.TOGGLE);
			userSearchFilter.setText("絞り込む");
			userSearchFilter.setEnabled(false);
			userSearchFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

			userListTableViewer = new TableViewer(userListContainer, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
			Table userListTable = userListTableViewer.getTable();

			TableColumn userSearchTableNameColumn = new TableColumn(userListTable, SWT.LEFT);
			userSearchTableNameColumn.setText("名前");
			SwtUtils.installSorter(userListTableViewer, userSearchTableNameColumn, LobbyUserUtils.NAME_SORTER);

			TableColumn userSearchTableStateColumn = new TableColumn(userListTable, SWT.LEFT);
			userSearchTableStateColumn.setText("状態");
			SwtUtils.installSorter(userListTableViewer, userSearchTableStateColumn, LobbyUserUtils.STATE_SORTER);

			TableColumn userSearchTableProfileColumn = new TableColumn(userListTable, SWT.LEFT);
			userSearchTableProfileColumn.setText("プロフィール");

			userListTable.setHeaderVisible(true);
			userListTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1));
			application.initControl(userListTable);

			userListTableViewer.setLabelProvider(LobbyUserUtils.LABEL_PROVIDER);
			userListTableViewer.setContentProvider(LobbyUserUtils.CONTENT_PROVIDER);
			userListTableViewer.setInput(memberMap);
			SwtUtils.enableColumnDrag(userListTable);

			userListTableViewer.addFilter(nameFilter);
			userListTableViewer.addFilter(profileFilter);
			userListTableViewer.addFilter(stateFilter);

			ModifyListener modifyListener = new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					userSearchFilter.setSelection(false);
				}
			};
			userSearchName.addModifyListener(modifyListener);
			userSearchProfile.addModifyListener(modifyListener);
			userSearchStateCombo.addModifyListener(modifyListener);

			userSearchFilter.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					filterUserList();
				}
			});
			userSearchClear.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					userSearchName.setText("");
					userSearchProfile.setText("");
					userSearchStateCombo.select(0);
					userSearchFilter.setSelection(false);

					filterUserList();
				}
			});

			if (showChatInput) {
				Composite chatContainer = new Composite(contents, SWT.NONE);
				gridLayout = new GridLayout(2, false);
				gridLayout.marginHeight = 0;
				gridLayout.marginWidth = 1;
				gridLayout.marginBottom = 2;
				gridLayout.marginRight = 0;
				chatContainer.setLayout(gridLayout);
				chatContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

				chatText = new Text(chatContainer, SWT.BORDER | SWT.SINGLE);
				chatText.setTextLimit(300);
				chatText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
				application.initChatControl(chatText);

				multilineChatButton = new Button(chatContainer, SWT.PUSH);
				multilineChatButton.setText("複数行");
				application.initControl(multilineChatButton);

				chatText.addKeyListener(new KeyListener() {
					@Override
					public void keyReleased(KeyEvent e) {
					}

					@Override
					public void keyPressed(KeyEvent e) {
						switch (e.character) {
						case SWT.CR:
						case SWT.LF:
							e.doit = false;
							if (sendChat(chatText.getText(), LobbyCircleTab.this)) {
								chatText.setText("");
							}
						}
					}
				});
				multilineChatButton.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						switch (lobbySession) {
						case LOGIN:
							MultiLineChatDialog dialog = new MultiLineChatDialog(shell, application);
							switch (dialog.open()) {
							case IDialogConstants.OK_ID:
								String message = dialog.getMessage();
								sendChat(message, LobbyCircleTab.this);
							}
						}
					}
				});
			}

			tabItem.setControl(contents);

			appData.restoreLobbyUserTable(userListTable);
			mainSash.setWeights(appData.getArenaLobbySashFormWeights());

			Menu lobbyUserMenu = new Menu(shell, SWT.POP_UP);
			menuViewProfile = new MenuItem(lobbyUserMenu, SWT.PUSH);
			menuViewProfile.setText("プロフィール表示");
			menuViewProfile.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					IStructuredSelection selection = (IStructuredSelection) userListTableViewer.getSelection();
					LobbyUser user = (LobbyUser) selection.getFirstElement();
					if (user == null)
						return;

					showProfileWindow(user);
				}
			});

			menuPrivateChat = new MenuItem(lobbyUserMenu, SWT.PUSH);
			menuPrivateChat.setText("プライベートメッセージ");
			menuPrivateChat.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					IStructuredSelection selection = (IStructuredSelection) userListTableViewer.getSelection();
					LobbyUser user = (LobbyUser) selection.getFirstElement();

					String myName = myself.getName();
					if (user == null || myName.equals(user.getName()))
						return;

					openPrivateMessageDialog(user);
				}
			});

			userListTable.setMenu(lobbyUserMenu);
			userListTable.addMenuDetectListener(new MenuDetectListener() {
				@Override
				public void menuDetected(MenuDetectEvent e) {
					IStructuredSelection selection = (IStructuredSelection) userListTableViewer.getSelection();
					LobbyUser user = (LobbyUser) selection.getFirstElement();

					menuViewProfile.setEnabled(user != null);
					menuPrivateChat.setEnabled(user != null && !myself.getName().equals(user.getName()));
				}
			});

			userListTableViewer.addDoubleClickListener(new IDoubleClickListener() {
				@Override
				public void doubleClick(DoubleClickEvent e) {
					StructuredSelection s = (StructuredSelection) e.getSelection();
					LobbyUser user = (LobbyUser) s.getFirstElement();
					if (user == null)
						return;

					showProfileWindow(user);
				}
			});
		}

		private void showProfileWindow(LobbyUser user) {
			if (profileWindow == null)
				profileWindow = new UserProfileWindow(application, shell, myself);
			profileWindow.switchProfile(user);
			profileWindow.setVisible(true);
		}

		private void addMember(LobbyUser member) {
			memberMap.put(member.getName(), member);
			userListTableViewer.add(member);
			userListTableViewer.refresh(member);

			InfoLog log = new InfoLog(member.getName() + " が参加しました");
			chatLogViewer.appendMessage(log);
		}

		private void removeMember(LobbyUser member) {
			if (memberMap.remove(member.getName()) == null)
				return;
			userListTableViewer.remove(member);
			userListTableViewer.refresh(member);

			InfoLog log = new InfoLog(member.getName() + " が退出しました");
			chatLogViewer.appendMessage(log);
		}

		private void filterUserList() {
			if (userSearchFilter.getSelection()) {
				nameFilter.setName(userSearchName.getText());
				setFilterValues(profileFilter, userSearchProfile);

				LobbyUserState state = null;
				switch (userSearchStateCombo.getSelectionIndex()) {
				case 1:
					state = LobbyUserState.LOGIN;
					break;
				case 2:
					state = LobbyUserState.AFK;
					break;
				case 3:
					state = LobbyUserState.PLAYING;
					break;
				case 4:
					state = LobbyUserState.INACTIVE;
					break;
				}
				stateFilter.setState(state);
			} else {
				nameFilter.setName(null);
				profileFilter.setValues(null);
				stateFilter.setState(null);
			}

			userListTableViewer.refresh();
		}
	}

	private static class UserNameFilter extends ViewerFilter {
		private String name;

		public void setName(String name) {
			this.name = name;
		}

		@Override
		public boolean select(Viewer viewer, Object parent, Object element) {
			if (Utility.isEmpty(name))
				return true;

			LobbyUser user = (LobbyUser) element;
			return user.getName().contains(name);
		}
	}

	private static class UserProfileFilter extends MultipleQueryStringFilter {
		private UserProfileFilter() {
			super(true);
		}

		@Override
		public boolean select(Viewer viewer, Object parent, Object element) {
			LobbyUser user = (LobbyUser) element;
			return filter(user.getProfile());
		}
	}

	private static class UserStateFilter extends ViewerFilter {
		private LobbyUserState state;

		public void setState(LobbyUserState state) {
			this.state = state;
		}

		@Override
		public boolean select(Viewer viewer, Object parent, Object element) {
			if (state == null)
				return true;

			LobbyUser user = (LobbyUser) element;
			return state.equals(user.getState());
		}
	}

	private interface LobbyCircleTabProcessor {
		public void process(LobbyCircleTab tab);
	}

	private void processTab(LobbyCircleTabProcessor processor) {
		processor.process(lobbyTab);
		processor.process(pmTab);
		for (LobbyCircleTab tab : myCircleTabs.values()) {
			processor.process(tab);
		}
	}

	private void connectToLobbyServer() {
		lobbyServerLoginButton.setEnabled(false);
		lobbyServerLoginButton.setSelection(true);

		PortalQuery query = new PortalQuery() {
			@Override
			public String getCommand() {
				return ProtocolConstants.Portal.COMMAND_FIND_LOBBY_SERVERS;
			}

			@Override
			public void failCallback(ErrorLog log) {
				lobbyTab.chatLogViewer.appendMessage(log);
				resetLobbyControllers();
			}

			@Override
			public void successCallback(String address) {
				connectToLobbyServer(address);
			}
		};
		application.queryPortalServer(query);
	}

	private void selectLobbyServer() {
		lobbyServerLoginButton.setEnabled(false);

		TextDialog dialog = new TextDialog(shell, "ロビーサーバーに接続します", "サーバーアドレスを入力してください", "接続する", 200, SWT.NONE);
		switch (dialog.open()) {
		case IDialogConstants.OK_ID:
			String address = dialog.getUserInput();
			connectToLobbyServer(address);
			break;
		case IDialogConstants.CANCEL_ID:
			lobbyServerLoginButton.setEnabled(true);
			break;
		}
	}

	private void connectToLobbyServer(final String address) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						connectToLobbyServer(address);
					}
				});
				return;
			}

			IniUserProfile profile = application.getUserProfile();
			myself.setName(profile.getUserName());
			myself.setUrl(profile.getUrl());
			myself.setIconUrl(profile.getIconUrl());
			myself.setProfile(profile.getProfile());

			lobbyServerLoginButton.setSelection(true);
			lobbyServerLoginButton.setEnabled(false);
			lobbyServerAddressLabel.setText("サーバー: " + address);

			try {
				InetSocketAddress socketAddress = Utility.parseSocketAddress(address);
				application.connectTcp(socketAddress, lobbyProtocol);
				lobbySession = LobbySessionState.CONNECTING;
			} catch (IOException e) {
				resetLobbyControllers();

				ErrorLog log = new ErrorLog(e);
				lobbyTab.chatLogViewer.appendMessage(log);
			}
		} catch (SWTException e) {
		}
	}

	private void resetLobbyControllers() {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						resetLobbyControllers();
					}
				});
				return;
			}

			lobbyServerLoginButton.setSelection(false);
			lobbyServerLoginButton.setEnabled(true);

			processTab(new LobbyCircleTabProcessor() {
				@Override
				public void process(LobbyCircleTab tab) {
					tab.userSearchFilter.setEnabled(false);
					tab.userSearchClear.setEnabled(false);
				}
			});
		} catch (SWTException e) {
		}
	}

	private void initializeLobbySession(final String[] userInfoList) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						initializeLobbySession(userInfoList);
					}
				});
				return;
			}
			allLobbyUsers.clear();
			lobbyTab.userListTableViewer.refresh();
			pmTab.userListTableViewer.refresh();

			setLobbyLoginUserName(myself.getName());

			lobbyServerLoginButton.setText("ログイン中");
			lobbyServerLoginButton.setEnabled(true);
			lobbyServerLoginButton.getParent().layout();

			myself.setState(LobbyUserState.LOGIN);
			allLobbyUsers.put(myself.getName(), myself);
			lobbyTab.userListTableViewer.add(myself);
			pmTab.userListTableViewer.add(myself);

			lobbyUserStateCombo.setEnabled(true);
			lobbyUserStateCombo.select(0);

			int index = lobbyCircleTabFolder.getSelectionIndex();
			switch (index) {
			case 0:
				lobbyTab.chatText.setFocus();
				break;
			case 1:
			case 2:
				break;
			default:
				TabItem item = lobbyCircleTabFolder.getItem(index);
				LobbyCircleTab tab = myCircleTabs.get(item.getText());
				tab.chatText.setFocus();
			}

			ServerLog log = new ServerLog(myself.getName() + " としてログインしました");
			lobbyTab.chatLogViewer.appendMessage(log);

			for (int i = 0; i < userInfoList.length - 1; i++) {
				String name = userInfoList[i];
				LobbyUserState state = LobbyUserState.findState(userInfoList[++i]);

				if (Utility.isEmpty(name) || state == null)
					continue;

				String url = userInfoList[++i];
				String iconUrl = userInfoList[++i];
				String profile = userInfoList[++i];
				String[] circleList = userInfoList[++i].split("\n");

				LobbyUser user = new LobbyUser(name, state);
				user.setUrl(url);
				user.setIconUrl(iconUrl);
				user.setProfile(profile);

				for (String circle : circleList) {
					if (Utility.isEmpty(circle))
						continue;

					user.addCircle(circle);

					HashMap<String, LobbyUser> members = getCircleMemberMap(circle);
					members.put(user.getName(), user);

					LobbyCircleTab tab = myCircleTabs.get(circle);
					if (tab != null) {
						tab.memberMap.put(user.getName(), user);
						tab.userListTableViewer.add(user);
					}
				}

				allLobbyUsers.put(name, user);
				lobbyTab.userListTableViewer.add(user);
				pmTab.userListTableViewer.add(user);
			}

			if (!myCircleTabs.isEmpty()) {
				StringBuilder sb = new StringBuilder();
				for (String circle : myCircleTabs.keySet()) {
					sb.append(ProtocolConstants.Lobby.COMMAND_CIRCLE_JOIN);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(circle);
					sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
				}
				sb.deleteCharAt(sb.length() - 1);

				lobbyConnection.send(sb.toString());
			}

			updateLobbyUserCount();
			processTab(new LobbyCircleTabProcessor() {
				@Override
				public void process(LobbyCircleTab tab) {
					tab.userListTableViewer.refresh();
					tab.userSearchFilter.setEnabled(true);
					tab.userSearchClear.setEnabled(true);
				}
			});
		} catch (SWTException e) {
		} catch (RuntimeException e) {
			appendLog(Utility.stackTraceToString(e), true);
		}
	}

	private void updateLobbyUserCount() {
		lobbyUserCountLabel.setText("ユーザー数: " + allLobbyUsers.size());
		lobbyUserCountLabel.getParent().layout();
	}

	private void setLobbyLoginUserName(final String name) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						setLobbyLoginUserName(name);
					}
				});
				return;
			}

			lobbyUserNameLabel.setText(name);
			lobbyUserNameLabel.getParent().layout();
			lobbyLastActivity = System.currentTimeMillis();
		} catch (SWTException e) {
		}
	}

	private void updateLobbyUser(final LobbyUser user, final boolean profileUpdated) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateLobbyUser(user, profileUpdated);
					}
				});
				return;
			}

			processTab(new LobbyCircleTabProcessor() {
				@Override
				public void process(LobbyCircleTab tab) {
					if (tab.memberMap.containsKey(user.getName())) {
						tab.userListTableViewer.remove(user);
						tab.userListTableViewer.add(user);
						tab.userListTableViewer.refresh(user);
					}
				}
			});

			if (profileUpdated && profileWindow != null)
				profileWindow.profileRefreshed(user);

		} catch (SWTException e) {
		}
	}

	private void addLobbyUser(final LobbyUser user) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						addLobbyUser(user);
					}
				});
				return;
			}

			String name = user.getName();
			allLobbyUsers.put(name, user);

			lobbyTab.userListTableViewer.add(user);
			lobbyTab.userListTableViewer.refresh(user);
			pmTab.userListTableViewer.add(user);
			pmTab.userListTableViewer.refresh(user);

			IniSettings settings = application.getSettings();
			if (!name.equals(lobbyUserNameLabel.getText()) && settings.isLogLobbyEnterExit()) {
				InfoLog log = new InfoLog(name + " がログインしました");
				lobbyTab.chatLogViewer.appendMessage(log);

				if (settings.isBallonNotifyLobby())
					application.balloonNotify(shell, log.getMessage());
			}

			updateLobbyUserCount();
		} catch (SWTException e) {
		}
	}

	private void removeLobbyUser(final String name) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						removeLobbyUser(name);
					}
				});
				return;
			}

			LobbyUser user = allLobbyUsers.remove(name);
			if (user == null)
				return;

			lobbyTab.userListTableViewer.remove(user);
			lobbyTab.userListTableViewer.refresh(user);
			pmTab.userListTableViewer.remove(user);
			pmTab.userListTableViewer.refresh(user);

			for (String circle : user.getCircles()) {
				LobbyCircleTab tab = myCircleTabs.get(circle);
				if (tab == null) {
					HashMap<String, LobbyUser> members = getCircleMemberMap(circle);
					members.remove(user.getName());
				} else {
					tab.removeMember(user);
				}
			}

			IniSettings settings = application.getSettings();
			if (settings.isLogLobbyEnterExit()) {
				InfoLog log = new InfoLog(name + " がログアウトしました");
				lobbyTab.chatLogViewer.appendMessage(log);

				if (settings.isBallonNotifyLobby() && settings.isLogLobbyEnterExit())
					application.balloonNotify(shell, log.getMessage());
			}

			updateLobbyUserCount();

			if (profileWindow != null)
				profileWindow.userLoggedOut(user);
		} catch (SWTException e) {
		}
	}

	private void addUserCircle(final LobbyUser user, final String circle) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						addUserCircle(user, circle);
					}
				});
				return;
			}

			user.addCircle(circle);

			LobbyCircleTab tab = myCircleTabs.get(circle);
			if (tab == null) {
				HashMap<String, LobbyUser> members = getCircleMemberMap(circle);
				members.put(user.getName(), user);
			} else {
				tab.addMember(user);
			}

			if (profileWindow != null)
				profileWindow.circlesRefreshed(user);
		} catch (SWTException e) {
		}
	}

	private void removeUserCircle(final LobbyUser user, final String circle) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						removeUserCircle(user, circle);
					}
				});
				return;
			}

			user.removeCircle(circle);

			LobbyCircleTab tab = myCircleTabs.get(circle);
			if (tab == null) {
				HashMap<String, LobbyUser> members = getCircleMemberMap(circle);
				members.remove(user.getName());
			} else {
				tab.removeMember(user);
			}

			if (profileWindow != null)
				profileWindow.circlesRefreshed(user);
		} catch (SWTException e) {
		}
	}

	private void openAddCircleDialog() {
		TextDialog dialog = new TextDialog(shell, "サークル追加", "サークル名を入力してください", "参加", 200);
		switch (dialog.open()) {
		case IDialogConstants.OK_ID:
			String circle = dialog.getUserInput();
			if (Utility.isValidNameString(circle)) {
				requestCircleJoin(circle);
			} else {
				lobbyCircleTabFolder.setSelection(0);

				ErrorLog log = new ErrorLog("サークル名規則に従っていません");
				lobbyTab.chatLogViewer.appendMessage(log);
			}
			break;
		}
	}

	void requestCircleJoin(String circle) {
		if (!Utility.isValidNameString(circle))
			return;

		LobbyCircleTab tab = myCircleTabs.get(circle);
		if (tab == null) {
			HashMap<String, LobbyUser> memberMap = getCircleMemberMap(circle);
			Image icon = application.getImageRegistry().get(PlayClient.ICON_TAB_CIRCLE);
			Image iconNotify = application.getImageRegistry().get(PlayClient.ICON_TAB_CIRCLE_NOTIFY);
			tab = new LobbyCircleTab(circle, memberMap, icon, iconNotify, true);
			myCircleTabs.put(circle, tab);

			if (lobbyConnection.isConnected()) {
				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Lobby.COMMAND_CIRCLE_JOIN);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(circle);

				lobbyConnection.send(sb.toString());

				tab.userSearchFilter.setEnabled(true);
				tab.userSearchClear.setEnabled(true);
			}
		}
		lobbyCircleTabFolder.setSelection(tab.tabItem);
	}

	void requestCircleLeave(String circle) {
		if (!Utility.isValidNameString(circle))
			return;

		LobbyCircleTab tab = myCircleTabs.remove(circle);
		if (tab == null)
			return;
		tab.tabItem.dispose();

		if (!lobbyConnection.isConnected())
			return;

		StringBuilder sb = new StringBuilder();
		sb.append(ProtocolConstants.Lobby.COMMAND_CIRCLE_LEAVE);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(circle);

		lobbyConnection.send(sb.toString());
	}

	private void notifyTabUpdate(final LobbyCircleTab tab) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						notifyTabUpdate(tab);
					}
				});
				return;
			}

			if (tab.tabItem == lobbyCircleTabFolder.getItem(lobbyCircleTabFolder.getSelectionIndex()))
				return;

			tab.tabItem.setImage(tab.iconNotify);
		} catch (SWTException e) {
		}
	}

	private boolean sendChat(String message, LobbyCircleTab tab) {
		if (!Utility.isEmpty(message)) {
			switch (lobbySession) {
			case LOGIN: {
				changeLobbyStateTo(LobbyUserState.LOGIN);

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Lobby.COMMAND_CHAT);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(tab == lobbyTab ? "" : tab.circleName);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(message);
				lobbyConnection.send(sb.toString());
				return true;
			}
			default:
				InfoLog log = new InfoLog("サーバーにログインしていません");
				lobbyTab.chatLogViewer.appendMessage(log);
			}
		}
		return false;
	}

	public void openPrivateMessageDialog(LobbyUser user) {
		TextDialog dialog = new TextDialog(shell, "プライベートメッセージの送信", user.getName() + " にメッセージを送信します", "送信", 250);
		switch (dialog.open()) {
		case IDialogConstants.OK_ID:
			String message = dialog.getUserInput();

			StringBuilder sb = new StringBuilder();
			sb.append(ProtocolConstants.Lobby.COMMAND_PRIVATE_CHAT);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(user.getName());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(message);

			lobbyConnection.send(sb.toString());

			PrivateChat chat = new PrivateChat(myself.getName(), user.getName(), message, true);
			pmTab.chatLogViewer.appendMessage(chat);
			break;
		}
	}

	public void changeLobbyStateTo(final LobbyUserState state) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						changeLobbyStateTo(state);
					}
				});
				return;
			}
			if (!lobbyConnection.isConnected() || lobbySession != LobbySessionState.LOGIN)
				return;

			lobbyUserState = state;

			int index;
			switch (state) {
			case LOGIN:
				index = 0;
				break;
			case AFK:
				index = 1;
				break;
			case PLAYING:
				index = 2;
				break;
			case INACTIVE:
				index = 3;
				break;
			default:
				return;
			}

			lobbyLastActivity = System.currentTimeMillis();

			if (lobbyUserStateCombo.getSelectionIndex() == index)
				return;
			lobbyUserStateCombo.select(index);
			sendLobbyState();
		} catch (SWTException e) {
		}
	}

	private void sendLobbyState() {
		switch (lobbyUserStateCombo.getSelectionIndex()) {
		case 0:
			lobbyUserState = LobbyUserState.LOGIN;
			break;
		case 1:
			lobbyUserState = LobbyUserState.AFK;
			break;
		case 2:
			lobbyUserState = LobbyUserState.PLAYING;
			break;
		case 3:
			lobbyUserState = LobbyUserState.INACTIVE;
			break;
		default:
			return;
		}

		lobbyConnection.send(ProtocolConstants.Lobby.COMMAND_CHANGE_STATE + TextProtocolDriver.ARGUMENT_SEPARATOR
				+ lobbyUserState.getAbbreviation());
	}

	private class LobbyProtocol implements IProtocol {
		@Override
		public void log(String message) {
			ErrorLog log = new ErrorLog(message);
			lobbyTab.chatLogViewer.appendMessage(log);
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_LOBBY;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			lobbyConnection = connection;

			StringBuilder sb = new StringBuilder();
			sb.append(ProtocolConstants.Lobby.COMMAND_LOGIN);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(myself.getName());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(myself.getUrl());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(myself.getIconUrl());
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(myself.getProfile());

			connection.send(sb.toString());

			return new LobbyProtocolDriver(connection);
		}
	}

	private class LobbyProtocolDriver extends TextProtocolDriver {
		public LobbyProtocolDriver(ISocketConnection connection) {
			super(connection, lobbyHandlers);
		}

		@Override
		public void log(String message) {
			ErrorLog log = new ErrorLog(message);
			lobbyTab.chatLogViewer.appendMessage(log);
		}

		@Override
		public void errorProtocolNumber(String number) {
			String error = String.format("サーバーとのプロトコルナンバーが一致しないので接続できません サーバー:%s クライアント:%s", number, IProtocol.NUMBER);
			ErrorLog log = new ErrorLog(error);
			lobbyTab.chatLogViewer.appendMessage(log);
		}

		@Override
		public void connectionDisconnected() {
			try {
				if (SwtUtils.isNotUIThread()) {
					SwtUtils.DISPLAY.asyncExec(new Runnable() {
						@Override
						public void run() {
							connectionDisconnected();
						}
					});
					return;
				}
				lobbyConnection = ISocketConnection.NULL;

				lobbySession = LobbySessionState.OFFLINE;
				lobbyUserState = LobbyUserState.OFFLINE;

				lobbyServerLoginButton.setText("ロビーログイン");
				setLobbyLoginUserName("");
				resetLobbyControllers();

				lobbyUserStateCombo.setEnabled(false);
				lobbyUserStateCombo.deselect(0);
				lobbyServerAddressLabel.setText("サーバー: ");
				lobbyUserCountLabel.setText("ユーザー数: ");
				lobbyServerAddressLabel.getParent().layout();

				allLobbyUsers.clear();
				lobbyTab.userListTableViewer.refresh();
				pmTab.userListTableViewer.refresh();

				ServerLog log = new ServerLog("ロビーサーバーからログアウトしました");
				lobbyTab.chatLogViewer.appendMessage(log);

				for (LobbyCircleTab tab : myCircleTabs.values()) {
					tab.chatLogViewer.appendMessage(log);
					tab.memberMap.clear();
					tab.userListTableViewer.refresh();
				}
				circleMap.clear();

				if (application.getSettings().isBallonNotifyLobby() && !isActive)
					application.balloonNotify(shell, log.getMessage());

				if (profileWindow != null)
					profileWindow.switchProfile(null);
			} catch (SWTException e) {
			}
		}
	}

	private HashMap<String, IProtocolMessageHandler> lobbyHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_LOGIN, new IProtocolMessageHandler() {
			private String[] EMPTY = new String[] {};

			@Override
			public boolean process(IProtocolDriver driver, String args) {
				lobbySession = LobbySessionState.LOGIN;
				lobbyUserState = LobbyUserState.LOGIN;

				String[] userInfoList;
				if (args.length() == 0)
					userInfoList = EMPTY;
				else {
					userInfoList = args.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
					if (userInfoList.length % 6 != 0)
						return false;
				}
				initializeLobbySession(userInfoList);

				lobbyLastActivity = System.currentTimeMillis();
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_CHAT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, 3);
				if (tokens.length != 3)
					return true;

				String name = tokens[0];
				String circle = tokens[1];
				String message = tokens[2];

				LobbyCircleTab tab;
				if (Utility.isEmpty(circle)) {
					tab = lobbyTab;
				} else {
					tab = myCircleTabs.get(circle);
					if (tab == null)
						return true;
				}

				boolean isMine = name.equals(myself.getName());
				for (String line : message.replace("\r", "").split("\n", -1)) {
					Chat chat = new Chat(name, line, isMine);
					tab.chatLogViewer.appendMessage(chat);
					name = "";
				}

				if (!isMine)
					notifyTabUpdate(tab);

				name = tokens[0];
				Chat chat = new Chat(name, message, isMine);

				if (!isMine && !isActive && application.getSettings().isBallonNotifyLobby())
					application.balloonNotify(shell, "<" + name + "> " + message);

				application.lobbyMessageReceived(chat);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_PRIVATE_CHAT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, 2);
				if (tokens.length != 2)
					return true;

				String sender = tokens[0];
				String message = tokens[1];

				String myName = myself.getName();
				boolean isMine = sender.equals(myName);
				for (String line : message.replace("\r", "").split("\n", -1)) {
					PrivateChat chat = new PrivateChat(sender, myName, line, false);
					pmTab.chatLogViewer.appendMessage(chat);
				}

				notifyTabUpdate(pmTab);

				PrivateChat chat = new PrivateChat(sender, myName, message, isMine);

				if (!isActive)
					application.balloonNotify(shell, "(" + sender + " → " + myName + ") " + message);

				application.lobbyMessageReceived(chat);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_FROM_ADMIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String message) {
				for (String line : message.replace("\r", "").split("\n", -1)) {
					AdminNotify log = new AdminNotify(line);
					lobbyTab.chatLogViewer.appendMessage(log);
				}

				if (!isActive)
					application.balloonNotify(shell, message);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_STATE_CHANGE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return true;

				String name = tokens[0];
				final LobbyUser user = allLobbyUsers.get(name);
				if (user == null) {
					return true;
				}
				LobbyUserState state = LobbyUserState.findState(tokens[1]);
				if (state == null)
					return true;

				user.setState(state);
				updateLobbyUser(user, false);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_PROFILE_UPDATE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 4)
					return true;

				String name = tokens[0];
				LobbyUser user = allLobbyUsers.get(name);
				if (user == null)
					return true;

				String url = tokens[1];
				String iconUrl = tokens[2];
				String profile = tokens[3];

				user.setUrl(url);
				user.setIconUrl(iconUrl);
				user.setProfile(profile);

				updateLobbyUser(user, true);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_LOGIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 4)
					return true;

				String name = tokens[0];
				String url = tokens[1];
				String iconUrl = tokens[2];
				String profile = tokens[3];

				LobbyUser user = new LobbyUser(name, LobbyUserState.LOGIN);
				user.setUrl(url);
				user.setIconUrl(iconUrl);
				user.setProfile(profile);

				addLobbyUser(user);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_LOGOUT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String name) {
				removeLobbyUser(name);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_CIRCLE_JOIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return true;

				String name = tokens[0];
				String circle = tokens[1];

				LobbyUser user = allLobbyUsers.get(name);
				if (user == null)
					return true;

				addUserCircle(user, circle);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_CIRCLE_LEAVE, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return true;

				String name = tokens[0];
				String circle = tokens[1];

				LobbyUser user = allLobbyUsers.get(name);
				if (user == null)
					return true;

				removeUserCircle(user, circle);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.ERROR_LOGIN_USER_BEYOND_CAPACITY, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				ErrorLog log = new ErrorLog("ロビーが満室なので入れません");
				lobbyTab.chatLogViewer.appendMessage(log);
				return false;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.ERROR_LOGIN_USER_DUPLICATED_NAME, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				ErrorLog log = new ErrorLog("同名のユーザーが既にログインしているのでログインできません");
				lobbyTab.chatLogViewer.appendMessage(log);
				return false;
			}
		});
	}
}
