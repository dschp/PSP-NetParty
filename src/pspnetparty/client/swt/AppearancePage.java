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

import java.util.ArrayList;

import org.eclipse.jface.preference.ColorSelector;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FontDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

import pspnetparty.client.swt.IApplication.ColorType;
import pspnetparty.client.swt.IApplication.FontType;

public class AppearancePage extends PreferencePage {

	private IApplication application;
	private IniAppearance iniAppearance;

	private FontData fontGlobal;
	private FontData fontLog;
	private FontData fontChat;

	private Text sampleFontGlobal;
	private Text sampleFontLog;
	private Text sampleFontChat;

	private RGB colorBackground;
	private RGB colorForeground;
	private RGB colorLogBackground;
	private RGB colorLogInfo;
	private RGB colorLogError;
	private RGB colorLogServer;
	private RGB colorLogRoom;
	private RGB colorChatMine;
	private RGB colorChatOthers;
	private RGB colorChatPrivate;

	private ColorSelector selectorColorBackground;
	private ColorSelector selectorColorForeground;
	private ColorSelector selectorColorLogBackground;
	private ColorSelector selectorColorLogInfo;
	private ColorSelector selectorColorLogError;
	private ColorSelector selectorColorLogServer;
	private ColorSelector selectorColorLogRoom;
	private ColorSelector selectorColorChatMine;
	private ColorSelector selectorColorChatOthers;
	private ColorSelector selectorColorChatPrivate;

	private RGB colorTimestampRulerBG;
	private RGB colorTimestampRulerFG;
	private Integer timestampRulerWidth;
	private RGB colorNameRulerBG;
	private RGB colorNameRulerFG;
	private Integer nameRulerWidth;

	private ColorSelector selectorColorTimestampRulerBG;
	private ColorSelector selectorColorTimestampRulerFG;
	private Spinner spinnerTimestampRulerWidth;
	private ColorSelector selectorColorNameRulerBG;
	private ColorSelector selectorColorNameRulerFG;
	private Spinner spinnerNameRulerWidth;

	public AppearancePage(IApplication application) {
		super("フォントと色");
		this.application = application;
		iniAppearance = application.getAppearance();
		// noDefaultAndApplyButton();
	}

	@Override
	protected Control createContents(Composite parent) {
		GridLayout gridLayout;
		GridData gridData;
		FontData fontData;
		Color bgColor = parent.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND);

		Composite container = new Composite(parent, SWT.NONE);
		gridLayout = new GridLayout(1, false);
		gridLayout.horizontalSpacing = 0;
		gridLayout.verticalSpacing = 6;
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.marginTop = 2;
		container.setLayout(gridLayout);

