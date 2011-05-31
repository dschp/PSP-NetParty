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
package pspnetparty.client.swt.config;

import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

public class ChatTextPresetsPage extends PreferencePage {

	private IniSettings settings;
	private IniChatTextPresets chatTextPresets;

	private Button showPresetButtons;
	private Spinner presetButtonMaxLength;
	private Text textF1;
	private Text textF2;
	private Text textF3;
	private Text textF4;
	private Text textF5;
	private Text textF6;
	private Text textF7;
	private Text textF8;
	private Text textF9;
	private Text textF10;
	private Text textF11;
	private Text textF12;
	private Button enableKeyInput;

	public ChatTextPresetsPage(IniSettings settings, IniChatTextPresets presets) {
		super("チャット定型文");
		this.settings = settings;
		chatTextPresets = presets;

		noDefaultAndApplyButton();
	}

	@Override
	protected Control createContents(Composite parent) {
		GridLayout gridLayout;
		GridData gridData;
		GridData textGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);

		Composite container = new Composite(parent, SWT.NONE);
		gridLayout = new GridLayout(1, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 6;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = 2;
		container.setLayout(gridLayout);

		Composite showButtonContainer = new Composite(container, SWT.NONE);
		gridLayout = new GridLayout(4, false);
		gridLayout.horizontalSpacing = 4;
		gridLayout.verticalSpacing = 0;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		showButtonContainer.setLayout(gridLayout);
		showButtonContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		showPresetButtons = new Button(showButtonContainer, SWT.CHECK | SWT.FLAT);
		showPresetButtons.setText("定型文ボタンを表示する");
		showPresetButtons.setSelection(settings.isShowChatPresetButtons());

		Label presetButtonMaxLengthLabel1 = new Label(showButtonContainer, SWT.NONE);
		presetButtonMaxLengthLabel1.setText("ボタンの最大文字数");
		gridData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
		gridData.horizontalIndent = 30;
		presetButtonMaxLengthLabel1.setLayoutData(gridData);

		presetButtonMaxLength = new Spinner(showButtonContainer, SWT.BORDER);
		presetButtonMaxLength.setMinimum(1);
		presetButtonMaxLength.setSelection(settings.getChatPresetButtonMaxLength());
		presetButtonMaxLength.setEnabled(settings.isShowChatPresetButtons());

		Label presetButtonMaxLengthLabel2 = new Label(showButtonContainer, SWT.NONE);
		presetButtonMaxLengthLabel2.setText("文字");

		Composite enableKeyInputContainer = new Composite(container, SWT.NONE);
		gridLayout = new GridLayout(1, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 0;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		enableKeyInputContainer.setLayout(gridLayout);
		enableKeyInputContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		enableKeyInput = new Button(enableKeyInputContainer, SWT.CHECK | SWT.FLAT);
		enableKeyInput.setText("キーボードでの入力を有効にする");
		enableKeyInput.setSelection(settings.isChatPresetEnableKeyInput());

		Composite presetContainer = new Composite(container, SWT.NONE);
		gridLayout = new GridLayout(2, false);
		gridLayout.horizontalSpacing = 8;
		gridLayout.verticalSpacing = 6;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = 5;
		presetContainer.setLayout(gridLayout);
		presetContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

		GridData labelGridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);

		Label labelF1 = new Label(presetContainer, SWT.NONE);
		labelF1.setText("F1");
		labelF1.setLayoutData(labelGridData);

		textF1 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF1.setText(chatTextPresets.getPresetF1());
		textF1.setLayoutData(textGridData);

		Label labelF2 = new Label(presetContainer, SWT.NONE);
		labelF2.setText("F2");
		labelF2.setLayoutData(labelGridData);

		textF2 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF2.setText(chatTextPresets.getPresetF2());
		textF2.setLayoutData(textGridData);

		Label labelF3 = new Label(presetContainer, SWT.NONE);
		labelF3.setText("F3");
		labelF3.setLayoutData(labelGridData);

		textF3 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF3.setText(chatTextPresets.getPresetF3());
		textF3.setLayoutData(textGridData);

		Label labelF4 = new Label(presetContainer, SWT.NONE);
		labelF4.setText("F4");
		labelF4.setLayoutData(labelGridData);

		textF4 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF4.setText(chatTextPresets.getPresetF4());
		textF4.setLayoutData(textGridData);

		Label labelF5 = new Label(presetContainer, SWT.NONE);
		labelF5.setText("F5");
		labelF5.setLayoutData(labelGridData);

		textF5 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF5.setText(chatTextPresets.getPresetF5());
		textF5.setLayoutData(textGridData);

		Label labelF6 = new Label(presetContainer, SWT.NONE);
		labelF6.setText("F6");
		labelF6.setLayoutData(labelGridData);

		textF6 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF6.setText(chatTextPresets.getPresetF6());
		textF6.setLayoutData(textGridData);

		Label labelF7 = new Label(presetContainer, SWT.NONE);
		labelF7.setText("F7");
		labelF7.setLayoutData(labelGridData);

		textF7 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF7.setText(chatTextPresets.getPresetF7());
		textF7.setLayoutData(textGridData);

		Label labelF8 = new Label(presetContainer, SWT.NONE);
		labelF8.setText("F8");
		labelF8.setLayoutData(labelGridData);

		textF8 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF8.setText(chatTextPresets.getPresetF8());
		textF8.setLayoutData(textGridData);

		Label labelF9 = new Label(presetContainer, SWT.NONE);
		labelF9.setText("F9");
		labelF9.setLayoutData(labelGridData);

		textF9 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF9.setText(chatTextPresets.getPresetF9());
		textF9.setLayoutData(textGridData);

		labelGridData = new GridData(SWT.CENTER, SWT.CENTER, false, false);

		Label labelF10 = new Label(presetContainer, SWT.NONE);
		labelF10.setText("F10");
		labelF10.setLayoutData(labelGridData);

		textF10 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF10.setText(chatTextPresets.getPresetF10());
		textF10.setLayoutData(textGridData);

		Label labelF11 = new Label(presetContainer, SWT.NONE);
		labelF11.setText("F11");
		labelF11.setLayoutData(labelGridData);

		textF11 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF11.setText(chatTextPresets.getPresetF11());
		textF11.setLayoutData(textGridData);

		Label labelF12 = new Label(presetContainer, SWT.NONE);
		labelF12.setText("F12");
		labelF12.setLayoutData(labelGridData);

		textF12 = new Text(presetContainer, SWT.SINGLE | SWT.BORDER);
		textF12.setText(chatTextPresets.getPresetF12());
		textF12.setLayoutData(textGridData);

		showPresetButtons.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				presetButtonMaxLength.setEnabled(showPresetButtons.getSelection());
			}
		});

		return container;
	}

	private void reflectValues() {
		if (!isControlCreated())
			return;

		settings.setShowChatPresetButtons(showPresetButtons.getSelection());
		settings.setChatPresetButtonMaxLength(presetButtonMaxLength.getSelection());
		settings.setChatPresetEnableKeyInput(enableKeyInput.getSelection());

		chatTextPresets.setPresetF1(textF1.getText());
		chatTextPresets.setPresetF2(textF2.getText());
		chatTextPresets.setPresetF3(textF3.getText());
		chatTextPresets.setPresetF4(textF4.getText());
		chatTextPresets.setPresetF5(textF5.getText());
		chatTextPresets.setPresetF6(textF6.getText());
		chatTextPresets.setPresetF7(textF7.getText());
		chatTextPresets.setPresetF8(textF8.getText());
		chatTextPresets.setPresetF9(textF9.getText());
		chatTextPresets.setPresetF10(textF10.getText());
		chatTextPresets.setPresetF11(textF11.getText());
		chatTextPresets.setPresetF12(textF12.getText());
	}

	@Override
	public boolean performOk() {
		reflectValues();
		return super.performOk();
	}
}
