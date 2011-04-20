package pspnetparty.client.swt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;

import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import pspnetparty.client.swt.message.ErrorLog;
import pspnetparty.lib.IniSection;
import pspnetparty.lib.socket.IProtocol;

public interface IApplication {
	enum FontType {
		GLOBAL, LOG, CHAT,
	}

	enum ColorType {
		BACKGROUND, FOREGROUND, LOG_BACKGROUND,
	}

	public final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

	interface PortalQuery {
		public String getCommand();

		public void successCallback(String message);

		public void failCallback(ErrorLog errorLog);
	}

	public IniSettings getSettings();

	public IniAppearance getAppearance();

	public IniAppData getAppData();

	public IniSection getIniSection(String sectionName);

	public Image[] getShellImages();

	public ImageRegistry getImageRegistry();

	public RoomWindow getRoomWindow();

	public SearchWindow getSearchWindow();

	public LobbyWindow getLobbyWindow(boolean create);

	public LogWindow getLogWindow();

	public void openConfigDialog();

	public void initControl(Control control);

	public void initControl(Button buttonControl);

	public void initControl(Label labelControl);

	public void initLogControl(StyledText logControl);

	public void initChatControl(Text chatControl);

	public void applyFont(FontType type, FontData data);

	public void applyColor(ColorType type, RGB rgb);

	public void reflectAppearance();

	public void putClipboard(String data);

	public String getClipboardContents();

	public void setTaskTrayTooltipText(String title);

	public void balloonNotify(Shell shell, String message);

	public void connectTcp(InetSocketAddress address, IProtocol protocol) throws IOException;

	public void connectUdp(InetSocketAddress address, IProtocol protocol) throws IOException;

	public void execute(Runnable task);

	public void queryPortalServer(PortalQuery query);
}
