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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

import pspnetparty.client.swt.config.AppearancePage;
import pspnetparty.client.swt.config.IPreferenceNodeProvider;
import pspnetparty.client.swt.config.IniAppData;
import pspnetparty.client.swt.config.IniAppearance;
import pspnetparty.client.swt.config.IniSettings;
import pspnetparty.client.swt.config.IniUserProfile;
import pspnetparty.client.swt.config.MiscSettingPage;
import pspnetparty.client.swt.config.UserProfilePage;
import pspnetparty.client.swt.message.ErrorLog;
import pspnetparty.client.swt.message.IMessage;
import pspnetparty.client.swt.message.IMessageListener;
import pspnetparty.client.swt.plugin.BouyomiChanPlugin;
import pspnetparty.client.swt.plugin.IPlugin;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IServerRegistry;
import pspnetparty.lib.constants.IniPublicServerRegistry;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.socket.AsyncTcpClient;
import pspnetparty.lib.socket.AsyncUdpClient;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.PacketData;
import pspnetparty.wlan.JnetPcapWlanDevice;
import pspnetparty.wlan.NativeWlanDevice;
import pspnetparty.wlan.WlanLibrary;

public class PlayClient {
	public enum FontType {
		GLOBAL, LOG, CHAT,
	}

	public enum ColorType {
		BACKGROUND, FOREGROUND, LOG_BACKGROUND,
	}

	interface PortalQuery {
		public String getCommand();

		public void successCallback(String message);

		public void failCallback(ErrorLog errorLog);
	}

	public static final String ICON_APP16 = "app.icon16";
	public static final String ICON_APP32 = "app.icon32";
	public static final String ICON_APP48 = "app.icon48";
	public static final String ICON_APP96 = "app.icon96";

	public static final String ICON_TOOLBAR_ROOM = "toolbar.room";
	public static final String ICON_TOOLBAR_ARENA = "toolbar.arena";
	public static final String ICON_TOOLBAR_LOG = "toolbar.log";
	public static final String ICON_TOOLBAR_CONFIG = "toolbar.config";
	public static final String ICON_TOOLBAR_WIKI = "toolbar.wiki";
	public static final String ICON_TOOLBAR_EXIT = "toolbar.exit";

	public static final String ICON_TAB_LOBBY = "tab.lobby";
	public static final String ICON_TAB_LOBBY_NOTIFY = "tab.lobby_notify";
	public static final String ICON_TAB_LOG = "tab.system";
	public static final String ICON_TAB_LOG_NOTIFY = "tab.system_notify";
	public static final String ICON_TAB_PM = "tab.pm";
	public static final String ICON_TAB_PM_NOTIFY = "tab.pm_notify";
	public static final String ICON_TAB_CIRCLE = "tab.circle";
	public static final String ICON_TAB_CIRCLE_NOTIFY = "tab.circle_notify";

	public static final String COLOR_OK = "color.ok";
	public static final String COLOR_NG = "color.ng";
	public static final String COLOR_APP_NUMBER = "color.appnumber";
	public static final String COLOR_TAB_NOTIFY = "color.tabnotify";

	private static final String INI_SETTING_FILE_NAME = "PlayClient.ini";
	private static final String INI_APPDATA_FILE_NAME = "PlayClient.appdata";

	private IniFile iniSettingFile;
	private IniFile iniAppDataFile;

	private IniSettings iniSettings;
	private IniAppearance iniAppearance;
	private IniUserProfile iniUserProfile;
	private IniAppData iniAppData;

	private TrayItem trayItem;
	private ToolTip toolTip;
	private Clipboard clipboard;
	private TextTransfer[] textTransfers = new TextTransfer[] { TextTransfer.getInstance() };
	private Image[] shellImages;
	private ImageRegistry imageRegistry;
	private ColorRegistry colorRegistry;

	private ArrayList<Control> controls = new ArrayList<Control>();
	private ArrayList<Label> labels = new ArrayList<Label>();
	private ArrayList<Button> buttons = new ArrayList<Button>();
	private ArrayList<StyledText> logControls = new ArrayList<StyledText>();
	private ArrayList<Text> chatControls = new ArrayList<Text>();

