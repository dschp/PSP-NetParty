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
package pspnetparty.lib.engine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
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

import pspnetparty.lib.FileContentCache;
import pspnetparty.lib.ILogger;
import pspnetparty.lib.Utility;
import pspnetparty.lib.constants.IServerRegistry;
import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.constants.ProtocolConstants.Search;
import pspnetparty.lib.socket.IProtocol;
import pspnetparty.lib.socket.IProtocolDriver;
import pspnetparty.lib.socket.IProtocolMessageHandler;
import pspnetparty.lib.socket.IServer;
import pspnetparty.lib.socket.IServerListener;
import pspnetparty.lib.socket.ISocketConnection;
import pspnetparty.lib.socket.TextProtocolDriver;

public class SearchEngine {

	private ILogger logger;

	private RAMDirectory ramDirectory;
	private IndexWriter indexWriter;
	private IndexSearcher indexSearcher;
	private QueryParser searchParser;

	private ConcurrentHashMap<String, PlayRoom> playRoomEntries;
	private ConcurrentHashMap<SearchProtocolDriver, Object> searchClientConnections;

	private IServerRegistry serverNetwork;
	private ConcurrentSkipListMap<SearchStatusProtocolDriver, Object> portalConnections;
	private SearchStatusProtocolDriver roomDataSource;

	private FileContentCache loginMessageFile = new FileContentCache();

	private int maxUsers = 30;
	private int descriptionMaxLength = 100;
	private int maxSearchResults = 50;
	private boolean isAcceptingPortal = true;

	private int updateCount = 0;

	public SearchEngine(IServer server, ILogger logger, IServerRegistry net) throws IOException {
		this.logger = logger;

		serverNetwork = net;

		playRoomEntries = new ConcurrentHashMap<String, PlayRoom>();
		searchClientConnections = new ConcurrentHashMap<SearchProtocolDriver, Object>();

		portalConnections = new ConcurrentSkipListMap<SearchEngine.SearchStatusProtocolDriver, Object>();

		ramDirectory = new RAMDirectory();
		indexWriter = new IndexWriter(ramDirectory, new CJKAnalyzer(Version.LUCENE_30, new HashSet<String>()), true,
				MaxFieldLength.UNLIMITED);
		searchParser = new QueryParser(Version.LUCENE_30, "title", indexWriter.getAnalyzer());

		server.addServerListener(new IServerListener() {
			@Override
			public void log(String message) {
				SearchEngine.this.logger.log(message);
			}

			@Override
			public void serverStartupFinished() {
			}

			@Override
			public void serverShutdownFinished() {
			}
		});
		server.addProtocol(new SearchProtocol());
		server.addProtocol(new SearchStatusProtocol());
	}

	public int getCurrentUsers() {
		return searchClientConnections.size();
	}

	public int getMaxUsers() {
		return maxUsers;
	}

	public void setMaxUsers(int maxUsers) {
		if (maxUsers < 0)
			return;
		this.maxUsers = maxUsers;
		notifyServerStatus();
	}

	public int getDescriptionMaxLength() {
		return descriptionMaxLength;
	}

	public void setDescriptionMaxLength(int descriptionMaxLength) {
		if (descriptionMaxLength < 1)
			return;
		this.descriptionMaxLength = descriptionMaxLength;
	}

	public int getMaxSearchResults() {
		return maxSearchResults;
	}

	public void setMaxSearchResults(int maxSearchResults) {
		if (maxSearchResults < 1)
			return;
		this.maxSearchResults = maxSearchResults;
	}

	public void setLoginMessageFile(String loginMessageFile) {
		this.loginMessageFile.setFile(loginMessageFile);
	}

	public boolean isAcceptingPortal() {
		return isAcceptingPortal;
	}

