package turfwar.rmi;

import java.io.Serializable;

// serializable DTO (data transfer object)
// a plain container that carries one player's info over RMI. (server -> client)
public class PlayerData implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String id;
    public final String name;
    public final int colorIndex;
    public final boolean host;
    // the number of squares this player currently owns
    public final int squares;

    public PlayerData(String id, String name, int colorIndex, boolean host, int squares) {
        this.id = id;
        this.name = name;
        this.colorIndex = colorIndex;
        this.host = host;
        this.squares = squares;
    }
}