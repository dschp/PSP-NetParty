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

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;

public class LobbyChangeNameDialog extends Dialog {

	private String origName;
	private String newName;

	protected LobbyChangeNameDialog(Shell parentShell, String name) {
		super(parentShell);
		origName = name;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("新しいユーザー名を入力してください");
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		composite.setLayout(new GridLayout(1, false));

		GridData gridData;

		Label label = new Label(composite, SWT.NONE);
		label.setText("現在のユーザー名: " + origName);

		final Text newNameText = new Text(composite, SWT.BORDER | SWT.SINGLE);
		newNameText.setText(origName);
		newNameText.setSelection(origName.length());
		newNameText.setTextLimit(AppConstants.LOGIN_NAME_LIMIT);
		gridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		gridData.widthHint = 250;
		newNameText.setLayoutData(gridData);

		newNameText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				newName = newNameText.getText();
				getButton(OK).setEnabled(!newName.equals(origName) && Utility.isValidUserName(newName));
			}
		});

		return composite;
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		Control control = super.createButtonBar(parent);

		getButton(OK).setEnabled(false);

		return control;
	}

	public String getNewName() {
		return newName;
	}
}
