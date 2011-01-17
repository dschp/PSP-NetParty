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
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import pspnetparty.lib.Utility;

public class PasswordDialog extends Dialog {

	private String password;

	protected PasswordDialog(Shell parentShell) {
		super(parentShell);
	}

	protected Point getInitialSize() {
		getShell().pack();
		return getShell().getSize();
		// return new Point(400, 300);
	}

	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("パスワードを入力してください");
	}

	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		composite.setLayout(new GridLayout(1, false));

		GridData gridData;

		Label label = new Label(composite, SWT.NONE);
		label.setText("部屋にパスワードが設定されています");

		final Text passwordText = new Text(composite, SWT.BORDER | SWT.SINGLE);
		gridData = new GridData(GridData.FILL, GridData.CENTER, true, false);
		gridData.minimumWidth = 180;
		passwordText.setLayoutData(gridData);

		passwordText.addVerifyListener(new VerifyListener() {
			@Override
			public void verifyText(VerifyEvent e) {
				switch (e.character) {
				case ' ':
					e.doit = false;
					break;
				case '\0':
					e.text = e.text.replace(" ", "").trim();
					break;
				default:
				}
			}
		});
		passwordText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				password = passwordText.getText();
				getButton(OK).setEnabled(!Utility.isEmpty(password));
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
	
	public String getPassword() {
		return password;
	}
}