package pspnetparty.client.swt.config;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import pspnetparty.client.swt.SwtUtils;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;

public class UserProfilePage extends PreferencePage {

	public static final String PAGE_ID = "userprofile";

	private IniUserProfile userProfile;

	private Text userNameText;
	private Text url;
	private Text iconUrl;
	private Text profile;

	public UserProfilePage(IniUserProfile profile) {
		super("ユーザープロフィール");
		userProfile = profile;

		noDefaultAndApplyButton();
	}

	@Override
	protected Control createContents(Composite parent) {
		GridLayout gridLayout;
		GridData gridDataLabel, gridDataWidget, gridDataHint;

		gridDataWidget = new GridData(SWT.FILL, SWT.CENTER, false, false);
		gridDataWidget.verticalIndent = 6;

		Composite configContainer = new Composite(parent, SWT.NONE);
		gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 0;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = 2;
		configContainer.setLayout(gridLayout);

		Label configUserNameLabel = new Label(configContainer, SWT.NONE);
		configUserNameLabel.setText("ユーザー名");
		configUserNameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		userNameText = new Text(configContainer, SWT.SINGLE | SWT.BORDER);
		userNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		userNameText.setTextLimit(AppConstants.NAME_STRING_MAX_LENGTH);

		Label urlLabel = new Label(configContainer, SWT.NONE);
		urlLabel.setText("URL");
		gridDataLabel = new GridData(SWT.END, SWT.CENTER, false, false);
		gridDataLabel.verticalIndent = 6;
		urlLabel.setLayoutData(gridDataLabel);

		url = new Text(configContainer, SWT.SINGLE | SWT.BORDER);
		url.setTextLimit(IniUserProfile.URL_MAX_LENGTH);
		url.setLayoutData(gridDataWidget);

		new Label(configContainer, SWT.NONE);

		Label urlHint = new Label(configContainer, SWT.NONE);
		urlHint.setText("自分のブログ・HP・SNSのアカウント等のURLを入力して下さい");
		gridDataHint = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		urlHint.setData(gridDataHint);

		Label iconUrlLabel = new Label(configContainer, SWT.NONE);
		iconUrlLabel.setText("アイコンURL");
		gridDataLabel = new GridData(SWT.END, SWT.CENTER, false, false);
		gridDataLabel.verticalIndent = 6;
		iconUrlLabel.setLayoutData(gridDataLabel);

		iconUrl = new Text(configContainer, SWT.SINGLE | SWT.BORDER);
		iconUrl.setTextLimit(IniUserProfile.URL_MAX_LENGTH);
		iconUrl.setLayoutData(gridDataWidget);

		new Label(configContainer, SWT.NONE);

		Label iconUrlHint = new Label(configContainer, SWT.NONE);
		iconUrlHint.setText("Web上の画像(GIF,JPEG,PNG,BMP,ICO,TIFF)のURLを入力して下さい");
		gridDataHint = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		iconUrlHint.setData(gridDataHint);

		Label profileLabel = new Label(configContainer, SWT.NONE);
		profileLabel.setText("プロフィール");
		gridDataLabel = new GridData(SWT.RIGHT, SWT.TOP, false, false);
		gridDataLabel.verticalIndent = 9;
		profileLabel.setLayoutData(gridDataLabel);

		profile = new Text(configContainer, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		gridDataWidget = new GridData(SWT.FILL, SWT.FILL, true, true);
		gridDataWidget.verticalIndent = 6;
		profile.setLayoutData(gridDataWidget);

		userNameText.setText(Utility.validateNameString(userProfile.getUserName()));
		url.setText(userProfile.getUrl());
		iconUrl.setText(userProfile.getIconUrl());
		profile.setText(userProfile.getProfile());

		userNameText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				checkUserName();
			}
		});

		url.addVerifyListener(SwtUtils.NOT_ACCEPT_SPACE_CONTROL_CHAR_LISTENER);
		iconUrl.addVerifyListener(SwtUtils.NOT_ACCEPT_SPACE_CONTROL_CHAR_LISTENER);
		profile.addVerifyListener(SwtUtils.NOT_ACCEPT_CONTROL_CHAR_LISTENER);

		checkUserName();
		return configContainer;
	}

	private void checkUserName() {
		if (Utility.isValidNameString(userNameText.getText())) {
			setValid(true);
			setErrorMessage(null);
		} else {
			setValid(false);
			setErrorMessage("ユーザー名が不正な値です");
		}
	}

	private void reflectValues() {
		if (!isControlCreated())
			return;

		userProfile.setUserName(userNameText.getText());
		userProfile.setUrl(url.getText());
		userProfile.setIconUrl(iconUrl.getText());
		userProfile.setProfile(profile.getText());
	}

	@Override
	protected void performApply() {
		super.performApply();
		reflectValues();
	}

	@Override
	public boolean performOk() {
		reflectValues();
		return super.performOk();
	}
}
