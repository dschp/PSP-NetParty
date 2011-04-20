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

public class LobbyServerSelectDialog extends Dialog {

	private List<LobbyServerInfo> serverList;
	private LobbyServerInfo selectedServer;

	protected LobbyServerSelectDialog(Shell parentShell, List<LobbyServerInfo> serverList) {
		super(parentShell);
		this.serverList = serverList;
		selectedServer = serverList.get(0);
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("ロビーサーバー選択");
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite composite = (Composite) super.createDialogArea(parent);
		composite.setLayout(new GridLayout(1, false));

		TableViewer tableViewer = new TableViewer(composite, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);

		Table table = tableViewer.getTable();
		table.setHeaderVisible(true);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		TableColumn titleColumn = new TableColumn(table, SWT.LEFT);
		titleColumn.setText("ロビー名");

		TableColumn userCountColumn = new TableColumn(table, SWT.RIGHT);
		userCountColumn.setText("ユーザー数");

		TableColumn addressColumn = new TableColumn(table, SWT.LEFT);
		addressColumn.setText("サーバー");

		tableViewer.setContentProvider(new ArrayContentProvider());
		tableViewer.setLabelProvider(LobbyServerInfo.LABEL_PROVIDER);

		tableViewer.setInput(serverList);

		tableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent e) {
				IStructuredSelection selection = (IStructuredSelection) e.getSelection();
				selectedServer = (LobbyServerInfo) selection.getFirstElement();
				getButton(OK).setEnabled(selectedServer != null);
			}
		});
		tableViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent e) {
				IStructuredSelection selection = (IStructuredSelection) e.getSelection();
				selectedServer = (LobbyServerInfo) selection.getFirstElement();
				setReturnCode(OK);
				close();
			}
		});

		addressColumn.pack();
		userCountColumn.pack();
		titleColumn.pack();

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

	public LobbyServerInfo getSelectedServer() {
		return selectedServer;
	}
}
