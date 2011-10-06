package pspnetparty.client.swt;

import java.util.Map;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.graphics.Image;

import pspnetparty.lib.LobbyUser;

public class LobbyUserUtils {
	public static final IStructuredContentProvider CONTENT_PROVIDER = new IStructuredContentProvider() {
		@Override
		public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
		}

		@Override
		public void dispose() {
		}

		@Override
		public Object[] getElements(Object input) {
			@SuppressWarnings("unchecked")
			Map<String, LobbyUser> map = (Map<String, LobbyUser>) input;
			return map.values().toArray();
		}
	};

	public static final ITableLabelProvider LABEL_PROVIDER = new ITableLabelProvider() {
		@Override
		public void removeListener(ILabelProviderListener arg0) {
		}

		@Override
		public boolean isLabelProperty(Object arg0, String arg1) {
			return false;
		}

		@Override
		public void dispose() {
		}

		@Override
		public void addListener(ILabelProviderListener arg0) {
		}

		@Override
		public Image getColumnImage(Object element, int index) {
			return null;
		}

		@Override
		public String getColumnText(Object element, int index) {
			LobbyUser user = (LobbyUser) element;

			switch (index) {
			case 0:
				return user.getName();
			case 1:
				switch (user.getState()) {
				case LOGIN:
					return "参加中";
				case AFK:
					return "離席中";
				case PLAYING:
					return "プレイ中";
				case INACTIVE:
					return "非アクティブ";
				default:
					return "";
				}
			case 2:
				return user.getProfileOneLine();
			}

			return "";
		}
	};

	public static final ViewerSorter NAME_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			LobbyUser u1 = (LobbyUser) e1;
			LobbyUser u2 = (LobbyUser) e2;
			return u1.getName().compareTo(u2.getName());
		}
	};

	public static final ViewerSorter STATE_SORTER = new ViewerSorter() {
		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			LobbyUser u1 = (LobbyUser) e1;
			LobbyUser u2 = (LobbyUser) e2;
			return u1.getState().compareTo(u2.getState());
		}
	};
}
