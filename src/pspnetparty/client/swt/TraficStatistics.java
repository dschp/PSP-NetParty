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

import java.util.HashMap;
import java.util.Map.Entry;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.graphics.Image;

public class TraficStatistics {
	
	public long lastModified;
	public int currentInBytes;
	public int currentOutBytes;
	
	public double currentInKbps;
	public double currentOutKbps;

	public long totalInBytes;
	public long totalOutBytes;
	
	public boolean isMine;
	
	public TraficStatistics(boolean isMine) {
		this.isMine = isMine;
	}
	
	public void clearTotal() {
		totalInBytes = 0;
		totalOutBytes = 0;
	}
	
	public static class ContentProvider implements IStructuredContentProvider {

		@Override
		public void dispose() {
		}

		@Override
		public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
		}

		@Override
		public Object[] getElements(Object input) {
			@SuppressWarnings("unchecked")
			HashMap<String, TraficStatistics> map = (HashMap<String, TraficStatistics>) input;
			return map.entrySet().toArray();
		}
	}
	
	public static class LabelProvider implements ITableLabelProvider {

		@Override
		public void addListener(ILabelProviderListener arg0) {
		}

		@Override
		public void dispose() {
		}

		@Override
		public boolean isLabelProperty(Object arg0, String arg1) {
			return false;
		}

		@Override
		public void removeListener(ILabelProviderListener arg0) {
		}

		@Override
		public Image getColumnImage(Object element, int index) {
			return null;
		}

		@Override
		public String getColumnText(Object element, int index) {
			@SuppressWarnings("unchecked")
			Entry<String, TraficStatistics> entry = (Entry<String, TraficStatistics>) element;
			
			switch (index) {
			case 0:
				return entry.getValue().isMine ? "Ž©" : "";
			case 1:
				return entry.getKey();
			case 2:
				return String.format("%.1f", entry.getValue().currentInKbps);
			case 3:
				return String.format("%.1f", entry.getValue().currentOutKbps);
			case 4:
				return Long.toString(entry.getValue().totalInBytes);
			case 5:
				return Long.toString(entry.getValue().totalOutBytes);
			}
			
			return "";
		}
	}
}
