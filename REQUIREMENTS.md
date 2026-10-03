# COMP90015 Distributed Systems, Assignment 2

## Turf War, a Distributed Multiplayer Game (25 marks)

**Submission Due on Monday 19 October 2026 at 5 PM**

---

## 1. Overview

In this assignment you will build a distributed real-time multiplayer game called Turf War for up to 16 players. Each player uses their own computer to paint squares on a shared grid in their own colour. The player with the most squares when the round timer runs out wins. Section 2 explains how a match is played.

You will build the distributed system that makes this possible, and it has three parts:

- A **server**, which holds the state of the match, decides the order in which player actions take effect, keeps the round clock, and sends every change to every client.
- A **client**, which runs on each player's computer, connects the provided game window to the server, sends the player's actions to the server, and shows the server's replies in the window.
- A **protocol**, which is the set of messages that the client and the server exchange, together with the rules for when each message is sent.

Every player must see the same arena, scoreboard and clock, even when several players act on the same squares simultaneously, when someone joins in the middle of a round, or when someone disconnects without warning.

This assignment exercises four skills covered in lectures:

- You will keep a shared state consistent across many clients while they update it at the same time.
- You will design a message protocol and choose a communication technology such as sockets or RMI.
- You will manage membership. Players join with the host's approval. Some may join only after the round has started. Players may leave cleanly when a game ends or disconnect without warning, and your system must handle every one of these cases.
- You will handle time in a distributed system, where the server's clock must be displayed on every screen.

Unlike Assignment 1, the protocol is not fixed, so you need to design it yourself. The user interface is fixed and we provide it along with these detailed specifications (see Section 4). The rules of the game are fixed too (see Section 5).

**Follow the rules in Section 5 precisely,** because every submission must play the same game so that the teaching team can test every feature and edge case in the same way. If anything in this document is unclear, ask on Ed Discussion rather than guessing.

---

## 2. How Turf War is Played

This section walks through one match from start to finish, and Section 5 gives the precise rules.

1. One player hosts a match by starting the server and joining it as the first player, choosing a round length between 60 and 600 seconds.
2. Other players join by giving the address and port of the server and a username, and the server assigns each of them a colour. The host sees each join request and approves or denies it, and approved players appear on the scoreboard of every client.
3. When the host starts the round, the arena of 20 x 15 grey squares appears on every screen and the countdown begins.
4. Everyone plays at the same time. A player paints a square by clicking it, or paints a trail by dragging across the arena. This takes a square from another player if they already own it. The scoreboard shows how many squares each player owns and updates live.
5. Power-ups let a player claim several squares with one click. The available power-ups are Line, Block and Wedge. Each of these claims a fixed shape around the clicked square. A player selects a power-up with its toolbar button or its number key and then clicks a square. Each power-up can be used three times per round.
6. The bomb destroys a fixed 3x3 area around the click, turning every square in it black so that it stays out of the game for the rest of the round. Each player has only one Bomb for the whole match, not one per round.
7. Players send fixed taunts such as "Too slow!" with the taunt buttons or with Ctrl+1 to Ctrl+8, and the taunts appear on every screen.
8. The host may pause and resume the round and may also remove a player from the match.
9. When the countdown reaches zero, the round ends and every screen shows the final scores and the winner, the player with the most squares. The host may then start another round on an empty arena.
10. After a round, the host can watch a replay of it, save the replay to a file, and open a saved replay later.

Figure 1 in Section 4 shows the game window in the middle of a round.

---

## 3. Architecture

- The system has exactly one server that orders player actions. It may run as a separate program or inside the host's client. Both approaches are acceptable, including a server implemented using Java RMI.
- Each client keeps a copy of the match state for display only and never changes that copy except on instructions from the server.
- The server applies requests one at a time, evaluating each against the state at that moment, and then broadcasts the resulting changes to every client in the same order.
- The server keeps the round clock and broadcasts the remaining time at least once per second. Clients display that time rather than using a clock of their own.
- Communication may use TCP or UDP sockets or Java RMI. Whatever you choose, reliable delivery of updates is your responsibility. A lost update means clients disagree.
- The threading model is a design decision for you to make and justify in your report. The Swing event thread must never block on the network.

---

## 4. Provided Scaffolding Code

