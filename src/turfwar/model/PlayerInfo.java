package turfwar.model;

import java.awt.Color;

/** A player as shown on the scoreboard. Immutable snapshot. */
public final class PlayerInfo {
    /** Unique id assigned by the server (never shown to users, may equal the name). */
    public final String id;
    public final String name;
    /** Index into Config.java > COLOURS. */
    public final int colourIndex;
    public final boolean host;
    /** Number of squares currently owned. */
    public final int squares;

    public PlayerInfo(String id, String name, int colourIndex, boolean host, int squares) {
        this.id = id;
        this.name = name;
        this.colourIndex = colourIndex;
        this.host = host;
        this.squares = squares;
    }

    public PlayerInfo withSquares(int n) {
        return new PlayerInfo(id, name, colourIndex, host, n);
    }

    public Color colour() { return Config.COLOURS[colourIndex]; }
    public String colourName() { return Config.COLOUR_NAMES[colourIndex]; }

    @Override public String toString() { return name + "(" + colourName() + "," + squares + ")"; }
}
