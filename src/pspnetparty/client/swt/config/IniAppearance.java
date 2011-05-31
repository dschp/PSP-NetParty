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

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;

import pspnetparty.client.swt.SwtUtils;
import pspnetparty.lib.IniSection;

public class IniAppearance {
	public static final String SECTION = "Appearance";

	private static final String FONT_GLOBAL = "FontGlobal";
	private static final String FONT_LOG = "FontLog";
	private static final String FONT_CHAT = "FontChat";

	private static final String COLOR_BACKGROUND = "ColorBackground";
	private static final String COLOR_FOREGROUND = "ColorForeground";

	private static final String LOG_TIMESTAMP_RULER_WIDTH = "LogTimestampRulerWidth";
	private static final String LOG_NAME_RULER_WIDTH = "LogNameRulerWidth";
	private static final String COLOR_LOG_TIMESTAMP_RULER_BG = "ColorLogTimestampRulerBG";
	private static final String COLOR_LOG_TIMESTAMP_RULER_FG = "ColorLogTimestampRulerFG";
	private static final String COLOR_LOG_NAME_RULER_BG = "ColorLogNameRulerBG";
	private static final String COLOR_LOG_NAME_RULER_FG = "ColorLogNameRulerFG";

	private static final String COLOR_LOG_BACKGROUND = "ColorLogBackground";
	private static final String COLOR_LOG_INFO = "ColorLogInfo";
	private static final String COLOR_LOG_ERROR = "ColorLogError";
	private static final String COLOR_LOG_APP = "ColorLogApp";
	private static final String COLOR_LOG_SERVER = "ColorLogServer";
	private static final String COLOR_LOG_ROOM = "ColorLogRoom";

	private static final String COLOR_CHAT_MINE = "ColorChatMine";
	private static final String COLOR_CHAT_OTHERS = "ColorChatOthers";
	private static final String COLOR_CHAT_PRIVATE = "ColorChatPrivate";

	public static final RGB DEFAULT_COLOR_BACKGROUND = new RGB(255, 255, 255);
	public static final RGB DEFAULT_COLOR_FOREGROUND = new RGB(0, 0, 0);

	public static final RGB DEFAULT_COLOR_LOG_TIMESTAMP_RULER_BG = new RGB(230, 230, 230);
	public static final RGB DEFAULT_COLOR_LOG_TIMESTAMP_RULER_FG = new RGB(0, 0, 100);
	public static final int DEFAULT_TIMESTAMP_RULER_WIDTH = 55;
	public static final RGB DEFAULT_COLOR_LOG_NAME_RULER_BG = new RGB(255, 230, 230);
	public static final RGB DEFAULT_COLOR_LOG_NAME_RULER_FG = new RGB(20, 0, 0);
	public static final int DEFAULT_NAME_RULER_WIDTH = 70;

	public static final RGB DEFAULT_COLOR_LOG_BACKGROUND = new RGB(255, 255, 255);
	public static final RGB DEFAULT_COLOR_LOG_INFO = new RGB(128, 128, 128);
	public static final RGB DEFAULT_COLOR_LOG_ERROR = new RGB(255, 0, 0);
	public static final RGB DEFAULT_COLOR_LOG_APP = new RGB(0, 100, 0);
	public static final RGB DEFAULT_COLOR_LOG_SERVER = new RGB(0, 0, 255);
	public static final RGB DEFAULT_COLOR_LOG_ROOM = new RGB(128, 0, 128);
	public static final RGB DEFAULT_COLOR_CHAT_MINE = new RGB(200, 70, 0);
	public static final RGB DEFAULT_COLOR_CHAT_OTHERS = new RGB(0, 0, 0);
	public static final RGB DEFAULT_COLOR_CHAT_PRIVATE = new RGB(0, 128, 0);

	private IniSection section;

