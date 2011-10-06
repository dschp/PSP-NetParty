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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LobbyUser {

	private String name;
	private LobbyUserState state;
	private String url;
	private String iconUrl;
	private String profile;
	private String profileOneLine;

	private HashSet<String> circles = new HashSet<String>();
	private Set<String> immutableCircles = Collections.unmodifiableSet(circles);

	public LobbyUser(String name, LobbyUserState state) {
		this.name = name;
		this.state = state;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LobbyUserState getState() {
		return state;
	}

	public void setState(LobbyUserState state) {
		this.state = state;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getIconUrl() {
		return iconUrl;
	}

	public void setIconUrl(String iconUrl) {
		this.iconUrl = iconUrl;
	}

	public String getProfile() {
		return profile;
	}

	public void setProfile(String profile) {
		this.profile = profile;
		profileOneLine = null;
	}

	public String getProfileOneLine() {
		if (profileOneLine == null)
			profileOneLine = Utility.multiLineToSingleLine(profile);
		return profileOneLine;
	}

	public void addCircle(String circleName) {
		circles.add(circleName);
	}

	public void removeCircle(String circleName) {
		circles.remove(circleName);
	}

	public Set<String> getCircles() {
		return immutableCircles;
	}
}
