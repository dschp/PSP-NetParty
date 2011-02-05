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
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.constants.ProtocolConstants.Search;

public class RoomSearchEngine {

	private ILogger logger;

	private IServer<SearchState> searchServer;
	private SearchHandler searchHandler;

	private AsyncTcpClient tcpClient;
	private AsyncUdpClient udpClient;

	private ConcurrentHashMap<String, PlayRoom> playRoomEntries = new ConcurrentHashMap<String, PlayRoom>();

	private RAMDirectory ramDirectory;
	private IndexWriter indexWriter;
	private IndexSearcher indexSearcher;
	private QueryParser searchParser;

	private int descriptionMaxLength = 100;
	private int maxSearchResults = 50;

	private int updateCount = 0;

	public RoomSearchEngine(ILogger logger) throws IOException {
		this.logger = logger;

		searchServer = new AsyncTcpServer<SearchState>();
		searchHandler = new SearchHandler();

		tcpClient = new AsyncTcpClient(4000);
		udpClient = new AsyncUdpClient();

		ramDirectory = new RAMDirectory();
		indexWriter = new IndexWriter(ramDirectory, new CJKAnalyzer(Version.LUCENE_30, new HashSet<String>()), true,
				MaxFieldLength.UNLIMITED);
		searchParser = new QueryParser(Version.LUCENE_30, "title", indexWriter.getAnalyzer());
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

	public void start(int port) throws IOException {
		if (searchServer.isListening())
			stop();

		logger.log("プロトコル: " + ProtocolConstants.PROTOCOL_NUMBER);

		InetSocketAddress bindAddress = new InetSocketAddress(port);
		searchServer.startListening(bindAddress, searchHandler);
	}

	public void stop() {
		if (!searchServer.isListening())
			return;
		searchServer.stopListening();
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

	private void updateRoomEntry(PlayRoom room) throws IOException {
		Document doc = new Document();
		doc.add(new Field("address", room.getRoomAddress(), Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
		doc.add(new Field("title", room.getTitle().replace("　", " "), Field.Store.NO, Field.Index.ANALYZED));
		doc.add(new Field("masterName", room.getMasterName().replace("　", " "), Field.Store.NO, Field.Index.ANALYZED));
		doc.add(new Field("serverAddress", room.getServerAddress(), Field.Store.NO, Field.Index.ANALYZED));
		doc.add(new Field("hasPassword", room.hasPassword() ? "y" : "n", Field.Store.NO, Field.Index.NOT_ANALYZED));

		indexWriter.updateDocument(new Term("address", room.getRoomAddress()), doc);
		// indexWriter.addDocument(doc);

		indexWriter.commit();
		updateCount++;
		if (updateCount > 10) {
			indexWriter.optimize();
			updateCount = 0;
		}

		indexSearcher = null;
	}

	private class SearchHandler implements IAsyncServerHandler<SearchState> {
		private HashMap<String, IServerMessageHandler<SearchState>> protocolHandlers = new HashMap<String, IServerMessageHandler<SearchState>>();
		private HashMap<String, IServerMessageHandler<SearchState>> loginHandlers = new HashMap<String, IServerMessageHandler<SearchState>>();
		private HashMap<String, IServerMessageHandler<SearchState>> masterEntryHandlers = new HashMap<String, IServerMessageHandler<SearchState>>();
		private HashMap<String, IServerMessageHandler<SearchState>> masterHandlers = new HashMap<String, IServerMessageHandler<SearchState>>();
		private HashMap<String, IServerMessageHandler<SearchState>> participantHandlers = new HashMap<String, IServerMessageHandler<SearchState>>();

		SearchHandler() {
			protocolHandlers.put(Search.PROTOCOL_NAME, new ProtocolMatchHandler());
			loginHandlers.put(Search.COMMAND_LOGIN, new LoginHandler());

			masterEntryHandlers.put(Search.COMMAND_ENTRY, new RoomEntryHandler());
			masterEntryHandlers.put(Search.COMMAND_LOGOUT, new LogoutHandler());

			masterHandlers.put(Search.COMMAND_LOGOUT, new LogoutHandler());
			masterHandlers.put(Search.COMMAND_UPDATE, new RoomUpdateHandler());
			masterHandlers.put(Search.COMMAND_UPDATE_PLAYER_COUNT, new RoomUpdatePlayerCountHandler());

			participantHandlers.put(Search.COMMAND_SEARCH, new RoomSearchHandler());
			participantHandlers.put(Search.COMMAND_LOGOUT, new LogoutHandler());
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
		public SearchState createState(ISocketConnection connection) {
			SearchState state = new SearchState(connection);
			state.messageHandlers = protocolHandlers;
			return state;
		}

		@Override
		public void disposeState(SearchState state) {
			if (state.entryRoom != null) {
				String address = state.entryRoom.getRoomAddress();
				playRoomEntries.remove(address);

				try {
					indexWriter.deleteDocuments(new Term("address", address));
					indexWriter.commit();

					indexSearcher = null;

				} catch (IOException e) {
					log(Utility.makeStackTrace(e));
				}
			}
		}

		@Override
		public boolean processIncomingData(SearchState state, PacketData data) {
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

				IServerMessageHandler<SearchState> handler = state.messageHandlers.get(command);
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

		private boolean checkRoomEntry(final SearchState state, final InetSocketAddress address, final PlayRoom room, final String authCode) {
			try {
				tcpClient.connect(address, new IAsyncClientHandler() {
					boolean tcpReadSuccess = false;

					@Override
					public void log(ISocketConnection connection, String message) {
						logger.log(connection.getRemoteAddress().toString());
						logger.log(message);
					}

					@Override
					public void connectCallback(ISocketConnection connection) {
						StringBuilder sb = new StringBuilder();
						sb.append(ProtocolConstants.Room.PROTOCOL_NAME);
						sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
						sb.append(ProtocolConstants.PROTOCOL_NUMBER);
						sb.append(ProtocolConstants.MESSAGE_SEPARATOR);

						sb.append(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE);
						sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
						sb.append(room.getMasterName());
						sb.append(ProtocolConstants.ARGUMENT_SEPARATOR);
						sb.append(authCode);

						connection.send(sb.toString());
					}

					@Override
					public void readCallback(ISocketConnection connection, PacketData data) {
						String message = data.getMessage();
						if (ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE.equals(message)) {
							try {
								udpClient.connect(address, new IAsyncClientHandler() {
									boolean udpReadSuccess = false;

									@Override
									public void log(ISocketConnection connection, String message) {
										logger.log(connection.getRemoteAddress().toString());
										logger.log(message);
									}

									@Override
									public void connectCallback(ISocketConnection connection) {
										connection.send(ProtocolConstants.Room.TUNNEL_DUMMY_PACKET);
									}

									@Override
									public void readCallback(ISocketConnection connection, PacketData data) {
										String message = data.getMessage();
										if (!Utility.isEmpty(message)) {
											udpReadSuccess = true;
										}
										connection.disconnect();
									}

									@Override
									public void disconnectCallback(ISocketConnection connection) {
										if (udpReadSuccess) {
											try {
												updateRoomEntry(room);

												state.getConnection().send(Search.COMMAND_ENTRY);
												state.entryRoom = room;
												playRoomEntries.put(room.getRoomAddress(), room);

												state.messageHandlers = masterHandlers;
											} catch (IOException e) {
												state.getConnection().send(Search.ERROR_MASTER_DATABASE_ENTRY);
												state.getConnection().disconnect();
											}
										} else {
											state.getConnection().send(Search.ERROR_MASTER_UDP_PORT);
											state.getConnection().disconnect();
										}
									}
								});
							} catch (IOException e) {
								state.getConnection().send(Search.ERROR_MASTER_UDP_PORT);
								state.getConnection().disconnect();
							}
						} else {
							state.getConnection().send(Search.ERROR_MASTER_INVALID_AUTH_CODE);
							state.getConnection().disconnect();
						}

						tcpReadSuccess = true;
						connection.disconnect();
					}

					@Override
					public void disconnectCallback(ISocketConnection connection) {
						if (!tcpReadSuccess) {
							state.getConnection().send(Search.ERROR_MASTER_TCP_PORT);
							state.getConnection().disconnect();
						}
					}
				});

				return true;
			} catch (IOException e) {
				state.getConnection().send(Search.ERROR_MASTER_TCP_PORT);
				return false;
			}

		}

		private class ProtocolMatchHandler implements IServerMessageHandler<SearchState> {
			String errorMessage = ProtocolConstants.ERROR_PROTOCOL_MISMATCH + ProtocolConstants.ARGUMENT_SEPARATOR
					+ ProtocolConstants.PROTOCOL_NUMBER;

			@Override
			public boolean process(SearchState state, String argument) {
				if (ProtocolConstants.PROTOCOL_NUMBER.equals(argument)) {
					state.messageHandlers = loginHandlers;
					return true;
				} else {
					state.getConnection().send(errorMessage);
					return false;
				}
			}
		}

		private class LoginHandler implements IServerMessageHandler<SearchState> {
			@Override
			public boolean process(SearchState state, String argument) {
				if (Search.MODE_MASTER.equals(argument)) {
					state.messageHandlers = masterEntryHandlers;
				} else if (Search.MODE_PARTICIPANT.equals(argument)) {
					state.messageHandlers = participantHandlers;
				} else {
					return false;
				}
				return true;
			}
		}

		private class LogoutHandler implements IServerMessageHandler<SearchState> {
			@Override
			public boolean process(SearchState state, String argument) {
				return false;
			}
		}

		private class RoomEntryHandler implements IServerMessageHandler<SearchState> {
			private String makeHostName(String s, SearchState state) {
				return s.length() > 0 ? s : state.getConnection().getRemoteAddress().getAddress().getHostAddress();
			}

			@Override
			public boolean process(final SearchState state, String argument) {
				// R authCode hostName:port masterName title currentPlayers
				// maxPlayers hasPassword description
				final String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 8)
					return false;

				try {
					final String authCode = tokens[0];

					String[] address = tokens[1].split(":");
					String hostname = makeHostName(address[0], state);
					int port = Integer.parseInt(address[1]);

					String masterName = tokens[2];
					String title = tokens[3];

					int currentPlayers = Integer.parseInt(tokens[4]);
					int maxPlayers = Integer.parseInt(tokens[5]);
					boolean hasPassword = "Y".equals(tokens[6]);
					String description = Utility.trim(tokens[7], descriptionMaxLength);

					PlayRoom room = new PlayRoom(hostname + ":" + port, masterName, title, hasPassword, currentPlayers, maxPlayers);
					room.setDescription(description);

					InetSocketAddress socketAddress = new InetSocketAddress(hostname, port);

					return checkRoomEntry(state, socketAddress, room, authCode);
				} catch (NumberFormatException e) {
					return false;
				}
			}
		}

		private class RoomUpdateHandler implements IServerMessageHandler<SearchState> {
			@Override
			public boolean process(SearchState state, String argument) {
				if (state.entryRoom == null)
					return false;

				// U title maxPlayers hasPassword description
				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 4)
					return false;

				try {
					String title = tokens[0];
					int maxPlayers = Integer.parseInt(tokens[1]);
					boolean hasPassword = "Y".equals(tokens[2]);
					String description = Utility.trim(tokens[3], descriptionMaxLength);

					PlayRoom room = state.entryRoom;
					room.setTitle(title);
					room.setMaxPlayers(maxPlayers);
					room.setHasPassword(hasPassword);
					room.setDescription(description);

					updateRoomEntry(room);

					return true;
				} catch (NumberFormatException e) {
				} catch (IOException e) {
					log(Utility.makeStackTrace(e));
				}
				return false;
			}
		}

		private class RoomUpdatePlayerCountHandler implements IServerMessageHandler<SearchState> {
			@Override
			public boolean process(SearchState state, String argument) {
				if (state.entryRoom == null)
					return false;

				try {
					// C playerCount
					int playerCount = Integer.parseInt(argument);
					state.entryRoom.setCurrentPlayers(playerCount);
					return true;
				} catch (NumberFormatException e) {
				}
				return false;
			}
		}

		private class RoomSearchHandler implements IServerMessageHandler<SearchState> {
			@Override
			public boolean process(SearchState state, String argument) {
				// S title masterName serverHostName hasPassword

				String[] tokens = argument.split(ProtocolConstants.ARGUMENT_SEPARATOR, -1);
				if (tokens.length != 4)
					return false;

				String title = tokens[0].replace("　", " ").replaceAll(" {2,}", " ").trim();
				String masterName = tokens[1].replace("　", " ").replaceAll(" {2,}", " ").trim();
				String serverName = tokens[2].trim();
				boolean queryHasPassword = "Y".equals(tokens[3]);

				StringBuilder queryBuilder = new StringBuilder();
				if (!Utility.isEmpty(title)) {
					appendQuery(queryBuilder, "title", title);
				}
				if (!Utility.isEmpty(masterName)) {
					appendQuery(queryBuilder, "masterName", masterName);
				}
				if (!Utility.isEmpty(serverName)) {
					if (queryBuilder.length() > 0)
						queryBuilder.append(" AND ");
					queryBuilder.append("serverAddress:").append(QueryParser.escape(serverName));//.append('*');
				}

				if (queryBuilder.length() > 0)
					queryBuilder.append(" AND ");
				queryBuilder.append("hasPassword:").append(queryHasPassword ? 'y' : 'n');

				try {
					Query query = searchParser.parse(queryBuilder.toString());
					//logger.log(query.toString());

					if (indexSearcher == null)
						indexSearcher = new IndexSearcher(ramDirectory);

					TopDocs docs = indexSearcher.search(query, maxSearchResults);
					ScoreDoc[] hits = docs.scoreDocs;

					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < hits.length; i++) {
						Document d = indexSearcher.doc(hits[i].doc);
						String address = d.get("address");
						PlayRoom room = playRoomEntries.get(address);
						if (room == null) {
							logger.log("Defunct document: id=" + address);
							continue;
						}

						sb.append(ProtocolConstants.Search.COMMAND_SEARCH);
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

					sb.append(ProtocolConstants.Search.COMMAND_SEARCH);
					state.getConnection().send(sb.toString());

				} catch (IOException e) {
					log(Utility.makeStackTrace(e));
				} catch (Exception e) {
					log(Utility.makeStackTrace(e));
				}
				return false;
			}
		}
	}

	private static void appendQuery(StringBuilder sb, String field, String query) {
		String[] tokens = query.split(" ");
		for (String s : tokens) {
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
