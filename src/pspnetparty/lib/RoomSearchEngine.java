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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import pspnetparty.lib.constants.ProtocolConstants;
import pspnetparty.lib.constants.ProtocolConstants.Search;

public class RoomSearchEngine {

	private ILogger logger;

	private IServer<SearchState> searchServer;
	private SearchHandler searchHandler;

	private AsyncTcpClient tcpClient;
	private AsyncUdpClient udpClient;

	private PreparedStatement insertRoomStatement;
	private PreparedStatement updateRoomStatement;
	private PreparedStatement updatePlayerCountStatement;
	private PreparedStatement deleteRoomStatement;

	private PreparedStatement searchNonFullRoomStatement;
	private PreparedStatement searchAllRoomStatement;
	private PreparedStatement getAllRoomStatement;

	private int descriptionMaxLength = 100;

	public RoomSearchEngine(Connection dbConn, ILogger logger, String pingSQL) throws SQLException {
		// this.dbConnection = dbConn;
		this.logger = logger;

		searchServer = new AsyncTcpServer<SearchState>();
		searchHandler = new SearchHandler();

		tcpClient = new AsyncTcpClient();
		udpClient = new AsyncUdpClient();

		Statement stmt = dbConn.createStatement();
		stmt.executeUpdate("DELETE FROM rooms");
		stmt.close();

		if (!Utility.isEmpty(pingSQL)) {
			final PreparedStatement pingStatement = dbConn.prepareStatement(pingSQL);
			Thread pingThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						while (searchServer.isListening()) {
							pingStatement.execute();
							Thread.sleep(600000);
						}
					} catch (SQLException e) {
						RoomSearchEngine.this.logger.log(Utility.makeStackTrace(e));
					} catch (InterruptedException e) {
					}
				}
			}, RoomSearchEngine.class.getName() + " PingThread");
			pingThread.setDaemon(true);
			pingThread.start();
		}

		String sql;
		sql = String.format("INSERT INTO rooms (%s,%s,%s,%s,%s,%s,%s) VALUES (?,?,?,?,?,?,?)", Search.DB_COLUMN_ADDRESS,
				Search.DB_COLUMN_MASTER_NAME, Search.DB_COLUMN_TITLE, Search.DB_COLUMN_CURRENT_PLAYERS, Search.DB_COLUMN_MAX_PLAYERS,
				Search.DB_COLUMN_HAS_PASSWORD, Search.DB_COLUMN_DESCRIPTION);
		insertRoomStatement = dbConn.prepareStatement(sql);

		sql = String.format("UPDATE rooms SET %s=?, %s=?, %s=?, %s=? WHERE %s=?", Search.DB_COLUMN_TITLE, Search.DB_COLUMN_MAX_PLAYERS,
				Search.DB_COLUMN_HAS_PASSWORD, Search.DB_COLUMN_DESCRIPTION, Search.DB_COLUMN_ADDRESS);
		updateRoomStatement = dbConn.prepareStatement(sql);

		sql = String.format("UPDATE rooms SET %s=? WHERE %s=?", Search.DB_COLUMN_CURRENT_PLAYERS, Search.DB_COLUMN_ADDRESS);
		updatePlayerCountStatement = dbConn.prepareStatement(sql);

		sql = String.format("DELETE FROM rooms WHERE %s = ?", Search.DB_COLUMN_ADDRESS);
		deleteRoomStatement = dbConn.prepareStatement(sql);

		sql = String.format("SELECT * FROM rooms WHERE %s LIKE ? AND %s LIKE ? AND %s LIKE ? AND %s = ? AND %s < %s",
				Search.DB_COLUMN_ADDRESS, Search.DB_COLUMN_MASTER_NAME, Search.DB_COLUMN_TITLE, Search.DB_COLUMN_HAS_PASSWORD,
				Search.DB_COLUMN_CURRENT_PLAYERS, Search.DB_COLUMN_MAX_PLAYERS);
		searchNonFullRoomStatement = dbConn.prepareStatement(sql);

		sql = String.format("SELECT * FROM rooms WHERE %s LIKE ? AND %s LIKE ? AND %s LIKE ? AND %s = ?", Search.DB_COLUMN_ADDRESS,
				Search.DB_COLUMN_MASTER_NAME, Search.DB_COLUMN_TITLE, Search.DB_COLUMN_HAS_PASSWORD);
		searchAllRoomStatement = dbConn.prepareStatement(sql);

		getAllRoomStatement = dbConn.prepareStatement("SELECT * FROM rooms");
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
		StringBuilder sb;
		try {
			ResultSet rs = getAllRoomStatement.executeQuery();

			sb = new StringBuilder();
			while (rs.next()) {
				sb.append(rs.getString(Search.DB_COLUMN_ADDRESS)).append('\t');
				sb.append(rs.getString(Search.DB_COLUMN_MASTER_NAME)).append('\t');
				sb.append(rs.getString(Search.DB_COLUMN_TITLE)).append('\t');
				sb.append(rs.getInt(Search.DB_COLUMN_CURRENT_PLAYERS));
				sb.append(" / ");
				sb.append(rs.getInt(Search.DB_COLUMN_MAX_PLAYERS));
				sb.append('\n');
			}
		} catch (SQLException e) {
			return Utility.makeStackTrace(e);
		}
		return sb.toString();
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
			if (!Utility.isEmpty(state.address)) {
				try {
					deleteRoomStatement.setString(1, state.address);
					deleteRoomStatement.executeUpdate();
				} catch (SQLException e) {
					log(Utility.makeStackTrace(e));
				}
			}
		}

		@Override
		public boolean processIncomingData(SearchState state, PacketData data) {
			boolean sessionContinue = false;

			for (String message : data.getMessages()) {
				int commandEndIndex = message.indexOf(' ');
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

		private class ProtocolMatchHandler implements IServerMessageHandler<SearchState> {
			String errorMessage = ProtocolConstants.ERROR_PROTOCOL_MISMATCH + " " + ProtocolConstants.PROTOCOL_NUMBER;

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
				// maxPlayers hasPassword
				// "description"
				final String[] tokens = argument.split(" ");
				if (tokens.length != 8)
					return false;

				try {
					final String authCode = tokens[0];

					String[] address = tokens[1].split(":");
					final String hostname = makeHostName(address[0], state);
					final int port = Integer.parseInt(address[1]);

					final String masterName = tokens[2];
					final String title = tokens[3];

					final int currentPlayers = Integer.parseInt(tokens[4]);
					final int maxPlayers = Integer.parseInt(tokens[5]);
					final boolean hasPassword = "Y".equals(tokens[6]);
					final String description = Utility.trim(Utility.removeQuotations(tokens[7]), descriptionMaxLength);

					final InetSocketAddress socketAddress = new InetSocketAddress(hostname, port);

					tcpClient.connect(socketAddress, new IAsyncClientHandler() {
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
							sb.append(' ').append(ProtocolConstants.PROTOCOL_NUMBER);
							sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
							sb.append(ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE);
							sb.append(' ').append(masterName);
							sb.append(' ').append(authCode);

							connection.send(sb.toString());
						}

						@Override
						public void readCallback(ISocketConnection connection, PacketData data) {
							String message = data.getMessage();
							if (ProtocolConstants.Room.COMMAND_CONFIRM_AUTH_CODE.equals(message)) {
								try {
									udpClient.connect(socketAddress, new IAsyncClientHandler() {
										boolean udpReadSuccess = false;

										@Override
										public void log(ISocketConnection connection, String message) {
											logger.log(connection.getRemoteAddress().toString());
											logger.log(message);
										}

										@Override
										public void connectCallback(ISocketConnection connection) {
											connection.send(" ");
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
													String address = hostname + ":" + port + ":" + masterName;

													insertRoomStatement.setString(1, address);
													insertRoomStatement.setString(2, masterName);
													insertRoomStatement.setString(3, title);
													insertRoomStatement.setInt(4, currentPlayers);
													insertRoomStatement.setInt(5, maxPlayers);
													insertRoomStatement.setBoolean(6, hasPassword);
													insertRoomStatement.setString(7, description);

													int c = insertRoomStatement.executeUpdate();
													if (c != 1) {
														logger.log("Room register failed.");
													}

													state.getConnection().send(Search.COMMAND_ENTRY);
													state.address = address;
													state.messageHandlers = masterHandlers;

												} catch (SQLException e) {
													logger.log(Utility.makeStackTrace(e));
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
				} catch (NumberFormatException e) {
					return false;
				} catch (IOException e) {
					state.getConnection().send(Search.ERROR_MASTER_TCP_PORT);
					return false;
				}
			}
		}

		private class RoomUpdateHandler implements IServerMessageHandler<SearchState> {
			@Override
			public boolean process(SearchState state, String argument) {
				// U title maxPlayers hasPassword "description"
				String[] tokens = argument.split(" ");
				try {
					updateRoomStatement.setString(1, tokens[0]);
					updateRoomStatement.setInt(2, Integer.parseInt(tokens[1]));
					updateRoomStatement.setBoolean(3, "Y".equals(tokens[2]));
					updateRoomStatement.setString(4, Utility.trim(Utility.removeQuotations(tokens[3]), descriptionMaxLength));
					updateRoomStatement.setString(5, state.address);

					updateRoomStatement.executeUpdate();
					return true;
				} catch (NumberFormatException e) {
				} catch (SQLException e) {
				}
				return false;
			}
		}

		private class RoomUpdatePlayerCountHandler implements IServerMessageHandler<SearchState> {
			@Override
			public boolean process(SearchState state, String argument) {
				// C playerCount
				try {
					int playerCount = Integer.parseInt(argument);
					updatePlayerCountStatement.setInt(1, playerCount);
					updatePlayerCountStatement.setString(2, state.address);

					updatePlayerCountStatement.executeUpdate();
					return true;
				} catch (NumberFormatException e) {
				} catch (SQLException e) {
				}
				return false;
			}
		}

		private class RoomSearchHandler implements IServerMessageHandler<SearchState> {
			@Override
			public boolean process(SearchState state, String argument) {
				// S title masterName serverHostName includeFullRoom hasPassword

				String[] tokens = argument.split(" ");
				if (tokens.length != 5)
					return false;

				String queryTitle = "%" + tokens[0] + "%"; // Utility.removeQuotations(tokens[0]);
				String queryMasterName = "%" + tokens[1] + "%"; // Utility.removeQuotations(tokens[1]);
				String queryServerHost = "%" + tokens[2] + "%"; // Utility.removeQuotations(tokens[2]);
				boolean includeFullRoom = "Y".equals(tokens[3]);
				boolean queryHasPassword = "Y".equals(tokens[4]);

				PreparedStatement stmt = includeFullRoom ? searchAllRoomStatement : searchNonFullRoomStatement;

				try {
					stmt.setString(1, queryServerHost);
					stmt.setString(2, queryMasterName);
					stmt.setString(3, queryTitle);
					stmt.setBoolean(4, queryHasPassword);

					ResultSet rs = stmt.executeQuery();

					StringBuilder sb = new StringBuilder();
					while (rs.next()) {
						String address = rs.getString(Search.DB_COLUMN_ADDRESS);
						String master = rs.getString(Search.DB_COLUMN_MASTER_NAME);
						String title = rs.getString(Search.DB_COLUMN_TITLE);
						int currentPlayers = rs.getInt(Search.DB_COLUMN_CURRENT_PLAYERS);
						int maxPlayers = rs.getInt(Search.DB_COLUMN_MAX_PLAYERS);
						String description = rs.getString(Search.DB_COLUMN_DESCRIPTION);
						boolean hasPassword = rs.getBoolean(Search.DB_COLUMN_HAS_PASSWORD);

						sb.append(ProtocolConstants.Search.COMMAND_SEARCH);
						sb.append(' ').append(address);
						sb.append(' ').append(master);
						sb.append(' ').append(title);
						sb.append(' ').append(currentPlayers);
						sb.append(' ').append(maxPlayers);
						sb.append(' ').append(hasPassword ? "Y" : "N");
						sb.append(" \"").append(description).append('"');
						sb.append(ProtocolConstants.MESSAGE_SEPARATOR);
					}

					rs.close();

					sb.append(ProtocolConstants.Search.COMMAND_SEARCH);

					state.getConnection().send(sb.toString());

				} catch (SQLException e) {
					log(Utility.makeStackTrace(e));
				}

				return false;
			}
		}
	}
}
