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

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import pspnetparty.client.swt.SwtUtils;
import pspnetparty.lib.IniSection;

public class IniAppData {
	private static final String MAIN_WINDOW_BOUNDS = "MainWindowBounds";
	private static final String SEARCH_WINDOW_BOUNDS = "SearchWindowBounds";
	private static final String LOBBY_WINDOW_BOUNDS = "LobbyWindowBounds";
	private static final String LOG_WINDOW_BOUNDS = "LogWindowBounds";

	private static final String SEARCH_SASH_WEIGHT = "SearchSashWeight";
	private static final String LOBBY_SASH_WEIGHT = "LobbySashWeight";
	private static final String ROOM_SASH_WEIGHT = "RoomSashWeight";
	private static final String ROOM_CENTER_SASH_WEIGHT = "RoomCenterSashWeight";
	private static final String ROOM_RIGHT_SASH_WEIGHT = "RoomRightSashWeight";

	private static final String SEARCH_RESULT_TABLE = "SearchResultTable";
	private static final String LOBBY_USER_TABLE = "LobbyUserTable";
	private static final String ROOM_PACKET_MONITOR_TABLE = "RoomPacketMonitorTable";
	private static final String ROOM_SSID_SCAN_TABLE = "RoomSsidScanTable";
	private static final String ROOM_PLAYER_TABLE = "RoomPlayerTable";

	private static final String LAST_LAN_ADAPTER = "LastLanAdapter";

	private static final String SEARCH_HISTORY_ROOM_MASTER = "SearchHistoryRoomMaster";
	private static final String SEARCH_HISTORY_ROOM_MASTER_NG = "SearchHistoryRoomMasterNG";
	private static final String SEARCH_HISTORY_TITLE = "SearchHistoryTitle";
	private static final String SEARCH_HISTORY_TITLE_NG = "SearchHistoryTitleNG";

	private static final String ROOM_SERVER_HISTORY = "RoomServerHistory";
	private static final String ROOM_ADDRESS_HISTORY = "RoomAddressHistory";

	private IniSection section;

	public IniAppData(IniSection section) {
		this.section = section;
	}

	private void restoreShellBounds(String iniItem, Shell shell, int width, int height) {
		String size = section.get(iniItem, "");

		String[] tokens = size.split(",");
		if (tokens.length == 4) {
			try {
				int x = Integer.parseInt(tokens[0]);
				int y = Integer.parseInt(tokens[1]);
				shell.setLocation(x, y);
				width = Integer.parseInt(tokens[2]);
				height = Integer.parseInt(tokens[3]);
			} catch (NumberFormatException e) {
			}
		}
		shell.setSize(width, height);
	}

	private void storeBounds(String iniItem, Rectangle bounds) {
		section.set(iniItem, bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height);
	}

	public void restoreMainWindow(Shell shell) {
		restoreShellBounds(MAIN_WINDOW_BOUNDS, shell, 800, 600);
	}

	public void storeMainWindow(Rectangle bounds) {
		storeBounds(MAIN_WINDOW_BOUNDS, bounds);
	}

	public void restoreSearchWindow(Shell shell) {
		restoreShellBounds(SEARCH_WINDOW_BOUNDS, shell, 700, 400);
	}

	public void storeSearchWindow(Rectangle bounds) {
		storeBounds(SEARCH_WINDOW_BOUNDS, bounds);
	}

	public void restoreLobbyWindow(Shell shell) {
		restoreShellBounds(LOBBY_WINDOW_BOUNDS, shell, 500, 500);
	}

	public void storeLobbyWindow(Rectangle bounds) {
		storeBounds(LOBBY_WINDOW_BOUNDS, bounds);
	}

	public void restoreLogWindow(Shell shell) {
		restoreShellBounds(LOG_WINDOW_BOUNDS, shell, 400, 300);
	}

	public void storeLogWindow(Rectangle bounds) {
		storeBounds(LOG_WINDOW_BOUNDS, bounds);
	}

	public String getLastLanAdapter() {
		return section.get(LAST_LAN_ADAPTER, "");
	}

	public void setLastLanAdapter(String adapter) {
		section.set(LAST_LAN_ADAPTER, adapter);
	}

	public String[] getRoomServerHistory() {
		return section.get(ROOM_SERVER_HISTORY, "").split(",");
	}

	public void setRoomServerHistory(String csvHistory) {
		section.set(ROOM_SERVER_HISTORY, csvHistory);
	}

	public String[] getRoomAddressHistory() {
		return section.get(IniAppData.ROOM_ADDRESS_HISTORY, "").split(",");
	}

	public void setRoomAddressHistory(String csvHistory) {
		section.set(ROOM_ADDRESS_HISTORY, csvHistory);
	}

