package pspnetparty.client.swt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
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
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;

import pspnetparty.client.swt.IApplication.PortalQuery;
import pspnetparty.client.swt.message.AdminNotify;
import pspnetparty.client.swt.message.Chat;
import pspnetparty.client.swt.message.ErrorLog;
import pspnetparty.client.swt.message.IMessageListener;
import pspnetparty.client.swt.message.IMessageSource;
import pspnetparty.client.swt.message.InfoLog;
import pspnetparty.client.swt.message.LogViewer;
import pspnetparty.client.swt.message.PrivateChat;
import pspnetparty.client.swt.message.ServerLog;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.engine.LobbyUserState;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.IProtocolMessageHandler;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.TextProtocolDriver;

public class LobbyWindow implements IMessageSource {
	enum SessionState {
		OFFLINE, CONNECTING, LOGIN,
	}

	private SessionState sessionState = SessionState.OFFLINE;
	private LobbyUserState userState = LobbyUserState.OFFLINE;

	private LobbyProtocol lobbyProtocol = new LobbyProtocol();
	private ISocketConnection lobbyConnection = ISocketConnection.NULL;
	private HashMap<String, LobbyUser> lobbyUserMap = new HashMap<String, LobbyUser>();

	private String loginUserName;
	private long lastLobbyActivity = 0L;

	private IApplication application;

	private Shell shell;
	private boolean isActive;

	private SashForm sashForm;
	private Button serverLoginButton;
	private Combo userStateCombo;
	private TableViewer userListTableViewer;
	private LogViewer chatLogViewer;
	private Label userNameLabel;
	private Text chatText;
	private Button multilineChatButton;
	private Label statusServerAddressLabel;
	private Label statusLobbyNameLabel;

	private MenuItem menuLoginNameChange;
	private MenuItem menuPrivateChat;

	private HashSet<IMessageListener> messageListeners = new HashSet<IMessageListener>();

