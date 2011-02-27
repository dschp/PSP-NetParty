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
}
