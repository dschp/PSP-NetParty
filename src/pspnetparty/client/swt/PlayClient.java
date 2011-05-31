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
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;

import pspnetparty.client.swt.config.AppearancePage;
import pspnetparty.client.swt.config.BasicSettingPage;
import pspnetparty.client.swt.config.IPreferenceNodeProvider;
import pspnetparty.client.swt.config.IniAppData;
import pspnetparty.client.swt.config.IniAppearance;
import pspnetparty.client.swt.config.IniSettings;
import pspnetparty.client.swt.message.ErrorLog;
import pspnetparty.client.swt.plugin.BouyomiChanPlugin;
import pspnetparty.client.swt.plugin.IPlugin;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.constants.IniPublicServer;
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

public class PlayClient implements IPlayClient {
	public static final String ICON_APP16 = "app.icon16";
	public static final String ICON_APP32 = "app.icon32";
	public static final String ICON_APP48 = "app.icon48";
	public static final String ICON_APP96 = "app.icon96";

	public static final String ICON_TOOLBAR_CONFIG = "toolbar.config";
	public static final String ICON_TOOLBAR_LOBBY = "toolbar.lobby";
	public static final String ICON_TOOLBAR_SEARCH = "toolbar.search";
	public static final String ICON_TOOLBAR_LOG = "toolbar.log";
	public static final String ICON_TOOLBAR_WIKI = "toolbar.wiki";

	private static final String INI_SETTING_FILE_NAME = "PlayClient.ini";
	private static final String INI_APPDATA_FILE_NAME = "PlayClient.appdata";

	private IniFile iniSettingFile;
	private IniFile iniAppDataFile;

	private IniSettings iniSettings;
	private IniAppearance iniAppearance;
	private IniAppData iniAppData;

	private TrayItem trayItem;
	private ToolTip toolTip;
	private Clipboard clipboard;
	private TextTransfer[] textTransfers = new TextTransfer[] { TextTransfer.getInstance() };
	private Image[] shellImages;
	private ImageRegistry imageRegistry;

	private ArrayList<Control> controls = new ArrayList<Control>();
	private ArrayList<Label> labels = new ArrayList<Label>();
	private ArrayList<Button> buttons = new ArrayList<Button>();
	private ArrayList<StyledText> logControls = new ArrayList<StyledText>();
	private ArrayList<Text> chatControls = new ArrayList<Text>();

	private AsyncTcpClient tcpClient;
	private AsyncUdpClient udpClient;

	private ExecutorService executorService = Executors.newCachedThreadPool();

	private Iterator<String> portalServerList;

	private RoomWindow roomWindow;
	private SearchWindow searchWindow;
	private LobbyWindow lobbyWindow;
	private LogWindow logWindow;

	private ArrayList<IPlugin> pluginList = new ArrayList<IPlugin>();
	private ArrayList<IPreferenceNodeProvider> preferenceNodeProviders = new ArrayList<IPreferenceNodeProvider>();

	public PlayClient() throws IOException {
		iniSettingFile = new IniFile(INI_SETTING_FILE_NAME);
		iniAppDataFile = new IniFile(INI_APPDATA_FILE_NAME);

		ILogger logger = new ILogger() {
			@Override
			public void log(String message) {
				if (logWindow != null)
					logWindow.appendLog(message, true, true);
				else
					System.out.println(message);
			}
		};
		tcpClient = new AsyncTcpClient(logger, 1000000, 0);
		udpClient = new AsyncUdpClient(logger);

		Display display = SwtUtils.DISPLAY;
		imageRegistry = new ImageRegistry(display);

		iniSettings = new IniSettings(iniSettingFile.getSection(IniSettings.SECTION));
		iniAppearance = new IniAppearance(iniSettingFile.getSection(IniAppearance.SECTION));
		iniAppData = new IniAppData(iniAppDataFile.getSection(null));

		clipboard = new Clipboard(display);

		try {
			ImageData iconData16 = new ImageData("icon/blue16.png");
			ImageData iconData32 = new ImageData("icon/blue32.png");
			ImageData iconData48 = new ImageData("icon/blue48.png");
			ImageData iconData96 = new ImageData("icon/blue96.png");
			Image icon16 = new Image(display, iconData16);
			Image icon32 = new Image(display, iconData32);
			Image icon48 = new Image(display, iconData48);
			Image icon96 = new Image(display, iconData96);
			imageRegistry.put(ICON_APP16, icon16);
			imageRegistry.put(ICON_APP32, icon32);
			imageRegistry.put(ICON_APP48, icon48);
			imageRegistry.put(ICON_APP96, icon96);

			shellImages = new Image[] { icon16, icon32, icon48, icon96 };

			ImageData toolConfig = new ImageData("icon/toolbar/config.png");
			ImageData toolLobby = new ImageData("icon/toolbar/lobby.png");
			ImageData toolSearch = new ImageData("icon/toolbar/search.png");
			ImageData toolLog = new ImageData("icon/toolbar/log.png");
			ImageData toolWiki = new ImageData("icon/toolbar/wiki.png");

			imageRegistry.put(ICON_TOOLBAR_CONFIG, new Image(SwtUtils.DISPLAY, toolConfig));
			imageRegistry.put(ICON_TOOLBAR_LOBBY, new Image(SwtUtils.DISPLAY, toolLobby));
			imageRegistry.put(ICON_TOOLBAR_SEARCH, new Image(SwtUtils.DISPLAY, toolSearch));
			imageRegistry.put(ICON_TOOLBAR_LOG, new Image(SwtUtils.DISPLAY, toolLog));
			imageRegistry.put(ICON_TOOLBAR_WIKI, new Image(SwtUtils.DISPLAY, toolWiki));
		} catch (SWTException e) {
		}

		Shell mainShell = new Shell(SwtUtils.DISPLAY);
		logWindow = new LogWindow(this, mainShell);

		try {
			Tray systemTray = display.getSystemTray();
			if (systemTray != null) {
				trayItem = new TrayItem(systemTray, SWT.NONE);
				trayItem.setImage(imageRegistry.get(ICON_APP16));
				trayItem.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						roomWindow.getShell().setActive();
					}
				});

