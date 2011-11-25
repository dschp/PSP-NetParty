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

public class FixedSizeList<T> {

	private T[] buffer;

	private int offset = 0;
	private int position = 0;

	@SuppressWarnings("unchecked")
	public FixedSizeList(int size) {
		this.buffer = (T[]) new Object[size];
	}

	@SuppressWarnings("unchecked")
	public void resize(int newSize) {
		if (newSize == buffer.length)
			return;

		T[] newBuffer = (T[]) new Object[newSize];

		int length = Math.min(size(), newSize);
		for (int i = 0; i < length; i++) {
			newBuffer[i] = get(i);
		}

		buffer = newBuffer;
		offset = 0;
		position = length;
	}

	public T add(T element) {
		T removed = null;

		if (offset == 0) {
			if (position < buffer.length) {
				buffer[position] = element;
				position++;
			} else {
				removed = buffer[0];
				buffer[0] = element;
				offset = 1;
			}
		} else {
			int index = offset;
			removed = buffer[index];
			buffer[index] = element;

			offset++;
			if (offset == buffer.length)
				offset = 0;
		}

		return removed;
	}

	public T get(int index) {
		if (index < 0 || index >= size())
			throw new ArrayIndexOutOfBoundsException();
		int i = (offset + index) % buffer.length;
		return buffer[i];
	}

	public int size() {
		if (offset == 0)
			return position;
		return buffer.length;
	}

	public void clear() {
		for (int i = 0; i < buffer.length; i++) {
			buffer[i] = null;
		}
		offset = position = 0;
	}

	public static void main(String[] args) {
		int testCount = 10;

		FixedSizeList<String> list = new FixedSizeList<String>(testCount / 2);

		for (int i = 0; i < testCount; i++) {
			String item = "Test " + i;
			System.out.println("Adding: " + item);
			String removed = list.add(item);
			if (removed != null) {
				System.out.println("Removed: " + removed);
			}
		}

		for (int i = 0; i < list.size(); i++) {
			System.out.print(list.get(i));
			System.out.print(", ");
		}
	}
}
