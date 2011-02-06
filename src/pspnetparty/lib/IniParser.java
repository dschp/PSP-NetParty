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
package pspnetparty.lib;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;

import pspnetparty.lib.constants.AppConstants;

public class IniParser {

	private HashMap<String, HashMap<String, String>> sectionMap = new LinkedHashMap<String, HashMap<String, String>>();
	private HashMap<String, String> rootSection = new LinkedHashMap<String, String>();

	private String iniFilePath;

	public IniParser(String iniPath) throws IOException {
		if (Utility.isEmpty(iniPath)) {
			throw new IllegalArgumentException();
		}

		iniFilePath = iniPath;

		BufferedReader bufferedReader = null;

		File file = new File(iniPath);
		if (file.isFile()) {
			try {
				FileInputStream fileInputStream = new FileInputStream(file);
				InputStreamReader streamReader = new InputStreamReader(fileInputStream, AppConstants.CHARSET);
				bufferedReader = new BufferedReader(streamReader);

				bufferedReader.mark(1);
				if (bufferedReader.read() != 65279) {
					bufferedReader.reset();
				}

				HashMap<String, String> currentSection = rootSection;

				String line;
				while ((line = bufferedReader.readLine()) != null) {
					line = line.trim();
					if (Utility.isEmpty(line) || line.startsWith("'"))
						continue;

					if (line.startsWith("[") && line.endsWith("]")) {
						String section = line.substring(1, line.length() - 1);
						currentSection = sectionMap.get(section);

						if (currentSection == null) {
							currentSection = new LinkedHashMap<String, String>();
							sectionMap.put(section, currentSection);
						}
					} else {
						String[] keyValue = line.split("=", 2);
						if (keyValue.length == 2) {
							String key = keyValue[0].trim();
							String value = keyValue[1].trim();
							currentSection.put(key, value);
						}
					}
				}

			} catch (IOException e) {
				throw e;
			} finally {
				if (bufferedReader != null)
					bufferedReader.close();
			}
		} else {
			FileWriter writer = new FileWriter(iniPath, false);
			writer.close();
		}
	}

	public Section getSection(String section) {
		HashMap<String, String> map = Utility.isEmpty(section) ? rootSection : sectionMap.get(section);
		if (map == null) {
			map = new LinkedHashMap<String, String>();
			sectionMap.put(section, map);
		}
		final HashMap<String, String> settings = map;

		return new Section() {
			@Override
			public void set(String key, String value) {
				settings.put(key, value);
			}
			
			@Override
			public void set(String key, boolean value) {
				settings.put(key, value ? "Yes" : "No");
			}

			@Override
			public void remove(String key) {
				settings.remove(key);
			}

			@Override
			public String get(String key, String defaultValue) {
				if (settings.containsKey(key)) {
					return settings.get(key);
				}
				settings.put(key, defaultValue);
				return defaultValue;
			}

			@Override
			public int get(String key, int defaultValue) {
				String string = settings.get(key);
				if (string == null) {
					settings.put(key, Integer.toString(defaultValue));
					return defaultValue;
				}
				try {
					int value = Integer.parseInt(string);
					return value;
				} catch (NumberFormatException e) {
					settings.put(key, Integer.toString(defaultValue));
					return defaultValue;
				}
			}
			
			@Override
			public boolean get(String key, boolean defaultValue) {
				String string = settings.get(key);
				if (string == null) {
					settings.put(key, defaultValue ? "Yes" : "No");
					return defaultValue;
				}
				return "Yes".equals(string);
			}

			@Override
			public Set<Entry<String, String>> entrySet() {
				return settings.entrySet();
			}
		};
	}

	public void saveToIni() throws IOException {
		saveToIni(iniFilePath);
	}

	public void saveToIni(String newFilePath) throws IOException {
		String filePath = Utility.isEmpty(newFilePath) ? iniFilePath : newFilePath;

		FileOutputStream fileOutputStream = new FileOutputStream(filePath, false);
		OutputStreamWriter streamWriter = new OutputStreamWriter(fileOutputStream, AppConstants.CHARSET);

		BufferedWriter bufferedWriter = new BufferedWriter(streamWriter);
		PrintWriter printWriter = new PrintWriter(bufferedWriter);

		appendSettings(printWriter, rootSection);

		for (Entry<String, HashMap<String, String>> entry : sectionMap.entrySet()) {
			printWriter.println("[" + entry.getKey() + "]");
			appendSettings(printWriter, entry.getValue());
			printWriter.println();
		}

		printWriter.close();
	}

	private void appendSettings(PrintWriter writer, HashMap<String, String> props) {
		for (Entry<String, String> entry : props.entrySet()) {
			String value = entry.getValue();
			if (value == null)
				value = "";
			writer.printf("%s=%s", entry.getKey(), value);
			writer.println();
		}
	}

	public static void main(String[] args) throws Exception {
		IniParser parser = new IniParser("Test.ini");

		parser.saveToIni("Test2.ini");
	}

	public interface Section {
		public String get(String key, String defaultValue);

		public int get(String key, int defaultValue);

		public boolean get(String key, boolean defaultValue);

		public void set(String key, String value);
		public void set(String key, boolean value);

		public void remove(String key);

		public Set<Entry<String, String>> entrySet();
	}
}
