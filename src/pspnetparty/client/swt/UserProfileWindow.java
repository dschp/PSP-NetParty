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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import pspnetparty.client.swt.config.IniAppData;
import pspnetparty.lib.LobbyUser;
import pspnetparty.lib.Utility;

public class UserProfileWindow {

	private static final int ICON_SIZE = 96;

	private PlayClient application;
	private Shell shell;

	private Composite dataContainer;
	private Label iconPlaceHolder;
	private Label userName;
	private Link url;
	private Text profileText;
	private Composite circleContainer;
	private Label circleLabel;
	private Label circleLabelSuffix;
	private ArrayList<Button> circleList = new ArrayList<Button>();
	private Button privateMessage;
	private Button closeWindow;

	private LobbyUser myself;
	private LobbyUser currentUser;

	public UserProfileWindow(PlayClient application, Shell parentShell, LobbyUser myself) {
		this.application = application;
		this.myself = myself;

		shell = new Shell(parentShell, SWT.SHELL_TRIM | SWT.TOOL);

		shell.setText("ユーザープロフィール");
		try {
			shell.setImages(application.getShellImages());
		} catch (RuntimeException e) {
			e.printStackTrace();
		}

		GridLayout gridLayout;
		GridData gridData;

		gridLayout = new GridLayout(1, false);
		gridLayout.marginHeight = 0;
		gridLayout.marginWidth = 0;
		gridLayout.verticalSpacing = 3;
		shell.setLayout(gridLayout);

		dataContainer = new Composite(shell, SWT.NONE);
		dataContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		gridLayout = new GridLayout(2, false);
		gridLayout.marginHeight = 1;
		gridLayout.marginWidth = 1;
		gridLayout.marginTop = 3;
		gridLayout.horizontalSpacing = 10;
		dataContainer.setLayout(gridLayout);

		iconPlaceHolder = new Label(dataContainer, SWT.BORDER);
		gridData = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 3);
		gridData.heightHint = gridData.widthHint = ICON_SIZE;
		iconPlaceHolder.setLayoutData(gridData);

		Font font = new Font(SwtUtils.DISPLAY, SwtUtils.DISPLAY.getSystemFont().getFontData()[0].getName(), 14, SWT.BOLD);

		userName = new Label(dataContainer, SWT.NONE);
		userName.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
		userName.setFont(font);

		url = new Link(dataContainer, SWT.NONE);
		gridData = new GridData(SWT.LEFT, SWT.TOP, true, false);
		gridData.verticalIndent = 2;
		url.setLayoutData(gridData);

		circleContainer = new Composite(dataContainer, SWT.NONE);
		gridData = new GridData(SWT.FILL, SWT.TOP, true, false);
		gridData.verticalIndent = 4;
		circleContainer.setLayoutData(gridData);
		RowLayout rowLayout = new RowLayout();
		rowLayout.center = true;
		rowLayout.marginLeft = 0;
		circleContainer.setLayout(rowLayout);

		circleLabel = new Label(circleContainer, SWT.NONE);
		circleLabelSuffix = new Label(circleContainer, SWT.NONE);

		Label profileLabel = new Label(shell, SWT.NONE);
		profileLabel.setText("プロフィール");
		gridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		gridData.verticalIndent = 1;
		gridData.horizontalIndent = 2;
		profileLabel.setLayoutData(gridData);

