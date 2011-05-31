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

import pspnetparty.lib.IniSection;

public class IniChatTextPresets {
	public static final String SECTION_NAME = "ChatTextPresets";

	private static final String F1 = "F1";
	private static final String F2 = "F2";
	private static final String F3 = "F3";
	private static final String F4 = "F4";
	private static final String F5 = "F5";
	private static final String F6 = "F6";
	private static final String F7 = "F7";
	private static final String F8 = "F8";
	private static final String F9 = "F9";
	private static final String F10 = "F10";
	private static final String F11 = "F11";
	private static final String F12 = "F12";

	private IniSection section;

	public IniChatTextPresets(IniSection section) {
		this.section = section;

		presetF1 = section.get(F1, "");
		presetF2 = section.get(F2, "");
		presetF3 = section.get(F3, "");
		presetF4 = section.get(F4, "");
		presetF5 = section.get(F5, "");
		presetF6 = section.get(F6, "");
		presetF7 = section.get(F7, "");
		presetF8 = section.get(F8, "");
		presetF9 = section.get(F9, "");
		presetF10 = section.get(F10, "");
		presetF11 = section.get(F11, "");
		presetF12 = section.get(F12, "");
	}

	private String presetF1;
	private String presetF2;
	private String presetF3;
	private String presetF4;
	private String presetF5;
	private String presetF6;
	private String presetF7;
	private String presetF8;
	private String presetF9;
	private String presetF10;
	private String presetF11;
	private String presetF12;

	public String getPresetF1() {
		return presetF1;
	}

	public void setPresetF1(String presetF1) {
		this.presetF1 = presetF1;
		section.set(F1, presetF1);
	}

	public String getPresetF2() {
		return presetF2;
	}

	public void setPresetF2(String presetF2) {
		this.presetF2 = presetF2;
		section.set(F2, presetF2);
	}

	public String getPresetF3() {
		return presetF3;
	}

	public void setPresetF3(String presetF3) {
		this.presetF3 = presetF3;
		section.set(F3, presetF3);
	}

	public String getPresetF4() {
		return presetF4;
	}

	public void setPresetF4(String presetF4) {
		this.presetF4 = presetF4;
		section.set(F4, presetF4);
	}

	public String getPresetF5() {
		return presetF5;
	}

	public void setPresetF5(String presetF5) {
		this.presetF5 = presetF5;
		section.set(F5, presetF5);
	}

	public String getPresetF6() {
		return presetF6;
	}

	public void setPresetF6(String presetF6) {
		this.presetF6 = presetF6;
		section.set(F6, presetF6);
	}

	public String getPresetF7() {
		return presetF7;
	}

	public void setPresetF7(String presetF7) {
		this.presetF7 = presetF7;
		section.set(F7, presetF7);
	}

	public String getPresetF8() {
		return presetF8;
	}

	public void setPresetF8(String presetF8) {
		this.presetF8 = presetF8;
		section.set(F8, presetF8);
	}

	public String getPresetF9() {
		return presetF9;
	}

	public void setPresetF9(String presetF9) {
		this.presetF9 = presetF9;
		section.set(F9, presetF9);
	}

	public String getPresetF10() {
		return presetF10;
	}

	public void setPresetF10(String presetF10) {
		this.presetF10 = presetF10;
		section.set(F10, presetF10);
	}

	public String getPresetF11() {
		return presetF11;
	}

	public void setPresetF11(String presetF11) {
		this.presetF11 = presetF11;
		section.set(F11, presetF11);
	}

	public String getPresetF12() {
		return presetF12;
	}

	public void setPresetF12(String presetF12) {
		this.presetF12 = presetF12;
		section.set(F12, presetF12);
	}
}
