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
package pspnetparty.lib;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.constants.ProtocolConstants.Portal;

public class PortalEngine {

	private static final int RETRY_INTERVAL = 5 * 60 * 1000;

	private ILogger logger;

	private IServer<PortalClientState> searchServer;
	private PortalHandler searchHandler;

	private AsyncTcpClient tcpClient;

	private RAMDirectory ramDirectory;
	private IndexWriter indexWriter;
	private IndexSearcher indexSearcher;
	private QueryParser searchParser;

	private ConcurrentHashMap<String, PlayRoom> playRoomEntries = new ConcurrentHashMap<String, PlayRoom>();
	private ConcurrentHashMap<PortalClientState, Object> clientMap = new ConcurrentHashMap<PortalClientState, Object>();

	private HashSet<InetSocketAddress> watchingAddresses = new HashSet<InetSocketAddress>();
	private ConcurrentHashMap<InetSocketAddress, RoomServerState> roomServerStates;
	private HashMap<InetSocketAddress, RetryInfo> retryAddresses = new HashMap<InetSocketAddress, RetryInfo>();

	private File loginMessageFile;

	private WatcherHandler watcherHandler;

	private int port = -1;
	private int maxUsers = 30;

	private int descriptionMaxLength = 100;
	private int maxSearchResults = 50;

	private int updateCount = 0;

	private static class RetryInfo {
		private long timestamp;
	}

	public PortalEngine(ILogger logger) throws IOException {
		this.logger = logger;

		searchServer = new AsyncTcpServer<PortalClientState>();
		searchHandler = new PortalHandler();

		tcpClient = new AsyncTcpClient(1000000, 2000);
		watcherHandler = new WatcherHandler();
		roomServerStates = new ConcurrentHashMap<InetSocketAddress, RoomServerState>();

		ramDirectory = new RAMDirectory();
		indexWriter = new IndexWriter(ramDirectory, new CJKAnalyzer(Version.LUCENE_30, new HashSet<String>()), true,
				MaxFieldLength.UNLIMITED);
		searchParser = new QueryParser(Version.LUCENE_30, "title", indexWriter.getAnalyzer());
	}

	public int getCurrentUsers() {
		return clientMap.size();
	}

	public int getMaxUsers() {
		return maxUsers;
	}

	public void setMaxUsers(int maxUsers) {
		this.maxUsers = maxUsers;
		notifyServerStatus();
	}

	public int getDescriptionMaxLength() {
		return descriptionMaxLength;
	}

	public void setDescriptionMaxLength(int descriptionMaxLength) {
		this.descriptionMaxLength = descriptionMaxLength;
	}

	public int getMaxSearchResults() {
		return maxSearchResults;
	}

	public void setMaxSearchResults(int maxSearchResults) {
		this.maxSearchResults = maxSearchResults;
	}

	public void setLoginMessageFile(String loginMessageFile) {
		if (Utility.isEmpty(loginMessageFile)) {
			this.loginMessageFile = null;
		} else {
			this.loginMessageFile = new File(loginMessageFile);
		}
	}

