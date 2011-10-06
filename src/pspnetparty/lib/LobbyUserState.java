package pspnetparty.lib;

import java.util.HashMap;

public enum LobbyUserState {
	OFFLINE("O"), LOGIN("L"), AFK("A"), PLAYING("P"), INACTIVE("I");

	private String abbreviation;

	private LobbyUserState(String s) {
		abbreviation = s;
	}

	public String getAbbreviation() {
		return abbreviation;
	}

	private static HashMap<String, LobbyUserState> STATE_MAP;

	public static LobbyUserState findState(String abbr) {
		if (STATE_MAP == null) {
			STATE_MAP = new HashMap<String, LobbyUserState>();
			for (LobbyUserState s : LobbyUserState.values())
				STATE_MAP.put(s.abbreviation, s);
		}
		return STATE_MAP.get(abbr);
	}

	public static void main(String[] args) {
		for (LobbyUserState state : LobbyUserState.values()) {
			System.out.println(state);
			System.out.println(state.name());
			System.out.println(LobbyUserState.findState(state.abbreviation));
		}
	}
}