The scaffold `turfwar-scaffold.zip` contains the complete graphical user interface and an empty server skeleton, which lets you focus on the distributed system. Every student uses the same interface, so the demonstration and the marks concentrate on the distributed system rather than on Swing programming.

This scaffolding covers the user interface only. The server, all networking, state distribution, membership, timing, disconnect detection and replay must be written by you from scratch. These are the core skills this assignment assesses.

### 4.1. Contents

| Package | Purpose | Contents |
|---------|---------|----------|
| `turfwar.api` | The two interfaces that connect your code to the window. | `GameController` lists what the window asks your code to do, and `GameView` lists what your code tells the window. |
| `turfwar.model` | The shared data types and rule constants that both your code and the window rely on. | `Config` holds every rule constant, `Tool`, `Phase`, `Square` and `PlayerInfo` are the shared data types, and `Geometry` defines the exact squares of each shape. |
| `turfwar.ui` | The game window itself, along with its panels and dialogs. | `MainWindow` implements `GameView`, and the other classes are `ArenaPanel`, `ToolbarPanel`, `ScoreboardPanel`, `TauntPanel`, `StartDialog` and `JoinRequestDialog`. |
| `turfwar.demo` | A throwaway single-player stand-in so the window runs before you have written any networking. | `DemoController` is a single-player stand-in with no network that lets the window run on day one. You replace it with your own client. |
| `turfwar.server` | The entry point of the server jar, which you replace with your own server. | `ServerMain` currently only parses the port and prints a message. |
| `turfwar.Main` | The entry point of the client jar. | This class shows the start dialog, creates the controller, and contains the TODO where your client begins. |

### 4.2. The two interfaces

You implement `GameController`. The window calls it on the Swing event thread, so every method must return immediately. Send the request and return, then update the window later through `GameView`.

**GameController methods (window calls these):**

| Method | The window calls it when |
|--------|--------------------------|
| `paint(col, row)` | The player clicked or dragged over a square with Paint selected. |
| `usePowerUp(tool, col, row)` | The player used Line, Block or Wedge anchored at the square. |
| `useBomb(col, row)` | The player dropped the Bomb, a fixed 3x3 area. |
| `sendTaunt(index)` | The player pressed a taunt button or Ctrl+1 to Ctrl+8. |
| `startRound()` | The host chose Game > Start Round. |
| `pauseOrResume()` | The host chose Game > Pause or Resume. |
| `kick(playerId)` | The host chose Game > Kick Selected Player. |
| `respondToJoin(requestId, approve)` | The host answered a join request dialog. |
| `leave()` | The user chose Game > Leave or closed the window. |
| `replayLastRound()`, `saveReplay(file)`, `openReplay(file)` | The host chose an item of the Replay menu, which is disabled for other players. |

**GameView methods (your code calls these):**

`MainWindow` implements `GameView`. Your code may call it from any thread, since calls are applied in order.

| Method | What it does |
|--------|-------------|
| `setLocalPlayer(me)` | This tells the window who the local player is, with their name, colour and host flag. |
| `setPlayers(list)` | This replaces the scoreboard and tells the arena which colour belongs to each player id. |
| `setSquare(col, row, square)` and `setArena(grid)` | These update one square or the whole arena. |
| `setPhase(phase)` | This sets the phase to LOBBY, RUNNING, PAUSED, OVER or REPLAY. The window enables arena input only in RUNNING. |
| `setRemainingSeconds(s)` | This shows the countdown as decided by the server. |
| `setAllowances(map)` | This shows the remaining uses of each power-up and the Bomb for the local player. |
| `showTaunt(from, text)` | This appends a taunt to the taunt feed. |
| `showInfo(msg)` and `showError(msg)` | These show a message in the status bar, with errors shown in red. The window never shows a modal error dialog. |
| `showRoundOver(winners, standings)` | This shows the round-over dialog and sets the phase to OVER. |
| `showJoinRequest(requestId, name)` | This is used on the host only. It shows a non-blocking dialog with Approve and Deny. |
| `showMatchClosed(reason)` | This shows the reason, then closes the window and exits. |

The window never assumes that an action succeeded. Nothing changes on screen until your code calls a `GameView` method. What the players see is therefore exactly the state your system agreed on.

### 4.3. What you may change

