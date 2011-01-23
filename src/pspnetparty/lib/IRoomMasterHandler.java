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

import java.nio.ByteBuffer;

public interface IRoomMasterHandler extends ILogger {
	public void chatReceived(String message);
	public void playerEntered(String player);
	public void playerExited(String player);
	public void pingInformed(String player, int ping);
	public void tunnelPacketReceived(ByteBuffer packet);
	public void roomOpened();
	public void roomClosed();
}
