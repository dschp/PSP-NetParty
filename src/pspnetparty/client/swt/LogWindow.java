package pspnetparty.client.swt;

import java.util.Date;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import pspnetparty.lib.Utility;

public class LogWindow {

	private IApplication application;
	private Shell shell;
	private Text logText;

	public LogWindow(IApplication application, Shell parentShell) {
		this.application = application;

		// shell = new Shell((Shell) null, SWT.SHELL_TRIM | SWT.TOOL);
		shell = new Shell(parentShell, SWT.SHELL_TRIM | SWT.TOOL);

		shell.setText("ログ");
		try {
			shell.setImages(application.getShellImages());
		} catch (RuntimeException e) {
			e.printStackTrace();
		}
		shell.setLayout(new FillLayout());

		logText = new Text(shell, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		application.initControl(logText);

		shell.addShellListener(new ShellListener() {
			@Override
			public void shellIconified(ShellEvent e) {
			}

			@Override
			public void shellDeiconified(ShellEvent e) {
			}

			@Override
			public void shellDeactivated(ShellEvent e) {
			}

			@Override
			public void shellActivated(ShellEvent e) {
			}

			@Override
			public void shellClosed(ShellEvent e) {
				e.doit = false;
				shell.setVisible(false);
			}
		});
		shell.addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				IniAppData appData = LogWindow.this.application.getAppData();
				appData.storeLogWindow(shell.getBounds());
			}
		});

		application.getAppData().restoreLogWindow(shell);
	}

	public void reflectApperance() {
		shell.layout(true, true);
	}

	public void setVisible(boolean visible) {
		shell.setVisible(visible);
	}

	public void appendLogTo(final String message, final boolean timestamp, final boolean showWindow) {
		if (Utility.isEmpty(message))
			return;

		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						appendLogTo(message, timestamp, showWindow);
					}
				});
				return;
			}

			if (logText.getCharCount() > 0)
				logText.append("\n");

			if (timestamp) {
				Date now = new Date();
				logText.append(IApplication.LOG_DATE_FORMAT.format(now));
				logText.append(" - ");
			}

			logText.append(message);
			logText.setTopIndex(logText.getLineCount());

			if (showWindow)
				shell.setVisible(true);
		} catch (SWTException e) {
		}
	}
}