- You must not modify `turfwar.api`, `turfwar.model` or `turfwar.ui`. If you find a bug in them, report it on Ed Discussion, so that we can publish a fix for everyone.
- You may replace `turfwar.demo`, `turfwar.server` and `turfwar.Main`. You may also add any packages you like.
- Marks do not depend on the look of the interface. Any innovations belong in your own code and should be described in the report, for example a smarter protocol, compression of snapshots or reconnection.

### 4.4. Building and running the scaffold

The scaffold builds into two jars. Run `build.sh` on macOS or Linux, or `build.bat` on Windows. The build needs Java 17 or later and no libraries, and it writes the jars into the `dist` folder.

```
java -jar dist/TurfWarServer.jar 4444
java -jar dist/TurfWarClient.jar 10.0.0.5 4444 alice
```

The server jar currently prints a message and exits. You replace its main method with your server. The client jar opens the start dialog, which asks for the server address, the port and a username. The command-line values only pre-fill the dialog. Choose "Local demo" in the dialog to explore the window without a network.

Your submission runs the same way. You may submit two jars, one for the client and one for the server. You may instead submit three jars, one for the client, one for the host and one for the server. Both layouts are acceptable (see Section 11).

---

## 5. The Rules of the Game

This section is the complete rule set and assumes no knowledge of any existing game. Words in bold are defined terms used throughout the document.

### 5.1. The arena

- The **arena** is a grid of 20 columns and 15 rows, giving 300 **squares** in total. Column 0 is the left edge, and row 0 is the top edge. A square is identified by its column and row.
- At every moment each square is in exactly one of three states. A **neutral** square is grey and owned by nobody. An **owned** square belongs to one player and is drawn in that player's colour. A **scorched** square is black with a cross and destroyed.
- At the start of every round, all 300 squares are neutral.

### 5.2. Players

- A **match** supports up to 16 admitted players, including the host, each with a distinct colour and their own client. The Lobby may contain only the host, but at least two players are required to start a round.
- The player who creates the match is the **host**. The host is a normal player who also controls the match by starting rounds, approving joiners, pausing and kicking.
- A **username** is 1 to 16 characters from letters, digits and underscore. Usernames are unique within a match, compared without regard to case.
- The server assigns each player one of the 16 **colours** in Table 1 when the player joins. It gives the lowest numbered colour that is free. Colours are unique within a match, and a player cannot choose their own.

**Table 1: Colour palette**

| 0 Red | 1 Blue | 2 Green | 3 Orange |
|-------|--------|---------|----------|
| 4 Purple | 5 Teal | 6 Pink | 7 Brown |
| 8 Navy | 9 Lime | 10 Magenta | 11 Gold |
| 12 Sky | 13 Olive | 14 Maroon | 15 Cyan |

The exact RGB values are in `Config.COLOURS`.

### 5.3. Phases of a match

| Phase | Meaning | What is accepted |
|-------|---------|-----------------|
| Lobby | The match exists and no round has started yet, so the arena is empty. | The server accepts join requests and Leave. The host may start a round, kick another player or open a saved replay. Arena actions and taunts are rejected. |
| Running | The round clock is counting down. | The server accepts all arena actions, taunts, join requests and Leave, and the host may also pause and kick. |
| Paused | The host stopped the clock. | The server accepts taunts, join requests and Leave, and the host may resume and kick, but arena actions are rejected. |
| Over | The clock reached zero and the final standings are shown. | The server accepts join requests and Leave. The host may start a new round, kick another player or use the Replay menu. Arena actions and taunts are rejected. |

The host chooses the round length when hosting, from 60 to 600 seconds with a default of 180. This length applies to every round of the match. The server keeps the round clock. Every client displays the remaining time it receives from the server, rather than a clock of its own.

When the remaining time reaches zero, the round is over. The winner is the player who owns the most squares, or the round is a draw if several players tie for the most. Every client shows the winner or the draw and the final standings. The final standings and replay are frozen at round end. Later joins or departures update the current match state but do not change the completed round's results or replay. The host may then start a new round. A new round resets every square to neutral, every count to zero, and every power-up allowance to its full value, and players keep their names and colours. The Bomb is the one exception. It is not restored by a new round, because each player has only one for the whole match.

### 5.4. Actions during a round

A player acts by selecting a tool and then clicking a square of the arena. A tool is selected by clicking its toolbar button or by pressing its number key. Paint is the default tool. After a power-up or a Bomb is used, the selected tool automatically returns to Paint, so each power-up click is a deliberate single use.

