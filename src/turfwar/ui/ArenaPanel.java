package turfwar.ui;

import turfwar.model.Config;
import turfwar.model.Geometry;
import turfwar.model.Square;
import turfwar.model.Tool;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The arena: a grid of COLS x ROWS squares. Shows the square states it is given and reports the
 * local player's clicks and drags. It never changes a square by itself.
 */
public final class ArenaPanel extends JPanel {

    /** Receives the local player's input. */
    public interface Input {
        void paint(int col, int row);
        void powerUp(Tool tool, int col, int row);
        void bomb(int col, int row);
    }

    private static final int PREFERRED_CELL = 34;

    private final Square[][] grid = new Square[Config.COLS][Config.ROWS];
    private final Map<String, Color> colours = new HashMap<>();
    private Input input;
    private Tool tool = Tool.PAINT;
    private boolean inputEnabled = false;
    private Point hover;
    private Point lastDragged;

    public ArenaPanel() {
        for (int c = 0; c < Config.COLS; c++)
            for (int r = 0; r < Config.ROWS; r++) grid[c][r] = Square.NEUTRAL;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(Config.COLS * PREFERRED_CELL + 1, Config.ROWS * PREFERRED_CELL + 1));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                Point cell = cellAt(e.getX(), e.getY());
                if (cell == null || !inputEnabled || input == null) return;
                if (tool == Tool.PAINT) {
                    lastDragged = cell;
                    input.paint(cell.x, cell.y);
                } else if (tool == Tool.BOMB) {
                    input.bomb(cell.x, cell.y);
                } else {
                    input.powerUp(tool, cell.x, cell.y);
                }
            }
            @Override public void mouseDragged(MouseEvent e) {
                Point cell = cellAt(e.getX(), e.getY());
                hover = cell;
                repaint();
                if (cell == null || !inputEnabled || input == null || tool != Tool.PAINT) return;
                if (cell.equals(lastDragged)) return;
                lastDragged = cell;
                input.paint(cell.x, cell.y);
            }
            @Override public void mouseReleased(MouseEvent e) { lastDragged = null; }
            @Override public void mouseMoved(MouseEvent e) { hover = cellAt(e.getX(), e.getY()); repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = null; repaint(); }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setInput(Input input) { this.input = input; }

    public void setSquare(int col, int row, Square s) {
        if (Geometry.inArena(col, row)) { grid[col][row] = s; repaint(); }
    }

    public void setArena(Square[][] squares) {
        for (int c = 0; c < Config.COLS; c++)
            for (int r = 0; r < Config.ROWS; r++) grid[c][r] = squares[c][r] == null ? Square.NEUTRAL : squares[c][r];
        repaint();
    }

    public Square[][] snapshot() {
        Square[][] copy = new Square[Config.COLS][Config.ROWS];
        for (int c = 0; c < Config.COLS; c++) System.arraycopy(grid[c], 0, copy[c], 0, Config.ROWS);
        return copy;
    }

    /** Colour to draw for each player id. Unknown owners are drawn dark grey. */
    public void setPlayerColours(Map<String, Color> byPlayerId) {
        colours.clear();
        colours.putAll(byPlayerId);
        repaint();
    }

    public void setTool(Tool tool) { this.tool = tool; repaint(); }
    public void setInputEnabled(boolean enabled) { this.inputEnabled = enabled; if (!enabled) lastDragged = null; repaint(); }

    // ------------------------------------------------------------------ drawing

    private int cellSize() {
        return Math.max(4, Math.min((getWidth() - 1) / Config.COLS, (getHeight() - 1) / Config.ROWS));
    }
    private int originX() { return (getWidth() - cellSize() * Config.COLS) / 2; }
    private int originY() { return (getHeight() - cellSize() * Config.ROWS) / 2; }

    private Point cellAt(int x, int y) {
        int size = cellSize();
        int c = (x - originX()) / size, r = (y - originY()) / size;
        if (x < originX() || y < originY() || !Geometry.inArena(c, r)) return null;
        return new Point(c, r);
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int size = cellSize(), ox = originX(), oy = originY();
        for (int c = 0; c < Config.COLS; c++) {
            for (int r = 0; r < Config.ROWS; r++) {
                Square s = grid[c][r];
                int x = ox + c * size, y = oy + r * size;
                g.setColor(colourFor(s));
                g.fillRect(x, y, size, size);
                if (s.isScorched()) {
                    g.setColor(new Color(0x666666));
                    g.drawLine(x + 4, y + 4, x + size - 4, y + size - 4);
                    g.drawLine(x + size - 4, y + 4, x + 4, y + size - 4);
                }
                g.setColor(Color.WHITE);
                g.drawRect(x, y, size, size);
            }
        }
        g.setColor(new Color(0x555555));
        g.drawRect(ox, oy, size * Config.COLS, size * Config.ROWS);

        if (hover != null && inputEnabled) {
            List<Point> preview = Geometry.squares(tool, hover.x, hover.y);
            g.setColor(tool == Tool.BOMB ? Color.BLACK : new Color(0x1A1A1A));
            g.setStroke(new BasicStroke(2f));
            for (Point p : preview)
                g.drawRect(ox + p.x * size + 1, oy + p.y * size + 1, size - 2, size - 2);
        }
    }

    private Color colourFor(Square s) {
        if (s.isNeutral()) return Config.NEUTRAL_COLOUR;
        if (s.isScorched()) return Config.SCORCHED_COLOUR;
        return colours.getOrDefault(s.ownerId, Color.DARK_GRAY);
    }
}
