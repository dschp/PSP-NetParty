package pspnetparty.lib.constants;

import java.net.InetAddress;
import java.util.Iterator;

public interface IServerRegistry {

	public String[] getPortalServers();

	public String[] getRoomServers();

	public String[] getSearchServers();

	public String[] getLobbyServers();

	public Iterator<String> getPortalRotator();

	public void reload();

	public boolean isValidPortalServer(InetAddress address);

	public final IServerRegistry NULL = new IServerRegistry() {
		private final String[] EMPTY = new String[] {};

		@Override
		public void reload() {
		}

		@Override
		public boolean isValidPortalServer(InetAddress address) {
			return false;
		}

		@Override
		public String[] getPortalServers() {
			return EMPTY;
		}

		@Override
		public Iterator<String> getPortalRotator() {
			return new Iterator<String>() {
				@Override
				public void remove() {
				}

				@Override
				public String next() {
					return null;
				}

				@Override
				public boolean hasNext() {
					return false;
				}
			};
		}

		@Override
		public String[] getRoomServers() {
			return EMPTY;
		}

		@Override
		public String[] getSearchServers() {
			return EMPTY;
		}

		@Override
		public String[] getLobbyServers() {
			return EMPTY;
		}
	};

}