**Table 2: Tools**

| Tool | Key | Effect of one click on square (c, r) | Uses per player |
|------|-----|--------------------------------------|-----------------|
| Paint | 1 | Square (c, r) becomes yours, and dragging with the button held paints every square the pointer enters. | Unlimited |
| Line | 2 | The 5 squares (c, r), (c+1, r), (c+2, r), (c+3, r) and (c+4, r) become yours. | 3 per round |
| Block | 3 | The 9 squares with column c-1 to c+1 and row r-1 to r+1 become yours. | 3 per round |
| Wedge | 4 | The 6 squares (c, r), (c+1, r), (c+2, r), (c, r+1), (c+1, r+1) and (c, r+2) become yours, forming a right-angled triangle whose corner is the clicked square. | 3 per round |
| Bomb | 5 | The 9 squares with column c-1 to c+1 and row r-1 to r+1 become scorched, whoever owned them. | 1 per match |

The class `turfwar.model.Geometry` computes exactly these sets and is the normative definition.

**Paint.** Clicking a neutral square, or a square owned by another player, makes it yours immediately. This is how painting takes squares from opponents. Clicking your own square or a scorched square does nothing and is not an error. Dragging paints every square the pointer enters while the button is held. Each square is a separate action.

**Power-ups, meaning Line, Block and Wedge.** A power-up takes every square of its shape that is neutral or owned by another player. Squares that are already yours stay untouched, and scorched squares are never affected. Squares of the shape that fall outside the arena are simply ignored. For example, a Line clicked at column 18 takes only columns 18 and 19. Each of the three power-ups can be used 3 times per player per round. Every accepted click consumes a use, even if no square changed. The toolbar shows the uses left and disables a tool with none remaining. The server enforces this limit, so a request beyond it is rejected and nothing changes. A power-up cannot be moved, resized or undone once used.

**Bomb.** The Bomb is destructive. It scorches a fixed 3x3 area centred on the clicked square, whoever owned each square, including squares owned by the bomber. A scorched square is out of the game, so it cannot be painted or taken by any power-up for the rest of the round. This lets the Bomb remove an opponent's squares from an area in one click, and it also denies that area to everyone for the rest of the round. Each player has exactly 1 Bomb for the whole match. This is not reset when a new round starts, unlike the power-ups, so a player who has used their Bomb has none left in later rounds either. At most 30 percent of the arena, or 90 squares, may be scorched at any time. This cap keeps the arena playable. A Bomb that would take the total above 90 is rejected with a message that the arena is too damaged. It is not consumed, and nothing changes. Squares that are already scorched do not count again towards the cap.

**Taunts.** The game is too fast for typing, so players communicate with eight fixed taunts. They are "Nice try!", "Too slow!", "Watch out!", "My turf!", "Boom!", "GG", "Help!" and "Truce?". A taunt is sent with a button or with Ctrl+1 to Ctrl+8, and it appears in the taunt feed of every client with the sender's name. A player may send at most one taunt every 2 seconds. Extra taunts are dropped, and taunts are accepted only while the round is running or paused.

### 5.5. Simultaneous actions and the consistency rule

Several players may act at the same moment, and one rule keeps everyone's screen identical. Exactly one server decides the order in which actions take effect, applying them one at a time. Each action is evaluated against the arena as it is at that moment, so its result is final. Every client shows the server's state. A rejected action changes nothing anywhere.

For example, suppose Alice paints square (5, 5) at the same moment that Bob uses Block centred on (5, 5). If the server applies Alice's action first, the square is red for an instant, and then Bob's Block takes it. The final owner is Bob on every screen, including Alice's. If the server instead applies Bob's action first, Alice takes the square from him. Either order is correct. What would be wrong is Alice's screen showing red while Bob's shows blue. A client may show its own action before the server confirms it, but it must then correct itself when the server's decision arrives.

### 5.6. Joining, leaving and the host