	public LobbyWindow(IApplication application) {
		this.application = application;

		// Shell parentShell = application.getShell();
		shell = new Shell(SWT.SHELL_TRIM);// | SWT.TOOL);

		shell.setText("ロビー");
		try {
			shell.setImages(application.getShellImages());
		} catch (RuntimeException e) {
			e.printStackTrace();
		}

		GridData gridData;

		GridLayout gridLayout = new GridLayout(1, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = 2;
		shell.setLayout(gridLayout);

		sashForm = new SashForm(shell, SWT.HORIZONTAL);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		chatLogViewer = new LogViewer(sashForm, application.getSettings().getMaxLogCount(), application);

		Composite infoContainer = new Composite(sashForm, SWT.NONE);
		gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = 3;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = 0;
		infoContainer.setLayout(gridLayout);

		serverLoginButton = new Button(infoContainer, SWT.PUSH);
		serverLoginButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		application.initControl(serverLoginButton);

		userStateCombo = new Combo(infoContainer, SWT.BORDER | SWT.READ_ONLY);
		userStateCombo.setItems(new String[] { "参加中", "離席中", "プレイ中", "非アクティブ" });
		userStateCombo.setEnabled(false);
		userStateCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initControl(userStateCombo);

		userListTableViewer = new TableViewer(infoContainer, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
		Table userListTable = userListTableViewer.getTable();

		userListTable.setHeaderVisible(true);
		userListTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		application.initControl(userListTable);

		TableColumn columnUserNameColumn = new TableColumn(userListTable, SWT.LEFT);
		columnUserNameColumn.setText("名前");
		SwtUtils.installSorter(userListTableViewer, columnUserNameColumn, LobbyUser.NAME_SORTER);

		TableColumn columnUserState = new TableColumn(userListTable, SWT.LEFT);
		columnUserState.setText("状態");
		SwtUtils.installSorter(userListTableViewer, columnUserState, LobbyUser.STATE_SORTER);

		userListTableViewer.setLabelProvider(LobbyUser.LABEL_PROVIDER);
		userListTableViewer.setContentProvider(LobbyUser.CONTENT_PROVIDER);
		userListTableViewer.setInput(lobbyUserMap);
		SwtUtils.enableColumnDrag(userListTable);

		Composite chatContainer = new Composite(shell, SWT.NONE);
		gridLayout = new GridLayout(3, false);
		gridLayout.verticalSpacing = 0;
		gridLayout.horizontalSpacing = 3;
		gridLayout.marginHeight = 0;
		gridLayout.marginWidth = 0;
		gridLayout.marginTop = 0;
		gridLayout.marginBottom = 1;
		gridLayout.marginRight = 1;
		chatContainer.setLayout(gridLayout);
		chatContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		userNameLabel = new Label(chatContainer, SWT.NONE);
		userNameLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		application.initControl(userNameLabel);

		chatText = new Text(chatContainer, SWT.BORDER | SWT.SINGLE);
		chatText.setTextLimit(300);
		chatText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initChatControl(chatText);

		multilineChatButton = new Button(chatContainer, SWT.PUSH);
		multilineChatButton.setText("複数行");
		application.initControl(multilineChatButton);

		Composite statusBarContainer = new Composite(shell, SWT.NONE);
		statusBarContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(4, false);
		gridLayout.marginWidth = 3;
		gridLayout.marginHeight = 2;
		statusBarContainer.setLayout(gridLayout);

		statusServerAddressLabel = new Label(statusBarContainer, SWT.NONE);
		statusServerAddressLabel.setText("ロビーサーバー: ");

		gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		gridData.heightHint = 15;
		new Label(statusBarContainer, SWT.SEPARATOR | SWT.VERTICAL).setLayoutData(gridData);

		statusLobbyNameLabel = new Label(statusBarContainer, SWT.NONE);
		statusLobbyNameLabel.setText("ログインしていません");

		new Label(statusBarContainer, SWT.SEPARATOR | SWT.VERTICAL).setLayoutData(gridData);

		initWidgetListeners();

		updateLobbyServerLoginButton(false);

		IniAppData appData = application.getAppData();
		sashForm.setWeights(appData.getArenaLobbySashFormWeights());
		appData.restoreLobbyUserTableSetting(userListTable);
		appData.restoreLobbyWindow(shell);
	}

	private void initWidgetListeners() {
		serverLoginButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (lobbyConnection.isConnected()) {
					lobbyConnection.send(ProtocolConstants.Lobby.COMMAND_LOGOUT);
				} else {
					showLobbyServers();
				}
			}
		});
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
					if (sendChat(chatText.getText())) {
						chatText.setText("");
					}
				}
			}
		});
		multilineChatButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				switch (sessionState) {
				case LOGIN:
					MultiLineChatDialog dialog = new MultiLineChatDialog(shell, application);
					switch (dialog.open()) {
					case IDialogConstants.OK_ID:
						String message = dialog.getMessage();
						sendChat(message);
					}
				}
			}
		});
		userStateCombo.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				sendLobbyState();
				chatText.setFocus();
			}
		});

		shell.addShellListener(new ShellListener() {
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

			@Override
			public void shellClosed(ShellEvent e) {
				e.doit = false;
				shell.setVisible(false);
				isActive = false;
			}
		});
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				storeAppData();
			}
		});

		Menu lobbyUserMenu = new Menu(shell, SWT.POP_UP);
		menuPrivateChat = new MenuItem(lobbyUserMenu, SWT.PUSH);
		menuPrivateChat.setText("プライベートメッセージ");
		menuPrivateChat.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				IStructuredSelection selection = (IStructuredSelection) userListTableViewer.getSelection();
				LobbyUser user = (LobbyUser) selection.getFirstElement();

				String myName = userNameLabel.getText();
				if (user == null || myName.equals(user.getName()))
					return;

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

					PrivateChat chat = new PrivateChat(loginUserName, user.getName(), message, true);
					chatLogViewer.appendMessage(chat);
					break;
				}
			}
		});

		Table userListTable = userListTableViewer.getTable();
		userListTable.setMenu(lobbyUserMenu);
		userListTable.addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				IStructuredSelection selection = (IStructuredSelection) userListTableViewer.getSelection();
				LobbyUser user = (LobbyUser) selection.getFirstElement();

				String myName = userNameLabel.getText();
				if (user == null || myName.equals(user.getName()))
					menuPrivateChat.setEnabled(false);
				else
					menuPrivateChat.setEnabled(true);
			}
		});

		Menu lobbyLoginNameMenu = new Menu(shell, SWT.POP_UP);
		menuLoginNameChange = new MenuItem(lobbyLoginNameMenu, SWT.PUSH);
		menuLoginNameChange.setText("ユーザー名変更");
		menuLoginNameChange.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				LobbyChangeNameDialog dialog = new LobbyChangeNameDialog(shell, userNameLabel.getText());
				switch (dialog.open()) {
				case IDialogConstants.OK_ID:
					String newName = dialog.getNewName();

					StringBuilder sb = new StringBuilder();
					sb.append(ProtocolConstants.Lobby.COMMAND_CHANGE_NAME);
					sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
					sb.append(newName);

					lobbyConnection.send(sb.toString());
					break;
				case IDialogConstants.CANCEL_ID:
					break;
				}
			}
		});
		userNameLabel.setMenu(lobbyLoginNameMenu);
		userNameLabel.addMenuDetectListener(new MenuDetectListener() {
			@Override
			public void menuDetected(MenuDetectEvent e) {
				menuLoginNameChange.setEnabled(lobbyConnection.isConnected());
			}
		});
	}

	private void storeAppData() {
		IniAppData appData = application.getAppData();
		appData.storeLobbyWindow(shell.getBounds());
		appData.storeLobbyUserTableSetting(userListTableViewer.getTable());
		appData.setArenaLobbySashFormWeights(sashForm.getWeights());
	}

	public void reflectAppearance() {
		chatLogViewer.applyAppearance();
		shell.layout(true, true);
	}

	public void show() {
		shell.open();
	}

	public void hide() {
		shell.setVisible(false);
	}

	@Override
	public void addMessageListener(IMessageListener listener) {
		messageListeners.add(listener);
	}

	@Override
	public void removeMessageListener(IMessageListener listener) {
		messageListeners.remove(listener);
	}

	private void updateLobbyServerLoginButton(final boolean loginSuccess) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateLobbyServerLoginButton(loginSuccess);
					}
				});
				return;
			}

			if (loginSuccess) {
				serverLoginButton.setText("ログアウト");
			} else {
				serverLoginButton.setText("ログイン");
			}
			serverLoginButton.getParent().layout();
			serverLoginButton.setEnabled(true);
		} catch (SWTException e) {
		}
	}

	private void showLobbyServers() {
		loginUserName = application.getSettings().getUserName();

		PortalQuery query = new PortalQuery() {
			@Override
			public String getCommand() {
				return ProtocolConstants.Portal.COMMAND_LIST_LOBBY_SERVERS;
			}

			@Override
			public void failCallback(ErrorLog log) {
				chatLogViewer.appendMessage(log);
			}

			@Override
			public void successCallback(String message) {
				final ArrayList<LobbyServerInfo> list = new ArrayList<LobbyServerInfo>();
				for (String info : message.split("\n")) {
					try {
						String[] values = info.split("\t");
						String address = values[0];
						int currentUsers = Integer.parseInt(values[1]);
						String title = values[2];

						LobbyServerInfo server = new LobbyServerInfo(address, currentUsers, title);
						list.add(server);
					} catch (NumberFormatException e) {
					}
				}

				showLobbyServerSelectDialog(list);
			}
		};
		application.queryPortalServer(query);
	}

	private void showLobbyServerSelectDialog(final List<LobbyServerInfo> list) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						showLobbyServerSelectDialog(list);
					}
				});
				return;
			}

			if (list.isEmpty()) {
				ErrorLog log = new ErrorLog("ロビーサーバーが見つかりません");
				chatLogViewer.appendMessage(log);
			} else {
				LobbyServerSelectDialog dialog = new LobbyServerSelectDialog(shell, list);
				switch (dialog.open()) {
				case IDialogConstants.OK_ID:
					LobbyServerInfo selected = dialog.getSelectedServer();
					connectToLobbyServer(selected.getAddress());
					break;
				case IDialogConstants.CANCEL_ID:
					break;
				}
			}
		} catch (SWTException e) {
		}
	}

	private void connectToLobbyServer(String address) {
		loginUserName = application.getSettings().getUserName();

		serverLoginButton.setEnabled(false);
		statusServerAddressLabel.setText("ロビーサーバー: " + address);

		try {
			InetSocketAddress socketAddress = Utility.parseSocketAddress(address);
			application.connectTcp(socketAddress, lobbyProtocol);
		} catch (IOException e) {
			updateLobbyServerLoginButton(false);

			ErrorLog log = new ErrorLog(e.getLocalizedMessage());
			chatLogViewer.appendMessage(log);
		}
	}

	private boolean sendChat(String message) {
		if (!Utility.isEmpty(message)) {
			switch (sessionState) {
			case LOGIN:
				changeLobbyStateTo(LobbyUserState.LOGIN);
				lobbyConnection.send(ProtocolConstants.Lobby.COMMAND_CHAT + TextProtocolDriver.ARGUMENT_SEPARATOR + message);
				return true;
			default:
				InfoLog log = new InfoLog("サーバーにログインしていません");
				chatLogViewer.appendMessage(log);
			}
		}
		return false;
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
			if (!lobbyConnection.isConnected() || sessionState != SessionState.LOGIN)
				return;

			userState = state;

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

			lastLobbyActivity = System.currentTimeMillis();

			if (userStateCombo.getSelectionIndex() == index)
				return;
			userStateCombo.select(index);
			sendLobbyState();
		} catch (SWTException e) {
		}
	}

	private void sendLobbyState() {
		switch (userStateCombo.getSelectionIndex()) {
		case 0:
			userState = LobbyUserState.LOGIN;
			break;
		case 1:
			userState = LobbyUserState.AFK;
			break;
		case 2:
			userState = LobbyUserState.PLAYING;
			break;
		case 3:
			userState = LobbyUserState.INACTIVE;
			break;
		default:
			return;
		}

		lobbyConnection.send(ProtocolConstants.Lobby.COMMAND_CHANGE_STATE + TextProtocolDriver.ARGUMENT_SEPARATOR
				+ userState.getAbbreviation());
	}

	private void updateLobbyTitle(final String title) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateLobbyTitle(title);
					}
				});
				return;
			}

			if (Utility.isEmpty(title)) {
				statusLobbyNameLabel.setText("ログインしていません");
				shell.setText("ロビー");
			} else {
				statusLobbyNameLabel.setText("ロビー名: " + title);
				shell.setText("ロビー (" + title + ")");
			}

			statusLobbyNameLabel.getParent().layout();
		} catch (SWTException e) {
		}
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

			loginUserName = name;

			userNameLabel.setText(name);
			userNameLabel.getParent().layout();
			lastLobbyActivity = System.currentTimeMillis();
		} catch (SWTException e) {
		}
	}

	private void replaceLobbyUserList(final String[] userInfoList) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						replaceLobbyUserList(userInfoList);
					}
				});
				return;
			}
			TableViewer viewer = userListTableViewer;

			viewer.getTable().clearAll();
			lobbyUserMap.clear();
			for (int i = 0; i < userInfoList.length - 1; i++) {
				String name = userInfoList[i];
				LobbyUserState state = LobbyUserState.findState(userInfoList[++i]);

				if (Utility.isEmpty(name) || state == null)
					continue;

				LobbyUser user = new LobbyUser(name, state);

				lobbyUserMap.put(name, user);
				viewer.add(user);
			}
			viewer.refresh();
		} catch (SWTException e) {
		}
	}

	private void updateLobbyUser(final String name, final LobbyUserState state) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateLobbyUser(name, state);
					}
				});
				return;
			}

			LobbyUser user = lobbyUserMap.get(name);
			if (user == null) {
				user = new LobbyUser(name, state);
				lobbyUserMap.put(name, user);
				userListTableViewer.add(user);
				userListTableViewer.refresh(user);

				if (!name.equals(userNameLabel.getText())) {
					InfoLog log = new InfoLog(name + " がログインしました");
					chatLogViewer.appendMessage(log);

					IniSettings settings = application.getSettings();
					if (settings.isBallonNotifyLobby() && settings.isLogLobbyEnterExit())
						application.balloonNotify(shell, log.getMessage());
				}
			} else {
				user.setState(state);
				userListTableViewer.refresh(user);
			}
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

			LobbyUser user = lobbyUserMap.remove(name);
			if (user == null)
				return;

			userListTableViewer.remove(user);
			userListTableViewer.refresh();

			InfoLog log = new InfoLog(name + " がログアウトしました");
			chatLogViewer.appendMessage(log);

			IniSettings settings = application.getSettings();
			if (settings.isBallonNotifyLobby() && settings.isLogLobbyEnterExit())
				application.balloonNotify(shell, log.getMessage());
		} catch (SWTException e) {
		}
	}

	private void renameLobbyUser(final String oldName, final String newName) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						renameLobbyUser(oldName, newName);
					}
				});
				return;
			}

			LobbyUser user = lobbyUserMap.get(oldName);
			if (user == null)
				return;

			if (oldName.equals(userNameLabel.getText())) {
				setLobbyLoginUserName(newName);

				InfoLog log = new InfoLog(newName + " に名前を変更しました");
				chatLogViewer.appendMessage(log);
			} else {
				InfoLog log = new InfoLog(oldName + " が " + newName + " に名前を変更しました");
				chatLogViewer.appendMessage(log);
			}

			user.setName(newName);
			lobbyUserMap.remove(oldName);
			lobbyUserMap.put(newName, user);

			TableViewer viewer = userListTableViewer;

			viewer.remove(user);
			viewer.add(user);
			viewer.refresh();
		} catch (SWTException e) {
		}
	}

	private final long lobbyInactivityInterval = 30 * 60 * 1000;

	public void cronJob() {
		if (sessionState != SessionState.LOGIN || userState != LobbyUserState.LOGIN)
			return;
		long deadline = System.currentTimeMillis() - lobbyInactivityInterval;
		if (deadline < lastLobbyActivity)
			return;

		changeLobbyStateTo(LobbyUserState.INACTIVE);
	}

	private class LobbyProtocol implements IProtocol {
		@Override
		public void log(String message) {
			ErrorLog log = new ErrorLog(message);
			chatLogViewer.appendMessage(log);
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_LOBBY;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			lobbyConnection = connection;

			connection.send(ProtocolConstants.Lobby.COMMAND_LOGIN + TextProtocolDriver.ARGUMENT_SEPARATOR + loginUserName);

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
			chatLogViewer.appendMessage(log);
		}

		@Override
		public void errorProtocolNumber(String number) {
			String error = String.format("サーバーとのプロトコルナンバーが一致しないので接続できません サーバー:%s クライアント:%s", number, IProtocol.NUMBER);
			ErrorLog log = new ErrorLog(error);
			chatLogViewer.appendMessage(log);
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

				sessionState = SessionState.OFFLINE;
				userState = LobbyUserState.OFFLINE;

				updateLobbyServerLoginButton(false);
				setLobbyLoginUserName("");

				userStateCombo.setEnabled(false);
				userStateCombo.deselect(0);
				statusServerAddressLabel.setText("ロビーサーバー: ");
				updateLobbyTitle("");

				lobbyUserMap.clear();
				userListTableViewer.refresh();

				ServerLog log = new ServerLog("ロビーサーバーからログアウトしました");
				chatLogViewer.appendMessage(log);
				if (application.getSettings().isBallonNotifyLobby())
					application.balloonNotify(shell, log.getMessage());
			} catch (SWTException e) {
			}
		}
	}

	private HashMap<String, IProtocolMessageHandler> lobbyHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_LOGIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String name) {
				sessionState = SessionState.LOGIN;
				userState = LobbyUserState.LOGIN;

				login(name);
				lastLobbyActivity = System.currentTimeMillis();
				return true;
			}

			private void login(final String name) {
				try {
					if (SwtUtils.isNotUIThread()) {
						SwtUtils.DISPLAY.asyncExec(new Runnable() {
							@Override
							public void run() {
								login(name);
							}
						});
						return;
					}

					setLobbyLoginUserName(name);
					updateLobbyServerLoginButton(true);
					updateLobbyUser(name, LobbyUserState.LOGIN);
					userStateCombo.setEnabled(true);
					userStateCombo.select(0);
					chatText.setFocus();

					ServerLog log = new ServerLog(name + " としてログインしました");
					chatLogViewer.appendMessage(log);
				} catch (SWTException e) {
				}
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.COMMAND_CHAT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, 2);
				if (tokens.length != 2)
					return true;

				String name = tokens[0];
				String message = tokens[1];

				boolean isMine = name.equals(loginUserName);
				for (String line : message.replace("\r", "").split("\n", -1)) {
					Chat chat = new Chat(name, line, isMine);
					chatLogViewer.appendMessage(chat);
					name = "";
				}

				name = tokens[0];
				Chat chat = new Chat(name, message, isMine);

				if (!isMine && !isActive && application.getSettings().isBallonNotifyLobby())
					application.balloonNotify(shell, "<" + name + "> " + message);

				for (IMessageListener listener : messageListeners)
					listener.messageReceived(chat);
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

				boolean isMine = sender.equals(loginUserName);
				for (String line : message.replace("\r", "").split("\n", -1)) {
					PrivateChat chat = new PrivateChat(sender, loginUserName, line, false);
					chatLogViewer.appendMessage(chat);
				}

				PrivateChat chat = new PrivateChat(sender, loginUserName, message, isMine);

				if (!isActive)
					application.balloonNotify(shell, "(" + sender + " → " + loginUserName + ") " + message);

				for (IMessageListener listener : messageListeners)
					listener.messageReceived(chat);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_FROM_ADMIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String message) {
				for (String line : message.replace("\r", "").split("\n", -1)) {
					AdminNotify log = new AdminNotify(line);
					chatLogViewer.appendMessage(log);
				}

				if (!isActive)
					application.balloonNotify(shell, message);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_LOBBY_INFO, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String title) {
				updateLobbyTitle(title);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_USER_LIST, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String args) {
				String[] userInfoList = args.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				replaceLobbyUserList(userInfoList);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_USER_INFO, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return true;

				String name = tokens[0];
				LobbyUserState state = LobbyUserState.findState(tokens[1]);
				if (state == null)
					return true;

				updateLobbyUser(name, state);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_USER_LOGOUT, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String name) {
				removeLobbyUser(name);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.NOTIFY_USER_NAME_CHANGED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return true;

				String oldName = tokens[0];
				String newName = tokens[1];

				renameLobbyUser(oldName, newName);
				return true;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.ERROR_LOGIN_USER_BEYOND_CAPACITY, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				ErrorLog log = new ErrorLog("ロビーが満室なので入れません");
				chatLogViewer.appendMessage(log);
				return false;
			}
		});
		lobbyHandlers.put(ProtocolConstants.Lobby.ERROR_LOGIN_USER_DUPLICATED_NAME, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				switch (sessionState) {
				case CONNECTING: {
					ErrorLog log = new ErrorLog("同名のユーザーが既にログインしているのでログインできません");
					chatLogViewer.appendMessage(log);
					break;
				}
				case LOGIN: {
					ErrorLog log = new ErrorLog("同名のユーザーが既にログインしているので名前の変更はできません");
					chatLogViewer.appendMessage(log);
					break;
				}
				}
				return false;
			}
		});
	}
}
