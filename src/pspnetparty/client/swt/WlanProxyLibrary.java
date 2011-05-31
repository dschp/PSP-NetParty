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
package pspnetparty.client.swt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.IDialogConstants;

import pspnetparty.lib.Utility;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.PacketData;
import pspnetparty.lib.socket.TransportLayer;
import pspnetparty.wlan.WlanDevice;
import pspnetparty.wlan.WlanLibrary;
import pspnetparty.wlan.WlanNetwork;

public class WlanProxyLibrary implements WlanLibrary {

	private IPlayClient application;
	private boolean isSSIDEnabled;

	private ProxyProtocol protocol = new ProxyProtocol();
	private ISocketConnection activeConnection = ISocketConnection.NULL;

	private String ssid;
	private ArrayList<WlanNetwork> networks = new ArrayList<WlanNetwork>();

	private final Object captureLock = new Object();
	private ByteBuffer captureBuffer = ByteBuffer.allocate(1);
	private ByteBuffer sendBuffer = ByteBuffer.allocate(1);

	public WlanProxyLibrary(IPlayClient application) {
		this.application = application;
	}

	@Override
	public String getName() {
		return "WlanProxy";
	}

	@Override
	public void findDevices(List<WlanDevice> devices) {
		if (application.getRoomWindow() == null)
			return;
		ConnectAddressDialog dialog = new ConnectAddressDialog(application.getRoomWindow().getShell());
		switch (dialog.open()) {
		case IDialogConstants.OK_ID:
			try {
				InetSocketAddress address = new InetSocketAddress(dialog.getHostName(), dialog.getPort());
				ProxyWlanDevice dev = new ProxyWlanDevice(dialog.getTransport(), address);
				devices.add(dev);
			} catch (Exception e) {
			}
			break;
		case IDialogConstants.CANCEL_ID:
			break;
		}
	}

	@Override
	public boolean isSSIDEnabled() {
		return isSSIDEnabled;
	}

	private class ProxyWlanDevice implements WlanDevice {
		private TransportLayer transport;
		private InetSocketAddress address;

		public ProxyWlanDevice(TransportLayer transport, InetSocketAddress address) {
			this.transport = transport;
			this.address = address;
		}

		@Override
		public String getName() {
			StringBuilder sb = new StringBuilder();
			sb.append("(");
			switch (transport) {
			case TCP:
				sb.append("TCP");
				break;
			case UDP:
				sb.append("UDP");
				break;
			}
			sb.append(") ");
			sb.append(address);
			return sb.toString();
		}

		@Override
		public byte[] getHardwareAddress() {
			return null;
		}

		@Override
		public void open() throws IOException {
			isSSIDEnabled = false;

			switch (transport) {
			case TCP:
				application.connectTcp(address, protocol);
				break;
			case UDP:
				application.connectUdp(address, protocol);
				break;
			}

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				throw new IOException(e);
			}
		}

		@Override
		public int capturePacket(ByteBuffer buffer) {
			synchronized (captureLock) {
				int remaining = captureBuffer.remaining();
				if (remaining > 0) {
					System.out.println("Capture2: " + captureBuffer);
					//System.out.println("Capture3: " + buffer);
					buffer.put(captureBuffer);
					return remaining;
				}
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
			return 0;
		}

		@Override
		public boolean sendPacket(ByteBuffer buffer) {
			if (sendBuffer.capacity() <= buffer.capacity()) {
				sendBuffer = ByteBuffer.allocateDirect(buffer.capacity() * 2);
			} else {
				sendBuffer.clear();
			}

			sendBuffer.put(WlanProxyConstants.BYTE_COMMAND_PACKET);
			sendBuffer.put(buffer);
			sendBuffer.flip();

			activeConnection.send(sendBuffer);
			return true;
		}

		@Override
		public String getSSID() {
			return ssid;
		}

		@Override
		public void setSSID(String ssid) {
			WlanProxyLibrary.this.ssid = ssid;
			activeConnection.send(WlanProxyConstants.COMMAND_SET_SSID + ssid);
		}

		@Override
		public boolean scanNetwork() {
			activeConnection.send(String.valueOf(WlanProxyConstants.COMMAND_SCAN_NETWORK));
			return true;
		}

		@Override
		public boolean findNetworks(List<WlanNetwork> networkList) {
			activeConnection.send(String.valueOf(WlanProxyConstants.COMMAND_FIND_NETWORK));
			networkList.addAll(networks);
			return true;
		}

		@Override
		public void close() {
			activeConnection.disconnect();
			activeConnection = ISocketConnection.NULL;
		}
	}

	private class ProxyProtocol implements IProtocol {
		@Override
		public void log(String message) {
			application.getLogWindow().appendLog(message, true, true);
		}

		@Override
		public String getProtocol() {
			return WlanProxyConstants.PROTOCOL;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			application.getLogWindow().appendLog("プロキシに接続しました: " + connection.getRemoteAddress(), true, false);

			activeConnection = connection;

			ProxyProtocolDriver driver = new ProxyProtocolDriver();
			driver.connection = connection;

			return driver;
		}
	}

	private class ProxyProtocolDriver implements IProtocolDriver {
		private ISocketConnection connection;

		@Override
		public ISocketConnection getConnection() {
			return connection;
		}

		@Override
		public boolean process(PacketData data) {
			ByteBuffer buffer = data.getBuffer();
			application.getLogWindow().appendLog(buffer.toString(), true, false);

			int origLimit = buffer.limit();
			buffer.limit(1);
			char c = Utility.decode(buffer).charAt(0);
			buffer.limit(origLimit);

			switch (c) {
			case WlanProxyConstants.COMMAND_PACKET:
				synchronized (captureLock) {
					if (captureBuffer.capacity() <= buffer.capacity()) {
						captureBuffer = ByteBuffer.allocateDirect(buffer.capacity() * 2);
					} else {
						captureBuffer.clear();
					}

					System.out.println("Capture1: " + buffer);
					captureBuffer.put(buffer);
					captureBuffer.flip();
				}
				break;
			case WlanProxyConstants.COMMAND_GET_SSID: {
				String ssid = Utility.decode(buffer);
				WlanProxyLibrary.this.ssid = ssid;
				break;
			}
			case WlanProxyConstants.COMMAND_FIND_NETWORK: {
				networks.clear();

				String message = Utility.decode(buffer);
				// application.getLogWindow().appendLog(message, true, false);
				for (String row : message.split("\f")) {
					String[] values = row.split("\t");
					if (values.length != 2)
						continue;

					try {
						String ssid = values[0];
						int rssi = Integer.parseInt(values[1]);

						WlanNetwork network = new WlanNetwork(ssid, rssi);
						networks.add(network);
					} catch (NumberFormatException e) {
					}
				}
				break;
			}
			case WlanProxyConstants.COMMAND_SSID_FEATURE_ENABLED:
				isSSIDEnabled = true;
				break;
			case WlanProxyConstants.COMMAND_SSID_FEATURE_DISABLED:
				isSSIDEnabled = false;
				break;
			default:
				return false;
			}

			return true;
		}

		@Override
		public void connectionDisconnected() {
			application.getLogWindow().appendLog("プロキシと切断しました: " + connection.getRemoteAddress(), true, false);
		}

		@Override
		public void errorProtocolNumber(String number) {
		}
	}
}
