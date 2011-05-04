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

import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

public class SearchServerSelectDialog extends Dialog {

	private List<SearchServerInfo> serverList;
	private SearchServerInfo selectedServer;

	protected SearchServerSelectDialog(Shell parentShell, List<SearchServerInfo> list) {
		super(parentShell);
		serverList = list;
		selectedServer = list.get(0);
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("検索サーバー選択");
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		composite.setLayout(new GridLayout(1, false));

		TableViewer viewer = new TableViewer(composite, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);

		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		TableColumn addressColumn = new TableColumn(table, SWT.LEFT);
		addressColumn.setText("アドレス");

		TableColumn useRateColumn = new TableColumn(table, SWT.RIGHT);
		useRateColumn.setText("利用率");

		viewer.setContentProvider(new ArrayContentProvider());
		viewer.setLabelProvider(SearchServerInfo.LABEL_PROVIDER);

		viewer.setInput(serverList);

		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent e) {
				IStructuredSelection selection = (IStructuredSelection) e.getSelection();
				selectedServer = (SearchServerInfo) selection.getFirstElement();
				getButton(OK).setEnabled(selectedServer != null);
			}
		});
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent e) {
				IStructuredSelection selection = (IStructuredSelection) e.getSelection();
				selectedServer = (SearchServerInfo) selection.getFirstElement();
				setReturnCode(OK);
				close();
			}
		});

		addressColumn.pack();
		useRateColumn.pack();

		table.select(0);

		return composite;
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		Control control = super.createButtonBar(parent);

		Button ok = getButton(OK);
		ok.setText("ログイン");

		return control;
	}

	public SearchServerInfo getSelectedServer() {
		return selectedServer;
	}
}