		Group groupFont = new Group(container, SWT.SHADOW_IN);
		groupFont.setText("フォント");
		gridLayout = new GridLayout(3, false);
		gridLayout.horizontalSpacing = 8;
		gridLayout.verticalSpacing = 5;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 0;
		gridLayout.marginBottom = 5;
		groupFont.setLayout(gridLayout);
		groupFont.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false));

		Label labelFontGlobal = new Label(groupFont, SWT.NONE);
		labelFontGlobal.setText("全体:");
		labelFontGlobal.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		sampleFontGlobal = new Text(groupFont, SWT.BORDER | SWT.READ_ONLY);
		sampleFontGlobal.setBackground(bgColor);
		sampleFontGlobal.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		fontData = iniAppearance.getFontGlobal().getFontData()[0];
		sampleFontGlobal.setText(fontToString(fontData));

		Button buttonFontGlobal = new Button(groupFont, SWT.PUSH);
		buttonFontGlobal.setText("選択");
		buttonFontGlobal.setLayoutData(new GridData(convertWidthInCharsToPixels(12), SWT.DEFAULT));
		buttonFontGlobal.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				FontDialog dialog = new FontDialog(getShell());
				dialog.setFontList(iniAppearance.getFontGlobal().getFontData());

				fontGlobal = dialog.open();
				if (fontGlobal != null) {
					sampleFontGlobal.setText(fontToString(fontGlobal));
				}
			}
		});

		Label labelFontLog = new Label(groupFont, SWT.NONE);
		labelFontLog.setText("ログ:");
		labelFontLog.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		sampleFontLog = new Text(groupFont, SWT.BORDER | SWT.READ_ONLY);
		sampleFontLog.setBackground(bgColor);
		sampleFontLog.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		fontData = iniAppearance.getFontLog().getFontData()[0];
		sampleFontLog.setText(fontToString(fontData));

		Button buttonFontLog = new Button(groupFont, SWT.PUSH);
		buttonFontLog.setText("選択");
		buttonFontLog.setLayoutData(new GridData(convertWidthInCharsToPixels(12), SWT.DEFAULT));
		buttonFontLog.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				FontDialog dialog = new FontDialog(getShell());
				dialog.setFontList(iniAppearance.getFontLog().getFontData());

				fontLog = dialog.open();
				if (fontLog != null) {
					sampleFontLog.setText(fontToString(fontLog));
				}
			}
		});

		Label labelFontChat = new Label(groupFont, SWT.NONE);
		labelFontChat.setText("チャット入力:");
		labelFontChat.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		sampleFontChat = new Text(groupFont, SWT.BORDER | SWT.READ_ONLY);
		sampleFontChat.setBackground(bgColor);
		sampleFontChat.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		fontData = iniAppearance.getFontChat().getFontData()[0];
		sampleFontChat.setText(fontToString(fontData));

		Button buttonFontChat = new Button(groupFont, SWT.PUSH);
		buttonFontChat.setText("選択");
		buttonFontChat.setLayoutData(new GridData(convertWidthInCharsToPixels(12), SWT.DEFAULT));
		buttonFontChat.addListener(SWT.Selection, new Listener() {
			@Override
			public void handleEvent(Event event) {
				FontDialog dialog = new FontDialog(getShell());
				dialog.setFontList(iniAppearance.getFontChat().getFontData());

				fontChat = dialog.open();
				if (fontChat != null) {
					sampleFontChat.setText(fontToString(fontChat));
				}
			}
		});

		Group groupColorGlobal = new Group(container, SWT.SHADOW_IN);
		groupColorGlobal.setText("全体的な色");
		groupColorGlobal.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, false, false));
		gridLayout = new GridLayout(6, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 8;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		groupColorGlobal.setLayout(gridLayout);

		new Label(groupColorGlobal, SWT.NONE).setText("背景");

		selectorColorBackground = new ColorSelector(groupColorGlobal);
		selectorColorBackground.setColorValue(iniAppearance.getColorBackground().getRGB());
		selectorColorBackground.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorBackground = (RGB) event.getNewValue();
			}
		});

		new Label(groupColorGlobal, SWT.NONE).setText("文字");

		selectorColorForeground = new ColorSelector(groupColorGlobal);
		selectorColorForeground.setColorValue(iniAppearance.getColorForeground().getRGB());
		selectorColorForeground.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorForeground = (RGB) event.getNewValue();
			}
		});

		new Label(groupColorGlobal, SWT.NONE).setText("ログ背景");

		selectorColorLogBackground = new ColorSelector(groupColorGlobal);
		selectorColorLogBackground.setColorValue(iniAppearance.getColorLogBackground().getRGB());
		selectorColorLogBackground.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorLogBackground = (RGB) event.getNewValue();
			}
		});

		Group groupColorLog = new Group(container, SWT.SHADOW_IN);
		groupColorLog.setText("ログの色");
		groupColorLog.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, false, false));
		gridLayout = new GridLayout(8, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 8;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		groupColorLog.setLayout(gridLayout);

		new Label(groupColorLog, SWT.NONE).setText("情報");

		selectorColorLogInfo = new ColorSelector(groupColorLog);
		selectorColorLogInfo.setColorValue(iniAppearance.getColorLogInfo().getRGB());
		selectorColorLogInfo.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorLogInfo = (RGB) event.getNewValue();
			}
		});

		new Label(groupColorLog, SWT.NONE).setText("エラー");

		selectorColorLogError = new ColorSelector(groupColorLog);
		selectorColorLogError.setColorValue(iniAppearance.getColorLogError().getRGB());
		selectorColorLogError.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorLogError = (RGB) event.getNewValue();
			}
		});

		new Label(groupColorLog, SWT.NONE).setText("サーバー");

		selectorColorLogServer = new ColorSelector(groupColorLog);
		selectorColorLogServer.setColorValue(iniAppearance.getColorLogServer().getRGB());
		selectorColorLogServer.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorLogServer = (RGB) event.getNewValue();
			}
		});

		new Label(groupColorLog, SWT.NONE).setText("ルーム");

		selectorColorLogRoom = new ColorSelector(groupColorLog);
		selectorColorLogRoom.setColorValue(iniAppearance.getColorLogRoom().getRGB());
		selectorColorLogRoom.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorLogRoom = (RGB) event.getNewValue();
			}
		});

		Group groupColorChat = new Group(container, SWT.SHADOW_IN);
		groupColorChat.setText("チャットログの色");
		groupColorChat.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, false, false));
		gridLayout = new GridLayout(6, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 8;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		groupColorChat.setLayout(gridLayout);

		new Label(groupColorChat, SWT.NONE).setText("自分");

		selectorColorChatMine = new ColorSelector(groupColorChat);
		selectorColorChatMine.setColorValue(iniAppearance.getColorChatMine().getRGB());
		selectorColorChatMine.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorChatMine = (RGB) event.getNewValue();
			}
		});

		new Label(groupColorChat, SWT.NONE).setText("他の人");

		selectorColorChatOthers = new ColorSelector(groupColorChat);
		selectorColorChatOthers.setColorValue(iniAppearance.getColorChatOthers().getRGB());
		selectorColorChatOthers.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorChatOthers = (RGB) event.getNewValue();
			}
		});

		new Label(groupColorChat, SWT.NONE).setText("プライベート");

		selectorColorChatPrivate = new ColorSelector(groupColorChat);
		selectorColorChatPrivate.setColorValue(iniAppearance.getColorChatPrivate().getRGB());
		selectorColorChatPrivate.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorChatPrivate = (RGB) event.getNewValue();
			}
		});

		Group groupLogRuler = new Group(container, SWT.SHADOW_IN);
		groupLogRuler.setText("ログルーラー");
		groupLogRuler.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, false, false));
		gridLayout = new GridLayout(8, false);
		gridLayout.horizontalSpacing = 5;
		gridLayout.verticalSpacing = 8;
		gridLayout.marginWidth = 4;
		gridLayout.marginHeight = 5;
		groupLogRuler.setLayout(gridLayout);

		new Label(groupLogRuler, SWT.NONE).setText("タイムスタンプ:");

		Label labelTimestampRulerBG = new Label(groupLogRuler, SWT.NONE);
		labelTimestampRulerBG.setText("背景");
		labelTimestampRulerBG.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		selectorColorTimestampRulerBG = new ColorSelector(groupLogRuler);
		selectorColorTimestampRulerBG.setColorValue(iniAppearance.getColorLogTimestampRulerBG().getRGB());
		selectorColorTimestampRulerBG.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorTimestampRulerBG = (RGB) event.getNewValue();
			}
		});

		new Label(groupLogRuler, SWT.NONE).setText("文字");

		selectorColorTimestampRulerFG = new ColorSelector(groupLogRuler);
		selectorColorTimestampRulerFG.setColorValue(iniAppearance.getColorLogTimestampRulerFG().getRGB());
		selectorColorTimestampRulerFG.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorTimestampRulerFG = (RGB) event.getNewValue();
			}
		});

		Label labelTimeStampRulerWidth = new Label(groupLogRuler, SWT.NONE);
		labelTimeStampRulerWidth.setText("幅");
		gridData = new GridData();
		gridData.horizontalIndent = 10;
		labelTimeStampRulerWidth.setLayoutData(gridData);

		spinnerTimestampRulerWidth = new Spinner(groupLogRuler, SWT.BORDER);
		spinnerTimestampRulerWidth.setMinimum(0);
		spinnerTimestampRulerWidth.setSelection(iniAppearance.getLogTimestampRulerWidth());
		spinnerTimestampRulerWidth.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				timestampRulerWidth = spinnerTimestampRulerWidth.getSelection();
			}
		});
		new Label(groupLogRuler, SWT.NONE).setText("ピクセル");

		new Label(groupLogRuler, SWT.NONE).setText("名前:");

		Label labelNameRulerBG = new Label(groupLogRuler, SWT.NONE);
		labelNameRulerBG.setText("背景");
		labelNameRulerBG.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		selectorColorNameRulerBG = new ColorSelector(groupLogRuler);
		selectorColorNameRulerBG.setColorValue(iniAppearance.getColorLogNameRulerBG().getRGB());
		selectorColorNameRulerBG.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorNameRulerBG = (RGB) event.getNewValue();
			}
		});

		new Label(groupLogRuler, SWT.NONE).setText("文字");

		selectorColorNameRulerFG = new ColorSelector(groupLogRuler);
		selectorColorNameRulerFG.setColorValue(iniAppearance.getColorLogNameRulerFG().getRGB());
		selectorColorNameRulerFG.addListener(new IPropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent event) {
				colorNameRulerFG = (RGB) event.getNewValue();
			}
		});

		Label labelNameRulerWidth = new Label(groupLogRuler, SWT.NONE);
		labelNameRulerWidth.setText("幅");
		gridData = new GridData();
		gridData.horizontalIndent = 10;
		labelNameRulerWidth.setLayoutData(gridData);

		spinnerNameRulerWidth = new Spinner(groupLogRuler, SWT.BORDER);
		spinnerNameRulerWidth.setMinimum(0);
		spinnerNameRulerWidth.setSelection(iniAppearance.getLogNameRulerWidth());
		spinnerNameRulerWidth.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				nameRulerWidth = spinnerNameRulerWidth.getSelection();
			}
		});
		new Label(groupLogRuler, SWT.NONE).setText("ピクセル");

		return container;
	}

	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		getApplyButton().setText("適用する");
		getDefaultsButton().setText("デフォルトに戻す");
	}

	private static String fontToString(FontData data) {
		StringBuilder sb = new StringBuilder();
		sb.append(data.getName());
		sb.append(",");
		sb.append(data.getHeight());
		sb.append(",");
		switch (data.getStyle()) {
		case SWT.NORMAL:
			sb.append("Normal");
			break;
		case SWT.BOLD:
			sb.append("Bold");
			break;
		case SWT.ITALIC:
			sb.append("Italic");
			break;
		case SWT.BOLD | SWT.ITALIC:
			sb.append("Bold Italic");
			break;
		}
		return sb.toString();
	}

	@Override
	protected void performDefaults() {
		Display display = getShell().getDisplay();
		Font systemFomt = display.getSystemFont();
		FontData systemFont = systemFomt.getFontData()[0];

		fontGlobal = systemFont;
		fontLog = systemFont;
		fontChat = new FontData(systemFont.getName(), systemFont.getHeight() + 4, systemFont.getStyle());
		sampleFontGlobal.setText(fontToString(fontGlobal));
		sampleFontLog.setText(fontToString(fontLog));
		sampleFontChat.setText(fontToString(fontChat));

		colorBackground = IniAppearance.DEFAULT_COLOR_BACKGROUND;
		colorForeground = IniAppearance.DEFAULT_COLOR_FOREGROUND;
		colorLogBackground = IniAppearance.DEFAULT_COLOR_LOG_BACKGROUND;
		selectorColorBackground.setColorValue(colorBackground);
		selectorColorForeground.setColorValue(colorForeground);
		selectorColorLogBackground.setColorValue(colorLogBackground);

		colorLogInfo = IniAppearance.DEFAULT_COLOR_LOG_INFO;
		colorLogError = IniAppearance.DEFAULT_COLOR_LOG_ERROR;
		colorLogServer = IniAppearance.DEFAULT_COLOR_LOG_SERVER;
		colorLogRoom = IniAppearance.DEFAULT_COLOR_LOG_ROOM;
		colorChatMine = IniAppearance.DEFAULT_COLOR_CHAT_MINE;
		colorChatOthers = IniAppearance.DEFAULT_COLOR_CHAT_OTHERS;
		colorChatPrivate = IniAppearance.DEFAULT_COLOR_CHAT_PRIVATE;
		selectorColorLogInfo.setColorValue(colorLogInfo);
		selectorColorLogError.setColorValue(colorLogError);
		selectorColorLogServer.setColorValue(colorLogServer);
		selectorColorLogRoom.setColorValue(colorLogRoom);
		selectorColorChatMine.setColorValue(colorChatMine);
		selectorColorChatOthers.setColorValue(colorChatOthers);
		selectorColorChatPrivate.setColorValue(colorChatPrivate);

		colorTimestampRulerBG = IniAppearance.DEFAULT_COLOR_LOG_TIMESTAMP_RULER_BG;
		colorTimestampRulerFG = IniAppearance.DEFAULT_COLOR_LOG_TIMESTAMP_RULER_FG;
		timestampRulerWidth = IniAppearance.DEFAULT_TIMESTAMP_RULER_WIDTH;
		colorNameRulerBG = IniAppearance.DEFAULT_COLOR_LOG_NAME_RULER_BG;
		colorNameRulerFG = IniAppearance.DEFAULT_COLOR_LOG_NAME_RULER_FG;
		nameRulerWidth = IniAppearance.DEFAULT_NAME_RULER_WIDTH;
		selectorColorTimestampRulerBG.setColorValue(colorTimestampRulerBG);
		selectorColorTimestampRulerFG.setColorValue(colorTimestampRulerFG);
		spinnerTimestampRulerWidth.setSelection(timestampRulerWidth);
		selectorColorNameRulerBG.setColorValue(colorNameRulerBG);
		selectorColorNameRulerFG.setColorValue(colorNameRulerFG);
		spinnerNameRulerWidth.setSelection(nameRulerWidth);
	}

	@Override
	protected void performApply() {
		reflectValues();
	}

	@Override
	public boolean performOk() {
		reflectValues();
		return super.performOk();
	}

	private void reflectValues() {
		if (!isControlCreated())
			return;

		boolean sizeChanged = false;
		if (fontGlobal != null) {
			application.applyFont(FontType.GLOBAL, fontGlobal);
			sizeChanged = true;
		}
		if (fontLog != null) {
			application.applyFont(FontType.LOG, fontLog);
			sizeChanged = true;
		}
		if (fontChat != null) {
			application.applyFont(FontType.CHAT, fontChat);
			sizeChanged = true;
		}

		if (colorBackground != null) {
			application.applyColor(ColorType.BACKGROUND, colorBackground);
		}
		if (colorForeground != null) {
			application.applyColor(ColorType.FOREGROUND, colorForeground);
		}
		if (colorLogBackground != null) {
			application.applyColor(ColorType.LOG_BACKGROUND, colorLogBackground);
		}

		ArrayList<Color> removedColors = new ArrayList<Color>();
		if (colorLogInfo != null) {
			removedColors.add(iniAppearance.getColorLogInfo());
			iniAppearance.setColorLogInfo(new Color(SwtUtils.DISPLAY, colorLogInfo));
		}
		if (colorLogError != null) {
			removedColors.add(iniAppearance.getColorLogError());
			iniAppearance.setColorLogError(new Color(SwtUtils.DISPLAY, colorLogError));
		}
		if (colorLogServer != null) {
			removedColors.add(iniAppearance.getColorLogServer());
			iniAppearance.setColorLogServer(new Color(SwtUtils.DISPLAY, colorLogServer));
		}
		if (colorLogRoom != null) {
			removedColors.add(iniAppearance.getColorLogRoom());
			iniAppearance.setColorLogRoom(new Color(SwtUtils.DISPLAY, colorLogRoom));
		}
		if (colorChatMine != null) {
			removedColors.add(iniAppearance.getColorChatMine());
			iniAppearance.setColorChatMine(new Color(SwtUtils.DISPLAY, colorChatMine));
		}
		if (colorChatOthers != null) {
			removedColors.add(iniAppearance.getColorChatOthers());
			iniAppearance.setColorChatOthers(new Color(SwtUtils.DISPLAY, colorChatOthers));
		}
		if (colorChatPrivate != null) {
			removedColors.add(iniAppearance.getColorChatPrivate());
			iniAppearance.setColorChatPrivate(new Color(SwtUtils.DISPLAY, colorChatPrivate));
		}
		if (colorTimestampRulerBG != null) {
			removedColors.add(iniAppearance.getColorLogTimestampRulerBG());
			iniAppearance.setColorLogTimestampRulerBG(new Color(SwtUtils.DISPLAY, colorTimestampRulerBG));
		}
		if (colorTimestampRulerFG != null) {
			removedColors.add(iniAppearance.getColorLogTimestampRulerFG());
			iniAppearance.setColorLogTimestampRulerFG(new Color(SwtUtils.DISPLAY, colorTimestampRulerFG));
		}
		if (timestampRulerWidth != null) {
			iniAppearance.setLogTimestampRulerWidth(timestampRulerWidth);
			sizeChanged = true;
		}
		if (colorNameRulerBG != null) {
			removedColors.add(iniAppearance.getColorLogNameRulerBG());
			iniAppearance.setColorLogNameRulerBG(new Color(SwtUtils.DISPLAY, colorNameRulerBG));
		}
		if (colorNameRulerFG != null) {
			removedColors.add(iniAppearance.getColorLogNameRulerFG());
			iniAppearance.setColorLogNameRulerFG(new Color(SwtUtils.DISPLAY, colorNameRulerFG));
		}
		if (nameRulerWidth != null) {
			iniAppearance.setLogNameRulerWidth(nameRulerWidth);
			sizeChanged = true;
		}

		if (sizeChanged || !removedColors.isEmpty())
			application.reflectAppearance();

		for (Color c : removedColors) {
			c.dispose();
		}
	}
}
