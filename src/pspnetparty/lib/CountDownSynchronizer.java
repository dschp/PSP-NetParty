package pspnetparty.lib;

public class CountDownSynchronizer {

	private int count;

	public CountDownSynchronizer(int count) {
		this.count = count;
	}

	public synchronized int countDown() {
		if (count == 0)
			return 0;
		return --count;
	}
}
