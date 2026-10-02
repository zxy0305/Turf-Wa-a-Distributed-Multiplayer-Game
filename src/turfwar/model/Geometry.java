package turfwar.model;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * The exact set of squares affected by each tool. This class is the normative definition of the
 * power-up and Bomb shapes; the specification describes the same shapes in words.
 * Squares that fall outside the arena are simply omitted.
 */
public final class Geometry {
    private Geometry() {}

    public static boolean inArena(int col, int row) {
        return col >= 0 && col < Config.COLS && row >= 0 && row < Config.ROWS;
    }

    /**
     * @param tool the tool used (for PAINT only the anchor square is returned)
     * @param col  anchor column (the square that was clicked)
     * @param row  anchor row
     */
    public static List<Point> squares(Tool tool, int col, int row) {
        List<Point> out = new ArrayList<>();
        switch (tool) {
            case PAINT:
                add(out, col, row);
                break;
            case LINE: // the anchor and the four squares to its right
                for (int i = 0; i < 5; i++) add(out, col + i, row);
                break;
            case BLOCK: // fall through: Block and Bomb both affect the same 3 x 3 square centred on
            case BOMB:  // the anchor. Bomb differs only in what it does to those squares (it scorches
                addSquare(out, col, row); // them permanently) and how often it can be used.
                break;
            case WEDGE: // a right-angled triangle: rows of 3, 2 and 1 going down from the anchor
                add(out, col, row); add(out, col + 1, row); add(out, col + 2, row);
                add(out, col, row + 1); add(out, col + 1, row + 1);
                add(out, col, row + 2);
                break;
            default:
                throw new IllegalArgumentException("unknown tool " + tool);
        }
        return out;
    }

    private static void addSquare(List<Point> out, int col, int row) {
        for (int dr = -1; dr <= 1; dr++)
            for (int dc = -1; dc <= 1; dc++) add(out, col + dc, row + dr);
    }

    private static void add(List<Point> out, int col, int row) {
        if (inArena(col, row)) out.add(new Point(col, row));
    }
}
