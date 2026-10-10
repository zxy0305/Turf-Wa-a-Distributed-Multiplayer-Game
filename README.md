# Turf War (COMP90015 Assignment 2)

A distributed multiplayer game for up to 16 players, built on Java RMI. One server holds the match
state and decides the order of all actions; every client only sends requests and displays what the
server tells it. The game window is the provided one (`turfwar.ui`); the networking, server,
membership, clock and replay are in `turfwar.rmi`, `turfwar.server` and `turfwar.client`.

## Build

Run `./build.sh` on macOS or Linux, or `build.bat` on Windows. This compiles everything and
writes two jars into `dist`.

```
dist/TurfWarClient.jar
dist/TurfWarServer.jar
```

The build needs Java 17 or later and no libraries.

## Run

One line per jar:

```
java -jar TurfWarClient.jar [address] [port] [username]
java -jar TurfWarServer.jar <port>
```

- **TurfWarClient.jar** opens the start dialog (server address, port, username). The command-line
  values only pre-fill the dialog.
  - **Host a match:** choose "Host a match", enter a port, your username and the round length
    (60 to 600 s). The host's client starts the server itself, so no other program is needed.
  - **Join a match:** choose "Join a match" and enter the host's IP address, the same port and a
    username. The host approves or denies the request.
  - "Local demo" runs the window without a network.
- **TurfWarServer.jar** (optional) runs a standalone server on the given port. The first player who
  connects to it becomes the host. Use it instead of "Host a match" if the server should run on a
  separate machine.

The machines must be able to reach each other on the chosen port (same network, no blocking
firewall).

## Playing

The host starts a round with Game > Start Round (at least 2 players). Paint by clicking or
dragging, select tools with keys 1 to 5 or the toolbar (Paint, Line, Block, Wedge, Bomb), and send
taunts with Ctrl+1 to Ctrl+8. The host can also pause and resume, kick a selected player, and
after a round use the Replay menu (replay the last round, save it to a file, open a saved file).

## Code layout

| Package | Contents |
|---|---|
| `turfwar.api`, `turfwar.model`, `turfwar.ui` | Provided: interfaces, rule constants and shared types, the game window |
| `turfwar.rmi` | The protocol: `IGameServer` (client to server), `IClientCallback` (server to client), `PlayerData` |
| `turfwar.server` | `GameServerImpl` (all match state), `ServerPlayer`, `ReplayLog`, `ServerMain` (standalone server entry point) |
| `turfwar.client` | `ClientController` (implements `GameController`), `ClientCallbackImpl` (receives server pushes), `ReplayPlayer` |
| `turfwar.Main` | Entry point of the client jar; also starts the server in-process in "Host a match" mode |
| `turfwar.demo` | Provided single-player stand-in used by "Local demo" |
