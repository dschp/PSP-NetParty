package pspnetparty.wlan;

public class Win32Handle {
	private byte[] guid;
	private String guidString;
	private long ndisHandle;
	private long pcapHandle;
	
	@Override
	public String toString() {
		return guidString;
	}
}
