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
import java.util.Date;
import java.util.HashMap;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import pspnetparty.client.swt.IApplication.PortalQuery;
import pspnetparty.client.swt.message.ErrorLog;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.engine.PlayRoom;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.IProtocolMessageHandler;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.TextProtocolDriver;

public class SearchWindow {
	enum SessionState {
		OFFLINE, CONNECTING, LOGIN, QUERYING,
	}

	private static final long SEARCH_QUERY_INTEVAL_MILLIS = 2500;
	private static final long SEARCH_FORM_MODIFY_DELAY_MILLIS = 1000;

	private SearchProtocol searchProtocol = new SearchProtocol();
	private ISocketConnection searchConnection = ISocketConnection.NULL;
	private ArrayList<PlayRoom> searchResultRooms = new ArrayList<PlayRoom>();

	private SessionState sessionState = SessionState.OFFLINE;
	private boolean isQueryUpdateOn = true;
	private long nextQueryTime = 0L;

	private IApplication application;

	private Shell shell;

	private Button searchServerLoginButton;
	private Combo searchFormTitleCombo;
	private Combo searchFormTitleNgCombo;
	private Combo searchFormMasterNameCombo;
	private Combo searchFormMasterNameNgCombo;
	private Button searchFormHasPassword;
	private Button searchFormOnlyVacant;
	private Button searchFormAutoQuery;
	private TableViewer searchResultTableViewer;
	private Label statusServerLabel;
	private Label statusServerStatusLabel;
	private Label statusSearchResultLabel;

	private ComboHistoryManager queryTitleHistoryManager;
	private ComboHistoryManager queryTitleNgHistoryManager;
	private ComboHistoryManager queryMasterNameHistoryManager;
	private ComboHistoryManager queryMasterNameNgHistoryManager;

	public SearchWindow(IApplication application) {
		this.application = application;

		shell = new Shell(SWT.SHELL_TRIM);// | SWT.TOOL);

		shell.setText("部屋検索");
		try {
			shell.setImages(application.getShellImages());
		} catch (RuntimeException e) {
			e.printStackTrace();
		}

		GridLayout gridLayout;

		gridLayout = new GridLayout(1, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = 3;
		shell.setLayout(gridLayout);

		Composite formContainer = new Composite(shell, SWT.NONE);
		gridLayout = new GridLayout(4, false);
		gridLayout.horizontalSpacing = 3;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 1;
		gridLayout.marginLeft = 1;
		formContainer.setLayout(gridLayout);
		formContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		searchServerLoginButton = new Button(formContainer, SWT.PUSH);
		searchServerLoginButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		application.initControl(searchServerLoginButton);

		Composite formOptionContainer = new Composite(formContainer, SWT.NONE);
		formOptionContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 3, 1));
		gridLayout = new GridLayout(4, false);
		gridLayout.horizontalSpacing = 6;
		gridLayout.verticalSpacing = 0;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginLeft = 4;
		formOptionContainer.setLayout(gridLayout);

		Label serverAddressLabel = new Label(formOptionContainer, SWT.NONE);
		serverAddressLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initControl(serverAddressLabel);

		searchFormHasPassword = new Button(formOptionContainer, SWT.CHECK | SWT.FLAT);
		searchFormHasPassword.setText("鍵付き");
		application.initControl(searchFormHasPassword);

		searchFormOnlyVacant = new Button(formOptionContainer, SWT.CHECK | SWT.FLAT);
		searchFormOnlyVacant.setText("満室非表示");
		searchFormOnlyVacant.setSelection(true);
		application.initControl(searchFormOnlyVacant);

		searchFormAutoQuery = new Button(formOptionContainer, SWT.TOGGLE);
		searchFormAutoQuery.setText("検索更新オン");
		searchFormAutoQuery.setSelection(true);
		searchFormAutoQuery.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		application.initControl(searchFormAutoQuery);

		Label formMasterNameLabel = new Label(formContainer, SWT.NONE);
		formMasterNameLabel.setText("部屋主");
		formMasterNameLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		application.initControl(formMasterNameLabel);

		searchFormMasterNameCombo = new Combo(formContainer, SWT.BORDER);
		searchFormMasterNameCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initControl(searchFormMasterNameCombo);

		Label formMasterNameNgLabel = new Label(formContainer, SWT.NONE);
		formMasterNameNgLabel.setText("除外");
		formMasterNameNgLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		application.initControl(formMasterNameNgLabel);

