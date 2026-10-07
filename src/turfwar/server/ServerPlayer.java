package turfwar.server;

import turfwar.model.Config;
import turfwar.model.Tool;
import turfwar.rmi.IClientCallback;

import java.util.EnumMap;
import java.util.Map;


// ServerPlayer is only on the server and never sent,
// the lifetime is from join until leave, kick or timeout
class ServerPlayer {
    final String id;
    final String name;
    final int colorIndex;
    final boolean isHost;
    final IClientCallback callback;
    final Map<Tool, Integer> powerUpUses = new EnumMap<>(Tool.class);
    // 1 per match, NOT reset between rounds
    int bombRemaining = Config.BOMB_USES;
    // lastPongTime is the last time the server heard from this player
    // The server uses it to detect clients have crashed or disconnected without calling leave()
    long lastPongTime;
    // time of the last accepted taunt, used for the 1 taunt / 2 s limit
    long lastTauntTime;

    ServerPlayer(String id, String name, int colorIndex, boolean isHost,
                 IClientCallback callback) {
        this.id = id;
        this.name = name;
        this.colorIndex = colorIndex;
        this.isHost = isHost;
        this.callback = callback;
        this.lastPongTime = System.currentTimeMillis();
        resetPowerUps();
    }

    // resetPowerUps() refills this player's power-up counters to full
    // So the method is called:
    // 1. in the constructor, so a new player starts with full power-ups, and
    // 2. at the start of every round, for every player
    // (bombs are NOT reset: they are once per match)
    void resetPowerUps() {
        powerUpUses.put(Tool.LINE, Config.POWERUP_USES);

        powerUpUses.put(Tool.BLOCK, Config.POWERUP_USES);

        powerUpUses.put(Tool.WEDGE, Config.POWERUP_USES);
    }
}