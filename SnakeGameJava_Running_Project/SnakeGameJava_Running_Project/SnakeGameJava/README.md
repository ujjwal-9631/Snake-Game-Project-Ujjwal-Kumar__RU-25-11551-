# Snake Game Using Java — Running DSA Project

**Student:** Ujjwal Kumar  
**ERP ID:** RU-25-11551  
**Guide:** Suryamani Verma  
**University:** Rungta International Skills University

## Requirements
- JDK 8 or later
- Visual Studio Code (Java Extension Pack recommended)

## Run in VS Code
1. Extract this ZIP.
2. Open the `SnakeGameJava` folder in VS Code.
3. Open `src/snakegame/Main.java`.
4. Click **Run** above `main`.

## Run using terminal (Windows PowerShell)
From the project folder:
```powershell
javac -encoding UTF-8 -d out src/snakegame/Main.java
java -cp out snakegame.Main
```

## Run using terminal (macOS/Linux)
```bash
mkdir -p out
javac -encoding UTF-8 -d out src/snakegame/Main.java
java -cp out snakegame.Main
```

## Controls
- Arrow keys or W/A/S/D: Move
- Space: Pause / Resume
- On-screen buttons: Restart, Home, Exit

## Features
- Home screen, instructions, about page and high scores
- Snake movement, food collection and growth
- Wall and self-collision detection
- Pause, resume, restart and game-over screen
- Local high-score file (`snake-highscores.txt`)

## DSA explanation
- `LinkedList<Point>` stores the snake body from head to tail.
- Adding a new head and removing the tail are O(1) operations.
- Self-collision checking scans the snake body and is O(N).
- `Random` generates food positions; occupied cells are checked before use.
- Board dimensions and Point coordinates represent the 2D grid.

This project uses only standard Java libraries; no external dependencies are required.