		searchFormMasterNameNgCombo = new Combo(formContainer, SWT.NONE);
		searchFormMasterNameNgCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initControl(searchFormMasterNameNgCombo);

		Label formTitleLabel = new Label(formContainer, SWT.NONE);
		formTitleLabel.setText("部屋名");
		formTitleLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		application.initControl(formTitleLabel);

		searchFormTitleCombo = new Combo(formContainer, SWT.BORDER);
		searchFormTitleCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initControl(searchFormTitleCombo);

		Label formTitleNgLabel = new Label(formContainer, SWT.NONE);
		formTitleNgLabel.setText("除外");
		formTitleNgLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		application.initControl(formTitleNgLabel);

		searchFormTitleNgCombo = new Combo(formContainer, SWT.NONE);
		searchFormTitleNgCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		application.initControl(searchFormTitleNgCombo);

		searchResultTableViewer = new TableViewer(shell, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
		Table searchResultTable = searchResultTableViewer.getTable();

		searchResultTable.setHeaderVisible(true);
		searchResultTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		application.initControl(searchResultTable);

		TableColumn columnMasterName = new TableColumn(searchResultTable, SWT.LEFT);
		columnMasterName.setText("部屋主");
		SwtUtils.installSorter(searchResultTableViewer, columnMasterName, PlayRoomUtils.MASTER_NAME_SORTER);

		TableColumn columnTitle = new TableColumn(searchResultTable, SWT.LEFT);
		columnTitle.setText("部屋名");
		SwtUtils.installSorter(searchResultTableViewer, columnTitle, PlayRoomUtils.TITLE_SORTER);

		TableColumn columnCapacity = new TableColumn(searchResultTable, SWT.CENTER);
		columnCapacity.setText("定員");
		SwtUtils.installSorter(searchResultTableViewer, columnCapacity, PlayRoomUtils.CAPACITY_SORTER);

		TableColumn columnHasPassword = new TableColumn(searchResultTable, SWT.CENTER);
		columnHasPassword.setText("鍵");
		SwtUtils.installSorter(searchResultTableViewer, columnHasPassword, PlayRoomUtils.HAS_PASSWORD_SORTER);

		TableColumn columnTimestamp = new TableColumn(searchResultTable, SWT.CENTER);
		columnTimestamp.setText("作成日時");
		SwtUtils.installSorter(searchResultTableViewer, columnTimestamp, PlayRoomUtils.TIMESTAMP_SORTER);

		TableColumn columnDescription = new TableColumn(searchResultTable, SWT.LEFT);
		columnDescription.setText("詳細・備考");

		TableColumn columnRoomServer = new TableColumn(searchResultTable, SWT.LEFT);
		columnRoomServer.setText("ルームサーバー");
		SwtUtils.installSorter(searchResultTableViewer, columnRoomServer, PlayRoomUtils.ADDRESS_SORTER);

		searchResultTableViewer.setContentProvider(new ArrayContentProvider());
		searchResultTableViewer.setLabelProvider(new PlayRoomUtils.LabelProvider());
		SwtUtils.enableColumnDrag(searchResultTable);

		Composite statusBarContainer = new Composite(shell, SWT.NONE);
		statusBarContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(5, false);
		gridLayout.marginWidth = 3;
		gridLayout.marginHeight = 2;
		statusBarContainer.setLayout(gridLayout);

		statusServerLabel = new Label(statusBarContainer, SWT.NONE);
		statusServerLabel.setText("検索サーバー: ");

		GridData gridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);
		gridData.heightHint = 15;
		new Label(statusBarContainer, SWT.SEPARATOR | SWT.VERTICAL).setLayoutData(gridData);

		statusServerStatusLabel = new Label(statusBarContainer, SWT.NONE);
		statusServerStatusLabel.setText("ログインしていません");

		new Label(statusBarContainer, SWT.SEPARATOR | SWT.VERTICAL).setLayoutData(gridData);

