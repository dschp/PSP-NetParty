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
package pspnetparty.lib.constants;

import java.nio.charset.Charset;

public class AppConstants {

	private AppConstants() {
	}

	public static final String APP_NAME = "PSP NetParty";
	public static final String VERSION = "0.7.3";

	public static final Charset CHARSET = Charset.forName("UTF-8");
	public static final String NEW_LINE = System.getProperty("line.separator");

	public static final int LOGIN_NAME_LIMIT = 50;
}