	public IniAppearance(IniSection section) {
		this.section = section;

		FontData[] data = SwtUtils.DISPLAY.getSystemFont().getFontData();
		fontGlobal = SwtUtils.loadFont(section.get(FONT_GLOBAL, ""), data);
		fontLog = SwtUtils.loadFont(section.get(FONT_LOG, ""), data);
		for (FontData d : data)
			d.setHeight(d.getHeight() + 4);
		fontChat = SwtUtils.loadFont(section.get(FONT_CHAT, ""), data);

		colorBackground = SwtUtils.loadColor(section.get(COLOR_BACKGROUND, ""), DEFAULT_COLOR_BACKGROUND);
		colorForeground = SwtUtils.loadColor(section.get(COLOR_FOREGROUND, ""), DEFAULT_COLOR_FOREGROUND);

		logTimestampRulerWidth = section.get(LOG_TIMESTAMP_RULER_WIDTH, DEFAULT_TIMESTAMP_RULER_WIDTH);
		logNameRulerWidth = section.get(LOG_NAME_RULER_WIDTH, DEFAULT_NAME_RULER_WIDTH);
		colorLogTimestampRulerBG = SwtUtils.loadColor(section.get(COLOR_LOG_TIMESTAMP_RULER_BG, ""), DEFAULT_COLOR_LOG_TIMESTAMP_RULER_BG);
		colorLogTimestampRulerFG = SwtUtils.loadColor(section.get(COLOR_LOG_TIMESTAMP_RULER_FG, ""), DEFAULT_COLOR_LOG_TIMESTAMP_RULER_FG);
		colorLogNameRulerBG = SwtUtils.loadColor(section.get(COLOR_LOG_NAME_RULER_BG, ""), DEFAULT_COLOR_LOG_NAME_RULER_BG);
		colorLogNameRulerFG = SwtUtils.loadColor(section.get(COLOR_LOG_NAME_RULER_FG, ""), DEFAULT_COLOR_LOG_NAME_RULER_FG);

		colorLogBackground = SwtUtils.loadColor(section.get(COLOR_LOG_BACKGROUND, ""), DEFAULT_COLOR_LOG_BACKGROUND);
		colorLogInfo = SwtUtils.loadColor(section.get(COLOR_LOG_INFO, ""), DEFAULT_COLOR_LOG_INFO);
		colorLogError = SwtUtils.loadColor(section.get(COLOR_LOG_ERROR, ""), DEFAULT_COLOR_LOG_ERROR);
		colorLogApp = SwtUtils.loadColor(section.get(COLOR_LOG_APP, ""), DEFAULT_COLOR_LOG_APP);
		colorLogServer = SwtUtils.loadColor(section.get(COLOR_LOG_SERVER, ""), DEFAULT_COLOR_LOG_SERVER);
		colorLogRoom = SwtUtils.loadColor(section.get(COLOR_LOG_ROOM, ""), DEFAULT_COLOR_LOG_ROOM);
		colorChatMine = SwtUtils.loadColor(section.get(COLOR_CHAT_MINE, ""), DEFAULT_COLOR_CHAT_MINE);
		colorChatOthers = SwtUtils.loadColor(section.get(COLOR_CHAT_OTHERS, ""), DEFAULT_COLOR_CHAT_OTHERS);
		colorChatPrivate = SwtUtils.loadColor(section.get(COLOR_CHAT_PRIVATE, ""), DEFAULT_COLOR_CHAT_PRIVATE);
	}

	private Font fontGlobal;
	private Font fontLog;
	private Font fontChat;

	private Color colorBackground;
	private Color colorForeground;

	private int logTimestampRulerWidth;
	private int logNameRulerWidth;
	private Color colorLogTimestampRulerBG;
	private Color colorLogTimestampRulerFG;
	private Color colorLogNameRulerBG;
	private Color colorLogNameRulerFG;

	private Color colorLogBackground;
	private Color colorLogInfo;
	private Color colorLogError;
	private Color colorLogApp;
	private Color colorLogServer;
	private Color colorLogRoom;

	private Color colorChatMine;
	private Color colorChatOthers;
	private Color colorChatPrivate;

	public Font getFontGlobal() {
		return fontGlobal;
	}

	public void setFontGlobal(Font fontGlobal) {
		this.fontGlobal = fontGlobal;
		section.set(FONT_GLOBAL, SwtUtils.fontToString(fontGlobal));
	}

	public Font getFontLog() {
		return fontLog;
	}

	public void setFontLog(Font fontLog) {
		this.fontLog = fontLog;
		section.set(FONT_LOG, SwtUtils.fontToString(fontLog));
	}

	public Font getFontChat() {
		return fontChat;
	}

	public void setFontChat(Font fontChat) {
		this.fontChat = fontChat;
		section.set(FONT_CHAT, SwtUtils.fontToString(fontChat));
	}

	public Color getColorBackground() {
		return colorBackground;
	}

	public void setColorBackground(Color colorBackground) {
		this.colorBackground = colorBackground;
		section.set(COLOR_BACKGROUND, SwtUtils.colorToString(colorBackground));
	}

	public Color getColorForeground() {
		return colorForeground;
	}

	public void setColorForeground(Color colorForeground) {
		this.colorForeground = colorForeground;
		section.set(COLOR_FOREGROUND, SwtUtils.colorToString(colorForeground));
	}

	public int getLogTimestampRulerWidth() {
		return logTimestampRulerWidth;
	}

