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
package pspnetparty.client.swt.app;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

import pspnetparty.client.swt.WlanProxyConstants;
import pspnetparty.client.swt.SwtUtils;
import pspnetparty.lib.IniFile;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.AppConstants;
import pspnetparty.lib.socket.AsyncTcpServer;
import pspnetparty.lib.socket.AsyncUdpServer;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.IServerListener;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.PacketData;
import pspnetparty.wlan.JnetPcapWlanDevice;
import pspnetparty.wlan.NativeWlanDevice;
import pspnetparty.wlan.WlanDevice;
import pspnetparty.wlan.WlanLibrary;
import pspnetparty.wlan.WlanNetwork;

public class WlanProxyApp {
	private static final String INI_SETTING_FILE_NAME = "PlayClient.ini";
	private static final String SECTION_LAN_ADAPTERS = "LanAdapters";

	private Shell shell;
	private Spinner portSpinner;
	private Combo adapterCombo;
	private Button serverStart;
	private Text logText;

	private WlanLibrary wlanLibrary;
	private IniFile iniFile;
	private List<WlanDevice> wlanAdapterList = new ArrayList<WlanDevice>();

	private boolean isPacketCapturing = false;
	private WlanDevice currentDevice = WlanDevice.NULL;

	private Thread packetCaptureThread;
	private ByteBuffer captureBuffer = ByteBuffer.allocateDirect(WlanDevice.CAPTURE_BUFFER_SIZE);
	private Thread ssidPollingThread;

	private AsyncTcpServer tcpServer;
	private AsyncUdpServer udpServer;

	private ISocketConnection proxyClient = ISocketConnection.NULL;
	private boolean isScanNetworkRequested = false;
	private String lastSSID = "";

	public int sentBytes;
	public int capturedBytes;
	private Thread cronThread;
	private Label statusbarLabel;

	public WlanProxyApp() throws IOException {
		iniFile = new IniFile(INI_SETTING_FILE_NAME);

		shell = new Shell(SwtUtils.DISPLAY);
		shell.setText("PSP NetParty WLANアダプタプロキシ");

		GridLayout gridLayout;

		gridLayout = new GridLayout(5, false);
		gridLayout.marginWidth = 2;
		gridLayout.marginHeight = 2;
		gridLayout.marginBottom = 1;
		shell.setLayout(gridLayout);

		Label portLabel = new Label(shell, SWT.NONE);
		portLabel.setText("ポート");
		portLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		portSpinner = new Spinner(shell, SWT.BORDER);
		portSpinner.setMinimum(1);
		portSpinner.setMaximum(65535);
		portSpinner.setSelection(20000);

		Label adapterLabel = new Label(shell, SWT.NONE);
		adapterLabel.setText("アダプタ");
		adapterLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

		adapterCombo = new Combo(shell, SWT.BORDER | SWT.READ_ONLY);
		adapterCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		serverStart = new Button(shell, SWT.TOGGLE);
		serverStart.setText("開始");

		logText = new Text(shell, SWT.READ_ONLY | SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
		logText.setBackground(SwtUtils.DISPLAY.getSystemColor(SWT.COLOR_WHITE));
		logText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 5, 1));