	public void setAcceptingPortal(boolean isAcceptingPortal) {
		if (!isAcceptingPortal) {
			for (SearchStatusProtocolDriver portal : portalConnections.keySet()) {
				portal.getConnection().disconnect();
			}
			portalConnections.clear();
		}

		this.isAcceptingPortal = isAcceptingPortal;
	}

	public int getRoomEntryCount() {
		return playRoomEntries.size();
	}

	public String allRoomsToString() {
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

	public String allPortalsToString() {
		StringBuilder sb = new StringBuilder();
		for (SearchStatusProtocolDriver portal : portalConnections.keySet()) {
			InetSocketAddress address = portal.getConnection().getRemoteAddress();
			sb.append(address.getAddress().getHostAddress() + ":" + address.getPort());
			if (portal == roomDataSource) {
				sb.append(" *");
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	private Field docFieldAddress = new Field("address", "", Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS);
	private Field docFieldTitle = new Field("title", "", Field.Store.NO, Field.Index.ANALYZED);
	private Field docFieldMasterName = new Field("masterName", "", Field.Store.NO, Field.Index.ANALYZED);
	private Field docFieldServerAddress = new Field("serverAddress", "", Field.Store.NO, Field.Index.ANALYZED);
	private Field docFieldHasPassword = new Field("hasPassword", "", Field.Store.NO, Field.Index.NOT_ANALYZED);
	private Field docFieldIsVacant = new Field("isVacant", "", Field.Store.NO, Field.Index.NOT_ANALYZED);
	private Field docFieldSource = new Field("source", "", Field.Store.NO, Field.Index.NOT_ANALYZED_NO_NORMS);

	private void updateRoomEntry(PlayRoom room, String source) throws IOException {
		Document doc = new Document();
		docFieldAddress.setValue(room.getRoomAddress());
		docFieldTitle.setValue(room.getTitle().replace("　", " "));
		docFieldMasterName.setValue(room.getMasterName().replace("　", " "));
		docFieldServerAddress.setValue(room.getServerAddress());
		docFieldHasPassword.setValue(room.hasPassword() ? "y" : "n");
		docFieldIsVacant.setValue(room.getCurrentPlayers() < room.getMaxPlayers() ? "y" : "n");
		docFieldSource.setValue(source);

		doc.add(docFieldAddress);
		doc.add(docFieldTitle);
		doc.add(docFieldMasterName);
		doc.add(docFieldServerAddress);
		doc.add(docFieldHasPassword);
		doc.add(docFieldIsVacant);
		doc.add(docFieldSource);

		indexWriter.updateDocument(new Term("address", room.getRoomAddress()), doc);

		indexWriter.commit();

		updateCount++;
		if (updateCount > 10) {
			indexWriter.optimize();
			updateCount = 0;
		}

		indexSearcher = null;
	}

	private void notifyServerStatus() {
		StringBuilder sb = new StringBuilder();

		sb.append(ProtocolConstants.SERVER_STATUS);
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(searchClientConnections.size());
		sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
		sb.append(maxUsers);

		ByteBuffer buffer = Utility.encode(sb);
		for (SearchProtocolDriver client : searchClientConnections.keySet()) {
			buffer.position(0);
			client.getConnection().send(buffer);
		}
		for (SearchStatusProtocolDriver portal : portalConnections.keySet()) {
			buffer.position(0);
			portal.getConnection().send(buffer);
		}
	}

	public void notifyAllClients(String message) {
		ByteBuffer buffer = Utility.encode(ProtocolConstants.Room.NOTIFY_FROM_ADMIN + TextProtocolDriver.ARGUMENT_SEPARATOR + message);
		for (SearchProtocolDriver client : searchClientConnections.keySet()) {
			buffer.position(0);
			client.getConnection().send(buffer);
		}
	}

	private void appendLoginMessage(StringBuilder sb) {
		String loginMessage = loginMessageFile.getContent();
		if (!Utility.isEmpty(loginMessage)) {
			sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
			sb.append(ProtocolConstants.Room.NOTIFY_FROM_ADMIN);
			sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
			sb.append(loginMessage);
		}
	}

	private class SearchStatusProtocol implements IProtocol {
		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_SEARCH_STATUS;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			if (!isAcceptingPortal)
				return null;

			serverNetwork.reload();
			if (!serverNetwork.isValidPortalServer(connection.getRemoteAddress().getAddress()))
				return null;

			SearchStatusProtocolDriver driver = new SearchStatusProtocolDriver(connection);

			logger.log("ポータルから接続されました: " + driver.address);

			if (roomDataSource == null) {
				connection.send(Utility.encode(ProtocolConstants.SearchStatus.COMMAND_ASK_ROOM_DATA));
				roomDataSource = driver;

				logger.log("部屋情報ソースに設定: " + driver.address);
			}

			portalConnections.put(driver, this);
			notifyServerStatus();
			return driver;
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}
	}

	private class SearchStatusProtocolDriver extends TextProtocolDriver implements Comparable<SearchStatusProtocolDriver> {
		private String address;
		private final long connectedTime;

		private SearchStatusProtocolDriver(ISocketConnection connection) {
			super(connection, portalHandlers);

			address = Utility.socketAddressToStringByIP(connection.getRemoteAddress());
			connectedTime = System.currentTimeMillis();
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public void connectionDisconnected() {
			portalConnections.remove(this);
			logger.log("ポータルから切断されました: " + address);

			if (this == roomDataSource) {
				playRoomEntries.clear();
				try {
					indexWriter.deleteAll();
					indexWriter.commit();
					indexWriter.optimize();
					updateCount = 0;

					indexSearcher = null;
				} catch (IOException e) {
					logger.log(Utility.stackTraceToString(e));
				}

				if (portalConnections.isEmpty() || !isAcceptingPortal) {
					roomDataSource = null;
					logger.log("部屋情報ソース: なし");
				} else {
					roomDataSource = portalConnections.firstKey();
					roomDataSource.getConnection().send(Utility.encode(ProtocolConstants.SearchStatus.COMMAND_ASK_ROOM_DATA));

					String remoteAddress = Utility.socketAddressToStringByIP(roomDataSource.getConnection().getRemoteAddress());
					logger.log("部屋情報ソースに設定: " + remoteAddress);
				}
			}
		}

		@Override
		public int compareTo(SearchStatusProtocolDriver p) {
			int diff = (int) (connectedTime - p.connectedTime);
			if (diff == 0)
				return address.compareTo(p.address);
			return diff;
		}

		@Override
		public void errorProtocolNumber(String number) {
		}
	}

	private final HashMap<String, IProtocolMessageHandler> portalHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		portalHandlers.put(ProtocolConstants.SearchStatus.NOTIFY_ROOM_SERVER_REMOVED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String source) {
				try {
					Term term = new Term("source", source);
					Query query = new TermQuery(term);

					IndexSearcher localIndexSearcher = indexSearcher;
					if (localIndexSearcher == null)
						localIndexSearcher = indexSearcher = new IndexSearcher(ramDirectory);

					TopDocs docs = localIndexSearcher.search(query, Integer.MAX_VALUE);
					ScoreDoc[] hits = docs.scoreDocs;

					for (int i = 0; i < hits.length; i++) {
						Document d = localIndexSearcher.doc(hits[i].doc);
						String roomAddress = d.get("address");
						playRoomEntries.remove(roomAddress);
					}

					indexWriter.deleteDocuments(term);
					indexWriter.commit();

					indexSearcher = null;
				} catch (CorruptIndexException e) {
				} catch (IOException e) {
				}

				return true;
			}
		});
		portalHandlers.put(ProtocolConstants.SearchStatus.NOTIFY_ROOM_CREATED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// R hostname:port hostname:port masterName title currentPlayers
				// maxPlayers hasPassword createdTime description
				final String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 9)
					return true;

				try {
					String source = tokens[0];
					String server = tokens[1];
					if (Utility.isEmpty(server))
						server = source;
					String masterName = tokens[2];
					String title = tokens[3];

					int currentPlayers = Integer.parseInt(tokens[4]);
					int maxPlayers = Integer.parseInt(tokens[5]);
					boolean hasPassword = "Y".equals(tokens[6]);
					long created = Long.parseLong(tokens[7]);
					String description = Utility.trim(tokens[8], descriptionMaxLength);

					PlayRoom room = new PlayRoom(source, server, masterName, title, hasPassword, currentPlayers, maxPlayers, created);
					room.setDescription(description);

					updateRoomEntry(room, source);
					playRoomEntries.put(room.getRoomAddress(), room);
				} catch (NumberFormatException e) {
				} catch (IOException e) {
				}

				return true;
			}
		});
		portalHandlers.put(ProtocolConstants.SearchStatus.NOTIFY_ROOM_UPDATED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// U hostname:port:master title maxPlayers hasPassword
				// description
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 5)
					return true;

				try {
					String address = tokens[0];
					String title = tokens[1];
					int maxPlayers = Integer.parseInt(tokens[2]);
					boolean hasPassword = "Y".equals(tokens[3]);
					String description = Utility.trim(tokens[4], descriptionMaxLength);

					PlayRoom room = playRoomEntries.get(address);
					if (room == null)
						return true;

					room.setTitle(title);
					room.setMaxPlayers(maxPlayers);
					room.setHasPassword(hasPassword);
					room.setDescription(description);

					updateRoomEntry(room, room.getSourceServer());
				} catch (NumberFormatException e) {
				} catch (IOException e) {
					logger.log(Utility.stackTraceToString(e));
				}
				return true;
			}
		});
		portalHandlers.put(ProtocolConstants.SearchStatus.NOTIFY_ROOM_DELETED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// NRD hostname:port:master
				String address = argument;

				PlayRoom room = playRoomEntries.remove(address);
				if (room == null)
					return true;
				try {
					indexWriter.deleteDocuments(new Term("address", address));
					indexWriter.commit();

					indexSearcher = null;
				} catch (IOException e) {
					logger.log(Utility.stackTraceToString(e));
				}
				return true;
			}
		});
		portalHandlers.put(ProtocolConstants.SearchStatus.NOTIFY_ROOM_PLAYER_COUNT_CHANGED, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				// NRPC hostname:port:master playerCount
				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 2)
					return true;

				String address = tokens[0];

				PlayRoom room = playRoomEntries.get(address);
				if (room == null)
					return true;

				try {
					int playerCount = Integer.parseInt(tokens[1]);
					room.setCurrentPlayers(playerCount);

					updateRoomEntry(room, room.getSourceServer());
				} catch (NumberFormatException e) {
				} catch (IOException e) {
				}
				return true;
			}
		});
	}

	private class SearchProtocol implements IProtocol {
		@Override
		public String getProtocol() {
			return ProtocolConstants.PROTOCOL_SEARCH;
		}

		@Override
		public IProtocolDriver createDriver(ISocketConnection connection) {
			SearchProtocolDriver driver = new SearchProtocolDriver(connection);
			return driver;
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}
	}

	private class SearchProtocolDriver extends TextProtocolDriver {

		private SearchProtocolDriver(ISocketConnection connection) {
			super(connection, loginHandlers);
		}

		@Override
		public void log(String message) {
			logger.log(message);
		}

		@Override
		public void connectionDisconnected() {
			searchClientConnections.remove(this);

			notifyServerStatus();
		}

		@Override
		public void errorProtocolNumber(String number) {
		}
	}

	private final HashMap<String, IProtocolMessageHandler> loginHandlers = new HashMap<String, IProtocolMessageHandler>();
	private final HashMap<String, IProtocolMessageHandler> searchHandlers = new HashMap<String, IProtocolMessageHandler>();
	{
		loginHandlers.put(Search.COMMAND_LOGIN, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				SearchProtocolDriver client = (SearchProtocolDriver) driver;

				if (searchClientConnections.size() >= maxUsers) {
					client.getConnection().send(Utility.encode(ProtocolConstants.Search.ERROR_LOGIN_BEYOND_CAPACITY));
					return false;
				}

				StringBuilder sb = new StringBuilder();
				sb.append(ProtocolConstants.Search.COMMAND_LOGIN);

				appendLoginMessage(sb);

				client.getConnection().send(Utility.encode(sb));

				searchClientConnections.put(client, this);
				notifyServerStatus();

				client.setMessageHandlers(searchHandlers);
				return true;
			}
		});

		searchHandlers.put(Search.COMMAND_SEARCH, new IProtocolMessageHandler() {
			@Override
			public boolean process(IProtocolDriver driver, String argument) {
				SearchProtocolDriver client = (SearchProtocolDriver) driver;

				// S server ngServer roomMaster ngRoomMaster title ngTitle
				// hasPassword onlyVacant

				String[] tokens = argument.split(TextProtocolDriver.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 8)
					return false;

				String roomServer = tokens[0].trim();
				String ngRoomServer = tokens[1].trim();
				String roomMaster = tokens[2].replace("　", " ").replaceAll(" {2,}", " ").trim();
				String ngRoomMaster = tokens[3].replace("　", " ").replaceAll(" {2,}", " ").trim();
				String title = tokens[4].replace("　", " ").replaceAll(" {2,}", " ").trim();
				String ngTitle = tokens[5].replace("　", " ").replaceAll(" {2,}", " ").trim();
				boolean hasPassword = "Y".equals(tokens[6]);
				boolean onlyVacant = "Y".equals(tokens[7]);

				StringBuilder queryBuilder = new StringBuilder();
				queryBuilder.append("hasPassword:").append(hasPassword ? 'y' : 'n');

				if (!Utility.isEmpty(roomServer)) {
					queryBuilder.append(" AND ");
					queryBuilder.append("serverAddress:").append(QueryParser.escape(roomServer));// .append('*');
				}
				if (!Utility.isEmpty(ngRoomServer)) {
					queryBuilder.append(" AND ");
					queryBuilder.append("-serverAddress:").append(QueryParser.escape(ngRoomServer));// .append('*');
				}
				if (!Utility.isEmpty(title)) {
					appendQuery(queryBuilder, "title", title);
				}
				if (!Utility.isEmpty(ngTitle)) {
					appendQuery(queryBuilder, "-title", ngTitle);
				}
				if (!Utility.isEmpty(roomMaster)) {
					appendQuery(queryBuilder, "masterName", roomMaster);
				}
				if (!Utility.isEmpty(ngRoomMaster)) {
					appendQuery(queryBuilder, "-masterName", ngRoomMaster);
				}

				if (onlyVacant) {
					queryBuilder.append(" AND isVacant:y");
				}

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

						sb.append(ProtocolConstants.Search.COMMAND_SEARCH);
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(room.getServerAddress());
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(room.getMasterName());
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(room.getTitle());
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(room.getCurrentPlayers());
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(room.getMaxPlayers());
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(room.hasPassword() ? "Y" : "N");
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(room.getCreatedTime());
						sb.append(TextProtocolDriver.ARGUMENT_SEPARATOR);
						sb.append(room.getDescription());
						sb.append(TextProtocolDriver.MESSAGE_SEPARATOR);
					}

					sb.append(ProtocolConstants.Search.COMMAND_SEARCH);
					client.getConnection().send(Utility.encode(sb));

				} catch (IOException e) {
					logger.log(Utility.stackTraceToString(e));
				} catch (Exception e) {
					logger.log(Utility.stackTraceToString(e));
				}
				return true;
			}
		});
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
