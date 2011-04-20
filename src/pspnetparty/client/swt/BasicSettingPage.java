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