	private AsyncTcpClient tcpClient;
	private AsyncUdpClient udpClient;
	private WlanLibrary wlanLibrary;

	private ExecutorService executorService = Executors.newCachedThreadPool();

	private IServerRegistry serverRegistry;
	private Iterator<String> portalServerList;

	private boolean isRunning = true;
	private ArenaWindow arenaWindow;
	private RoomWindow roomWindow;

	private ArrayList<IPlugin> pluginList = new ArrayList<IPlugin>();
	private ArrayList<IPreferenceNodeProvider> preferenceNodeProviders = new ArrayList<IPreferenceNodeProvider>();

	private HashSet<IMessageListener> roomMessageListeners = new HashSet<IMessageListener>();
	private HashSet<IMessageListener> lobbyMessageListeners = new HashSet<IMessageListener>();

	private ArrayList<Label> toolbarSsidLibraryLabels = new ArrayList<Label>();

	public PlayClient() throws IOException {
		iniSettingFile = new IniFile(INI_SETTING_FILE_NAME);
		iniAppDataFile = new IniFile(INI_APPDATA_FILE_NAME);

		iniSettings = new IniSettings(iniSettingFile.getSection(IniSettings.SECTION), new WlanProxyLibrary(this));
		iniUserProfile = new IniUserProfile(iniSettingFile.getSection(IniUserProfile.SECTION));
		iniAppearance = new IniAppearance(iniSettingFile.getSection(IniAppearance.SECTION));
		iniAppData = new IniAppData(iniAppDataFile.getSection(null));

		ArrayList<String> pendingLogs = new ArrayList<String>();

		if (JnetPcapWlanDevice.LIBRARY.isReady()) {
			pendingLogs.add("PcapインストールOK");
		} else {
			pendingLogs.add("Pcapがインストールされていません");
		}
		if (NativeWlanDevice.LIBRARY.isReady()) {
			pendingLogs.add("Windowsワイヤレスネットワーク機能OK");
		} else {
			pendingLogs.add("Windowsワイヤレスネットワーク機能がインストールされていません");
		}

		wlanLibrary = iniSettings.getWlanLibrary();

		ILogger logger = new ILogger() {
			@Override
			public void log(String message) {
				getArenaWindow().appendToSystemLog(message, true);
			}
		};
		tcpClient = new AsyncTcpClient(logger, 1000000, 0);
		udpClient = new AsyncUdpClient(logger);

		imageRegistry = new ImageRegistry(SwtUtils.DISPLAY);
		colorRegistry = new ColorRegistry(SwtUtils.DISPLAY);

		clipboard = new Clipboard(SwtUtils.DISPLAY);

		try {
			Image icon16 = new Image(SwtUtils.DISPLAY, new ImageData("icon/blue16.png"));
			Image icon32 = new Image(SwtUtils.DISPLAY, new ImageData("icon/blue32.png"));
			Image icon48 = new Image(SwtUtils.DISPLAY, new ImageData("icon/blue48.png"));
			Image icon96 = new Image(SwtUtils.DISPLAY, new ImageData("icon/blue96.png"));
			imageRegistry.put(ICON_APP16, icon16);
			imageRegistry.put(ICON_APP32, icon32);
			imageRegistry.put(ICON_APP48, icon48);
			imageRegistry.put(ICON_APP96, icon96);

			shellImages = new Image[] { icon16, icon32, icon48, icon96 };

			Image toolArena = new Image(SwtUtils.DISPLAY, new ImageData("icon/toolbar/search.png"));
			Image toolRoom = new Image(SwtUtils.DISPLAY, new ImageData("icon/toolbar/lobby4.png"));
			Image toolConfig = new Image(SwtUtils.DISPLAY, new ImageData("icon/toolbar/config.png"));
			Image toolLog = new Image(SwtUtils.DISPLAY, new ImageData("icon/toolbar/log.png"));
			Image toolWiki = new Image(SwtUtils.DISPLAY, new ImageData("icon/toolbar/wiki.png"));
			Image toolExit = new Image(SwtUtils.DISPLAY, new ImageData("icon/toolbar/lobby3.png"));

			imageRegistry.put(ICON_TOOLBAR_ARENA, toolArena);
			imageRegistry.put(ICON_TOOLBAR_ROOM, toolRoom);
			imageRegistry.put(ICON_TOOLBAR_LOG, toolLog);
			imageRegistry.put(ICON_TOOLBAR_WIKI, toolWiki);
			imageRegistry.put(ICON_TOOLBAR_CONFIG, toolConfig);
			imageRegistry.put(ICON_TOOLBAR_EXIT, toolExit);

			Image tabLobby = new Image(SwtUtils.DISPLAY, new ImageData("icon/tab/lobby.png"));
			Image tabLobbyNotify = new Image(SwtUtils.DISPLAY, new ImageData("icon/tab/lobby2.png"));
			Image tabSystem = new Image(SwtUtils.DISPLAY, new ImageData("icon/tab/log.png"));
			Image tabSystemNotify = new Image(SwtUtils.DISPLAY, new ImageData("icon/tab/log2.png"));
			Image tabPm = new Image(SwtUtils.DISPLAY, new ImageData("icon/tab/pm.png"));
			Image tabPmNotify = new Image(SwtUtils.DISPLAY, new ImageData("icon/tab/pm2.png"));
			Image tabCircle = new Image(SwtUtils.DISPLAY, new ImageData("icon/tab/circle.png"));
			Image tabCircleNotify = new Image(SwtUtils.DISPLAY, new ImageData("icon/tab/circle2.png"));

			imageRegistry.put(ICON_TAB_LOBBY, tabLobby);
			imageRegistry.put(ICON_TAB_LOBBY_NOTIFY, tabLobbyNotify);
			imageRegistry.put(ICON_TAB_LOG, tabSystem);
			imageRegistry.put(ICON_TAB_LOG_NOTIFY, tabSystemNotify);
			imageRegistry.put(ICON_TAB_PM, tabPm);
			imageRegistry.put(ICON_TAB_PM_NOTIFY, tabPmNotify);
			imageRegistry.put(ICON_TAB_CIRCLE, tabCircle);
			imageRegistry.put(ICON_TAB_CIRCLE_NOTIFY, tabCircleNotify);
		} catch (SWTException e) {
		}

		colorRegistry.put(COLOR_OK, new RGB(0, 140, 0));
		colorRegistry.put(COLOR_NG, new RGB(200, 0, 0));
		colorRegistry.put(COLOR_APP_NUMBER, new RGB(0, 0, 220));
		colorRegistry.put(COLOR_TAB_NOTIFY, new RGB(0, 0, 220));

		arenaWindow = new ArenaWindow(this);

		try {
			Tray systemTray = SwtUtils.DISPLAY.getSystemTray();
			if (systemTray != null) {
				trayItem = new TrayItem(systemTray, SWT.NONE);
				trayItem.setImage(imageRegistry.get(ICON_APP16));
				trayItem.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						arenaWindow.show();
						if (roomWindow != null)
							roomWindow.show();
					}
				});