				toolTip = new ToolTip(mainShell, SWT.BALLOON | SWT.ICON_INFORMATION);
				trayItem.setToolTip(toolTip);
			}
		} catch (SWTException e) {
		}

		try {
			IniPublicServer publicServer = new IniPublicServer();
			final ArrayList<String> list = new ArrayList<String>();
			for (String s : publicServer.getPortalServers()) {
				if (Utility.isEmpty(s))
					continue;
				list.add(s);
			}
			portalServerList = new Iterator<String>() {
				int index = 0;

				@Override
				public void remove() {
				}

				@Override
				public String next() {
					if (list.isEmpty())
						return null;
					if (index >= list.size())
						index = 0;
					String s = list.get(index);
					index++;
					return s;
				}

				@Override
				public boolean hasNext() {
					return !list.isEmpty();
				}
			};
		} catch (IOException e) {
			logWindow.appendLog(Utility.stackTraceToString(e), true, false);
		}

		String software = String.format("%s プレイクライアント バージョン: %s", AppConstants.APP_NAME, AppConstants.VERSION);
		logWindow.appendLog(software, false, false);
		logWindow.appendLog("プロトコル: " + IProtocol.NUMBER, false, false);

		WlanLibrary wlanLibrary = null;
		try {
			wlanLibrary = NativeWlanDevice.LIBRARY;
			logWindow.appendLog("pnpwlanライブラリを読み込みました", false, false);
		} catch (Throwable e1) {
			logWindow.appendLog("pnpwlanライブラリが見つかりません", false, false);

			try {
				wlanLibrary = JnetPcapWlanDevice.LIBRARY;
				logWindow.appendLog("jnetpcapライブラリを読み込みました", false, false);
			} catch (Throwable e2) {
				logWindow.appendLog("jnetpcapライブラリが見つかりません", false, false);

				wlanLibrary = new WlanProxyLibrary(this);
			}
		}

		roomWindow = new RoomWindow(this, mainShell, wlanLibrary);

		Thread cronThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (!SwtUtils.DISPLAY.isDisposed()) {
						roomWindow.cronJob();

						if (searchWindow != null)
							searchWindow.cronJob();
						if (lobbyWindow != null)
							lobbyWindow.cronJob();

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
	}

	@Override
	public Image[] getShellImages() {
		return shellImages;
	}

	@Override
	public IniAppData getAppData() {
		return iniAppData;
	}

	@Override
	public IniSettings getSettings() {
		return iniSettings;
	}

	@Override
	public IniAppearance getAppearance() {
		return iniAppearance;
	}

	@Override
	public IniSection getIniSection(String sectionName) {
		return iniSettingFile.getSection(sectionName);
	}

	@Override
	public ImageRegistry getImageRegistry() {
		return imageRegistry;
	}

	@Override
	public RoomWindow getRoomWindow() {
		return roomWindow;
	}

	@Override
	public SearchWindow getSearchWindow() {
		if (searchWindow == null) {
			searchWindow = new SearchWindow(this);
		}
		return searchWindow;
	}

	@Override
	public LobbyWindow getLobbyWindow(boolean create) {
		if (lobbyWindow == null && create)
			lobbyWindow = new LobbyWindow(this);
		return lobbyWindow;
	}

	@Override
	public LogWindow getLogWindow() {
		return logWindow;
	}

	@Override
	public void addConfigPageProvider(IPreferenceNodeProvider provider) {
		preferenceNodeProviders.add(provider);
	}

	@Override
	public void openConfigDialog() {
		PreferenceManager manager = new PreferenceManager();

		PreferenceNode setting = new PreferenceNode("setting", new BasicSettingPage(iniSettings));
		manager.addToRoot(setting);
		PreferenceNode appearance = new PreferenceNode("appearance", new AppearancePage(this));
		manager.addToRoot(appearance);

		for (IPreferenceNodeProvider p : preferenceNodeProviders)
			manager.addToRoot(p.createPreferenceNode());

		PreferenceDialog dialog = new PreferenceDialog(roomWindow.getShell(), manager) {
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
		switch (dialog.open()) {
		case IDialogConstants.OK_ID:
			try {
				iniSettingFile.saveToIni();
			} catch (IOException e) {
				logWindow.appendLog(Utility.stackTraceToString(e), true, true);
				e.printStackTrace();
			}
		}
	}

	@Override
	public void initControl(Control control) {
		control.setFont(iniAppearance.getFontGlobal());
		control.setBackground(iniAppearance.getColorBackground());
		control.setForeground(iniAppearance.getColorForeground());

		controls.add(control);
	}

	@Override
	public void initControl(Label label) {
		label.setFont(iniAppearance.getFontGlobal());

		labels.add(label);
	}

	@Override
	public void initControl(Button button) {
		button.setFont(iniAppearance.getFontGlobal());

		buttons.add(button);
	}

	@Override
	public void initLogControl(StyledText log) {
		log.setFont(iniAppearance.getFontLog());
		log.setBackground(iniAppearance.getColorLogBackground());
		log.setForeground(iniAppearance.getColorLogBackground());

		logControls.add(log);
	}

	@Override
	public void initChatControl(Text chat) {
		chat.setFont(iniAppearance.getFontChat());
		chat.setBackground(iniAppearance.getColorBackground());
		chat.setForeground(iniAppearance.getColorForeground());

		chatControls.add(chat);
	}

	@Override
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

	@Override
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

	@Override
	public void reflectAppearance() {
		roomWindow.reflectAppearance();
		if (searchWindow != null)
			searchWindow.reflectAppearance();
		if (lobbyWindow != null)
			lobbyWindow.reflectAppearance();
		logWindow.reflectApperance();
	}

	@Override
	public void putClipboard(String data) {
		clipboard.setContents(new Object[] { data }, textTransfers);
	}

	@Override
	public String getClipboardContents() {
		return (String) clipboard.getContents(TextTransfer.getInstance());
	}

	@Override
	public void setTaskTrayTooltipText(String title) {
		if (trayItem != null) {
			trayItem.setToolTipText(title);
		}
	}

	@Override
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

	@Override
	public void execute(Runnable task) {
		executorService.execute(task);
	}

	@Override
	public void connectTcp(InetSocketAddress address, IProtocol protocol) throws IOException {
		if (address == null)
			return;
		tcpClient.connect(address, ProtocolConstants.TIMEOUT, protocol);
	}

	@Override
	public void connectUdp(InetSocketAddress address, IProtocol protocol) throws IOException {
		if (address == null)
			return;
		udpClient.connect(address, ProtocolConstants.TIMEOUT, protocol);
	}

	@Override
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
							logWindow.appendLog(message, true, true);
						}

						@Override
						public String getProtocol() {
							return ProtocolConstants.PROTOCOL_PORTAL;
						}

						@Override
						public IProtocolDriver createDriver(final ISocketConnection connection) {
							connection.send(query.getCommand());

							return new IProtocolDriver() {
								private String message;

								@Override
								public void errorProtocolNumber(String number) {
									String error = String.format("サーバーとのプロトコルナンバーが一致しないので接続できません サーバー:%s クライアント:%s", number,
											IProtocol.NUMBER);
									logWindow.appendLog(error, true, true);
								}

								@Override
								public ISocketConnection getConnection() {
									return connection;
								}

								@Override
								public boolean process(PacketData data) {
									message = data.getMessage();
									return true;
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
					query.failCallback(new ErrorLog(e.getMessage()));
				}
			}
		};
		executorService.execute(task);
	}

	private void startEventLoop() {
		Shell shell = roomWindow.getShell();
		while (!shell.isDisposed()) {
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