		statusbarLabel = new Label(shell, SWT.NONE);
		statusbarLabel.setText("通信はありません");
		statusbarLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 5, 1));

		String software = String.format("%s 通信サーバー バージョン: %s", AppConstants.APP_NAME, AppConstants.VERSION);
		appendLog(software, false);
		appendLog("プロトコル: " + IProtocol.NUMBER, false);

		try {
			wlanLibrary = NativeWlanDevice.LIBRARY;
			appendLog("pnpwlanライブラリを読み込みました", false);
		} catch (Throwable e1) {
			appendLog("pnpwlanライブラリが見つかりません", false);

			try {
				wlanLibrary = JnetPcapWlanDevice.LIBRARY;
				appendLog("jnetpcapライブラリを読み込みました", false);
			} catch (Throwable e2) {
				appendLog("jnetpcapライブラリが見つかりません", false);

				wlanLibrary = WlanLibrary.NULL;
			}
		}

		if (wlanLibrary == WlanLibrary.NULL) {
			adapterCombo.removeAll();
			adapterCombo.add("使用できません");
			adapterCombo.select(0);
			adapterCombo.setEnabled(false);

			portSpinner.setEnabled(false);
			serverStart.setEnabled(false);
		} else {
			adapterCombo.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					int index = adapterCombo.getSelectionIndex();
					int separatorIndex = wlanAdapterList.size() + 1;
					int refreshIndex = separatorIndex + 1;
					if (index == 0) {
						serverStart.setEnabled(false);
					} else if (index < separatorIndex) {
						serverStart.setEnabled(true);
					} else if (index == separatorIndex) {
						adapterCombo.select(0);
						serverStart.setEnabled(false);
					} else if (index == refreshIndex) {
						refreshAdapterList();
					}
				}
			});
			serverStart.addListener(SWT.Selection, new Listener() {
				@Override
				public void handleEvent(Event event) {
					if (serverStart.getSelection()) {
						if (startPacketCapturing()) {
							serverStart.setText("停止");
							adapterCombo.setEnabled(false);
						} else {
							serverStart.setSelection(false);
						}
					} else {
						serverStart.setEnabled(false);
						isPacketCapturing = false;
						statusbarLabel.setText("通信はありません");
					}
				}
			});

			refreshAdapterList();
			initBackgroundThreads();

			tcpServer = new AsyncTcpServer(WlanDevice.CAPTURE_BUFFER_SIZE + 10);
			udpServer = new AsyncUdpServer();

			ProxyProtocol protocol = new ProxyProtocol();
			tcpServer.addProtocol(protocol);
			udpServer.addProtocol(protocol);

			IServerListener listener = new IServerListener() {
				@Override
				public void log(String message) {
					appendLog(message, true);
				}

				@Override
				public void serverStartupFinished() {
				}

				@Override
				public void serverShutdownFinished() {
				}
			};
			tcpServer.addServerListener(listener);
			udpServer.addServerListener(listener);

			initCaptureBuffer();
		}

		shell.pack();
		shell.setMinimumSize(shell.getSize());

		shell.setSize(300, 200);
		shell.open();
	}

	private void initCaptureBuffer() {
		String c = String.valueOf(WlanProxyConstants.COMMAND_PACKET);
		ByteBuffer b = AppConstants.CHARSET.encode(c);
		captureBuffer.put(0, b.get());
	}

	private void appendLog(final String message, final boolean timestamp) {
		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						appendLog(message, timestamp);
					}
				});
				return;
			}
			if (logText.getCharCount() > 0)
				logText.append("\n");

			if (timestamp) {
				Date now = new Date();
				logText.append(SwtUtils.LOG_DATE_FORMAT.format(now));
				logText.append(" - ");
			}

			logText.append(message);
			logText.setTopIndex(logText.getLineCount());
		} catch (SWTException e) {
		}
	}

	private void refreshAdapterList() {
		adapterCombo.removeAll();
		adapterCombo.add("選択されていません");

		wlanAdapterList.clear();

		try {
			wlanLibrary.findDevices(wlanAdapterList);
		} catch (RuntimeException e) {
			appendLog(Utility.stackTraceToString(e), true);
			return;
		} catch (UnsatisfiedLinkError e) {
			appendLog(Utility.stackTraceToString(e), true);
			return;
		}

		IniSection nicSection = iniFile.getSection(SECTION_LAN_ADAPTERS);

		int maxNameLength = 15;
		int i = 1;
		for (Iterator<WlanDevice> iter = wlanAdapterList.iterator(); iter.hasNext(); i++) {
			WlanDevice device = iter.next();
			String macAddress = Utility.macAddressToString(device.getHardwareAddress(), 0, true);

			String display = nicSection.get(macAddress, "");

			if (Utility.isEmpty(display)) {
				display = device.getName();
				display = display.replace("(Microsoft's Packet Scheduler)", "");
				display = display.replaceAll(" {2,}", " ").trim();

				nicSection.set(macAddress, display);
			} else if (display.equals("")) {
				iter.remove();
				continue;
			}

			display += " [" + macAddress + "]";
			adapterCombo.add(display);

			maxNameLength = Math.max(display.length(), maxNameLength);
		}

		StringBuilder sb = new StringBuilder(maxNameLength);
		for (i = 0; i < maxNameLength; i++)
			sb.append('-');

		adapterCombo.add(sb.toString());
		adapterCombo.add("アダプターリストを再読み込み");

		adapterCombo.select(0);

		serverStart.setEnabled(false);
	}

	private boolean startPacketCapturing() {
		try {
			InetSocketAddress address = new InetSocketAddress(portSpinner.getSelection());
			tcpServer.startListening(address);
			udpServer.startListening(address);

			int index = adapterCombo.getSelectionIndex() - 1;
			WlanDevice device = wlanAdapterList.get(index);

			device.open();

			currentDevice = device;

			isPacketCapturing = true;
			wakeupThread(packetCaptureThread);
			if (wlanLibrary.isSSIDEnabled())
				wakeupThread(ssidPollingThread);
			wakeupThread(cronThread);

			return true;
		} catch (RuntimeException e) {
			appendLog(Utility.stackTraceToString(e), true);
			return false;
		} catch (Exception e) {
			appendLog(Utility.stackTraceToString(e), true);
			return false;
		}
	}

	private void processCapturedPacket() {
		// System.out.println(captureBuffer.toString());
		// System.out.println(bufferForCapturing.get(0));
		capturedBytes += captureBuffer.remaining();
		proxyClient.send(captureBuffer);
	}

	private void initBackgroundThreads() {
		packetCaptureThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Runnable prepareCaptureEndAction = new Runnable() {
					@Override
					public void run() {
						try {
							isPacketCapturing = false;
							serverStart.setEnabled(false);
						} catch (SWTException e) {
						}
					}
				};
				Runnable captureEndAction = new Runnable() {
					@Override
					public void run() {
						try {
							adapterCombo.setEnabled(true);
							serverStart.setText("開始");
							serverStart.setEnabled(true);
						} catch (SWTException e) {
						}
					}
				};

				try {
					while (!shell.isDisposed()) {
						synchronized (packetCaptureThread) {
							if (!isPacketCapturing)
								packetCaptureThread.wait();
						}

						try {
							while (isPacketCapturing) {
								captureBuffer.clear();
								captureBuffer.position(1);
								int ret = currentDevice.capturePacket(captureBuffer);
								if (ret > 0) {
									captureBuffer.flip();
									processCapturedPacket();
								} else if (ret == 0) {
								} else {
									SwtUtils.DISPLAY.syncExec(prepareCaptureEndAction);
									break;
								}
							}
						} catch (Exception e) {
							appendLog(Utility.stackTraceToString(e), true);
							isPacketCapturing = false;
						}

						currentDevice.close();
						currentDevice = WlanDevice.NULL;

						tcpServer.stopListening();
						udpServer.stopListening();

						SwtUtils.DISPLAY.syncExec(captureEndAction);
					}
				} catch (SWTException e) {
				} catch (Exception e) {
					appendLog(Utility.stackTraceToString(e), true);
				}
			}
		}, "PacketCaptureThread");
		packetCaptureThread.setDaemon(true);

		ssidPollingThread = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					while (!shell.isDisposed()) {
						synchronized (ssidPollingThread) {
							if (!isPacketCapturing)
								ssidPollingThread.wait();
						}

						try {
							while (isPacketCapturing) {
								String ssid = currentDevice.getSSID();
								if (!ssid.equals(lastSSID)) {
									proxyClient.send(WlanProxyConstants.COMMAND_GET_SSID + ssid);
									lastSSID = ssid;
								}

								if (isScanNetworkRequested) {
									currentDevice.scanNetwork();
									isScanNetworkRequested = false;
								}

								Thread.sleep(3000);
							}
						} catch (Exception e) {
						}
					}
				} catch (SWTException e) {
				} catch (Exception e) {
				}
			}
		}, "SsidPollingThread");
		ssidPollingThread.setDaemon(true);

		cronThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Runnable refreshAction = new Runnable() {
					@Override
					public void run() {
						statusbarLabel.setText("PSPからの受信: " + capturedBytes + " バイト  |  他参加者からの受信: " + sentBytes + " バイト");
						capturedBytes = sentBytes = 0;
					}
				};
				try {
					while (!shell.isDisposed()) {
						synchronized (cronThread) {
							if (!isPacketCapturing)
								cronThread.wait();
						}

						try {
							while (isPacketCapturing) {
								SwtUtils.DISPLAY.asyncExec(refreshAction);

								Thread.sleep(1000);
							}
						} catch (Exception e) {
						}
					}
				} catch (SWTException e) {
				} catch (Exception e) {
				}
			}
		}, "CronThread");
		cronThread.setDaemon(true);
	}

	private void wakeupThread(Thread thread) {
		synchronized (thread) {
			if (thread.isAlive()) {
				thread.notify();
				return;
			}
		}
		thread.start();
	}

	public void startEventLoop() {
		while (!shell.isDisposed()) {
			if (!SwtUtils.DISPLAY.readAndDispatch()) {
				SwtUtils.DISPLAY.sleep();
			}
		}
		try {
			SwtUtils.DISPLAY.dispose();
		} catch (RuntimeException e) {
		}
	}

	private class ProxyProtocol implements IProtocol {
		private ByteBuffer featureBuffer;

		private ProxyProtocol() {
			String c = String.valueOf(wlanLibrary.isSSIDEnabled() ? WlanProxyConstants.COMMAND_SSID_FEATURE_ENABLED
					: WlanProxyConstants.COMMAND_SSID_FEATURE_DISABLED);
			featureBuffer = AppConstants.CHARSET.encode(c);
		}

		@Override
		public void log(String message) {
			appendLog(message, true);
		}

		@Override
		public String getProtocol() {
			return WlanProxyConstants.PROTOCOL;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			appendLog(" " + connection.getRemoteAddress(), true);
			if (proxyClient != ISocketConnection.NULL)
				return null;

			appendLog("接続されました: " + connection.getRemoteAddress(), true);

			featureBuffer.clear();
			System.out.println(featureBuffer);
			connection.send(featureBuffer);

			connection.send(WlanProxyConstants.COMMAND_GET_SSID + lastSSID);

			proxyClient = connection;

			ProxyProtocolDriver driver = new ProxyProtocolDriver();
			driver.connection = connection;
			return driver;
		}
	}

	private class ProxyProtocolDriver implements IProtocolDriver {
		private ISocketConnection connection;
		private ArrayList<WlanNetwork> networks = new ArrayList<WlanNetwork>();

		@Override
		public ISocketConnection getConnection() {
			return connection;
		}

		@Override
		public boolean process(PacketData data) {
			ByteBuffer buffer = data.getBuffer();

			int origLimit = buffer.limit();
			buffer.limit(1);
			char c = Utility.decode(buffer).charAt(0);
			buffer.limit(origLimit);

			switch (c) {
			case WlanProxyConstants.COMMAND_PACKET:
				// System.out.println(buffer);
				sentBytes += buffer.remaining();
				currentDevice.sendPacket(buffer);
				break;
			case WlanProxyConstants.COMMAND_SET_SSID:
				String ssid = Utility.decode(buffer);
				currentDevice.setSSID(ssid);
				System.out.println(ssid);
				break;
			case WlanProxyConstants.COMMAND_SCAN_NETWORK:
				isScanNetworkRequested = true;
				break;
			case WlanProxyConstants.COMMAND_FIND_NETWORK:
				networks.clear();
				currentDevice.findNetworks(networks);

				StringBuilder sb = new StringBuilder();
				sb.append(WlanProxyConstants.COMMAND_FIND_NETWORK);
				for (WlanNetwork n : networks) {
					sb.append(n.getSsid());
					sb.append('\t');
					sb.append(n.getRssi());
					sb.append('\f');
				}

				connection.send(sb.toString());
				break;
			default:
				return false;
			}

			return true;
		}

		@Override
		public void connectionDisconnected() {
			proxyClient = ISocketConnection.NULL;

			appendLog("切断されました: " + connection.getRemoteAddress(), true);
		}

		@Override
		public void errorProtocolNumber(String number) {
		}
	}

	public static void main(String[] args) throws Exception {
		new WlanProxyApp().startEventLoop();
	}
}
