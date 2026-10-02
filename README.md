# Turf War scaffold (COMP90015 Assignment 2)

This scaffold contains the complete game window and a server skeleton. The window contains no
networking. It only draws what your code tells it and reports what the player does. The server
skeleton contains nothing but a main method with a TODO.

## Build

Run `./build.sh` on macOS or Linux, or `build.bat` on Windows. This compiles everything and
writes two jars into `dist`.

```
dist/TurfWarClient.jar
dist/TurfWarServer.jar
```

The build needs Java 17 or later and no libraries.

## Run

```
java -jar dist/TurfWarServer.jar 4444
java -jar dist/TurfWarClient.jar 10.0.0.5 4444 alice
```

The server jar currently prints a message and exits. You replace its main method with your server.
The client jar opens the start dialog, which asks for the server address, the port and a username. The server assigns colours. The command-line values only pre-fill the dialog. Choose
Local demo in the dialog to explore the window without a network. Use Game > Start Round, then
paint, use power-ups with keys 1 to 5, and send taunts with Ctrl+1 to Ctrl+8.

You may submit two jars, one for the client and one for the server. You may instead submit three
jars, one for the client, one for the host and one for the server. Both layouts are acceptable.
If you use three jars, add a third entry point and a third `jar` line to the build script.

## What is what

| Package | Contents | May you change it? |
|---|---|---|
| `turfwar.api` | `GameController` is called by the window. `GameView` is called by your code. | No |
| `turfwar.model` | `Config` holds all rule constants. `Tool`, `Phase`, `Square` and `PlayerInfo` are shared types. `Geometry` defines the exact power-up and Bomb shapes. | No |
| `turfwar.ui` | The window and its panels. | Only to fix a bug you report to us |
| `turfwar.demo` | `DemoController` is a single-player stand-in so the window runs. | Replace it |
| `turfwar.server` | `ServerMain` is the entry point of the server jar. It is empty. | Replace it |
| `turfwar.Main` | The entry point of the client jar. It shows the start dialog and creates the controller. | Yes |

Your work goes in new packages, for example `turfwar.net` and `turfwar.server`. You write a
server that holds the state of the match. You also write a client-side `GameController` that
sends requests to the server and updates the window through `GameView`.

## The two interfaces

You implement `GameController`. Its methods are `paint`, `usePowerUp`, `useBomb`, `sendTaunt`,
`startRound`, `pauseOrResume`, `kick`, `respondToJoin`, `leave`, `replayLastRound`, `saveReplay`
and `openReplay`. All are called on the Swing event thread. Return immediately and never block
on the network.

The window implements `GameView`. Its methods are `setLocalPlayer`, `setPlayers`, `setSquare`,
`setArena`, `setPhase`, `setRemainingSeconds`, `setAllowances`, `showTaunt`, `showInfo`,
`showError`, `showRoundOver`, `showJoinRequest` and `showMatchClosed`. They are safe to call
from any thread.

The window never assumes an action succeeded. Nothing changes on screen until your code calls a
`GameView` method. What players see is therefore exactly the state your server has decided.
