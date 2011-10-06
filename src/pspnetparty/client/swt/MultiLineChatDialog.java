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
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import pspnetparty.lib.Utility;

public class MultiLineChatDialog extends Dialog {

	private PlayClient application;
	private Text inputText;

	private String message;

	protected MultiLineChatDialog(Shell parentShell, PlayClient application) {
		super(parentShell);
		this.application = application;
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("複数行のチャット送信");
		newShell.setImeInputMode(getParentShell().getImeInputMode());
	}

	@Override
	protected Point getInitialLocation(Point initialSize) {
		Shell parentShell = getParentShell();
		int x = parentShell.getLocation().x + (parentShell.getSize().x - initialSize.x) / 2;
		int y = parentShell.getLocation().y + (parentShell.getSize().y - initialSize.y) / 2;
		return new Point(x, y);
	}

	@Override
	protected Point getInitialSize() {
		return new Point(400, 250);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		container.setLayout(new FillLayout());
		container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		inputText = new Text(container, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);

		String clipboard = application.getClipboardContents();
		if (!Utility.isEmpty(clipboard))
			inputText.setText(clipboard);

		inputText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				getButton(OK).setEnabled(inputText.getCharCount() > 0);
			}
		});

		return container;
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		Control control = super.createButtonBar(parent);

		Button button = getButton(OK);
		button.setEnabled(inputText.getCharCount() > 0);
		button.setText("送信");

		return control;
	}

	@Override
	protected void okPressed() {
		message = inputText.getText();
		super.okPressed();
	}

	public String getMessage() {
		return message;
	}
}
