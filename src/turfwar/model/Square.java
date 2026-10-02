package turfwar.model;

import java.util.Objects;

/** The state of one arena square. Immutable. */
public final class Square {
    public enum Kind { NEUTRAL, OWNED, SCORCHED }

    public static final Square NEUTRAL = new Square(Kind.NEUTRAL, null);
    public static final Square SCORCHED = new Square(Kind.SCORCHED, null);

    public final Kind kind;
    /** Player id of the owner when kind == OWNED, otherwise null. */
    public final String ownerId;

    private Square(Kind kind, String ownerId) {
        this.kind = kind;
        this.ownerId = ownerId;
    }

    public static Square ownedBy(String playerId) {
        return new Square(Kind.OWNED, Objects.requireNonNull(playerId));
    }

    public boolean isNeutral() { return kind == Kind.NEUTRAL; }
    public boolean isScorched() { return kind == Kind.SCORCHED; }
    public boolean isOwned() { return kind == Kind.OWNED; }
    public boolean isOwnedBy(String playerId) { return kind == Kind.OWNED && ownerId.equals(playerId); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Square)) return false;
        Square s = (Square) o;
        return kind == s.kind && Objects.equals(ownerId, s.ownerId);
    }

    @Override public int hashCode() { return Objects.hash(kind, ownerId); }

    @Override public String toString() { return kind == Kind.OWNED ? "OWNED:" + ownerId : kind.name(); }
}
