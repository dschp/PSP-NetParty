package pspnetparty.client.test;

import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ColorFieldEditor;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.FontFieldEditor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PathEditor;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.PreferenceManager;
import org.eclipse.jface.preference.PreferenceNode;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

public class TestJfacePreference {

	public static void main1(String[] args) {
		final Display display = new Display();

		Shell shell0 = new Shell();

		final Shell shell = new Shell(shell0, SWT.SHELL_TRIM | SWT.TOOL);
		shell.setLayout(new FillLayout());

		shell0.open();
		shell.open();
		// Set up the event loop.
		while (!shell0.isDisposed()) {
			if (!display.readAndDispatch()) {
				// If no more entries in event queue
				display.sleep();
			}
		}
		display.dispose();
	}

	public static void main(String[] args) {
		Display display = new Display();
		// Shell shell = new Shell(display);
		// FillLayout fillLayout = new FillLayout();
		// fillLayout.marginHeight = 5;
		// fillLayout.marginWidth = 5;
		// shell.setLayout(fillLayout);

		PreferenceManager manager = new PreferenceManager();
		PreferenceNode one = new PreferenceNode("one", new TestPage());
		PreferenceNode two = new PreferenceNode("two", new TestPage2());

		//manager.addToRoot(one);
		manager.addToRoot(two);
		
		PreferenceStore store = new PreferenceStore("Test.pref");
		try {
			store.load();
		} catch (IOException e) {
			e.printStackTrace();
		}

		PreferenceDialog dialog = new PreferenceDialog(null, manager) {
			@Override
			protected void configureShell(Shell newShell) {
				super.configureShell(newShell);
				newShell.setText("設定");
			}

			@Override
			protected void initializeBounds() {
				super.initializeBounds();
				getButton(CANCEL).setText("キャンセル");
			}
		};
		//dialog.setPreferenceStore(store);
		dialog.open();

		// TestPage page = new TestPage();
		// page.setPreferenceStore(store);
		// page.createControl(shell);
		//
		// shell.open();
		// while (!shell.isDisposed()) {
		// if (!display.readAndDispatch()) {
		// display.sleep();
		// }
		// }
		// try {
		// display.dispose();
		// } catch (RuntimeException e) {
		// }

		try {
			store.save();
		} catch (IOException e) {
			e.printStackTrace();
		}
		display.dispose();
	}
}

class TestPage extends FieldEditorPreferencePage {

	public TestPage() {
		super("TEST", FieldEditorPreferencePage.FLAT);
	}

	private ArrayList<FieldEditor> editors = new ArrayList<FieldEditor>();

	@Override
	protected void addField(FieldEditor editor) {
		super.addField(editor);
		editors.add(editor);
	}

	@Override
	protected void createFieldEditors() {
		// Add a boolean field
		BooleanFieldEditor bfe = new BooleanFieldEditor("myBoolean", "確認する", getFieldEditorParent());
		addField(bfe);

		// Add a color field
		ColorFieldEditor cfe = new ColorFieldEditor("myColor", "全体の色:", getFieldEditorParent());
		addField(cfe);

		// Add a directory field
		DirectoryFieldEditor dfe = new DirectoryFieldEditor("myDirectory", "フォルダ:", getFieldEditorParent());
		addField(dfe);

		// Add a file field
		FileFieldEditor ffe = new FileFieldEditor("myFile", "ファイル:", getFieldEditorParent());
		addField(ffe);

		// Add a font field
		FontFieldEditor fontFe = new FontFieldEditor("myFont", "全体のフォント:", getFieldEditorParent());
		addField(fontFe);

		// Add a radio group field
		RadioGroupFieldEditor rfe = new RadioGroupFieldEditor("myRadioGroup", "Radio Group", 2, new String[][] {
				{ "First Value", "first" }, { "Second Value", "second" }, { "Third Value", "third" }, { "Fourth Value", "fourth" } },
				getFieldEditorParent(), true);
		addField(rfe);

		// Add a path field
		PathEditor pe = new PathEditor("myPath", "Path:", "Choose a Path", getFieldEditorParent());
		addField(pe);
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		getApplyButton().setText("適用する");
		getDefaultsButton().setText("デフォルトに戻す");
	}

	private Button revertButton;

	@Override
	protected void contributeButtons(Composite parent) {
		// GridLayout layout = (GridLayout) parent.getLayout();
		// layout.numColumns += 1;
		// layout.makeColumnsEqualWidth = true;
		//
		// revertButton = new Button(parent, SWT.PUSH);
		// revertButton.setText("編集前に戻す");
		// revertButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false,
		// false));
		// revertButton.addListener(SWT.Selection, new Listener() {
		// @Override
		// public void handleEvent(Event event) {
		// for (FieldEditor editor : editors) {
		// editor.load();
		// }
		// }
		// });
	}

	@Override
	protected void performApply() {
		super.performApply();
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		super.propertyChange(event);
	}
}

class TestPage2 extends PreferencePage {
	// Names for preferences
	private static final String ONE = "two.one";
	private static final String TWO = "two.two";
	private static final String THREE = "two.three";

	private Button checkOne;
	private Button checkTwo;
	private Button checkThree;

	public TestPage2() {
		super("Two");
		setDescription("Check the checks");
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new RowLayout(SWT.VERTICAL));

		// Get the preference store
		IPreferenceStore preferenceStore = getPreferenceStore();

		// Create three checkboxes
		checkOne = new Button(composite, SWT.CHECK);
		checkOne.setText("Check One");
		//checkOne.setSelection(preferenceStore.getBoolean(ONE));
		checkOne.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				checkValid();
			}
		});

		checkTwo = new Button(composite, SWT.CHECK);
		checkTwo.setText("Check Two");
		//checkTwo.setSelection(preferenceStore.getBoolean(TWO));

		checkThree = new Button(composite, SWT.CHECK);
		checkThree.setText("Check Three");
		//checkThree.setSelection(preferenceStore.getBoolean(THREE));

		// noDefaultAndApplyButton();

		setValid(checkOne.getSelection());

		checkValid();

		setImageDescriptor(new ImageDescriptor() {
			@Override
			public ImageData getImageData() {
				return new ImageData("icon/aqua16.png");
			}
		});

		return composite;
	}

	private void checkValid() {
		setValid(!checkOne.getSelection());
		if (checkOne.getSelection()) {
			setErrorMessage("エラーがあります");
			setDescription("foo Bar");
		} else {
			setErrorMessage(null);
			setDescription("foo Bar");
		}
	}

	@Override
	protected void contributeButtons(Composite parent) {
		// Add a select all button
		Button selectAll = new Button(parent, SWT.PUSH);
		selectAll.setText("Select All");
		selectAll.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				checkOne.setSelection(true);
				checkTwo.setSelection(true);
				checkThree.setSelection(true);
			}
		});

		// Add a select all button
		Button clearAll = new Button(parent, SWT.PUSH);
		clearAll.setText("Clear All");
		clearAll.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				checkOne.setSelection(false);
				checkTwo.setSelection(false);
				checkThree.setSelection(false);
			}
		});

		// Add two columns to the parent's layout
		((GridLayout) parent.getLayout()).numColumns += 2;
		((GridLayout) parent.getLayout()).makeColumnsEqualWidth = true;

		GridData gridData;
		gridData = new GridData(SWT.FILL, SWT.CENTER, false, false);
		// gridData.widthHint = 200;
		selectAll.setLayoutData(gridData);
		clearAll.setLayoutData(gridData);
	}
}
