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

public class IniFile {

	private HashMap<String, HashMap<String, String>> sectionMap = new LinkedHashMap<String, HashMap<String, String>>();
	private HashMap<String, String> rootSection = new LinkedHashMap<String, String>();
	private boolean hasChanged = false;

	private String iniFilePath;

	public IniFile(String iniPath) throws IOException {
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

	public IniSection getSection(String section) {
		HashMap<String, String> map = Utility.isEmpty(section) ? rootSection : sectionMap.get(section);
		if (map == null) {
			map = new LinkedHashMap<String, String>();
			sectionMap.put(section, map);
		}
		final HashMap<String, String> settings = map;

		return new IniSection() {
			@Override
			public void set(String key, String value) {
				settings.put(key, value);
				hasChanged = true;
			}

			@Override
			public void set(String key, boolean value) {
				set(key, value ? "Yes" : "No");
			}

			@Override
			public void set(String key, int value) {
				set(key, Integer.toString(value));
			}

			@Override
			public void set(String key, int[] values) {
				StringBuilder sb = new StringBuilder();

				for (int i = 0; i < values.length; i++) {
					sb.append(values[i]);
					sb.append(',');
				}
				if (sb.length() > 0)
					sb.deleteCharAt(sb.length() - 1);

				set(key, sb.toString());
			}

			@Override
			public void remove(String key) {
				settings.remove(key);
				hasChanged = true;
			}

			@Override
			public String get(String key, String defaultValue) {
				if (settings.containsKey(key)) {
					return settings.get(key);
				}
				set(key, defaultValue);
				return defaultValue;
			}

			@Override
			public int get(String key, int defaultValue) {
				String string = settings.get(key);
				if (string == null) {
					set(key, Integer.toString(defaultValue));
					return defaultValue;
				}
				try {
					int value = Integer.parseInt(string);
					return value;
				} catch (NumberFormatException e) {
					set(key, Integer.toString(defaultValue));
					return defaultValue;
				}
			}

			@Override
			public boolean get(String key, boolean defaultValue) {
				String string = settings.get(key);
				if (string == null) {
					set(key, defaultValue ? "Yes" : "No");
					return defaultValue;
				}
				return "Yes".equals(string);
			}

			@Override
			public int[] get(String key, int[] defaultValues) {
				try {
					String string = settings.get(key);
					if (string != null) {
						String[] tokens = string.split(",");
						if (tokens.length == defaultValues.length) {
							int[] values = new int[tokens.length];
							for (int i = 0; i < tokens.length; i++) {
								values[i] = Integer.parseInt(tokens[i]);
							}
							return values;
						}
					}
				} catch (NumberFormatException e) {
				}
				set(key, defaultValues);
				return defaultValues;
			}

			@Override
			public Set<Entry<String, String>> entrySet() {
				return settings.entrySet();
			}
		};
	}

	public void saveToIni() throws IOException {
		saveToIni(null);
	}

	public void saveToIni(String newFilePath) throws IOException {
		if (!hasChanged)
			return;

		iniFilePath = Utility.isEmpty(newFilePath) ? iniFilePath : newFilePath;

		FileOutputStream fileOutputStream = new FileOutputStream(iniFilePath, false);
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

		hasChanged = false;
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
		IniFile parser = new IniFile("Test.ini");

		parser.saveToIni("Test2.ini");
	}
}
