package pspnetparty.client.swt.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import pspnetparty.lib.IniSection;

public class IniUserProfile {

	public static final String SECTION = "UserProfile";
	public static final int URL_MAX_LENGTH = 100;

	private static final String USER_NAME = "UserName";
	private static final String URL = "URL";
	private static final String ICON_URL = "IconURL";
	private static final String PROFILE = "Profile";
	private static final String CIRCLES = "Circles";

	private IniSection section;

	public IniUserProfile(IniSection section) {
		this.section = section;

		userName = section.get(USER_NAME, "");

		url = section.get(URL, "");
		iconUrl = section.get(ICON_URL, "");

		String profileEscaped = section.get(PROFILE, "");
		profile = profileEscaped.replace("\\n", "\n");

		circles = new HashSet<String>();
		String circlesEscaped = section.get(CIRCLES, "");
		for (String circle : circlesEscaped.split("\\\\n")) {
			circles.add(circle);
		}
	}

	private String userName;
	private String url;
	private String iconUrl;
	private String profile;
	private HashSet<String> circles;

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
		section.set(USER_NAME, userName);
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
		section.set(URL, url);
	}

	public String getIconUrl() {
		return iconUrl;
	}

	public void setIconUrl(String iconUrl) {
		this.iconUrl = iconUrl;
		section.set(ICON_URL, iconUrl);
	}

	public String getProfile() {
		return profile;
	}

	public void setProfile(String profile) {
		this.profile = profile;
		section.set(PROFILE, profile.replace("\r", "").replace("\n", "\\n"));
	}

	public Set<String> getCircles() {
		return Collections.unmodifiableSet(circles);
	}

	public void setCircles(Set<String> circles) {
		if (this.circles.equals(circles))
			return;

		this.circles.clear();
		this.circles.addAll(circles);

		StringBuilder sb = new StringBuilder();
		for (String circle : circles) {
			sb.append(circle).append("\\n");
		}
		if (sb.length() > 0)
			sb.delete(sb.length() - 2, sb.length());
		section.set(CIRCLES, sb.toString());
	}
}