- The first user hosts a match by giving a server address, a port, a username and a round length. This host is a player from the start and receives colour 0.
- Any user can ask to join at any time and in any phase by giving the server address, the port and a username. The host sees a non-blocking dialog with Approve and Deny naming the joiner. The server assigns the new player the lowest numbered free colour. The joiner sees a waiting message until the host answers. On approval, the joiner receives the complete current state, including every square, every player with counts, the phase, the remaining time and full allowances, so they can act immediately if the round is running. On denial, the joiner sees that the host denied the request, and the client exits.
- A player leaves with Game > Leave or by closing the window. Their squares become neutral on every client's current arena, and they are removed from the live scoreboard. If the round has ended, its final standings and replay remain unchanged. Everyone else continues.
- A player whose client crashes or loses the network is treated as having left. The server must notice within 5 seconds, whether by a heartbeat or by the failure of the connection.
- The host may remove any other player at any time (Section 6.2, A3). This turns the removed player's squares neutral. The host cannot kick themselves.
- If the host leaves, crashes or loses the network, the match is over. Within 5 seconds every other client shows that the host left and the match closed, and exits when the user clicks OK. No host handover is required.

---

## 6. Functional Requirements

Every requirement below must hold across the network. The acting player and the observing player are on different clients, normally on different computers. Implement the basic features first, since a working match is what makes any advanced feature possible to demonstrate at all. Each basic and advanced feature is still marked on its own, and a defect in one feature does not by itself cost you marks on an unrelated feature. An advanced feature earns its marks whenever it can be demonstrated, even if some other basic feature has a bug.

### 6.1. Basic features (16 marks)

| Id | Feature | Marks | Required behaviour |
|----|---------|-------|--------------------|
| B1 | Hosting a match | 2 | The first user hosts a match. That user's client starts the server or connects to it, and the user becomes the host. The round length is chosen in the start dialog, from 60 to 600 seconds, and it applies to every round of the match. Only the host can start a round, and only once the match has at least 2 players. Start Round resets all squares to neutral and all scores to zero, and restores three uses each of Line, Block and Wedge per player. Each player's remaining Bomb allowance stays unchanged. |
| B2 | Joining with approval and late-joiner synchronisation | 2 | Any user can ask to join at any time by giving the server address, the port and a username. The host sees a non-blocking join request and can approve or deny it. On approval the joiner receives the complete current state. This includes every square, every player with their counts, the phase, the remaining time and a full set of allowances. A player who joins during a running round can act immediately and sees exactly what everyone else sees. |
| B3 | Membership and scoreboard | 2 | Each player has a unique username, compared without regard to case. A duplicate is rejected with a clear message, and the user can try again. Every client shows the same scoreboard. The scoreboard lists every player in the match with their colour, name, host marker and live square count, sorted by count. |
| B4 | Power-ups | 2 | Line, Block and Wedge behave exactly as defined in Section 5.4. Their shapes must match Table 2 exactly. A power-up takes squares from other players but never affects scorched squares. Squares outside the arena are simply ignored. Each power-up has 3 uses per player per round, enforced by the server. A use is consumed even when nothing changes, and the toolbar of the acting player shows the remaining uses. |
| B5 | Painting | 1 | A click paints one square in the player's colour. A drag paints every square the pointer enters. Squares change on every client without appreciable delay while the player is still dragging. |
| B6 | Bomb | 1 | The Bomb scorches a fixed 3x3 area centred on the clicked square, including the bomber's own squares, and those squares stay scorched for the rest of the round. Each player has exactly 1 Bomb for the whole match, and this does not reset when a new round starts. A Bomb that would exceed the 30 percent scorch cap is rejected with a message and is not consumed. |
| B7 | Round timer and end of round | 1 | The server keeps the round clock. Every client shows a countdown that never differs from the server by more than one second, and it reaches zero on all clients together. At zero the server accepts no further arena action. Every client then shows the winner or the draw and the final standings, and the host can start a new round. If Pause is implemented (see A2), the countdown also stops while the round is paused, but this basic feature does not depend on Pause being implemented at all. |
| B8 | Consistency under simultaneous actions | 1 | When two or more players act on overlapping squares at the same moment, every client must end up showing the same owner for every square. The scoreboard counts must match the arena. This is tested with two clients painting and using power-ups over the same area at once. |
| B9 | Graceful exits | 2 | A player may choose Leave, close the window or disconnect without warning. In every case the player is removed within 5 seconds. Their squares become neutral on every client's current arena, and they are removed from the live scoreboard. If the round has ended, its final standings and replay remain unchanged. If the host leaves or crashes, every other client shows a message that the host left and the match closed, then exits cleanly. No client hangs or crashes in any of these cases. |
| B10 | Distributed technology and architecture | 1 | The submission makes a sound choice of Java networking technology and uses it correctly. The protocol is clear, and the architecture has exactly one server ordering actions. This is assessed in the demonstration discussion and in the report. |
| B11 | Robustness and error handling | 1 | Every rejected action is reported in the status bar with the reason. Invalid input, such as an unreachable server, a wrong port or a bad name, produces a clear message rather than a crash or a silent failure. The program never needs a restart during a normal match. |