		statusSearchResultLabel = new Label(statusBarContainer, SWT.NONE);
		statusSearchResultLabel.setText("検索結果: なし");
		statusSearchResultLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));

		searchServerLoginButton.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (searchConnection.isConnected()) {
					searchConnection.send(ProtocolConstants.Search.COMMAND_LOGOUT);
				} else {
					connectToSearchServer();
				}
			}
		});

		searchFormAutoQuery.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				searchFormAutoQuery.setText(searchFormAutoQuery.getSelection() ? "検索更新オン" : "検索更新オフ");
			}
		});

		searchResultTableViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent e) {
				IStructuredSelection sel = (IStructuredSelection) e.getSelection();
				PlayRoom room = (PlayRoom) sel.getFirstElement();
				if (room == null)
					return;

				SearchWindow.this.application.getRoomWindow().enterRoom(room);
			}
		});

		ModifyListener formModifyListener = new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (System.currentTimeMillis() > nextQueryTime - SEARCH_FORM_MODIFY_DELAY_MILLIS) {
					nextQueryTime += SEARCH_FORM_MODIFY_DELAY_MILLIS;
				}
			}
		};
		searchFormMasterNameCombo.addModifyListener(formModifyListener);
		searchFormMasterNameNgCombo.addModifyListener(formModifyListener);
		searchFormTitleCombo.addModifyListener(formModifyListener);
		searchFormTitleNgCombo.addModifyListener(formModifyListener);

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
		searchFormMasterNameCombo.addSelectionListener(formSelectionListener);
		searchFormMasterNameNgCombo.addSelectionListener(formSelectionListener);
		searchFormTitleCombo.addSelectionListener(formSelectionListener);
		searchFormTitleNgCombo.addSelectionListener(formSelectionListener);

		searchFormTitleCombo.addVerifyListener(SwtUtils.NOT_ACCEPT_CONTROL_CHAR_LISTENER);
		searchFormMasterNameCombo.addVerifyListener(SwtUtils.NOT_ACCEPT_CONTROL_CHAR_LISTENER);

		shell.addShellListener(new ShellListener() {
			@Override
			public void shellClosed(ShellEvent e) {
				e.doit = false;
				shell.setVisible(false);
			}

			@Override
			public void shellIconified(ShellEvent e) {
				isQueryUpdateOn = false;
			}

			@Override
			public void shellDeiconified(ShellEvent e) {
				isQueryUpdateOn = true;
			}

			@Override
			public void shellDeactivated(ShellEvent e) {
			}

			@Override
			public void shellActivated(ShellEvent e) {
			}
		});
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				storeAppData();
			}
		});

		updateServerLoginButton(false);

		String[] stringList;

		IniAppData appData = application.getAppData();

		stringList = appData.getSearchHistoryRoomMaster();
		queryMasterNameHistoryManager = new ComboHistoryManager(searchFormMasterNameCombo, stringList, 20, false);
		stringList = appData.getSearchHistoryRoomMasterNG();
		queryMasterNameNgHistoryManager = new ComboHistoryManager(searchFormMasterNameNgCombo, stringList, 20, false);

		stringList = appData.getSearchHistoryTitle();
		queryTitleHistoryManager = new ComboHistoryManager(searchFormTitleCombo, stringList, 20, false);
		stringList = appData.getSearchHistoryTitleNG();
		queryTitleNgHistoryManager = new ComboHistoryManager(searchFormTitleNgCombo, stringList, 20, false);

		appData.restoreSearchResultTable(searchResultTable);
		appData.restoreSearchWindow(shell);
	}

	private void storeAppData() {
		IniAppData appData = application.getAppData();

		appData.storeSearchWindow(shell.getBounds());
		appData.storeSearchResultTable(searchResultTableViewer.getTable());

		appData.setSearchHistoryRoomMaster(queryMasterNameHistoryManager.makeCSV());
		appData.setSearchHistoryRoomMasterNG(queryMasterNameNgHistoryManager.makeCSV());
		appData.setSearchHistoryTitle(queryTitleHistoryManager.makeCSV());
		appData.setSearchHistoryTitleNG(queryTitleNgHistoryManager.makeCSV());
	}

	public void reflectAppearance() {
		shell.layout(true, true);
	}

	public void show() {
		if (!searchConnection.isConnected())
			connectToSearchServer();
		if (shell.getMinimized())
			shell.setMinimized(false);
		shell.open();
	}

	public void hide() {
		shell.setVisible(false);
	}

	private void updateServerLoginButton(final boolean loginSuccess) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						updateServerLoginButton(loginSuccess);
					}
				});
				return;
			}

			if (loginSuccess) {
				searchServerLoginButton.setText("ログアウト");
			} else {
				searchServerLoginButton.setText("検索ログイン");
			}
			searchServerLoginButton.setEnabled(true);
		} catch (SWTException e) {
		}
	}

	private void connectToSearchServer() {
		searchServerLoginButton.setEnabled(false);

		PortalQuery queryer = new PortalQuery() {
			@Override
			public String getCommand() {
				return ProtocolConstants.Portal.COMMAND_FIND_SEARCH_SERVER;
			}

			@Override
			public void failCallback(ErrorLog log) {
				updateServerLoginButton(false);
				application.getLogWindow().appendLogTo(log.getMessage(), true, true);
			}

			@Override
			public void successCallback(final String address) {
				try {
					if (SwtUtils.isNotUIThread()) {
						if (address == null) {
							updateServerLoginButton(false);
							application.getLogWindow().appendLogTo("アドレスを取得できませんでした", true, true);
							return;
						}
						SwtUtils.DISPLAY.asyncExec(new Runnable() {
							@Override
							public void run() {
								successCallback(address);
							}
						});
						return;
					}

					sessionState = SessionState.CONNECTING;

					statusServerLabel.setText("検索サーバー: " + address);

					searchResultRooms.clear();
					searchResultTableViewer.refresh();
					searchResultTableViewer.setSorter(null);
					searchResultTableViewer.getTable().setSortDirection(SWT.NONE);

					Runnable task = new Runnable() {
						@Override
						public void run() {
							try {
								InetSocketAddress socketAddress = Utility.parseSocketAddress(address);
								application.connectTcp(socketAddress, searchProtocol);
							} catch (IOException e) {
								sessionState = SessionState.OFFLINE;
								updateServerLoginButton(false);
								application.getLogWindow().appendLogTo(e.getMessage(), true, true);
							}
						}
					};
					application.execute(task);
				} catch (SWTException e) {
				}
			}
		};

		application.queryPortalServer(queryer);
	}

	public void cronJob() {
		long now = System.currentTimeMillis();

		switch (sessionState) {
		case LOGIN:
			if (isQueryUpdateOn && now > nextQueryTime) {
				SwtUtils.DISPLAY.asyncExec(sendQueryAction);
			}
			break;
		}
	}

	private Runnable sendQueryAction = new Runnable() {
		StringBuilder sb = new StringBuilder();

		@Override
		public void run() {
			if (!searchConnection.isConnected())
				return;
			if (!searchFormAutoQuery.getSelection())
				return;

			try {
				searchResultRooms.clear();
				sb.delete(0, sb.length());

				sb.append(ProtocolConstants.Search.COMMAND_SEARCH);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(searchFormMasterNameCombo.getText());
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(searchFormMasterNameNgCombo.getText());
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(searchFormTitleCombo.getText());
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(searchFormTitleNgCombo.getText());
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(searchFormHasPassword.getSelection() ? "Y" : "N");
				sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
				sb.append(searchFormOnlyVacant.getSelection() ? "Y" : "N");

				searchConnection.send(sb.toString());
				sessionState = SessionState.QUERYING;
			} catch (SWTException e) {
			}
		}
	};

	private class SearchProtocol implements IProtocol {
		@Override
		public void log(String message) {
			application.getLogWindow().appendLogTo(message, true, true);
		}

		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_SEARCH;
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

				updateServerLoginButton(true);
				application.getLogWindow().appendLogTo("検索サーバーにログインしました", true, false);
			} catch (SWTException e) {
			}
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			searchConnection = connection;

			connection.send(ProtocolConstants.Search.COMMAND_LOGIN);

			updateLoginButton();
			nextQueryTime = 0L;

			SearchProtocolDriver driver = new SearchProtocolDriver(connection);
			return driver;
		}
	}

	private class SearchProtocolDriver extends TextProtocolDriver {

		public SearchProtocolDriver(ISocketConnection connection) {
			super(connection, searchHandlers);
		}

		@Override
		public void log(String message) {
			application.getLogWindow().appendLogTo(message, true, true);
		}

		@Override
		public void errorProtocolNumber(String number) {
			String error = String.format("サーバーとのプロトコルナンバーが一致しないので接続できません サーバー:%s クライアント:%s", number, IProtocol.NUMBER);
			application.getLogWindow().appendLogTo(error, true, true);
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

				switch (sessionState) {
				case CONNECTING:
					application.getLogWindow().appendLogTo("検索サーバーに接続できません", true, true);
					break;
				case LOGIN:
					application.getLogWindow().appendLogTo("検索サーバーからログアウトしました", true, false);
					break;
				}

				searchConnection = ISocketConnection.NULL;
				sessionState = SessionState.OFFLINE;

				searchResultRooms.clear();
				searchResultTableViewer.refresh();

				statusServerLabel.setText("検索サーバー: ");
				statusServerStatusLabel.setText("ログインしていません");
				statusSearchResultLabel.setText("検索結果: なし");
				statusServerLabel.getParent().layout();

				updateServerLoginButton(false);
			} catch (SWTException e) {
			}
		}
	}

	private HashMap<String, IProtocolMessageHandler> searchHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		searchHandlers.put(ProtocolConstants.Search.COMMAND_LOGIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				sessionState = SessionState.LOGIN;
				return true;
			}
		});
		searchHandlers.put(ProtocolConstants.Search.COMMAND_SEARCH, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// S address master title currentPlayers maxPlayers hasPassword
				// created description
				if (Utility.isEmpty(argument)) {
					updateSearchResult();
					return true;
				}

				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 8)
					return true;

				try {
					String address = tokens[0];
					String masterName = tokens[1];
					String title = tokens[2];
					int currentPlayers = Integer.parseInt(tokens[3]);
					int maxPlayers = Integer.parseInt(tokens[4]);
					boolean hasPassword = "Y".equals(tokens[5]);
					long created = Long.parseLong(tokens[6]);
					String description = tokens[7].replace("\n", " ");

					PlayRoom room = new PlayRoom(address, masterName, title, hasPassword, currentPlayers, maxPlayers, created);
					room.setDescription(description);
					searchResultRooms.add(room);
				} catch (NumberFormatException e) {
				}
				return true;
			}

			Date now = new Date();

			private void updateSearchResult() {
				try {
					if (SwtUtils.isNotUIThread()) {
						SwtUtils.DISPLAY.asyncExec(new Runnable() {
							public void run() {
								updateSearchResult();
							}
						});
						return;
					}
					searchResultTableViewer.setInput(searchResultRooms);
					searchResultTableViewer.refresh();

					now.setTime(System.currentTimeMillis());

					String message = "検索結果: " + searchResultRooms.size() + "件 (" + IApplication.LOG_DATE_FORMAT.format(now) + ")";
					statusSearchResultLabel.setText(message);
					statusSearchResultLabel.getParent().layout();

					queryTitleHistoryManager.addCurrentItem();
					queryTitleNgHistoryManager.addCurrentItem();
					queryMasterNameHistoryManager.addCurrentItem();
					queryMasterNameNgHistoryManager.addCurrentItem();

					sessionState = SessionState.LOGIN;
					nextQueryTime = now.getTime() + SEARCH_QUERY_INTEVAL_MILLIS;
				} catch (SWTException e) {
				}
			}
		});
		searchHandlers.put(ProtocolConstants.SERVER_STATUS, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR);
				if (tokens.length != 2)
					return true;

				updateServerStatus(tokens);
				return true;
			}

			private void updateServerStatus(final String[] tokens) {
				try {
					if (SwtUtils.isNotUIThread()) {
						SwtUtils.DISPLAY.asyncExec(new Runnable() {
							@Override
							public void run() {
								updateServerStatus(tokens);
							}
						});
						return;
					}

					int currentUsers = Integer.parseInt(tokens[0]);
					int maxUsers = Integer.parseInt(tokens[1]);

					double ratio = ((double) currentUsers) / ((double) maxUsers) * 100;

					String text = String.format("サーバー利用率: %.1f%%  (%d / %d)", ratio, currentUsers, maxUsers);
					statusServerStatusLabel.setText(text);
					statusServerStatusLabel.getParent().layout();
				} catch (SWTException e) {
				}
			}
		});
		searchHandlers.put(ProtocolConstants.Search.NOTIFY_FROM_ADMIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				application.getLogWindow().appendLogTo(argument, true, true);
				return true;
			}
		});
		searchHandlers.put(ProtocolConstants.Search.ERROR_LOGIN_BEYOND_CAPACITY, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				application.getLogWindow().appendLogTo("サーバーのログイン上限人数に達したのでログインできません", true, true);
				return true;
			}
		});
	}
}