	public void start(int port) throws IOException {
		if (searchServer.isListening())
			stop();

		logger.log("プロトコル: " + ProtocolConstants.PROTOCOL_NUMBER);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		searchServer.startListening(bindAddress, searchHandler);

		this.port = port;

		Thread retryThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (searchServer.isListening()) {
						synchronized (retryAddresses) {
							for (Entry<InetSocketAddress, RetryInfo> e : retryAddresses.entrySet()) {
								RetryInfo info = e.getValue();
								if (info.timestamp < System.currentTimeMillis()) {
									tcpClient.connect(e.getKey(), watcherHandler);
									info.timestamp = System.currentTimeMillis() + RETRY_INTERVAL;
								}
							}
						}

						Thread.sleep(5000);
					}
				} catch (InterruptedException e) {
				}
			}
		}, "RetryThread");
		retryThread.setDaemon(true);
		retryThread.start();
	}

	public void stop() {
		if (!searchServer.isListening())
			return;
		searchServer.stopListening();

		this.port = -1;

		for (Entry<InetSocketAddress, RoomServerState> e : roomServerStates.entrySet()) {
			e.getValue().connection.disconnect();
		}
		roomServerStates.clear();
	}

	public int getPort() {
		return port;
	}

	public int getRoomEntryCount() {
		return playRoomEntries.size();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Entry<String, PlayRoom> entry : playRoomEntries.entrySet()) {
			PlayRoom room = entry.getValue();

			sb.append(room.getRoomAddress()).append('\t');
			sb.append(room.getMasterName()).append('\t');
			sb.append(room.getTitle()).append('\t');
			sb.append(room.getCurrentPlayers());
			sb.append(" / ");
			sb.append(room.getMaxPlayers());
			sb.append('\n');
		}
		return sb.toString();
	}

	private boolean reuseFields = true;
	private Field docFieldAddress = new Field("address", "", Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS);
	private Field docFieldTitle = new Field("title", "", Field.Store.NO, Field.Index.ANALYZED);
	private Field docFieldMasterName = new Field("masterName", "", Field.Store.NO, Field.Index.ANALYZED);
	private Field docFieldServerAddress = new Field("serverAddress", "", Field.Store.NO, Field.Index.ANALYZED);
	private Field docFieldHasPassword = new Field("hasPassword", "", Field.Store.NO, Field.Index.NOT_ANALYZED);
	private Field docFieldSource = new Field("source", "", Field.Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS);

	private void updateRoomEntry(PlayRoom room, String source) throws IOException {
		if (room.getCurrentPlayers() < room.getMaxPlayers()) {
			Document doc = new Document();
			if (reuseFields) {
				docFieldAddress.setValue(room.getRoomAddress());
				docFieldTitle.setValue(room.getTitle().replace("　", " "));
				docFieldMasterName.setValue(room.getMasterName().replace("　", " "));
				docFieldServerAddress.setValue(room.getServerAddress());
				docFieldHasPassword.setValue(room.hasPassword() ? "y" : "n");
				docFieldSource.setValue(source);

				doc.add(docFieldAddress);
				doc.add(docFieldTitle);
				doc.add(docFieldMasterName);
				doc.add(docFieldServerAddress);
				doc.add(docFieldHasPassword);
				doc.add(docFieldSource);
			} else {
				doc.add(new Field("address", room.getRoomAddress(), Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
				doc.add(new Field("title", room.getTitle().replace("　", " "), Field.Store.NO, Field.Index.ANALYZED));
				doc.add(new Field("masterName", room.getMasterName().replace("　", " "), Field.Store.NO, Field.Index.ANALYZED));
				doc.add(new Field("serverAddress", room.getServerAddress(), Field.Store.NO, Field.Index.ANALYZED));
				doc.add(new Field("hasPassword", room.hasPassword() ? "y" : "n", Field.Store.NO, Field.Index.NOT_ANALYZED));
				doc.add(new Field("source", source, Field.Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS));
			}

			indexWriter.updateDocument(new Term("address", room.getRoomAddress()), doc);
		} else {
			indexWriter.deleteDocuments(new Term("address", room.getRoomAddress()));
		}

		indexWriter.commit();

		updateCount++;
		if (updateCount > 10) {
			indexWriter.optimize();
			updateCount = 0;
		}

		indexSearcher = null;
	}

	interface CommandHandler {
		public void process(ISocketConnection connection, String argument);
	}

	private static class RoomServerState {
		private ISocketConnection connection;
		private int currentRooms;
		private int maxRooms;
		private boolean roomPasswordAllowed = false;
		private int myRoomCount;

		private void appendServerStatus(StringBuilder sb) {
			sb.append(ProtocolConstants.Portal.ROOM_SERVER_STATUS);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(connection.getRemoteAddress().getHostName());
			sb.append(':');
			sb.append(connection.getRemoteAddress().getPort());
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(currentRooms);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(maxRooms);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(roomPasswordAllowed ? "Y" : "N");
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(myRoomCount);
		}
	}

	public void addWatching(InetSocketAddress address) {
		if (!searchServer.isListening())
			throw new IllegalStateException();
		if (watchingAddresses.contains(address))
			return;

		watchingAddresses.add(address);
		tcpClient.connect(address, watcherHandler);
	}

	public void removeWatching(InetSocketAddress address) {
		watchingAddresses.remove(address);
		retryAddresses.remove(address);

		RoomServerState state = roomServerStates.get(address);
		if (state == null || state.connection == null)
			return;

		state.connection.disconnect();
	}

	public InetSocketAddress[] listWatchlingAddress() {
		return watchingAddresses.toArray(new InetSocketAddress[watchingAddresses.size()]);
	}

	public InetSocketAddress[] listActiveAddress() {
		return roomServerStates.keySet().toArray(new InetSocketAddress[roomServerStates.size()]);
	}

	public InetSocketAddress[] listRetryAddress() {
		return retryAddresses.keySet().toArray(new InetSocketAddress[retryAddresses.size()]);
	}

	public void reconnectNow() {
		if (!searchServer.isListening())
			throw new IllegalStateException();

		synchronized (retryAddresses) {
			for (Entry<InetSocketAddress, RetryInfo> e : retryAddresses.entrySet()) {
				tcpClient.connect(e.getKey(), watcherHandler);

				RetryInfo info = e.getValue();
				info.timestamp = System.currentTimeMillis() + RETRY_INTERVAL;
			}
		}
	}

	private class WatcherHandler implements IAsyncClientHandler {
		private HashMap<String, CommandHandler> handlers = new HashMap<String, CommandHandler>();

		WatcherHandler() {
			handlers.put(ProtocolConstants.Room.SERVER_STATUS, new RoomServerStatusHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_CREATED, new NotifyRoomCreatedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_UPDATED, new NotifyRoomUpdatedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_DELETED, new NotifyRoomDeletedHandler());
			handlers.put(ProtocolConstants.Room.NOTIFY_ROOM_PLAYER_COUNT_CHANGED, new NotifyRoomPlayerCountChangeHandler());
		}

		@Override
		public void log(ISocketConnection connection, String message) {
			logger.log(message);
		}

		@Override
		public void connectCallback(ISocketConnection connection) {
			InetSocketAddress address = connection.getRemoteAddress();

			RoomServerState state = new RoomServerState();
			state.connection = connection;

			roomServerStates.put(address, state);
			retryAddresses.remove(address);

			StringBuilder sb = new StringBuilder();
			sb.append(ProtocolConstants.PROTOCOL_WATCHER);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(ProtocolConstants.PROTOCOL_NUMBER);
			connection.send(sb.toString());

			logger.log("接続しました: " + address.getHostName() + ":" + address.getPort());
		}

		@Override
		public void disconnectCallback(ISocketConnection connection) {
			InetSocketAddress address = connection.getRemoteAddress();
			if (roomServerStates.remove(address) == null) {
				RetryInfo info = new RetryInfo();
				info.timestamp = System.currentTimeMillis() + RETRY_INTERVAL;
				retryAddresses.put(address, info);

				logger.log("接続できませんでした: " + address.getHostName() + ":" + address.getPort());
				return;
			}

			String source = address.getHostName() + ":" + address.getPort();
			try {
				Term term = new Term("source", source);
				Query query = new TermQuery(term);

				if (indexSearcher == null)
					indexSearcher = new IndexSearcher(ramDirectory);

				TopDocs docs = indexSearcher.search(query, Integer.MAX_VALUE);
				ScoreDoc[] hits = docs.scoreDocs;

				for (int i = 0; i < hits.length; i++) {
					Document d = indexSearcher.doc(hits[i].doc);
					String roomAddress = d.get("address");
					playRoomEntries.remove(roomAddress);
				}

				indexWriter.deleteDocuments(term);
				indexWriter.commit();

				indexSearcher = null;

			} catch (IOException e) {
				logger.log(Utility.makeStackTrace(e));
			}

			String notify = ProtocolConstants.Portal.NOTIFY_ROOM_SERVER_REMOVED + ProtocolConstants.ARGUMENT_SEPARATOR + source;
			for (PortalClientState s : clientMap.keySet()) {
				s.getConnection().send(notify);
			}

			if (watchingAddresses.contains(address)) {
				RetryInfo info = new RetryInfo();
				info.timestamp = System.currentTimeMillis() + RETRY_INTERVAL;
				retryAddresses.put(address, info);

				logger.log("接続が切断されました: " + address.getHostName() + ":" + address.getPort());
			} else {
				logger.log("接続を切断しました: " + address.getHostName() + ":" + address.getPort());
			}
		}

		@Override
		public void readCallback(ISocketConnection connection, PacketData data) {
			for (String message : data.getMessages()) {
				int commandEndIndex = message.indexOf(ProtocolConstants.ARGUMENT_SEPARATOR);
				String command, argument;
				if (commandEndIndex > 0) {
					command = message.substring(0, commandEndIndex);
					argument = message.substring(commandEndIndex + 1);
				} else {
					command = message;
					argument = "";
				}
				//logger.log(message);

				CommandHandler handler = handlers.get(command);
				if (handler != null) {
					try {
						handler.process(connection, argument);
					} catch (RuntimeException e) {
						logger.log(Utility.makeStackTrace(e));
					}
				}
			}
		}

		private String supplementHostname(String address, ISocketConnection conn) {
			String[] tokens = address.split(":");
			if (!"".equals(tokens[0]))
				return address;

			return conn.getRemoteAddress().getHostName() + address;
		}

		private class RoomServerStatusHandler implements CommandHandler {
			@Override
			public void process(ISocketConnection conn, String argument) {
				// NRRC roomCount maxRooms roomPasswordAllowed myRoomCount
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 4)
					return;

				RoomServerState state = roomServerStates.get(conn.getRemoteAddress());
				if (state == null)
					return;
				try {
					int currentRooms = Integer.parseInt(tokens[0]);
					int maxRooms = Integer.parseInt(tokens[1]);
					boolean roomPasswordAllowed = "Y".equals(tokens[2]);
					int myRoomCount = Integer.parseInt(tokens[3]);

					state.currentRooms = currentRooms;
					state.maxRooms = maxRooms;
					state.roomPasswordAllowed = roomPasswordAllowed;
					state.myRoomCount = myRoomCount;

					StringBuilder sb = new StringBuilder();
					state.appendServerStatus(sb);

					String notify = sb.toString();
					for (PortalClientState s : clientMap.keySet()) {
						s.getConnection().send(notify);
					}
				} catch (NumberFormatException e) {
				}
			}
		}

		private class NotifyRoomCreatedHandler implements CommandHandler {
			@Override
			public void process(ISocketConnection conn, String argument) {
				// R hostName:port masterName title currentPlayers
				// maxPlayers hasPassword description
				final String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 7)
					return;

				try {
					String[] address = tokens[0].split(":");
					String hostname = address[0];
					if ("".equals(hostname))
						hostname = conn.getRemoteAddress().getHostName();
					int port = Integer.parseInt(address[1]);

					String masterName = tokens[1];
					String title = tokens[2];

					int currentPlayers = Integer.parseInt(tokens[3]);
					int maxPlayers = Integer.parseInt(tokens[4]);
					boolean hasPassword = "Y".equals(tokens[5]);
					String description = Utility.trim(tokens[6], descriptionMaxLength);

					PlayRoom room = new PlayRoom(hostname + ":" + port, masterName, title, hasPassword, currentPlayers, maxPlayers);
					room.setDescription(description);

					updateRoomEntry(room, room.getServerAddress());
					playRoomEntries.put(room.getRoomAddress(), room);

				} catch (NumberFormatException e) {
				} catch (IOException e) {
				}
			}
		}

		private class NotifyRoomUpdatedHandler implements CommandHandler {
			@Override
			public void process(ISocketConnection conn, String argument) {
				// U hostname:port:master title maxPlayers hasPassword
				// description
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 5)
					return;

				try {
					String address = supplementHostname(tokens[0], conn);
					String title = tokens[1];
					int maxPlayers = Integer.parseInt(tokens[2]);
					boolean hasPassword = "Y".equals(tokens[3]);
					String description = Utility.trim(tokens[4], descriptionMaxLength);

					PlayRoom room = playRoomEntries.get(address);
					if (room == null)
						return;

					room.setTitle(title);
					room.setMaxPlayers(maxPlayers);
					room.setHasPassword(hasPassword);
					room.setDescription(description);

					updateRoomEntry(room, room.getServerAddress());
				} catch (NumberFormatException e) {
				} catch (IOException e) {
					logger.log(Utility.makeStackTrace(e));
				}
			}
		}

		private class NotifyRoomDeletedHandler implements CommandHandler {
			@Override
			public void process(ISocketConnection conn, String argument) {
				// NRD hostname:port:master
				String address = supplementHostname(argument, conn);

				PlayRoom room = playRoomEntries.remove(address);
				if (room == null)
					return;
				try {
					indexWriter.deleteDocuments(new Term("address", address));
					indexWriter.commit();

					indexSearcher = null;

				} catch (IOException e) {
					logger.log(Utility.makeStackTrace(e));
				}
			}
		}

		private class NotifyRoomPlayerCountChangeHandler implements CommandHandler {
			@Override
			public void process(ISocketConnection conn, String argument) {
				// NRPC hostname:port:master playerCount
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return;

				String address = supplementHostname(tokens[0], conn);

				PlayRoom room = playRoomEntries.get(address);
				if (room == null)
					return;

				try {
					int playerCount = Integer.parseInt(tokens[1]);
					room.setCurrentPlayers(playerCount);

					updateRoomEntry(room, room.getServerAddress());
				} catch (NumberFormatException e) {
				} catch (IOException e) {
				}
			}
		}
	}

	private void notifyServerStatus() {
		StringBuilder sb = new StringBuilder();

		sb.append(ProtocolConstants.Portal.SERVER_STATUS);
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(clientMap.size());
		sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
		sb.append(maxUsers);

		String notify = sb.toString();
		for (PortalClientState s : clientMap.keySet()) {
			s.getConnection().send(notify);
		}
	}

	public void notifyAllPlayers(String message) {
		final String notify = ProtocolConstants.Room.NOTIFY_FROM_ADMIN + ProtocolConstants.ARGUMENT_SEPARATOR + message;
		for (PortalClientState s : clientMap.keySet()) {
			s.getConnection().send(notify);
		}
	}

	private void appendLoginMessage(StringBuilder sb) {
		String loginMessage = Utility.getFileContent(loginMessageFile);
		if (!Utility.isEmpty(loginMessage)) {
			sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
			sb.append(ProtocolConstants.Room.NOTIFY_FROM_ADMIN);
			sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
			sb.append(loginMessage);
		}
	}

	private class PortalHandler implements IAsyncServerHandler<PortalClientState> {
		private HashMap<String, IServerMessageHandler<PortalClientState>> protocolHandlers = new HashMap<String, IServerMessageHandler<PortalClientState>>();
		private HashMap<String, IServerMessageHandler<PortalClientState>> loginHandlers = new HashMap<String, IServerMessageHandler<PortalClientState>>();
		private HashMap<String, IServerMessageHandler<PortalClientState>> searchHandlers = new HashMap<String, IServerMessageHandler<PortalClientState>>();

		PortalHandler() {
			protocolHandlers.put(ProtocolConstants.PROTOCOL_PORTAL, new ProtocolMatchHandler());
			loginHandlers.put(Portal.COMMAND_LOGIN, new LoginHandler());

			searchHandlers.put(Portal.COMMAND_SEARCH, new RoomSearchHandler());
			searchHandlers.put(Portal.COMMAND_LOGOUT, new LogoutHandler());
		}

		@Override
		public void serverStartupFinished() {
		}

		@Override
		public void serverShutdownFinished() {
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public PortalClientState createState(ISocketConnection connection) {
			PortalClientState state = new PortalClientState(connection);
			state.messageHandlers = protocolHandlers;
			return state;
		}

		@Override
		public void disposeState(PortalClientState state) {
			clientMap.remove(state);

			notifyServerStatus();
		}

		@Override
		public boolean processIncomingData(PortalClientState state, PacketData data) {
			boolean sessionContinue = false;

			for (String message : data.getMessages()) {
				int commandEndIndex = message.indexOf(ProtocolConstants.ARGUMENT_SEPARATOR);
				String command, argument;
				if (commandEndIndex > 0) {
					command = message.substring(0, commandEndIndex);
					argument = message.substring(commandEndIndex + 1);
				} else {
					command = message;
					argument = "";
				}

				IServerMessageHandler<PortalClientState> handler = state.messageHandlers.get(command);
				if (handler != null) {
					try {
						sessionContinue = handler.process(state, argument);
					} catch (RuntimeException e) {
						logger.log(Utility.makeStackTrace(e));
					}
				}

				if (!sessionContinue)
					break;
			}

			return sessionContinue;
		}

		private class ProtocolMatchHandler implements IServerMessageHandler<PortalClientState> {
			String errorMessage = ProtocolConstants.ERROR_PROTOCOL_MISMATCH + ProtocolConstants.ARGUMENT_SEPARATOR
					+ ProtocolConstants.PROTOCOL_NUMBER;

			@Override
			public boolean process(PortalClientState state, String argument) {
				if (ProtocolConstants.PROTOCOL_NUMBER.equals(argument)) {
					state.messageHandlers = loginHandlers;
					return true;
				} else {
					state.getConnection().send(errorMessage);
					return false;
				}
			}
		}

		private class LoginHandler implements IServerMessageHandler<PortalClientState> {
			@Override
			public boolean process(PortalClientState state, String argument) {
				if (clientMap.size() >= maxUsers) {
					state.getConnection().send(ProtocolConstants.Portal.ERROR_LOGIN_BEYOND_CAPACITY);
					return false;
				}

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Portal.COMMAND_LOGIN);

				appendLoginMessage(sb);

				if (roomServerStates.size() > 0) {
					for (RoomServerState ws : roomServerStates.values()) {
						sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
						ws.appendServerStatus(sb);
					}
				}

				state.getConnection().send(sb.toString());

				clientMap.put(state, this);
				notifyServerStatus();

				state.messageHandlers = searchHandlers;
				return true;
			}
		}

		private class LogoutHandler implements IServerMessageHandler<PortalClientState> {
			@Override
			public boolean process(PortalClientState state, String argument) {
				return false;
			}
		}

		private class RoomSearchHandler implements IServerMessageHandler<PortalClientState> {
			@Override
			public boolean process(PortalClientState state, String argument) {
				// S title masterName serverName hasPassword ngTitle
				// ngMasterName ngServerName

				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 7)
					return false;

				String title = tokens[0].replace("　", " ").replaceAll(" {2,}", " ").trim();
				String masterName = tokens[1].replace("　", " ").replaceAll(" {2,}", " ").trim();
				String serverName = tokens[2].trim();
				boolean queryHasPassword = "Y".equals(tokens[3]);
				String ngTitle = tokens[4].replace("　", " ").replaceAll(" {2,}", " ").trim();
				String ngMasterName = tokens[5].replace("　", " ").replaceAll(" {2,}", " ").trim();
				String ngServerName = tokens[6].trim();

				StringBuilder queryBuilder = new StringBuilder();
				if (!Utility.isEmpty(title)) {
					appendQuery(queryBuilder, "title", title);
				}
				if (!Utility.isEmpty(ngTitle)) {
					appendQuery(queryBuilder, "-title", ngTitle);
				}
				if (!Utility.isEmpty(masterName)) {
					appendQuery(queryBuilder, "masterName", masterName);
				}
				if (!Utility.isEmpty(ngMasterName)) {
					appendQuery(queryBuilder, "masterName", ngMasterName);
				}
				if (!Utility.isEmpty(serverName)) {
					if (queryBuilder.length() > 0)
						queryBuilder.append(" AND ");
					queryBuilder.append("serverAddress:").append(QueryParser.escape(serverName));// .append('*');
				}
				if (!Utility.isEmpty(ngServerName)) {
					if (queryBuilder.length() > 0)
						queryBuilder.append(" AND ");
					queryBuilder.append("-serverAddress:").append(QueryParser.escape(ngServerName));// .append('*');
				}

				if (queryBuilder.length() > 0)
					queryBuilder.append(" AND ");
				queryBuilder.append("hasPassword:").append(queryHasPassword ? 'y' : 'n');

				try {
					Query query = searchParser.parse(queryBuilder.toString());
					// System.out.println(queryBuilder);
					// System.out.println(query);

					IndexSearcher localIndexSearcher = indexSearcher;
					if (localIndexSearcher == null)
						indexSearcher = localIndexSearcher = new IndexSearcher(ramDirectory);

					TopDocs docs = localIndexSearcher.search(query, maxSearchResults);
					ScoreDoc[] hits = docs.scoreDocs;

					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < hits.length; i++) {
						Document d = localIndexSearcher.doc(hits[i].doc);
						String address = d.get("address");
						PlayRoom room = playRoomEntries.get(address);
						if (room == null) {
							logger.log("Defunct document: address = " + address);
							continue;
						}

						sb.append(ProtocolConstants.Portal.COMMAND_SEARCH);
						sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
						sb.append(room.getServerAddress());
						sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
						sb.append(room.getMasterName());
						sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
						sb.append(room.getTitle());
						sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
						sb.append(room.getCurrentPlayers());
						sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
						sb.append(room.getMaxPlayers());
						sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
						sb.append(room.hasPassword() ? "Y" : "N");
						sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
						sb.append(room.getDescription());
						sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
					}

					sb.append(ProtocolConstants.Portal.COMMAND_SEARCH);
					state.getConnection().send(sb.toString());

				} catch (IOException e) {
					log(Utility.makeStackTrace(e));
				} catch (Exception e) {
					log(Utility.makeStackTrace(e));
				}
				return true;
			}
		}
	}

	private static void appendQuery(StringBuilder sb, String field, String query) {
		String[] tokens = query.split(" ");
		for (String s : tokens) {
			s = s.replaceAll("^\\*+", "");
			if (Utility.isEmpty(s))
				continue;
			if (sb.length() > 0)
				sb.append(" AND ");
			sb.append(field).append(':');
			sb.append(QueryParser.escape(s));
			if (s.matches("[\\x20-\\x7E]+"))
				sb.append('*');
		}
	}
}
