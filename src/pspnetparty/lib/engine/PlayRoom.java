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
package pspnetparty.lib.engine;

public class PlayRoom {
	private String roomAddress;
	private String serverAddress;
	private String masterName;
	private String title;
	private boolean hasPassword = false;
	private int currentPlayers = 0;
	private int maxPlayers;
	private long createdTime;
	private String description;

	private String sourceServer;

	public PlayRoom(String source, String serverAddress, String masterName, String title, boolean hasPassword, int currentPlayers,
			int maxPlayers, long created) {
		this.serverAddress = serverAddress;
		this.roomAddress = serverAddress + ":" + masterName;
		this.masterName = masterName;
		this.title = title;
		this.hasPassword = hasPassword;
		this.currentPlayers = currentPlayers;
		this.maxPlayers = maxPlayers;
		this.createdTime = created;

		this.sourceServer = source;
	}

	public String getServerAddress() {
		return serverAddress;
	}

	public String getRoomAddress() {
		return roomAddress;
	}

	public String getMasterName() {
		return masterName;
	}

	public void setMasterName(String masterName) {
		this.masterName = masterName;
	}

	public boolean hasPassword() {
		return hasPassword;
	}

	public void setHasPassword(boolean hasPassword) {
		this.hasPassword = hasPassword;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public int getCurrentPlayers() {
		return currentPlayers;
	}

	public void setCurrentPlayers(int currentPlayers) {
		this.currentPlayers = currentPlayers;
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

	public void setMaxPlayers(int maxPlayers) {
		this.maxPlayers = maxPlayers;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public long getCreatedTime() {
		return createdTime;
	}

	public String getSourceServer() {
		return sourceServer;
	}
}