		profileText = new Text(shell, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		application.initControl(profileText);
		profileText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Composite controlContainer = new Composite(shell, SWT.NONE);
		gridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gridData.verticalIndent = 7;
		controlContainer.setLayoutData(gridData);
		gridLayout = new GridLayout(2, false);
		controlContainer.setLayout(gridLayout);

		url.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				try {
					new URL(event.text);
					Program.launch(event.text);
				} catch (MalformedURLException e) {
				}
			}
		});

		privateMessage = new Button(controlContainer, SWT.PUSH);
		privateMessage.setText("プライベートメッセージを送る");
		privateMessage.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				if (currentUser == null)
					return;
				UserProfileWindow.this.application.getArenaWindow().openPrivateMessageDialog(currentUser);
			}
		});

		closeWindow = new Button(controlContainer, SWT.PUSH);
		closeWindow.setText("閉じる");
		gridData = new GridData(SWT.RIGHT, SWT.CENTER, true, false);
		gridData.widthHint = 80;
		closeWindow.setLayoutData(gridData);

		closeWindow.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				setVisible(false);
			}
		});

		shell.addShellListener(new ShellListener() {
			@Override
			public void shellClosed(ShellEvent e) {
				e.doit = false;
				shell.setVisible(false);
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
			public void shellActivated(ShellEvent e) {
			}
		});

		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				IniAppData appData = UserProfileWindow.this.application.getAppData();
				appData.storeUserProfilegWindow(shell.getBounds());
			}
		});

		shell.setMinimumSize(450, 300);
		application.getAppData().restoreUserProfileWindow(shell);
	}

	public void switchProfile(final LobbyUser user) {
		currentUser = user;
		privateMessage.setEnabled(user != null && user != myself);

		Image oldImage = iconPlaceHolder.getBackgroundImage();
		iconPlaceHolder.setBackgroundImage(null);
		if (oldImage != null)
			oldImage.dispose();

		if (user == null || Utility.isEmpty(user.getName())) {
			userName.setText("");
			url.setText("");
			profileText.setText("");

			updateCircleList(null);
			return;
		}

		userName.setText(user.getName());

		if (!Utility.isEmpty(user.getIconUrl())) {
			application.execute(new Runnable() {
				@Override
				public void run() {
					try {
						URL url = new URL(user.getIconUrl());
						ImageDescriptor desc = ImageDescriptor.createFromURL(url);
						setImage(desc);
					} catch (MalformedURLException ex) {
					}
				}

				private void setImage(final ImageDescriptor desc) {
					Image origImage = null;
					Image scaledImage = null;
					try {
						if (SwtUtils.isNotUIThread()) {
							SwtUtils.DISPLAY.asyncExec(new Runnable() {
								public void run() {
									setImage(desc);
								}
							});
							return;
						}

						origImage = desc.createImage();
						ImageData origImageData = origImage.getImageData();
						if (origImageData.width == ICON_SIZE && origImageData.height == ICON_SIZE) {
							scaledImage = origImage;
						} else {
							scaledImage = new Image(SwtUtils.DISPLAY, origImageData.scaledTo(ICON_SIZE, ICON_SIZE));
							origImage.dispose();
						}

						iconPlaceHolder.setBackgroundImage(scaledImage);
						return;
					} catch (SWTException e) {
					} catch (RuntimeException e) {
					}
					if (origImage != null && !origImage.isDisposed())
						origImage.dispose();
					if (scaledImage != null && !scaledImage.isDisposed())
						scaledImage.dispose();
					iconPlaceHolder.setBackgroundImage(null);
				}
			});
		} else {
		}

		if (Utility.isEmpty(user.getUrl()))
			url.setText("");
		else
			url.setText("<a>" + user.getUrl() + "</a>");

		if (Utility.isEmpty(user.getProfile()))
			profileText.setText("");
		else
			profileText.setText(user.getProfile());

		updateCircleList(user);

		dataContainer.layout();
	}

	private Listener circleButtonListener = new Listener() {
		@Override
		public void handleEvent(Event event) {
			Button button = (Button) event.widget;
			String circle = button.getText();
			if (button.getSelection()) {
				application.getArenaWindow().requestCircleJoin(circle);
			} else {
				application.getArenaWindow().requestCircleLeave(circle);
			}
		}
	};

	private void updateCircleList(LobbyUser user) {
		for (Button circle : circleList) {
			circle.dispose();
		}
		circleList.clear();

		if (user == null) {
			circleLabel.setText("");
			circleLabelSuffix.setText("");
		} else {
			circleLabel.setText("所属サークル");

			Set<String> myCircles = myself.getCircles();
			for (String circle : user.getCircles()) {
				Button button = new Button(circleContainer, SWT.TOGGLE);
				button.setText(circle);
				button.setSelection(myCircles.contains(circle));
				button.addListener(SWT.Selection, circleButtonListener);
				circleList.add(button);
			}
			circleLabelSuffix.setText(circleList.isEmpty() ? ":  なし" : "");
		}

		shell.layout(true, true);
	}

	public void profileRefreshed(LobbyUser user) {
		if (currentUser != user)
			return;

		switchProfile(user);
	}

	public void circlesRefreshed(LobbyUser user) {
		if (currentUser != user)
			return;

		updateCircleList(user);
	}

	public void userLoggedOut(LobbyUser user) {
		if (currentUser != user)
			return;

		switchProfile(null);
	}

	public void reflectApperance() {
		shell.layout(true, true);
	}

	public void setVisible(boolean visible) {
		shell.setVisible(visible);
	}
}
