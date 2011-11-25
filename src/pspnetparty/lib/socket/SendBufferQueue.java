package pspnetparty.lib.socket;

import java.nio.ByteBuffer;
import java.util.LinkedList;

import pspnetparty.lib.Utility;

public class SendBufferQueue<T> {

	public class Allotment {
		private T attachment;
		private ByteBuffer slice;
		private ByteBuffer parent;

		public T getAttachment() {
			return attachment;
		}

		public ByteBuffer getBuffer() {
			return slice;
		}

		@Override
		public String toString() {
			return slice.toString();
		}
	}

	private static final boolean DEBUG = false;

	private ByteBuffer queueingBuffer;
	private LinkedList<Allotment> sliceBufferList = new LinkedList<Allotment>();
	private int firstQueuePosition = -1;
	private Allotment lastRetrievedAllotment;

	public SendBufferQueue(int initialBufferSize) {
		queueingBuffer = ByteBuffer.allocateDirect(initialBufferSize);
		queueingBuffer.limit(0);
	}

	private void replaceNewBuffer(int lengthRequired) {
		int bufferSize = Math.max(lengthRequired, queueingBuffer.capacity() * 2);

		queueingBuffer = ByteBuffer.allocateDirect(bufferSize);
		lastRetrievedAllotment = null;
		firstQueuePosition = 0;
	}

	private ByteBuffer sliceBuffer(int length) {
		int slicePosition = queueingBuffer.limit();
		int sliceLimit = slicePosition + length;

		if (DEBUG) {
			System.out.println("Request: position=" + slicePosition + ", limit=" + sliceLimit + ", capacity=" + queueingBuffer.capacity());
			System.out.println("firstQueuePosition=" + firstQueuePosition);
		}

		if (firstQueuePosition == 0) {
			if (sliceLimit <= queueingBuffer.capacity()) {
				queueingBuffer.position(slicePosition);
				queueingBuffer.limit(sliceLimit);
			} else {
				replaceNewBuffer(length);
				queueingBuffer.limit(length);
			}
		} else if (sliceLimit <= firstQueuePosition) {
			queueingBuffer.position(slicePosition);
			queueingBuffer.limit(sliceLimit);
		} else if (slicePosition <= firstQueuePosition) {
			replaceNewBuffer(length);
			queueingBuffer.limit(length);
		} else if (sliceLimit <= queueingBuffer.capacity()) {
			queueingBuffer.position(slicePosition);
			queueingBuffer.limit(sliceLimit);
		} else if (firstQueuePosition >= length) {
			queueingBuffer.position(0);
			queueingBuffer.limit(length);
		} else {
			replaceNewBuffer(length);
			queueingBuffer.limit(length);
		}

		if (DEBUG) {
			System.out.println("Result: position=" + queueingBuffer.position() + ", limit=" + queueingBuffer.limit());
		}

		return queueingBuffer.slice();
	}

	public synchronized void queue(ByteBuffer buffer, boolean prependSizeHeader, T attachment) {
		if (DEBUG) {
			System.out.println("Queue: " + Utility.toHexString(buffer));
		}

		ByteBuffer slice;
		if (prependSizeHeader) {
			int length = buffer.remaining();
			int totalLength = length + IProtocol.HEADER_BYTE_SIZE;

			slice = sliceBuffer(totalLength);
			slice.putInt(length);
			slice.put(buffer);
		} else {
			int length = buffer.remaining();
			slice = sliceBuffer(length);
			slice.put(buffer);
		}
		slice.position(0);

		Allotment allot = new Allotment();
		allot.attachment = attachment;
		allot.slice = slice;
		allot.parent = queueingBuffer;
		sliceBufferList.add(allot);

		if (DEBUG) {
			System.out.println(sliceBufferList);
		}
	}

	public synchronized Allotment poll() {
		Allotment allot = sliceBufferList.poll();

		if (allot == null) {
			queueingBuffer.limit(0);
			firstQueuePosition = 0;
		} else if (lastRetrievedAllotment != null && lastRetrievedAllotment.parent == queueingBuffer) {
			int lastBufferLength = lastRetrievedAllotment.slice.capacity();
			firstQueuePosition += lastBufferLength;
			if (firstQueuePosition >= queueingBuffer.capacity())
				firstQueuePosition = lastBufferLength;
		}

		if (DEBUG) {
			if (allot != null)
				System.out.println("Poll: " + Utility.toHexString(allot.slice));
		}

		lastRetrievedAllotment = allot;
		return lastRetrievedAllotment;
	}

	public boolean isEmpty() {
		return sliceBufferList.isEmpty();
	}
}
