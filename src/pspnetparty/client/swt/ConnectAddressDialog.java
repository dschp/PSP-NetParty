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
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

import pspnetparty.lib.socket.TransportLayer;

public class ConnectAddressDialog extends Dialog {

	private TransportLayer transport;
	private String hostname;
	private int port;

	protected ConnectAddressDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("アドレスを入力してください");
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(4, false));
		composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		GridData gridData;

		{
			Label label = new Label(composite, SWT.NONE);
			label.setText("接続先のアドレス:");
			label.setLayoutData(new GridData(SWT.DEFAULT, SWT.DEFAULT, false, false, 4, 1));
		}
		{
			Combo transportCombo = new Combo(composite, SWT.BORDER | SWT.READ_ONLY);
			transportCombo.add("TCP");
			transportCombo.add("UDP");
			transportCombo.select(0);

			transport = TransportLayer.TCP;

			transportCombo.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event e) {
					Combo transportCombo = (Combo) e.widget;
					switch (transportCombo.getSelectionIndex()) {
					case 0:
						transport = TransportLayer.TCP;
						break;
					case 1:
						transport = TransportLayer.UDP;
						break;
					}
				}
			});
		}
		{
			Text hostnameText = new Text(composite, SWT.BORDER | SWT.SINGLE);
			hostnameText.setText(hostname = "");

			gridData = new GridData(GridData.FILL, GridData.CENTER, true, false);
			gridData.minimumWidth = 100;
			hostnameText.setLayoutData(gridData);

			hostnameText.setFocus();
			hostnameText.addModifyListener(new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					Text hostnameText = (Text) e.widget;
					hostname = hostnameText.getText();
				}
			});
		}
		{
			Label label = new Label(composite, SWT.NONE);
			label.setText(":");
		}
		{
			Spinner portSpinner = new Spinner(composite, SWT.BORDER);
			portSpinner.setMinimum(1);
			portSpinner.setMaximum(65535);
			portSpinner.setSelection(port = 20000);

			portSpinner.addModifyListener(new ModifyListener() {
				@Override
				public void modifyText(ModifyEvent e) {
					Spinner portSpinner = (Spinner) e.widget;
					port = portSpinner.getSelection();
				}
			});
		}

		return composite;
	}

	public TransportLayer getTransport() {
		return transport;
	}

	public String getHostName() {
		return hostname;
	}

	public int getPort() {
		return port;
	}
}
