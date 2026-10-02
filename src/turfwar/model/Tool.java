package turfwar.model;

/** The tools a player can select. Keys 1 to 5 select them from the keyboard. */
public enum Tool {
    PAINT("Paint", '1'),
    LINE("Line", '2'),
    BLOCK("Block", '3'),
    WEDGE("Wedge", '4'),
    BOMB("Bomb", '5');

    public final String label;
    public final char key;

    Tool(String label, char key) {
        this.label = label;
        this.key = key;
    }

    /** Line, Block and Wedge are power-ups; Paint and Bomb are not. */
    public boolean isPowerUp() {
        return this == LINE || this == BLOCK || this == WEDGE;
    }
}
