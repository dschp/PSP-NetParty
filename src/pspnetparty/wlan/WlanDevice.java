package pspnetparty.wlan;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public interface WlanDevice {
	public String getName();

	public byte[] getHardwareAddress();

	public void open() throws IOException;

	public int capturePacket(ByteBuffer buffer);

	public boolean sendPacket(ByteBuffer buffer);

	public String getSSID();

	public void setSSID(String ssid);

	public boolean scanNetwork();

	public boolean findNetworks(List<WlanNetwork> networkList);

	public void close();

	public static final int CAPTURE_BUFFER_SIZE = 5000;

	public static final WlanDevice NULL = new WlanDevice() {
		private byte[] emptyAddress = new byte[6];

		@Override
		public void open() throws IOException {
		}

		@Override
		public String getName() {
			return "";
		}

		@Override
		public byte[] getHardwareAddress() {
			return emptyAddress;
		}

		@Override
		public String getSSID() {
			return "";
		}

		@Override
		public void setSSID(String ssid) {
		}

		@Override
		public boolean scanNetwork() {
			return false;
		}

		@Override
		public boolean findNetworks(List<WlanNetwork> networkList) {
			return false;
		}

		@Override
		public int capturePacket(ByteBuffer buffer) {
			return -1;
		}

		@Override
		public boolean sendPacket(ByteBuffer buffer) {
			return false;
		}

		@Override
		public void close() {
		}
	};
}
