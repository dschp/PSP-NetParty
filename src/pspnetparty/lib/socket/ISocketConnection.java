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
package pspnetparty.lib.socket;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public interface ISocketConnection {
	public InetSocketAddress getRemoteAddress();

	public InetSocketAddress getLocalAddress();

	public void send(ByteBuffer buffer);

	public void disconnect();

	public boolean isConnected();

	public static final ISocketConnection NULL = new ISocketConnection() {
		@Override
		public boolean isConnected() {
			return false;
		}

		@Override
		public InetSocketAddress getRemoteAddress() {
			return null;
		}

		@Override
		public InetSocketAddress getLocalAddress() {
			return null;
		}

		@Override
		public void send(ByteBuffer buffer) {
		}

		@Override
		public void disconnect() {
		}
	};
}