### 6.2. Advanced features (5 marks)

| Id | Feature | Marks | Required behaviour |
|----|---------|-------|--------------------|
| A1 | Taunts | 1 | Ctrl+1 to Ctrl+8 or the eight buttons send the corresponding fixed taunt. It appears in the taunt feed of every client with the sender's name within a second. A player can send at most one taunt every 2 seconds. The server drops extra taunts and tells the sender so. Taunts are accepted only in the Running and Paused phases. |
| A2 | Pause and resume | 1 | The host can pause the round. This stops the clock on every client and shows PAUSED everywhere. Every arena action is rejected with a message that the round is paused. If Taunts (A1) is implemented, taunts continue to work while the round is paused. Resume continues the clock from where it stopped. Time spent paused never counts against the round. |
| A3 | Kick | 1 | The host selects a player on the scoreboard and chooses Kick. The kicked player's client shows that they were removed by the host, and exits. Their squares become neutral everywhere, and they may ask to join again. The host cannot kick themselves. |
| A4 | Replay | 2 | The recording includes the initial arena and players with their colours, every accepted paint, power-up and bomb action, and every membership change. It also records the round's duration and final state. Events with the same timestamp retain the server's processing order. Each entry carries its time offset on the round's own clock, the same clock used for the countdown, so time spent paused is not included and a replay has no dead time from pauses. After the round is over, the host can choose Replay Last Round. Playback runs at 4 times speed and reproduces the recorded final state. REPLAY is a local display mode for the host. The server remains in Lobby or Over. Live updates, including membership changes, are received without overwriting the replay display. When playback ends, the display returns to the current server state. If the match closes, playback stops and the normal match-closure flow applies. Save Replay writes the recording to a file, and Open Replay plays a saved file back in the Lobby or Over phase. Only the host can use the Replay menu. The file format is yours to choose, but it must be documented in the report. |

### 6.3. Other functional requirements

- The server address and port are never hard-coded. They come from the start dialog or the command line. This must work both for two clients on one computer and for two clients on two computers.
- Nothing in the user interface freezes while waiting for the network, since every call into your code from the interface returns immediately.
- The program does not print stack traces for expected events such as a player leaving, a rejected action or a lost connection.
- Late joiners deliberately receive full allowances of power-ups and Bombs.

---

## 7. Communication

You must design and document your protocol in the report. The table below gives example messages, not a complete protocol. You may adapt or replace them, but your protocol must support all features you implement.

| Message | Direction | Purpose |
|---------|-----------|---------|
| `JOIN_REQUEST(name)` | client to server | A client sends this to ask to join. |
| `JOIN_APPROVAL_REQUEST(requestId, name)` | server to host | Asks the host to approve or deny a join request. |
| `JOIN_APPROVAL_RESPONSE(requestId, approve)` | host to server | Returns the host's decision for the identified request. |
| `JOIN_DECISION(approved, reason, playerId, colour)` | server to client | The server sends this once the host has decided, carrying the assigned colour. |
| `SNAPSHOT(arena, players, phase, remaining, allowances)` | server to client | The server sends the full state on approval, at round start and whenever a client may be out of date. |
| `PAINT(col,row)`, `POWERUP(tool,col,row)`, `BOMB(col,row)`, `TAUNT(i)` | client to server | A client sends one of these to request an action. It applies nothing locally until the server confirms it, unless the client reconciles later. |
| `REJECTED(requestRef, reason)` | server to client | The server sends this when it refuses a request. Nothing changed. |
| `SQUARES(list of (col,row,state))` | server to all | The server lists the squares that changed, in the order it applied them. |
| `PLAYERS(list)` | server to all | The server sends the scoreboard after any membership or count change. |
| `TIMER(remainingSeconds, phase)` | server to all | The server sends the remaining time at least once per second while the round runs. |
| `ROUND_OVER(winners, standings)` | server to all | The server sends the result once the clock reaches zero. |
| `PAUSE`, `RESUME`, `KICK(playerId)`, `LEAVE` | client to server | A client sends these to control the match. The server checks that host-only requests really come from the host. |
| `CLOSED(reason)` | server to client | The server tells a client that it was kicked or denied, or that the host left. |
| `PING` and `PONG` | both ways | Both sides exchange these as a heartbeat, detecting silent disconnects within 5 seconds. |

