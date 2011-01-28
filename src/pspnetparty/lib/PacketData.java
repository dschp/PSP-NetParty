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

import pspnetparty.lib.constants.ProtocolConstants;

public class PacketData {
	
	private ByteBuffer buffer;
	
	public PacketData(ByteBuffer b) {
		buffer = b;
	}
	
	public String getMessage() {
		return Utility.decode(buffer);
	}
	
	public String[] getMessages() {
		String data = Utility.decode(buffer);
		return data.split(ProtocolConstants.MESSAGE_SEPARATOR);
	}
	
	public ByteBuffer getBuffer() {
		return buffer;
	}
	
	public ByteBuffer replaceBuffer(ByteBuffer buffer) {
		ByteBuffer orig = this.buffer;
		this.buffer = buffer;
		return orig;
	}
}
