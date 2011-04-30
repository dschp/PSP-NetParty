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

public class RoomServerSelectDialog extends Dialog {

	private List<RoomServerInfo> serverList;
	private RoomServerInfo selectedServer;
	private String okLabel;

	protected RoomServerSelectDialog(Shell parentShell, List<RoomServerInfo> list, String okLabel) {
		super(parentShell);
		serverList = list;
		selectedServer = list.get(0);
		this.okLabel = okLabel;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("ルームサーバー選択");
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
		viewer.setLabelProvider(RoomServerInfo.LABEL_PROVIDER);

		viewer.setInput(serverList);

		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent e) {
				IStructuredSelection selection = (IStructuredSelection) e.getSelection();
				selectedServer = (RoomServerInfo) selection.getFirstElement();
				getButton(OK).setEnabled(selectedServer != null);
			}
		});
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent e) {
				IStructuredSelection selection = (IStructuredSelection) e.getSelection();
				selectedServer = (RoomServerInfo) selection.getFirstElement();
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

		GridLayout layout = (GridLayout) ((Composite) control).getLayout();
		layout.makeColumnsEqualWidth = false;

		Button ok = getButton(OK);
		ok.setText(okLabel);
		ok.pack();

		GridData data = (GridData) ok.getLayoutData();
		data.widthHint = SWT.DEFAULT;

		return control;
	}

	public RoomServerInfo getSelectedServer() {
		return selectedServer;
	}
}
