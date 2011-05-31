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

import pspnetparty.client.swt.config.IniAppData;
import pspnetparty.lib.Utility;

public class LogWindow {

	private IPlayClient application;
	private Shell shell;
	private Text logText;

	public LogWindow(IPlayClient application, Shell parentShell) {
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

	public void appendLog(final String message, final boolean timestamp, final boolean showWindow) {
		if (Utility.isEmpty(message))
			return;

		try {
			if (SwtUtils.isNotUIThread()) {
				SwtUtils.DISPLAY.asyncExec(new Runnable() {
					@Override
					public void run() {
						appendLog(message, timestamp, showWindow);
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

			if (showWindow)
				shell.setVisible(true);
		} catch (SWTException e) {
		}
	}
}
