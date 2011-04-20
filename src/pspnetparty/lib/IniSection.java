package pspnetparty.lib;

import java.util.Set;
import java.util.Map.Entry;

public interface IniSection {
	public String get(String key, String defaultValue);

	public int get(String key, int defaultValue);

	public boolean get(String key, boolean defaultValue);

	public int[] get(String key, int[] defaultValues);

	public void set(String key, String value);

	public void set(String key, boolean value);

	public void set(String key, int value);

	public void set(String key, int[] values);

	public void remove(String key);

	public Set<Entry<String, String>> entrySet();
}