	public String[] getSearchHistoryRoomMaster() {
		return section.get(IniAppData.SEARCH_HISTORY_ROOM_MASTER, "").split(",");
	}

	public void setSearchHistoryRoomMaster(String csv) {
		section.set(SEARCH_HISTORY_ROOM_MASTER, csv);
	}

	public String[] getSearchHistoryRoomMasterNG() {
		return section.get(IniAppData.SEARCH_HISTORY_ROOM_MASTER_NG, "").split(",");
	}

	public void setSearchHistoryRoomMasterNG(String csv) {
		section.set(SEARCH_HISTORY_ROOM_MASTER_NG, csv);
	}

	public String[] getSearchHistoryTitle() {
		return section.get(IniAppData.SEARCH_HISTORY_TITLE, "").split(",");
	}

	public void setSearchHistoryTitle(String csv) {
		section.set(SEARCH_HISTORY_TITLE, csv);
	}

	public String[] getSearchHistoryTitleNG() {
		return section.get(IniAppData.SEARCH_HISTORY_TITLE_NG, "").split(",");
	}

	public void setSearchHistoryTitleNG(String csv) {
		section.set(SEARCH_HISTORY_TITLE_NG, csv);
	}

	public int[] getArenaSearchSashWeights() {
		return section.get(IniAppData.SEARCH_SASH_WEIGHT, new int[] { 5, 7 });
	}

	public void setArenaSearchSashWeights(int[] weights) {
		section.set(SEARCH_SASH_WEIGHT, weights);
	}

	public int[] getArenaLobbySashFormWeights() {
		return section.get(IniAppData.LOBBY_SASH_WEIGHT, new int[] { 8, 3 });
	}

	public void setArenaLobbySashFormWeights(int[] weights) {
		section.set(LOBBY_SASH_WEIGHT, weights);
	}

	public int[] getRoomSashWeights() {
		return section.get(IniAppData.ROOM_SASH_WEIGHT, new int[] { 4, 7, 4 });
	}

	public void setRoomSashWeights(int[] weights) {
		section.set(ROOM_SASH_WEIGHT, weights);
	}

	public int[] getRoomCenterSashWeights() {
		return section.get(IniAppData.ROOM_CENTER_SASH_WEIGHT, new int[] { 3, 5 });
	}

	public void setRoomCenterSashWeights(int[] weights) {
		section.set(ROOM_CENTER_SASH_WEIGHT, weights);
	}

	public int[] getRoomRightSashWeights() {
		return section.get(IniAppData.ROOM_RIGHT_SASH_WEIGHT, new int[] { 4, 6, 1 });
	}

	public void setRoomRightSashWeights(int[] weights) {
		section.set(ROOM_RIGHT_SASH_WEIGHT, weights);
	}

	public void restoreSearchResultTable(Table table) {
		SwtUtils.deserializeTableColumns(table, section.get(SEARCH_RESULT_TABLE, ""), new int[] { 120, 200, 65, 40, 85, 250, 100 });
	}

	public void storeSearchResultTable(Table table) {
		section.set(SEARCH_RESULT_TABLE, SwtUtils.serializeTableColumns(table));
	}

	public void restoreLobbyUserTableSetting(Table table) {
		SwtUtils.deserializeTableColumns(table, section.get(LOBBY_USER_TABLE, ""), new int[] { 115, 55 });
	}

	public void storeLobbyUserTableSetting(Table table) {
		section.set(LOBBY_USER_TABLE, SwtUtils.serializeTableColumns(table));
	}

	public void restorePacketMonitorTableSetting(Table table) {
		SwtUtils.deserializeTableColumns(table, section.get(ROOM_PACKET_MONITOR_TABLE, ""), new int[] { 25, 100, 100, 80, 80, 100, 100 });
	}

	public void storePacketMonitorTable(Table table) {
		section.set(ROOM_PACKET_MONITOR_TABLE, SwtUtils.serializeTableColumns(table));
	}

	public void restoreSsidScanTableSetting(Table table) {
		SwtUtils.deserializeTableColumns(table, section.get(ROOM_SSID_SCAN_TABLE, ""), new int[] { 150, 40 });
	}

	public void storeSsidScanTable(Table table) {
		section.set(ROOM_SSID_SCAN_TABLE, SwtUtils.serializeTableColumns(table));
	}

	public void restoreRoomPlayerTableSetting(Table table) {
		SwtUtils.deserializeTableColumns(table, section.get(ROOM_PLAYER_TABLE, ""), new int[] { 22, 100, 100, 50 });
	}

	public void storeRoomPlayerTable(Table table) {
		section.set(ROOM_PLAYER_TABLE, SwtUtils.serializeTableColumns(table));
	}
}