	public void setLogTimestampRulerWidth(int logTimestampRulerWidth) {
		this.logTimestampRulerWidth = logTimestampRulerWidth;
		section.set(LOG_TIMESTAMP_RULER_WIDTH, logTimestampRulerWidth);
	}

	public int getLogNameRulerWidth() {
		return logNameRulerWidth;
	}

	public void setLogNameRulerWidth(int logNameRulerWidth) {
		this.logNameRulerWidth = logNameRulerWidth;
		section.set(LOG_NAME_RULER_WIDTH, logNameRulerWidth);
	}

	public Color getColorLogTimestampRulerBG() {
		return colorLogTimestampRulerBG;
	}

	public void setColorLogTimestampRulerBG(Color colorLogTimestampRulerBG) {
		this.colorLogTimestampRulerBG = colorLogTimestampRulerBG;
		section.set(COLOR_LOG_TIMESTAMP_RULER_BG, SwtUtils.colorToString(colorLogTimestampRulerBG));
	}

	public Color getColorLogTimestampRulerFG() {
		return colorLogTimestampRulerFG;
	}

	public void setColorLogTimestampRulerFG(Color colorLogTimestampRulerFG) {
		this.colorLogTimestampRulerFG = colorLogTimestampRulerFG;
		section.set(COLOR_LOG_TIMESTAMP_RULER_FG, SwtUtils.colorToString(colorLogTimestampRulerFG));
	}

	public Color getColorLogNameRulerBG() {
		return colorLogNameRulerBG;
	}

	public void setColorLogNameRulerBG(Color colorLogNameRulerBG) {
		this.colorLogNameRulerBG = colorLogNameRulerBG;
		section.set(COLOR_LOG_NAME_RULER_BG, SwtUtils.colorToString(colorLogNameRulerBG));
	}

	public Color getColorLogNameRulerFG() {
		return colorLogNameRulerFG;
	}

	public void setColorLogNameRulerFG(Color colorLogNameRulerFG) {
		this.colorLogNameRulerFG = colorLogNameRulerFG;
		section.set(COLOR_LOG_NAME_RULER_FG, SwtUtils.colorToString(colorLogNameRulerFG));
	}

	public Color getColorLogBackground() {
		return colorLogBackground;
	}

	public void setColorLogBackground(Color colorLogBackground) {
		this.colorLogBackground = colorLogBackground;
		section.set(COLOR_LOG_BACKGROUND, SwtUtils.colorToString(colorLogBackground));
	}

	public Color getColorLogInfo() {
		return colorLogInfo;
	}

	public void setColorLogInfo(Color colorLogInfo) {
		this.colorLogInfo = colorLogInfo;
		section.set(COLOR_LOG_INFO, SwtUtils.colorToString(colorLogInfo));
	}

	public Color getColorLogError() {
		return colorLogError;
	}

	public void setColorLogError(Color colorLogError) {
		this.colorLogError = colorLogError;
		section.set(COLOR_LOG_ERROR, SwtUtils.colorToString(colorLogError));
	}

	public Color getColorLogApp() {
		return colorLogApp;
	}

	public void setColorLogApp(Color colorLogApp) {
		this.colorLogApp = colorLogApp;
		section.set(COLOR_LOG_APP, SwtUtils.colorToString(colorLogApp));
	}

	public Color getColorLogServer() {
		return colorLogServer;
	}

	public void setColorLogServer(Color colorLogServer) {
		this.colorLogServer = colorLogServer;
		section.set(COLOR_LOG_SERVER, SwtUtils.colorToString(colorLogServer));
	}

	public Color getColorLogRoom() {
		return colorLogRoom;
	}

	public void setColorLogRoom(Color colorLogRoom) {
		this.colorLogRoom = colorLogRoom;
		section.set(COLOR_LOG_ROOM, SwtUtils.colorToString(colorLogRoom));
	}

	public Color getColorChatMine() {
		return colorChatMine;
	}

	public void setColorChatMine(Color colorChatMine) {
		this.colorChatMine = colorChatMine;
		section.set(COLOR_CHAT_MINE, SwtUtils.colorToString(colorChatMine));
	}

	public Color getColorChatOthers() {
		return colorChatOthers;
	}

	public void setColorChatOthers(Color colorChatOthers) {
		this.colorChatOthers = colorChatOthers;
		section.set(COLOR_CHAT_OTHERS, SwtUtils.colorToString(colorChatOthers));
	}

	public Color getColorChatPrivate() {
		return colorChatPrivate;
	}

	public void setColorChatPrivate(Color colorChatPrivate) {
		this.colorChatPrivate = colorChatPrivate;
		section.set(COLOR_CHAT_PRIVATE, SwtUtils.colorToString(colorChatPrivate));
	}
}