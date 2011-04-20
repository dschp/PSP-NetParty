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
