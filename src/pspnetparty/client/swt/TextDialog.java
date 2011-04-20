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
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import pspnetparty.lib.Utility;

public class TextDialog extends Dialog {

	private String title;
	private String label;
	private String buttonLabel;
	private int minWidth;

	private String userInput;
	private int imeInputMode;

	protected TextDialog(Shell parentShell, String title, String label, String buttonLabel, int minWidth) {
		this(parentShell, title, label, buttonLabel, minWidth, parentShell.getImeInputMode());
	}

	protected TextDialog(Shell parentShell, String title, String label, String buttonLabel, int minWidth, int imeMode) {
		super(parentShell);
		this.title = title;
		this.label = label;
		this.buttonLabel = buttonLabel;
		this.minWidth = minWidth;
		this.imeInputMode = imeMode;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(title);
		newShell.setImeInputMode(imeInputMode);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));

		GridData gridData;

		Label label = new Label(composite, SWT.NONE);
		label.setText(this.label);

		final Text text = new Text(composite, SWT.BORDER | SWT.SINGLE);
		gridData = new GridData(GridData.FILL, GridData.CENTER, true, false);
		gridData.minimumWidth = minWidth;
		text.setLayoutData(gridData);

		text.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				userInput = text.getText();
				getButton(OK).setEnabled(!Utility.isEmpty(userInput));
			}
		});

		return composite;
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		Control control = super.createButtonBar(parent);

		Button button = getButton(OK);
		button.setEnabled(false);
		if (!Utility.isEmpty(buttonLabel))
			button.setText(buttonLabel);

		return control;
	}

	public String getUserInput() {
		return userInput;
	}
}