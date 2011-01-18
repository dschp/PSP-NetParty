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
package pspnetparty.client.swing;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

public class ClientFrame extends JFrame {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ClientFrame frame = new ClientFrame();

		frame.setVisible(true);
	}

	public ClientFrame() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setTitle("PSP NetParty Client [Swing]");

		// add(new JButton("TEST"), BorderLayout.NORTH);
		SpringLayout springLayout;
		GridBagLayout gridBagLayout;
		GridBagConstraints gbc;
		Insets insets;

		Font font = new Font("Sans serif", Font.PLAIN, 12);
		UIManager.put("Button.font", font);
		UIManager.put("Label.font", font);
		UIManager.put("ComboBox.font", font);
		UIManager.put("TabbedPane.font", font);
		UIManager.put("Menu.font", font);
		UIManager.put("PopupMenu.font", font);
		UIManager.put("List.font", font);
		UIManager.put("Table.font", font);
		UIManager.put("TableHeader.font", font);
		UIManager.put("PasswordField.font", font);
		UIManager.put("TextArea.font", font);
		UIManager.put("TextPane.font", font);
		UIManager.put("EditorPane.font", font);
		UIManager.put("ToolBar.font", font);
		UIManager.put("ToolTip.font", font);
		UIManager.put("Tree.font", font);

		JTabbedPane mainTabPane = new JTabbedPane();

		JSplitPane arenaMainPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);

		JPanel arenaMainLeftPanel = new JPanel();
		arenaMainPane.setLeftComponent(arenaMainLeftPanel);
		arenaMainLeftPanel.setLayout(new BorderLayout(0, 2));

		JPanel arenaMainLeftServerPanel = new JPanel();
		arenaMainLeftServerPanel.setLayout(new BorderLayout(5, 0));
		arenaMainLeftServerPanel.setBorder(new EmptyBorder(5, 3, 3, 3));
		arenaMainLeftServerPanel.add(new JLabel("アドレス"), BorderLayout.WEST);

		JComboBox serverAddressComboBox = new JComboBox();
		serverAddressComboBox.setMinimumSize(new Dimension(100, serverAddressComboBox.getSize().height));
		arenaMainLeftServerPanel.add(serverAddressComboBox, BorderLayout.CENTER);

		arenaMainLeftServerPanel.add(new JButton("ログイン"), BorderLayout.EAST);

		arenaMainLeftPanel.add(arenaMainLeftServerPanel, BorderLayout.NORTH);

		String[] roomListColumns = { "部屋主", "鍵", "部屋名", "定員" };

		TableModel tableModel = new TableModel() {
			@Override
			public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			}

			@Override
			public void removeTableModelListener(TableModelListener l) {
			}

			@Override
			public boolean isCellEditable(int rowIndex, int columnIndex) {
				return false;
			}

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				return "TEST";
			}

			@Override
			public int getRowCount() {
				return 0;
			}

			@Override
			public String getColumnName(int columnIndex) {
				return "VALUE";
			}

			@Override
			public int getColumnCount() {
				return 5;
			}

			@Override
			public Class<?> getColumnClass(int columnIndex) {
				return String.class;
			}

			@Override
			public void addTableModelListener(TableModelListener l) {
			}
		};

		JTable arenaMainLeftRoomListTable = new JTable(new String[][] {}, roomListColumns);// tableModel);
		// JTable arenaMainLeftRoomListTable = new JTable(tableModel);
		arenaMainLeftRoomListTable.setShowGrid(false);

		JScrollPane arenaMainLeftRoomListPane = new JScrollPane(arenaMainLeftRoomListTable);
		arenaMainLeftPanel.add(arenaMainLeftRoomListPane, BorderLayout.CENTER);

		JPanel arenaMainRightPanel = new JPanel(new BorderLayout(3, 3));
		arenaMainRightPanel.setBorder(new EmptyBorder(2, 2, 1, 2));
		arenaMainPane.setRightComponent(arenaMainRightPanel);

		JSplitPane arenaMainViewPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
		arenaMainRightPanel.add(arenaMainViewPane, BorderLayout.CENTER);
		arenaMainViewPane.setDividerLocation(300);

		JTextPane arenaChatLogPane = new JTextPane();
		arenaChatLogPane.setMinimumSize(new Dimension(100, 100));
		arenaChatLogPane.setEditable(false);
		JScrollPane arenaChatLogScrollPane = new JScrollPane(arenaChatLogPane);
		arenaChatLogScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		arenaMainViewPane.setLeftComponent(arenaChatLogScrollPane);

		JTable arenaPlayerListTable = new JTable(new String[][] {}, new String[] { "名前" });
		JScrollPane arenaPlayerListPane = new JScrollPane(arenaPlayerListTable);
		arenaMainViewPane.setRightComponent(arenaPlayerListPane);

		JPanel arenaChatPanel = new JPanel(new BorderLayout());
		arenaChatPanel.add(new JTextField(), BorderLayout.CENTER);
		arenaChatPanel.add(new JButton("発言"), BorderLayout.EAST);

		arenaMainRightPanel.add(arenaChatPanel, BorderLayout.SOUTH);

		mainTabPane.add("アリーナロビー", arenaMainPane);

		JSplitPane roomMainPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
		roomMainPane.setDividerLocation(200);

		JPanel roomMainLeftPanel = new JPanel();
		roomMainPane.setLeftComponent(roomMainLeftPanel);
		springLayout = new SpringLayout();
		roomMainLeftPanel.setLayout(springLayout);
		roomMainLeftPanel.setMinimumSize(new Dimension(200, 100));
		roomMainLeftPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

		JButton roomInfoExitButton = new JButton("部屋を閉じる");
		springLayout.putConstraint(SpringLayout.NORTH, roomInfoExitButton, 1, SpringLayout.NORTH, roomMainLeftPanel);
		springLayout.putConstraint(SpringLayout.WEST, roomInfoExitButton, 0, SpringLayout.WEST, roomMainLeftPanel);
		roomMainLeftPanel.add(roomInfoExitButton);

		JButton roomInfoMakeButton = new JButton("部屋を作成");
		springLayout.putConstraint(SpringLayout.NORTH, roomInfoMakeButton, 1, SpringLayout.NORTH, roomMainLeftPanel);
		springLayout.putConstraint(SpringLayout.EAST, roomInfoMakeButton, 0, SpringLayout.EAST, roomMainLeftPanel);
		roomMainLeftPanel.add(roomInfoMakeButton);

		gridBagLayout = new GridBagLayout();
		JPanel roomInfoFormPanel = new JPanel(gridBagLayout);
		springLayout.putConstraint(SpringLayout.NORTH, roomInfoFormPanel, 5, SpringLayout.SOUTH, roomInfoExitButton);
		springLayout.putConstraint(SpringLayout.WEST, roomInfoFormPanel, 0, SpringLayout.WEST, roomMainLeftPanel);
		springLayout.putConstraint(SpringLayout.EAST, roomInfoFormPanel, 0, SpringLayout.EAST, roomMainLeftPanel);
		roomMainLeftPanel.add(roomInfoFormPanel);
		// roomInfoFormPanel.setBackground(new Color(255,0,0));

		insets = new Insets(1, 1, 1, 1);

		JLabel roomInfoFormMasterLabel = new JLabel("部屋主");
		gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.insets = insets;
		gridBagLayout.setConstraints(roomInfoFormMasterLabel, gbc);
		roomInfoFormPanel.add(roomInfoFormMasterLabel);

		JTextField roomInfoFormMasterText = new JTextField(20);
		gbc = new GridBagConstraints();
		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		gbc.insets = insets;
		gridBagLayout.setConstraints(roomInfoFormMasterText, gbc);
		roomInfoFormPanel.add(roomInfoFormMasterText);

		JLabel roomInfoFormTitleLabel = new JLabel("部屋名");
		gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.insets = insets;
		gridBagLayout.setConstraints(roomInfoFormTitleLabel, gbc);
		roomInfoFormPanel.add(roomInfoFormTitleLabel);

		JTextField roomInfoFormTitleText = new JTextField();
		gbc = new GridBagConstraints();
		gbc.gridx = 1;
		gbc.gridy = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		gbc.insets = insets;
		gridBagLayout.setConstraints(roomInfoFormTitleText, gbc);
		roomInfoFormPanel.add(roomInfoFormTitleText);

		JLabel roomInfoFormPasswordLabel = new JLabel("パスワード");
		gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.insets = insets;
		gridBagLayout.setConstraints(roomInfoFormPasswordLabel, gbc);
		roomInfoFormPanel.add(roomInfoFormPasswordLabel);

		JTextField roomInfoFormPasswordText = new JTextField();
		gbc = new GridBagConstraints();
		gbc.gridx = 1;
		gbc.gridy = 2;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		gbc.insets = insets;
		gridBagLayout.setConstraints(roomInfoFormPasswordText, gbc);
		roomInfoFormPanel.add(roomInfoFormPasswordText);

		JLabel roomInfoFormMaxPlayersLabel = new JLabel("制限人数");
		gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 3;
		gbc.insets = insets;
		gridBagLayout.setConstraints(roomInfoFormMaxPlayersLabel, gbc);
		roomInfoFormPanel.add(roomInfoFormMaxPlayersLabel);

		JSpinner roomInfoFormMaxPlayersSpiner = new JSpinner(new SpinnerNumberModel(4, 2, 16, 1));
		gbc = new GridBagConstraints();
		gbc.gridx = 1;
		gbc.gridy = 3;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = insets;
		gridBagLayout.setConstraints(roomInfoFormMaxPlayersSpiner, gbc);
		roomInfoFormPanel.add(roomInfoFormMaxPlayersSpiner);

		JLabel roomInfoDescriptionLabel = new JLabel("部屋の紹介・備考");
		springLayout.putConstraint(SpringLayout.NORTH, roomInfoDescriptionLabel, 10, SpringLayout.SOUTH, roomInfoFormPanel);
		springLayout.putConstraint(SpringLayout.WEST, roomInfoDescriptionLabel, 5, SpringLayout.WEST, roomInfoFormPanel);
		roomMainLeftPanel.add(roomInfoDescriptionLabel);

		JTextArea roomInfoDescriptionText = new JTextArea();
		JScrollPane roomInfoDescriptionScrollPane = new JScrollPane(roomInfoDescriptionText);
		springLayout.putConstraint(SpringLayout.NORTH, roomInfoDescriptionScrollPane, 3, SpringLayout.SOUTH, roomInfoDescriptionLabel);
		springLayout.putConstraint(SpringLayout.WEST, roomInfoDescriptionScrollPane, 0, SpringLayout.WEST, roomMainLeftPanel);
		springLayout.putConstraint(SpringLayout.EAST, roomInfoDescriptionScrollPane, 0, SpringLayout.EAST, roomMainLeftPanel);
		springLayout.putConstraint(SpringLayout.SOUTH, roomInfoDescriptionScrollPane, 0, SpringLayout.SOUTH, roomMainLeftPanel);
		roomMainLeftPanel.add(roomInfoDescriptionScrollPane);

		JPanel roomMainRightPanel = new JPanel(new BorderLayout(0, 2));
		roomMainRightPanel.setBorder(new EmptyBorder(0, 2, 2, 0));
		roomMainPane.setRightComponent(roomMainRightPanel);

		JPanel roomWlanAdaptorPanel = new JPanel(new BorderLayout(4, 0));
		roomWlanAdaptorPanel.setBorder(new EmptyBorder(3, 3, 2, 3));
		roomWlanAdaptorPanel.add(new JLabel("無線LANアダプタ"), BorderLayout.WEST);
		roomWlanAdaptorPanel.add(new JComboBox(), BorderLayout.CENTER);
		roomWlanAdaptorPanel.add(new JButton("PSPと通信開始"), BorderLayout.EAST);

		roomMainRightPanel.add(roomWlanAdaptorPanel, BorderLayout.NORTH);

		JSplitPane roomChatViewPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
		roomMainRightPanel.add(roomChatViewPane, BorderLayout.CENTER);
		roomChatViewPane.setDividerLocation(300);

		JTextPane roomChatLogPane = new JTextPane();
		roomChatLogPane.setEditable(false);
		JScrollPane roomChatLogScrollPane = new JScrollPane(roomChatLogPane);
		roomChatLogScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		roomChatViewPane.setLeftComponent(roomChatLogScrollPane);

		JSplitPane roomPlayerViewPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true);
		roomPlayerViewPane.setDividerLocation(140);
		roomChatViewPane.setRightComponent(roomPlayerViewPane);

		JTable roomPlayerListTable = new JTable(new String[][] {}, new String[] { "名前", "PING" });
		JScrollPane roomPlayerListPane = new JScrollPane(roomPlayerListTable);
		roomPlayerViewPane.setLeftComponent(roomPlayerListPane);

		JSplitPane roomPacketMonitorPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true);
		roomPacketMonitorPane.setDividerLocation(70);

		JPanel myPspMonitorPanel = new JPanel(new BorderLayout(0, 3));
		roomPacketMonitorPane.setLeftComponent(myPspMonitorPanel);
		myPspMonitorPanel.setBorder(new EmptyBorder(4, 1, 3, 1));

		JPanel myPspMonitorHeaderPanel = new JPanel(new BorderLayout());
		myPspMonitorHeaderPanel.setBorder(new EmptyBorder(0, 3, 0, 1));
		myPspMonitorHeaderPanel.add(new JLabel("自分のPSP"), BorderLayout.WEST);
		JButton myPspMonitorClearButton = new JButton("クリア");
		myPspMonitorClearButton.setPreferredSize(new Dimension(70, 18));
		myPspMonitorHeaderPanel.add(myPspMonitorClearButton, BorderLayout.EAST);
		myPspMonitorPanel.add(myPspMonitorHeaderPanel, BorderLayout.NORTH);

		JTable myPspMonitorTable = new JTable();
		JScrollPane myPspMonitorScrollPane = new JScrollPane(myPspMonitorTable);
		myPspMonitorPanel.add(myPspMonitorScrollPane, BorderLayout.CENTER);

		JPanel remotePspMonitorPanel = new JPanel(new BorderLayout(0, 3));
		roomPacketMonitorPane.setRightComponent(remotePspMonitorPanel);
		remotePspMonitorPanel.setBorder(new EmptyBorder(4, 1, 3, 1));

		JPanel remotePspMonitorHeaderPanel = new JPanel(new BorderLayout());
		remotePspMonitorHeaderPanel.setBorder(new EmptyBorder(0, 3, 0, 1));
		remotePspMonitorHeaderPanel.add(new JLabel("相手のPSP"), BorderLayout.WEST);
		JButton remotePspMonitorClearButton = new JButton("クリア");
		remotePspMonitorClearButton.setPreferredSize(new Dimension(70, 18));
		remotePspMonitorHeaderPanel.add(remotePspMonitorClearButton, BorderLayout.EAST);
		remotePspMonitorPanel.add(remotePspMonitorHeaderPanel, BorderLayout.NORTH);

		JTable remotePspMonitorTable = new JTable();
		JScrollPane remotePspMonitorScrollPane = new JScrollPane(remotePspMonitorTable);
		remotePspMonitorPanel.add(remotePspMonitorScrollPane, BorderLayout.CENTER);

		roomPlayerViewPane.setRightComponent(roomPacketMonitorPane);

		JPanel roomChatPanel = new JPanel(new BorderLayout());
		roomChatPanel.add(new JTextField(), BorderLayout.CENTER);
		roomChatPanel.add(new JButton("発言"), BorderLayout.EAST);

		roomMainRightPanel.add(roomChatPanel, BorderLayout.SOUTH);

		mainTabPane.add("プレイルーム", roomMainPane);

		springLayout = new SpringLayout();
		JPanel configPanel = new JPanel(springLayout);

		JLabel configUserNameLabel = new JLabel("ユーザー名");
		springLayout.putConstraint(SpringLayout.NORTH, configUserNameLabel, 10, SpringLayout.NORTH, configPanel);
		springLayout.putConstraint(SpringLayout.WEST, configUserNameLabel, 5, SpringLayout.WEST, configPanel);
		configPanel.add(configUserNameLabel);

		JTextField configUserNameTextField = new JTextField(20);
		springLayout.putConstraint(SpringLayout.VERTICAL_CENTER, configUserNameTextField, 1, SpringLayout.VERTICAL_CENTER,
				configUserNameLabel);
		springLayout.putConstraint(SpringLayout.WEST, configUserNameTextField, 5, SpringLayout.EAST, configUserNameLabel);

		configPanel.add(configUserNameTextField);

		JCheckBox configNotShowRoomEnterExitLogCheck = new JCheckBox("チャットログに入退室ログを表示しない");
		springLayout.putConstraint(SpringLayout.NORTH, configNotShowRoomEnterExitLogCheck, 3, SpringLayout.SOUTH, configUserNameTextField);
		configPanel.add(configNotShowRoomEnterExitLogCheck);

		JPanel configThemeSelectorPanel = new JPanel(new FlowLayout());
		springLayout.putConstraint(SpringLayout.NORTH, configThemeSelectorPanel, 3, SpringLayout.SOUTH, configNotShowRoomEnterExitLogCheck);
		configPanel.add(configThemeSelectorPanel);

		ActionListener themeActionListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String lafClassName = e.getActionCommand();

				try {
					UIManager.setLookAndFeel(lafClassName);
					SwingUtilities.updateComponentTreeUI(ClientFrame.this);
				} catch (Exception ex) {
					System.out.println("Error L&F Setting");
				}
			}
		};

		for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
			JButton button = new JButton(info.getName());
			button.addActionListener(themeActionListener);
			button.setActionCommand(info.getClassName());
			configThemeSelectorPanel.add(button);
		}

		mainTabPane.add("設定", configPanel);

		JTextPane logTextPane = new JTextPane();
		logTextPane.setEditable(false);
		JScrollPane logTextScrollPane = new JScrollPane(logTextPane);
		logTextScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

		mainTabPane.add("ログ", logTextScrollPane);

		add(mainTabPane, BorderLayout.CENTER);

		JPanel statusBarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		add(statusBarPanel, BorderLayout.SOUTH);
		statusBarPanel.add(new JLabel("サーバーアドレス"));
		statusBarPanel.add(new JLabel("サーバーステータス"));
		statusBarPanel.add(new JLabel("トラフィック"));

		setSize(750, 500);
		setMinimumSize(new Dimension(400, 300));
		setLocationRelativeTo(null);
	}
}
