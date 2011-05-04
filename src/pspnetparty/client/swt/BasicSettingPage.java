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

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;

public class BasicSettingPage extends PreferencePage {

	private IniSettings settings;

	private Text userNameText;
	private Button appCloseConfirmCheck;
	private Button logLobbyEnterExitCheck;
	private Button balloonNotifyLobbyCheck;
	private Button balloonNotifyRoomCheck;
	private Button privatePortalServerUseCheck;
	private Text privatePortalServerAddress;
	private Button myRoomAllowEmptyMasterNameCheck;

	private Button tunnelTransportTcp;
	private Button tunnelTransportUdp;

	public BasicSettingPage(IniSettings settings) {
		super("基本設定");
		this.settings = settings;

		noDefaultAndApplyButton();
	}

	@Override
	protected Control createContents(Composite parent) {
		GridLayout gridLayout;

		Composite configContainer = new Composite(parent, SWT.NONE);
		gridLayout = new GridLayout(1, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 6;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = 2;
		configContainer.setLayout(gridLayout);

		Composite configUserNameContainer = new Composite(configContainer, SWT.NONE);
		configUserNameContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = 7;
		gridLayout.verticalSpacing = 0;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		configUserNameContainer.setLayout(gridLayout);

		Label configUserNameLabel = new Label(configUserNameContainer, SWT.NONE);
		configUserNameLabel.setText("ユーザー名");

		userNameText = new Text(configUserNameContainer, SWT.SINGLE | SWT.BORDER);
		userNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		userNameText.setTextLimit(AppConstants.LOGIN_NAME_LIMIT);

		appCloseConfirmCheck = new Button(configContainer, SWT.CHECK | SWT.FLAT);
		appCloseConfirmCheck.setText("アプリケーションを閉じる時に確認する");

		logLobbyEnterExitCheck = new Button(configContainer, SWT.CHECK | SWT.FLAT);
		logLobbyEnterExitCheck.setText("ロビーの入退室ログを表示する");

		Group configTaskTrayBalloonGroup = new Group(configContainer, SWT.SHADOW_IN);
		configTaskTrayBalloonGroup.setText("タスクトレイからバルーンで通知");
		configTaskTrayBalloonGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		configTaskTrayBalloonGroup.setLayout(new GridLayout(1, false));

		balloonNotifyLobbyCheck = new Button(configTaskTrayBalloonGroup, SWT.CHECK | SWT.FLAT);
		balloonNotifyLobbyCheck.setText("ロビーのログメッセージ");

		balloonNotifyRoomCheck = new Button(configTaskTrayBalloonGroup, SWT.CHECK | SWT.FLAT);
		balloonNotifyRoomCheck.setText("プレイルームのログメッセージ");

		Group configTunnelGroup = new Group(configContainer, SWT.SHADOW_IN);
		configTunnelGroup.setText("トンネル通信");
		configTunnelGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(3, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		configTunnelGroup.setLayout(gridLayout);

		Label tunnelTransportLayer = new Label(configTunnelGroup, SWT.NONE);
		tunnelTransportLayer.setText("トランスポート層: ");

		tunnelTransportTcp = new Button(configTunnelGroup, SWT.RADIO | SWT.FLAT);
		tunnelTransportTcp.setText("TCP");

		tunnelTransportUdp = new Button(configTunnelGroup, SWT.RADIO | SWT.FLAT);
		tunnelTransportUdp.setText("UDP");

		Group configPortalServerGroup = new Group(configContainer, SWT.SHADOW_IN);
		configPortalServerGroup.setText("ポータルサーバー");
		configPortalServerGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 3;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		configPortalServerGroup.setLayout(gridLayout);

		privatePortalServerUseCheck = new Button(configPortalServerGroup, SWT.CHECK | SWT.FLAT);
		privatePortalServerUseCheck.setText("ポータルサーバーを指定する (指定しなければ公開のポータルサーバーを使います)");
		privatePortalServerUseCheck.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1));

		Label configPortalServerAddressLabel = new Label(configPortalServerGroup, SWT.NONE);
		configPortalServerAddressLabel.setText("サーバーアドレス");
		configPortalServerAddressLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

		privatePortalServerAddress = new Text(configPortalServerGroup, SWT.BORDER | SWT.SINGLE);
		privatePortalServerAddress.setTextLimit(100);
		privatePortalServerAddress.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Group configMyRoomGroup = new Group(configContainer, SWT.SHADOW_IN);
		configMyRoomGroup.setText("マイルーム");
		configMyRoomGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gridLayout = new GridLayout(1, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 5;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		configMyRoomGroup.setLayout(gridLayout);

		myRoomAllowEmptyMasterNameCheck = new Button(configMyRoomGroup, SWT.CHECK | SWT.FLAT);
		myRoomAllowEmptyMasterNameCheck.setText("アドレスの部屋主名を省略でもログインできるようにする");
		myRoomAllowEmptyMasterNameCheck.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		userNameText.setText(Utility.validateUserName(settings.getUserName()));

		appCloseConfirmCheck.setSelection(settings.isNeedAppCloseConfirm());
		logLobbyEnterExitCheck.setSelection(settings.isLogLobbyEnterExit());
		balloonNotifyLobbyCheck.setSelection(settings.isBallonNotifyLobby());
		balloonNotifyRoomCheck.setSelection(settings.isBallonNotifyRoom());

		privatePortalServerUseCheck.setSelection(settings.isPrivatePortalServerUse());
		privatePortalServerAddress.setText(settings.getPrivatePortalServerAddress());
		privatePortalServerAddress.setEnabled(settings.isPrivatePortalServerUse());

		myRoomAllowEmptyMasterNameCheck.setSelection(settings.isMyRoomAllowNoMasterName());

		switch (settings.getTunnelTransportLayer()) {
		case TCP:
			tunnelTransportTcp.setSelection(true);
			break;
		case UDP:
			tunnelTransportUdp.setSelection(true);
			break;
		}

		userNameText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				checkUserName();
			}
		});

		privatePortalServerUseCheck.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				privatePortalServerAddress.setEnabled(privatePortalServerUseCheck.getSelection());
			}
		});

		checkUserName();
		return configContainer;
	}

	private void checkUserName() {
		if (Utility.isValidUserName(userNameText.getText())) {
			setValid(true);
			setErrorMessage(null);
		} else {
			setValid(false);
			setErrorMessage("ユーザー名が不正な値です");
		}
	}

	private void refrectValues() {
		if (!isControlCreated())
			return;
		settings.setUserName(userNameText.getText());
		settings.setNeedAppCloseConfirm(appCloseConfirmCheck.getSelection());
		settings.setLogLobbyEnterExit(logLobbyEnterExitCheck.getSelection());
		settings.setBallonNotifyLobby(balloonNotifyLobbyCheck.getSelection());
		settings.setBallonNotifyRoom(balloonNotifyRoomCheck.getSelection());
		settings.setPrivatePortalServerUse(privatePortalServerUseCheck.getSelection());
		settings.setPrivatePortalServerAddress(privatePortalServerAddress.getText());

		if (tunnelTransportTcp.getSelection()) {
			settings.setTunnelTransportLayer(IniSettings.TransportLayer.TCP);
		} else {
			settings.setTunnelTransportLayer(IniSettings.TransportLayer.UDP);
		}
	}

	@Override
	protected void performApply() {
		super.performApply();
		refrectValues();
	}

	@Override
	public boolean performOk() {
		refrectValues();
		return super.performOk();
	}
}
