package turfwar.model;

import java.awt.Color;

/**
 * All fixed rule parameters of Turf War. These values are part of the specification:
 * every submission must use them unchanged so that the game plays identically across the class.
 */
public final class Config {
    private Config() {}

    /** Arena size in squares. Column 0 is the left edge, row 0 is the top edge. */
    public static final int COLS = 20;
    public static final int ROWS = 15;

    /** A round needs at least this many players. There is no upper limit; each player needs a free colour. */
    public static final int MIN_PLAYERS = 2;

    /** Round length chosen by the host when hosting, in seconds. */
    public static final int DEFAULT_ROUND_SECONDS = 180;
    public static final int MIN_ROUND_SECONDS = 60;
    public static final int MAX_ROUND_SECONDS = 600;

    /** Uses of each power-up type (Line, Block, Wedge) per player per round. Resets every round. */
    public static final int POWERUP_USES = 3;
    /** Bombs per player for the whole match. Unlike power-ups, this is NOT reset when a new round starts. */
    public static final int BOMB_USES = 1;
    /** A Bomb is rejected if it would leave more than this percentage of the arena scorched. */
    public static final int SCORCH_CAP_PERCENT = 30;

    /** Minimum time between two taunts from the same player. */
    public static final long TAUNT_MIN_INTERVAL_MS = 2000;

    /** Usernames: 1 to 16 characters, letters, digits and underscore only. Compared case-insensitively. */
    public static final int MAX_NAME_LENGTH = 16;
    public static final String NAME_PATTERN = "[A-Za-z0-9_]{1,16}";

    /** The 16 player colours. A player is identified on screen by the index into this table. */
    public static final String[] COLOUR_NAMES = {
        "Red", "Blue", "Green", "Orange", "Purple", "Teal", "Pink", "Brown",
        "Navy", "Lime", "Magenta", "Gold", "Sky", "Olive", "Maroon", "Cyan"
    };
    public static final Color[] COLOURS = {
        new Color(0xE53935), new Color(0x1E88E5), new Color(0x43A047), new Color(0xFB8C00),
        new Color(0x8E24AA), new Color(0x00897B), new Color(0xEC407A), new Color(0x795548),
        new Color(0x283593), new Color(0x7CB342), new Color(0xD81B60), new Color(0xC6A700),
        new Color(0x4FC3F7), new Color(0x827717), new Color(0x880E4F), new Color(0x00ACC1)
    };

    /** The eight fixed taunts. Taunt i is sent with Ctrl+(i+1) or its button. */
    public static final String[] TAUNTS = {
        "Nice try!", "Too slow!", "Watch out!", "My turf!",
        "Boom!", "GG", "Help!", "Truce?"
    };

    /** Colour used for neutral (unowned) squares and for scorched squares. */
    public static final Color NEUTRAL_COLOUR = new Color(0xD9D9D9);
    public static final Color SCORCHED_COLOUR = new Color(0x222222);

    /** Maximum number of scorched squares allowed in the arena at any time. */
    public static int scorchCap() {
        return COLS * ROWS * SCORCH_CAP_PERCENT / 100;
    }

    public static boolean validName(String name) {
        return name != null && name.matches(NAME_PATTERN);
    }
}
