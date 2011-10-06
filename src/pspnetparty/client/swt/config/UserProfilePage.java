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

		Composite configContainer = new Composite(parent, SWT.NONE);
		gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 6;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = 2;
		configContainer.setLayout(gridLayout);

		Label configUserNameLabel = new Label(configContainer, SWT.NONE);
		configUserNameLabel.setText("ユーザー名");

		userNameText = new Text(configContainer, SWT.SINGLE | SWT.BORDER);
		userNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		userNameText.setTextLimit(AppConstants.NAME_STRING_MAX_LENGTH);

		Label urlLabel = new Label(configContainer, SWT.NONE);
		urlLabel.setText("URL");
		urlLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		url = new Text(configContainer, SWT.SINGLE | SWT.BORDER);
		url.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		url.setTextLimit(IniUserProfile.URL_MAX_LENGTH);

		Label iconUrlLabel = new Label(configContainer, SWT.NONE);
		iconUrlLabel.setText("アイコンURL");
		iconUrlLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		iconUrl = new Text(configContainer, SWT.SINGLE | SWT.BORDER);
		iconUrl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		iconUrl.setTextLimit(IniUserProfile.URL_MAX_LENGTH);

		Label profileLabel = new Label(configContainer, SWT.NONE);
		profileLabel.setText("プロフィール");
		profileLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));

		profile = new Text(configContainer, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		profile.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

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
