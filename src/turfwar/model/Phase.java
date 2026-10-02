package turfwar.model;

/** The state of a match as seen by every client. */
public enum Phase {
    /** Players are gathering; the arena is empty; no arena action is accepted. */
    LOBBY,
    /** The round timer is running and arena actions are accepted. */
    RUNNING,
    /** The host paused the round: timer stopped, arena actions rejected, taunts still allowed. */
    PAUSED,
    /** The timer reached zero: final standings are shown; only the host can start a new round. */
    OVER,
    /** The client is replaying a finished round locally; all input is disabled. */
    REPLAY
}