The format may be JSON lines, Java serialisation or RMI calls. In every case, the receiving side is responsible for message boundaries. It must also handle a peer that disappears in the middle of a message.

---

## 8. Implementation Language and Technology

- Use Java 17 or later. The scaffold uses only the standard library. Your code may use a networking library or framework if you already know one, though sockets or RMI are sufficient on their own.
- The submission runs from executable jar files without an IDE.
- **Suggested Plan.** In weeks 1 and 2, run the local demo, design the protocol, and connect the window to a server that handles one client and Paint. In weeks 3 and 4, add joining with approval, the scoreboard, snapshots for late joiners, power-ups, the Bomb, the clock and the end of round, testing with two computers acting at once. In week 5, add departures, disconnect detection, host departure and error messages, and then move on to the advanced features and finally the report.

---

## 9. Assessment

| Component | Marks |
|-----------|-------|
| Basic features B1 to B11 (Section 6.1) | 16 |
| Advanced features A1 to A4 (Section 6.2) | 5 |
| Report (Section 10) | 4 |
| **Total** | **25** |

Your submission is assessed in two ways:

- You will demonstrate your system on your own laptop with at least two clients. Tutors will exercise the features of Section 6 and the edge cases in the companion Edge Cases and FAQ document, and they will ask you about your design. You should be able to explain and justify every design decision, in particular how your server orders actions and how disconnects are detected.
- Tutors will read your written report (Section 10).

Marks are awarded only for behaviour observed in the demonstration or read in the report. A feature that works only with both clients on one machine earns at most half of its marks.

---

## 10. Report

The report is at most 6 pages, not counting the title page, and must contain four parts:

1. **Architecture (1 mark):** Introduce the system and describe its architecture, saying which component is the server and why the design keeps clients consistent.
2. **Protocol (1 mark):** Describe the protocol, listing every message with its fields, when it is sent and the reply it produces, and describe the message format.
3. **Diagrams (1 mark):** Provide a class diagram and at least one interaction diagram, each with an explanation, such as a join with approval or two simultaneous actions on the same square.
4. **Implementation (1 mark):** Describe the implementation, covering threading, disconnect detection, clock handling and the replay file format if you implemented it, and state known limitations and any innovations. This part is worth 1 mark and should be clearly structured and presented.

Do not document anything you have not implemented. This is misconduct and will be penalised severely. The report must be a PDF.

---

## 11. Submission

Submit three items individually via the LMS. Do not combine them into one archive.

- Submit the report as a PDF.
- Submit the executable jar files, either `TurfWarClient.jar` and `TurfWarServer.jar`, or `TurfWarClient.jar`, `TurfWarHost.jar` and `TurfWarServer.jar`, including a one-line run instruction for each jar.
- Submit the source files as a .ZIP or .TAR archive.


---

## 12. Demonstration Schedule and Venue

You are free to develop your system wherever you are most comfortable. Keep in mind that this is a distributed system, and it must work across at least two different machines.

The demonstration date, time and venue will be announced closer to the submission due date. Each tutor will hold demonstration sessions, and you will demonstrate in the session held by the tutor of the workshop in which you are enrolled. Bring your own laptop. The demonstration is mandatory and not demonstrating results in a penalty of 10 marks. Demonstrating from an IDE instead of jar files results in a penalty of 2 marks.

If you need any clarification on the assignment, ask during tutorials or on Ed Discussion, so that all students can benefit from the answer.

---

## 13. Penalties for Late Submission

Assignments submitted late will be penalised as follows:

- A submission that is one day late loses 1 mark.
- A submission that is two days late loses 2 marks.
- A submission that is three days late loses 3 marks.
- Each further day of delay costs one more mark, and part days count as full days.