				toolTip = new ToolTip(arenaWindow.getShell(), SWT.BALLOON | SWT.ICON_INFORMATION);
				trayItem.setToolTip(toolTip);
				trayItem.setToolTipText(AppConstants.APP_NAME);

				final Menu menu = new Menu(arenaWindow.getShell());

				MenuItem itemArena = new MenuItem(menu, SWT.PUSH);
				itemArena.setText("アリーナ");
				itemArena.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						getArenaWindow().show();
					}
				});

				MenuItem itemRoom = new MenuItem(menu, SWT.PUSH);
				itemRoom.setText("ルーム");
				itemRoom.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						getRoomWindow().show();
					}
				});

				MenuItem itemShutdown = new MenuItem(menu, SWT.PUSH);
				itemShutdown.setText("アプリを終了");
				itemShutdown.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						isRunning = false;
					}
				});

				trayItem.addMenuDetectListener(new MenuDetectListener() {
					@Override
					public void menuDetected(MenuDetectEvent e) {
						menu.setVisible(true);
					}
				});
			}
		} catch (SWTException e) {
		}

		try {
			serverRegistry = new IniPublicServerRegistry();
		} catch (IOException e) {
			serverRegistry = IServerRegistry.NULL;
			arenaWindow.appendToSystemLog(Utility.stackTraceToString(e), true);
		}
		portalServerList = serverRegistry.getPortalRotator();

		String software = String.format("%s プレイクライアント バージョン: %s", AppConstants.APP_NAME, AppConstants.VERSION);
		arenaWindow.appendToSystemLog(software, false);
		arenaWindow.appendToSystemLog("プロトコル: " + IProtocol.NUMBER, false);

		for (String log : pendingLogs) {
			arenaWindow.appendToSystemLog(log, false);
		}

		Thread cronThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!SwtUtils.DISPLAY.isDisposed()) {
						arenaWindow.cronJob();
						if (roomWindow != null)
							roomWindow.cronJob();

						Thread.sleep(1000);
					}
				} catch (InterruptedException e) {
				} catch (SWTException e) {
				}
			}
		}, "CronThread");
		cronThread.setDaemon(true);
		cronThread.start();

		try {
			IPlugin plugin = (IPlugin) Class.forName(BouyomiChanPlugin.class.getName()).newInstance();
			plugin.initPlugin(this);
			pluginList.add(plugin);
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (ClassNotFoundException e) {
		}

		arenaWindow.getShell().addShellListener(new ShellListener() {
			@Override
			public void shellActivated(ShellEvent e) {
			}

			@Override
			public void shellIconified(ShellEvent e) {
			}

			@Override
			public void shellDeiconified(ShellEvent e) {
			}

			@Override
			public void shellDeactivated(ShellEvent e) {
			}

			@Override
			public void shellClosed(ShellEvent e) {
				if (roomWindow == null || !roomWindow.getShell().getVisible()) {
					checkApplicationShutdown(arenaWindow.getShell(), e);
				} else {
					e.doit = false;
					arenaWindow.hide();
				}
			}
		});

		if (Utility.isEmpty(iniUserProfile.getUserName())) {
			TextDialog dialog = new TextDialog(null, AppConstants.APP_NAME + " - ユーザー名が設定されていません", "ユーザー名を入力してください", null, 300, SWT.NONE);
			switch (dialog.open()) {
			case IDialogConstants.OK_ID:
				iniUserProfile.setUserName(dialog.getUserInput());
				break;
			default:
				iniUserProfile.setUserName("未設定");
			}
		}

		if (iniSettings.isStartupWindowArena()) {
			arenaWindow.show();
		} else {
			getRoomWindow().show();
		}
	}

	private boolean openShutdownConfirmDialog(Shell shell) {
		ConfirmDialog dialog = new ConfirmDialog(shell, "PSP NetPartyを終了します", "PSP NetPartyを終了します。よろしいですか？");
		switch (dialog.open()) {
		case IDialogConstants.CANCEL_ID:
			return false;
		default:
			return true;
		}
	}

	private void checkApplicationShutdown(Shell shell, ShellEvent e) {
		if (!iniSettings.isNeedAppCloseConfirm()) {
			isRunning = false;
			return;
		}
		if (openShutdownConfirmDialog(shell)) {
			isRunning = false;
		} else {
			e.doit = false;
		}
	}

	public void createToolBar(final Composite parent, IAppWindow window) {
		GridLayout gridLayout;

		Composite container = new Composite(parent, SWT.NONE);
		gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 0;
		gridLayout.marginWidth = 3;
		gridLayout.marginHeight = 1;
		container.setLayout(gridLayout);

		ToolBar toolBar = new ToolBar(container, SWT.FLAT | SWT.RIGHT);
		toolBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));

		IAppWindow.Type type = window.getType();
		if (type != IAppWindow.Type.ARENA) {
			ToolItem arenaWindowItem = new ToolItem(toolBar, SWT.PUSH);
			arenaWindowItem.setText("アリーナ");
			arenaWindowItem.setToolTipText("部屋の検索やロビーのチャット");
			arenaWindowItem.setImage(imageRegistry.get(ICON_TOOLBAR_ARENA));
			arenaWindowItem.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					arenaWindow.show();
				}
			});
		}
		if (type != IAppWindow.Type.ROOM) {
			ToolItem roomWindowItem = new ToolItem(toolBar, SWT.PUSH);
			roomWindowItem.setText("ルーム");
			roomWindowItem.setToolTipText("ルーム内で通信プレイができます");
			roomWindowItem.setImage(imageRegistry.get(ICON_TOOLBAR_ROOM));
			roomWindowItem.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					getRoomWindow().show();
				}
			});
		}

		ToolItem configWindowItem = new ToolItem(toolBar, SWT.PUSH);
		configWindowItem.setText("設定");
		configWindowItem.setToolTipText("アプリケーションの設定をします");
		configWindowItem.setImage(imageRegistry.get(ICON_TOOLBAR_CONFIG));
		configWindowItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (openConfigDialog(parent.getShell(), null)) {
					if (arenaWindow != null)
						arenaWindow.settingChanged();
					if (roomWindow != null)
						roomWindow.settingChanged();
				}
			}
		});

		ToolItem wikiItem = new ToolItem(toolBar, SWT.PUSH);
		wikiItem.setText("Wiki");
		wikiItem.setToolTipText(AppConstants.APP_NAME + "のWikiページを表示します");
		wikiItem.setImage(imageRegistry.get(ICON_TOOLBAR_WIKI));
		wikiItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				Program.launch("http://wiki.team-monketsu.net/");
			}
		});

		ToolItem exitItem = new ToolItem(toolBar, SWT.PUSH);
		exitItem.setText("終了");
		exitItem.setToolTipText(AppConstants.APP_NAME + "を終了します");
		exitItem.setImage(imageRegistry.get(ICON_TOOLBAR_EXIT));
		exitItem.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (openShutdownConfirmDialog(parent.getShell())) {
					isRunning = false;
				}
			}
		});

		Composite appVersionContainer = new Composite(container, SWT.NONE);
		appVersionContainer.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
		RowLayout rowLayout = new RowLayout();
		rowLayout.center = true;
		rowLayout.marginTop = 0;
		rowLayout.marginBottom = 0;
		rowLayout.marginLeft = 0;
		rowLayout.marginRight = 0;
		appVersionContainer.setLayout(rowLayout);

		Label statusApplicationVersionLabel = new Label(appVersionContainer, SWT.NONE);
		statusApplicationVersionLabel.setText("バージョン:");
		initControl(statusApplicationVersionLabel);

		Label statusApplicationVersionNumber = new Label(appVersionContainer, SWT.NONE);
		statusApplicationVersionNumber.setText(AppConstants.VERSION);
		statusApplicationVersionNumber.setForeground(colorRegistry.get(COLOR_APP_NUMBER));
		initControl(statusApplicationVersionNumber);

		Label statusApplicationProtocolLabel = new Label(appVersionContainer, SWT.NONE);
		statusApplicationProtocolLabel.setText("プロトコル:");
		initControl(statusApplicationProtocolLabel);

		Label statusApplicationProtocolNumber = new Label(appVersionContainer, SWT.NONE);
		statusApplicationProtocolNumber.setText(IProtocol.NUMBER);
		statusApplicationProtocolNumber.setForeground(colorRegistry.get(COLOR_APP_NUMBER));
		initControl(statusApplicationProtocolNumber);

		Label statusApplicationSsidLabel = new Label(appVersionContainer, SWT.NONE);
		statusApplicationSsidLabel.setText("SSID機能:");
		initControl(statusApplicationSsidLabel);

		Label statusApplicationSsidLibrary = new Label(appVersionContainer, SWT.NONE);
		toolbarSsidLibraryLabels.add(statusApplicationSsidLibrary);
		initControl(statusApplicationSsidLibrary);

		updateWlanLibraryStatus();
	}

	private void updateWlanLibraryStatus() {
		if (!wlanLibrary.isReady()) {
			for (Label ssidStatus : toolbarSsidLibraryLabels) {
				ssidStatus.setText("エラー");
				ssidStatus.setForeground(colorRegistry.get(COLOR_NG));

				ssidStatus.getParent().getParent().layout();
			}
		} else if (wlanLibrary instanceof WlanProxyLibrary) {
			for (Label ssidStatus : toolbarSsidLibraryLabels) {
				ssidStatus.setText("プロキシ");
				ssidStatus.setForeground(colorRegistry.get(COLOR_NG));

				ssidStatus.getParent().getParent().layout();
			}
		} else if (wlanLibrary.isSSIDEnabled()) {
			for (Label ssidStatus : toolbarSsidLibraryLabels) {
				ssidStatus.setText("On");
				ssidStatus.setForeground(colorRegistry.get(COLOR_APP_NUMBER));

				ssidStatus.getParent().getParent().layout();
			}
		} else {
			for (Label ssidStatus : toolbarSsidLibraryLabels) {
				ssidStatus.setText("Off");
				ssidStatus.setForeground(colorRegistry.get(COLOR_OK));

				ssidStatus.getParent().getParent().layout();
			}
		}
	}

	public Image[] getShellImages() {
		return shellImages;
	}

	public IniAppData getAppData() {
		return iniAppData;
	}

	public IniSettings getSettings() {
		return iniSettings;
	}

	public IniAppearance getAppearance() {
		return iniAppearance;
	}

	public IniUserProfile getUserProfile() {
		return iniUserProfile;
	}

	public IniSection getIniSection(String sectionName) {
		return iniSettingFile.getSection(sectionName);
	}

	public ImageRegistry getImageRegistry() {
		return imageRegistry;
	}

	public ColorRegistry getColorRegistry() {
		return colorRegistry;
	}

	public RoomWindow getRoomWindow() {
		if (roomWindow == null) {
			roomWindow = new RoomWindow(this);
			roomWindow.getShell().addShellListener(new ShellListener() {
				@Override
				public void shellIconified(ShellEvent e) {
				}

				@Override
				public void shellDeiconified(ShellEvent e) {
				}

				@Override
				public void shellDeactivated(ShellEvent e) {
				}

				@Override
				public void shellActivated(ShellEvent e) {
				}

				@Override
				public void shellClosed(ShellEvent e) {
					if (arenaWindow == null || !arenaWindow.getShell().getVisible()) {
						switch (roomWindow.confirmRoomDelete(true)) {
						case 0:
							e.doit = false;
							return;
						case 1:
						case -1:
							checkApplicationShutdown(roomWindow.getShell(), e);
							return;
						}
					} else {
						e.doit = false;
						roomWindow.hide();
					}
				}
			});
		}
		return roomWindow;
	}

	public ArenaWindow getArenaWindow() {
		return arenaWindow;
	}

	public void addConfigPageProvider(IPreferenceNodeProvider provider) {
		preferenceNodeProviders.add(provider);
	}

	public boolean openConfigDialog(Shell parentShell, String pageId) {
		PreferenceManager manager = new PreferenceManager();

		PreferenceNode profile = new PreferenceNode(UserProfilePage.PAGE_ID, new UserProfilePage(iniUserProfile));
		manager.addToRoot(profile);
		PreferenceNode setting = new PreferenceNode(MiscSettingPage.PAGE_ID, new MiscSettingPage(iniSettings));
		manager.addToRoot(setting);
		PreferenceNode appearance = new PreferenceNode(AppearancePage.PAGE_ID, new AppearancePage(this));
		manager.addToRoot(appearance);

		for (IPreferenceNodeProvider p : preferenceNodeProviders)
			manager.addToRoot(p.createPreferenceNode());

		PreferenceDialog dialog = new PreferenceDialog(parentShell, manager) {
			@Override
			protected void configureShell(Shell newShell) {
				super.configureShell(newShell);
				newShell.setText("設定");
				newShell.setImage(imageRegistry.get(ICON_TOOLBAR_CONFIG));
			}

			@Override
			protected Composite createTitleArea(Composite parent) {
				Composite composite = super.createTitleArea(parent);
				FormLayout layout = (FormLayout) composite.getLayout();
				layout.marginTop = 4;
				return composite;
			}
		};

		if (pageId != null)
			dialog.setSelectedNode(pageId);

		switch (dialog.open()) {
		case IDialogConstants.OK_ID:
			try {
				iniSettingFile.saveToIni();

				wlanLibrary = iniSettings.getWlanLibrary();
				updateWlanLibraryStatus();
				return true;
			} catch (IOException e) {
				arenaWindow.appendToSystemLog(Utility.stackTraceToString(e), true);
				e.printStackTrace();
			}
		}
		return false;
	}

	public void initControl(Control control) {
		control.setFont(iniAppearance.getFontGlobal());
		control.setBackground(iniAppearance.getColorBackground());
		control.setForeground(iniAppearance.getColorForeground());

		controls.add(control);
	}

	public void initControl(Label label) {
		label.setFont(iniAppearance.getFontGlobal());

		labels.add(label);
	}

	public void initControl(Button button) {
		button.setFont(iniAppearance.getFontGlobal());

		buttons.add(button);
	}

	public void initLogControl(StyledText log) {
		log.setFont(iniAppearance.getFontLog());
		log.setBackground(iniAppearance.getColorLogBackground());
		log.setForeground(iniAppearance.getColorLogBackground());

		logControls.add(log);
	}

	public void initChatControl(Text chat) {
		chat.setFont(iniAppearance.getFontChat());
		chat.setBackground(iniAppearance.getColorBackground());
		chat.setForeground(iniAppearance.getColorForeground());

		chatControls.add(chat);
	}

	public void applyFont(FontType type, FontData data) {
		Font newFont = new Font(SwtUtils.DISPLAY, data);
		Font oldFont = null;

		switch (type) {
		case GLOBAL: {
			oldFont = iniAppearance.getFontGlobal();

			for (Control control : controls) {
				if (control.isDisposed())
					continue;

				control.setFont(newFont);
			}

			for (Label label : labels) {
				if (label.isDisposed())
					continue;

				label.setFont(newFont);
			}

			for (Button button : buttons) {
				if (button.isDisposed())
					continue;

				button.setFont(newFont);
			}

			iniAppearance.setFontGlobal(newFont);
			break;
		}
		case LOG: {
			oldFont = iniAppearance.getFontLog();

			for (StyledText control : logControls) {
				if (control.isDisposed())
					continue;

				control.setFont(newFont);
			}

			iniAppearance.setFontLog(newFont);
			break;
		}
		case CHAT: {
			oldFont = iniAppearance.getFontChat();

			for (Text control : chatControls) {
				if (control.isDisposed())
					continue;

				control.setFont(newFont);
			}

			iniAppearance.setFontChat(newFont);
			break;
		}
		}

		if (oldFont != null)
			oldFont.dispose();
	}

	public void applyColor(ColorType type, RGB rgb) {
		Color newColor = new Color(SwtUtils.DISPLAY, rgb);
		Color oldColor = null;

		switch (type) {
		case BACKGROUND: {
			oldColor = iniAppearance.getColorBackground();

			for (Control control : controls) {
				if (control.isDisposed())
					continue;

				control.setBackground(newColor);
			}
			for (Text chat : chatControls) {
				if (chat.isDisposed())
					continue;

				chat.setBackground(newColor);
			}

			iniAppearance.setColorBackground(newColor);
			break;
		}
		case FOREGROUND: {
			oldColor = iniAppearance.getColorForeground();

			for (Control control : controls) {
				if (control.isDisposed())
					continue;

				control.setForeground(newColor);
			}
			for (Text chat : chatControls) {
				if (chat.isDisposed())
					continue;

				chat.setForeground(newColor);
			}

			iniAppearance.setColorForeground(newColor);
			break;
		}
		case LOG_BACKGROUND: {
			oldColor = iniAppearance.getColorLogBackground();

			for (StyledText text : logControls) {
				if (text.isDisposed())
					continue;

				text.setBackground(newColor);
				text.setForeground(newColor);
			}

			iniAppearance.setColorLogBackground(newColor);
		}
		}

		if (oldColor != null)
			oldColor.dispose();
	}

	public void reflectAppearance() {
		arenaWindow.reflectAppearance();
		if (roomWindow != null)
			roomWindow.reflectAppearance();
	}

	public void putClipboard(String data) {
		clipboard.setContents(new Object[] { data }, textTransfers);
	}

	public String getClipboardContents() {
		return (String) clipboard.getContents(TextTransfer.getInstance());
	}

	public void setTaskTrayTooltipText(String title) {
		if (trayItem != null) {
			trayItem.setToolTipText(title);
		}
	}

	public void balloonNotify(final Shell shell, final String message) {
		if (toolTip == null)
			return;
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						balloonNotify(shell, message);
					}
				});
				return;
			}

			toolTip.setText(shell.getText());
			toolTip.setMessage(message);
			toolTip.setVisible(true);
		} catch (SWTException e) {
		}
	}

	public void roomMessageReceived(IMessage message) {
		for (IMessageListener listener : roomMessageListeners)
			listener.messageReceived(message);
	}

	public void addRoomMessageListener(IMessageListener listener) {
		roomMessageListeners.add(listener);
	}

	public void removeRoomMessageListener(IMessageListener listener) {
		roomMessageListeners.remove(listener);
	}

	public void lobbyMessageReceived(IMessage message) {
		for (IMessageListener listener : lobbyMessageListeners)
			listener.messageReceived(message);
	}

	public void addLobbyMessageListener(IMessageListener listener) {
		lobbyMessageListeners.add(listener);
	}

	public void removeLobbyMessageListener(IMessageListener listener) {
		lobbyMessageListeners.remove(listener);
	}

	public void execute(Runnable task) {
		executorService.execute(task);
	}

	public void connectTcp(InetSocketAddress address, IProtocol protocol) throws IOException {
		if (address == null)
			throw new IOException("アドレスエラー");
		tcpClient.connect(address, ProtocolConstants.TIMEOUT, protocol);
	}

	public void connectUdp(InetSocketAddress address, IProtocol protocol) throws IOException {
		if (address == null)
			throw new IOException("アドレスエラー");
		udpClient.connect(address, ProtocolConstants.TIMEOUT, protocol);
	}

	public IServerRegistry getServerRegistry() {
		return serverRegistry;
	}

	public InetSocketAddress getPortalServer() {
		String server;
		if (iniSettings.isPrivatePortalServerUse())
			server = iniSettings.getPrivatePortalServerAddress();
		else
			server = portalServerList.next();

		return Utility.parseSocketAddress(server);
	}

	public void queryPortalServer(final PortalQuery query) {
		String server;
		if (iniSettings.isPrivatePortalServerUse())
			server = iniSettings.getPrivatePortalServerAddress();
		else
			server = portalServerList.next();

		if (Utility.isEmpty(server)) {
			query.failCallback(new ErrorLog("ポータルサーバーが設定されていません"));
			return;
		}

		final InetSocketAddress address = Utility.parseSocketAddress(server);

		Runnable task = new Runnable() {
			@Override
			public void run() {
				try {
					tcpClient.connect(address, ProtocolConstants.TIMEOUT, new IProtocol() {
						@Override
						public void log(String message) {
							arenaWindow.appendToSystemLog(message, true);
						}

						@Override
						public String getProtocol() {
							return ProtocolConstants.PROTOCOL_PORTAL;
						}

						@Override
						public IProtocolDriver createDriver(final ISocketConnection connection) {
							connection.send(Utility.encode(query.getCommand()));

							return new IProtocolDriver() {
								private String message;

								@Override
								public void errorProtocolNumber(String number) {
									String error = String.format("サーバーとのプロトコルナンバーが一致しないので接続できません サーバー:%s クライアント:%s", number,
											IProtocol.NUMBER);
									arenaWindow.appendToSystemLog(error, true);
								}

								@Override
								public ISocketConnection getConnection() {
									return connection;
								}

								@Override
								public boolean process(PacketData data) {
									message = data.getMessage();
									return false;
								}

								@Override
								public void connectionDisconnected() {
									if (message != null)
										query.successCallback(message);
									else
										query.failCallback(new ErrorLog("利用可能なサーバーが見つかりません"));
								}
							};
						}
					});
				} catch (IOException e) {
					query.failCallback(new ErrorLog(e));
				}
			}
		};
		executorService.execute(task);
	}

	private void startEventLoop() {
		while (isRunning) {
			if (!SwtUtils.DISPLAY.readAndDispatch()) {
				SwtUtils.DISPLAY.sleep();
			}
		}
		try {
			SwtUtils.DISPLAY.dispose();
		} catch (RuntimeException e) {
		}

		tcpClient.dispose();
		udpClient.dispose();

		try {
			executorService.shutdownNow();
		} catch (RuntimeException ex) {
		}

		try {
			iniSettingFile.saveToIni();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		try {
			iniAppDataFile.saveToIni();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		try {
			new PlayClient().startEventLoop();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
