 package snakegame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.Point;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * SNAKE GAME USING JAVA
 * Student: Ujjwal Kumar | ERP: RU-25-11551
 * DSA: LinkedList, arrays/coordinates, random number generation, collision checks.
 *
 * Run:
 *   javac -encoding UTF-8 -d out src/snakegame/Main.java
 *   java -cp out snakegame.Main
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new SnakeApplication().setVisible(true);
        });
    }
}

/* ----------------------------- CONFIGURATION ----------------------------- */
final class Config {
    private Config() {}
    static final String TITLE = "Snake Game | Java DSA Project";
    static final int CELL = 24;
    static final int COLS = 25;
    static final int ROWS = 20;
    static final int BOARD_W = CELL * COLS;
    static final int BOARD_H = CELL * ROWS;
    static final int START_LENGTH = 4;
    static final int START_DELAY = 125;
    static final int MIN_DELAY = 55;
    static final int SPEED_STEP = 4;
    static final int FOOD_POINTS = 10;
    static final int BONUS_POINTS = 25;
    static final int BONUS_CHANCE = 12;
    static final int BONUS_TICKS = 45;
    static final String SCORE_FILE = "snake-highscores.txt";

    static final Color NAVY = new Color(22, 39, 61);
    static final Color NAVY2 = new Color(39, 59, 82);
    static final Color GREEN = new Color(29, 151, 83);
    static final Color GREEN_DARK = new Color(19, 111, 61);
    static final Color GREEN_PALE = new Color(231, 246, 237);
    static final Color BG = new Color(247, 249, 251);
    static final Color TEXT = new Color(49, 64, 80);
    static final Color MUTED = new Color(112, 127, 144);
    static final Color BORDER = new Color(220, 228, 236);
    static final Color RED = new Color(205, 68, 68);
    static final Color GOLD = new Color(225, 169, 50);
    static final Color GRID = new Color(233, 238, 243);

    static Font font(int size, int style) {
        return new Font("SansSerif", style, size);
    }
}

/* -------------------------------- ENUMS ----------------------------------- */
enum Direction {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);
    final int dx, dy;
    Direction(int dx, int dy) { this.dx = dx; this.dy = dy; }
    Point next(Point p) { return new Point(p.x + dx, p.y + dy); }
    boolean opposite(Direction d) { return d != null && dx + d.dx == 0 && dy + d.dy == 0; }
    static Direction fromKey(int key) {
        switch (key) {
            case KeyEvent.VK_UP: case KeyEvent.VK_W: return UP;
            case KeyEvent.VK_DOWN: case KeyEvent.VK_S: return DOWN;
            case KeyEvent.VK_LEFT: case KeyEvent.VK_A: return LEFT;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: return RIGHT;
            default: return null;
        }
    }
}
enum State { MENU, PLAYING, PAUSED, GAME_OVER }

/* ------------------------------- SNAKE MODEL ------------------------------ */
/**
 * The body is stored in a LinkedList from head to tail.
 * addFirst() and removeLast() are O(1).
 */
class Snake {
    private final LinkedList<Point> body = new LinkedList<>();
    private Direction direction = Direction.RIGHT;
    private Direction requested = Direction.RIGHT;

    Snake() { reset(); }

    void reset() {
        body.clear();
        int x = Config.COLS / 2;
        int y = Config.ROWS / 2;
        for (int i = 0; i < Config.START_LENGTH; i++)
            body.add(new Point(x - i, y));
        direction = Direction.RIGHT;
        requested = Direction.RIGHT;
    }

    Point head() { return new Point(body.getFirst()); }
    Point tail() { return new Point(body.getLast()); }
    int length() { return body.size(); }
    Direction direction() { return direction; }

    void request(Direction next) {
        if (next != null && !direction.opposite(next)) requested = next;
    }

    Point nextHead() { return requested.next(head()); }

    void move(boolean grow) {
        direction = requested;
        body.addFirst(direction.next(head()));
        if (!grow) body.removeLast();
    }

    boolean contains(Point p) { return body.contains(p); }

    boolean containsExceptTail(Point p) {
        for (int i = 0; i < body.size() - 1; i++)
            if (body.get(i).equals(p)) return true;
        return false;
    }

    List<Point> copyBody() {
        List<Point> result = new ArrayList<>();
        for (Point p : body) result.add(new Point(p));
        return result;
    }
}

/* -------------------------------- FOOD ------------------------------------ */
class Food {
    private final Random random = new Random();
    private Point position = new Point(2, 2);
    private boolean bonus;
    private int bonusLife;

    void spawn(Snake snake, boolean mayBeBonus) {
        Point candidate;
        int guard = 0;
        do {
            candidate = new Point(random.nextInt(Config.COLS), random.nextInt(Config.ROWS));
            guard++;
        } while (snake.contains(candidate) && guard < 10000);
        position = candidate;
        bonus = mayBeBonus && random.nextInt(100) < Config.BONUS_CHANCE;
        bonusLife = bonus ? Config.BONUS_TICKS : 0;
    }

    Point position() { return new Point(position); }
    boolean isBonus() { return bonus; }
    int points() { return bonus ? Config.BONUS_POINTS : Config.FOOD_POINTS; }

    void tick() {
        if (bonus && --bonusLife <= 0) bonus = false;
    }
}

/* ------------------------------ SCORE MODEL ------------------------------- */
class ScoreBoard {
    private int score;
    private int foodCount;
    private int bonusCount;
    private int bestSession;

    void reset() { score = 0; foodCount = 0; bonusCount = 0; }
    void collect(int points, boolean isBonus) {
        score += points;
        foodCount++;
        if (isBonus) bonusCount++;
        bestSession = Math.max(bestSession, score);
    }
    int score() { return score; }
    int foodCount() { return foodCount; }
    int bonusCount() { return bonusCount; }
    int bestSession() { return bestSession; }
}

/* ------------------------------ GAME ENGINE ------------------------------- */
class GameEngine {
    private final Snake snake = new Snake();
    private final Food food = new Food();
    private final ScoreBoard scores = new ScoreBoard();
    private State state = State.MENU;
    private int delay = Config.START_DELAY;
    private String message = "Press Play Game to begin";

    void start() {
        snake.reset();
        scores.reset();
        delay = Config.START_DELAY;
        food.spawn(snake, false);
        state = State.PLAYING;
        message = "Collect food and avoid collisions";
    }

    void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            message = "Game paused";
        }
    }

    void resume() {
        if (state == State.PAUSED) {
            state = State.PLAYING;
            message = "Game resumed";
        }
    }

    void togglePause() {
        if (state == State.PLAYING) pause();
        else if (state == State.PAUSED) resume();
    }

    void menu() { state = State.MENU; message = "Welcome to Snake Game"; }
    void requestDirection(Direction d) { if (state == State.PLAYING) snake.request(d); }

    void update() {
        if (state != State.PLAYING) return;
        food.tick();
        Point next = snake.nextHead();
        boolean eating = next.equals(food.position());

        if (wallHit(next) || bodyHit(next, eating)) {
            state = State.GAME_OVER;
            message = "Game Over";
            return;
        }

        boolean bonus = food.isBonus();
        int points = food.points();
        snake.move(eating);
        if (eating) {
            scores.collect(points, bonus);
            food.spawn(snake, true);
            delay = Math.max(Config.MIN_DELAY, delay - Config.SPEED_STEP);
            message = bonus ? "Bonus food collected!" : "Food collected!";
        }
    }

    private boolean wallHit(Point p) {
        return p.x < 0 || p.y < 0 || p.x >= Config.COLS || p.y >= Config.ROWS;
    }

    private boolean bodyHit(Point p, boolean eating) {
        if (!eating && p.equals(snake.tail())) return false;
        return snake.containsExceptTail(p) || (eating && snake.contains(p));
    }

    Snake snake() { return snake; }
    Food food() { return food; }
    ScoreBoard scores() { return scores; }
    State state() { return state; }
    int delay() { return delay; }
    String message() { return message; }
}

/* ---------------------------- HIGH SCORE STORE ---------------------------- */
class HighScoreStore {
    private final List<String> rows = new ArrayList<>();
    HighScoreStore() { load(); }

    private void load() {
        rows.clear();
        Path path = Paths.get(Config.SCORE_FILE);
        if (!Files.exists(path)) return;
        try {
            rows.addAll(Files.readAllLines(path));
        } catch (IOException ex) {
            System.err.println("High-score file could not be read: " + ex.getMessage());
        }
    }

    void add(String name, int score) {
        if (score <= 0) return;
        rows.add(name + "|" + score + "|" +
            new SimpleDateFormat("dd MMM yyyy HH:mm").format(new Date()));
        rows.sort((a, b) -> Integer.compare(scoreOf(b), scoreOf(a)));
        while (rows.size() > 10) rows.remove(rows.size() - 1);
        try { Files.write(Paths.get(Config.SCORE_FILE), rows); }
        catch (IOException ex) {
            System.err.println("High-score file could not be saved: " + ex.getMessage());
        }
    }

    private int scoreOf(String row) {
        try { return Integer.parseInt(row.split("\\|")[1]); }
        catch (Exception ex) { return 0; }
    }

    int best() { return rows.isEmpty() ? 0 : scoreOf(rows.get(0)); }
    List<String> entries() { return new ArrayList<>(rows); }
}

/* ------------------------------ UI COMPONENTS ----------------------------- */
class ActionButton extends JButton {
    private final Color normal;
    private final Color hover;

    ActionButton(String text) { this(text, Config.GREEN, Config.GREEN_DARK, Color.WHITE); }

    ActionButton(String text, Color normal, Color hover, Color foreground) {
        super(text);
        this.normal = normal;
        this.hover = hover;
        setFont(Config.font(13, Font.BOLD));
        setForeground(foreground);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(11, 20, 11, 20));
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getModel().isRollover() ? hover : normal);
        g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
        g.dispose();
        super.paintComponent(graphics);
    }
}

class CardPanel extends JPanel {
    CardPanel() {
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Config.BORDER),
            new EmptyBorder(12, 16, 12, 16)
        ));
    }
}

/* -------------------------------- GAME BOARD ------------------------------ */
class BoardView extends JPanel {
    private final GameEngine engine;

    BoardView(GameEngine engine) {
        this.engine = engine;
        setPreferredSize(new Dimension(Config.BOARD_W, Config.BOARD_H));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(Config.BORDER));
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawGrid(g);
        drawFood(g);
        drawSnake(g);
        drawStateOverlay(g);
        g.dispose();
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(Config.GRID);
        for (int x = 0; x <= Config.COLS; x++)
            g.drawLine(x * Config.CELL, 0, x * Config.CELL, Config.BOARD_H);
        for (int y = 0; y <= Config.ROWS; y++)
            g.drawLine(0, y * Config.CELL, Config.BOARD_W, y * Config.CELL);
    }

    private void drawFood(Graphics2D g) {
        Point p = engine.food().position();
        int x = p.x * Config.CELL;
        int y = p.y * Config.CELL;
        g.setColor(engine.food().isBonus() ? Config.GOLD : Config.RED);
        g.fillOval(x + 4, y + 4, Config.CELL - 8, Config.CELL - 8);
        g.setColor(Color.WHITE);
        g.fillOval(x + 8, y + 7, 4, 4);
    }

    private void drawSnake(Graphics2D g) {
        List<Point> body = engine.snake().copyBody();
        for (int i = body.size() - 1; i >= 0; i--) {
            Point p = body.get(i);
            int x = p.x * Config.CELL;
            int y = p.y * Config.CELL;
            g.setColor(i == 0 ? Config.GREEN_DARK : Config.GREEN);
            g.fillRoundRect(x + 2, y + 2, Config.CELL - 4, Config.CELL - 4, 8, 8);
            if (i == 0) drawEyes(g, x, y);
        }
    }

    private void drawEyes(Graphics2D g, int x, int y) {
        g.setColor(Color.WHITE);
        g.fillOval(x + 7, y + 6, 4, 4);
        g.fillOval(x + 15, y + 6, 4, 4);
        g.setColor(Config.NAVY);
        g.fillOval(x + 8, y + 7, 2, 2);
        g.fillOval(x + 16, y + 7, 2, 2);
    }

    private void drawStateOverlay(Graphics2D g) {
        State state = engine.state();
        if (state == State.PLAYING) return;
        g.setColor(new Color(255, 255, 255, 210));
        g.fillRect(0, 0, getWidth(), getHeight());
        String title = state == State.PAUSED ? "PAUSED" :
                       state == State.GAME_OVER ? "GAME OVER" : "READY";
        g.setFont(Config.font(30, Font.BOLD));
        g.setColor(Config.NAVY);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (getWidth() - fm.stringWidth(title)) / 2, getHeight() / 2);
    }
}

/* -------------------------------- MENU VIEW ------------------------------- */
class MenuView extends JPanel {
    MenuView(Runnable play, Runnable instructions, Runnable scores,
             Runnable about, Runnable exit) {
        setLayout(new GridBagLayout());
        setBackground(Config.BG);
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel small = label("DATA STRUCTURES & ALGORITHMS PROJECT", 12, Config.GREEN_DARK, Font.BOLD);
        JLabel title = label("SNAKE GAME", 48, Config.NAVY, Font.BOLD);
        JLabel subtitle = label("A classic arcade game, built with Java", 16, Config.MUTED, Font.PLAIN);
        JLabel student = label("Developed by UJJWAL KUMAR  |  RU-25-11551", 12, Config.MUTED, Font.PLAIN);

        small.setAlignmentX(CENTER_ALIGNMENT);
        title.setAlignmentX(CENTER_ALIGNMENT);
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        student.setAlignmentX(CENTER_ALIGNMENT);

        content.add(small);
        content.add(Box.createVerticalStrut(10));
        content.add(title);
        content.add(Box.createVerticalStrut(6));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(8));
        content.add(student);
        content.add(Box.createVerticalStrut(28));

        content.add(menuButton("PLAY GAME", play));
        content.add(Box.createVerticalStrut(10));
        content.add(menuButton("HOW TO PLAY", instructions));
        content.add(Box.createVerticalStrut(10));
        content.add(menuButton("HIGH SCORES", scores));
        content.add(Box.createVerticalStrut(10));
        content.add(menuButton("ABOUT PROJECT", about));
        content.add(Box.createVerticalStrut(10));
        content.add(menuButton("EXIT", exit));

        add(content);
    }

    private JLabel label(String text, int size, Color color, int style) {
        JLabel l = new JLabel(text);
        l.setFont(Config.font(size, style));
        l.setForeground(color);
        return l;
    }

    private JButton menuButton(String text, Runnable action) {
        ActionButton b = new ActionButton(text);
        b.setAlignmentX(CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(270, 44));
        b.addActionListener(e -> action.run());
        return b;
    }
}

/* -------------------------------- INFO VIEW ------------------------------- */
class InfoView extends JPanel {
    InfoView(String title, String[] lines, Runnable back) {
        setLayout(new BorderLayout(18, 18));
        setBackground(Config.BG);
        setBorder(new EmptyBorder(36, 55, 36, 55));

        JLabel heading = new JLabel(title, SwingConstants.CENTER);
        heading.setFont(Config.font(30, Font.BOLD));
        heading.setForeground(Config.NAVY);
        add(heading, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Color.WHITE);
        content.setBorder(new EmptyBorder(28, 32, 28, 32));
        for (String line : lines) {
            JLabel row = new JLabel(line);
            row.setFont(Config.font(15, Font.PLAIN));
            row.setForeground(Config.TEXT);
            row.setAlignmentX(LEFT_ALIGNMENT);
            content.add(row);
            content.add(Box.createVerticalStrut(15));
        }
        add(content, BorderLayout.CENTER);

        ActionButton button = new ActionButton("BACK TO MENU");
        button.addActionListener(e -> back.run());
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.add(button);
        add(bottom, BorderLayout.SOUTH);
    }
}

/* ------------------------------- GAME SCREEN ------------------------------ */
class PlayView extends JPanel {
    private final GameEngine engine;
    private final Runnable goHome;
    private final Runnable gameOver;
    private final BoardView board;
    private final JLabel scoreValue = new JLabel("0");
    private final JLabel bestValue = new JLabel("0");
    private final JLabel lengthValue = new JLabel("4");
    private final JLabel status = new JLabel("Collect food and avoid collisions");
    private final ActionButton pauseButton = new ActionButton("PAUSE");
    private final javax.swing.Timer timer;

    PlayView(GameEngine engine, Runnable goHome, Runnable gameOver) {
        this.engine = engine;
        this.goHome = goHome;
        this.gameOver = gameOver;
        setLayout(new BorderLayout(16, 16));
        setBackground(Config.BG);
        setBorder(new EmptyBorder(20, 25, 20, 25));

        board = new BoardView(engine);
        add(buildHeader(), BorderLayout.NORTH);
        add(board, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        installKeys();
        timer = new javax.swing.Timer(engine.delay(), e -> tick());
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("SNAKE GAME");
        title.setFont(Config.font(22, Font.BOLD));
        title.setForeground(Config.NAVY);
        header.add(title, BorderLayout.WEST);

        JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 9, 0));
        stats.setOpaque(false);
        stats.add(statCard("SCORE", scoreValue));
        stats.add(statCard("BEST", bestValue));
        stats.add(statCard("LENGTH", lengthValue));
        header.add(stats, BorderLayout.EAST);
        return header;
    }

    private JPanel statCard(String caption, JLabel value) {
        CardPanel card = new CardPanel();
        card.setLayout(new GridLayout(2, 1, 0, 2));
        JLabel label = new JLabel(caption);
        label.setFont(Config.font(10, Font.BOLD));
        label.setForeground(Config.MUTED);
        value.setFont(Config.font(18, Font.BOLD));
        value.setForeground(Config.GREEN_DARK);
        card.add(label);
        card.add(value);
        return card;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        status.setFont(Config.font(12, Font.PLAIN));
        status.setForeground(Config.MUTED);
        footer.add(status, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        buttons.setOpaque(false);
        pauseButton.addActionListener(e -> togglePause());
        ActionButton restart = new ActionButton("RESTART", Config.NAVY2, Config.NAVY, Color.WHITE);
        restart.addActionListener(e -> start());
        ActionButton home = new ActionButton("HOME", Config.GREEN_PALE, Config.GREEN_PALE, Config.NAVY);
        home.addActionListener(e -> {
            timer.stop();
            engine.menu();
            goHome.run();
        });
        buttons.add(pauseButton);
        buttons.add(restart);
        buttons.add(home);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void installKeys() {
        bind(KeyEvent.VK_UP, "UP", Direction.UP);
        bind(KeyEvent.VK_DOWN, "DOWN", Direction.DOWN);
        bind(KeyEvent.VK_LEFT, "LEFT", Direction.LEFT);
        bind(KeyEvent.VK_RIGHT, "RIGHT", Direction.RIGHT);
        bind(KeyEvent.VK_W, "W", Direction.UP);
        bind(KeyEvent.VK_S, "S", Direction.DOWN);
        bind(KeyEvent.VK_A, "A", Direction.LEFT);
        bind(KeyEvent.VK_D, "D", Direction.RIGHT);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "PAUSE");
        getActionMap().put("PAUSE", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { togglePause(); }
        });
    }

    private void bind(int key, String name, Direction direction) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { engine.requestDirection(direction); }
        });
    }

    void start() {
        engine.start();
        scoreValue.setText("0");
        lengthValue.setText(String.valueOf(engine.snake().length()));
        bestValue.setText(String.valueOf(Math.max(0, engine.scores().bestSession())));
        status.setText(engine.message());
        pauseButton.setText("PAUSE");
        timer.setDelay(engine.delay());
        timer.start();
        board.repaint();
        requestFocusInWindow();
    }

    void stop() { timer.stop(); }

    private void tick() {
        engine.update();
        timer.setDelay(engine.delay());
        scoreValue.setText(String.valueOf(engine.scores().score()));
        lengthValue.setText(String.valueOf(engine.snake().length()));
        status.setText(engine.message());
        board.repaint();
        if (engine.state() == State.GAME_OVER) {
            timer.stop();
            gameOver.run();
        }
    }

    private void togglePause() {
        engine.togglePause();
        boolean paused = engine.state() == State.PAUSED;
        pauseButton.setText(paused ? "RESUME" : "PAUSE");
        status.setText(engine.message());
        board.repaint();
    }
}

/* ------------------------------ GAME OVER VIEW ---------------------------- */
class GameOverView extends JPanel {
    GameOverView(int score, int best, Runnable again, Runnable menu) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Config.BG);
        setBorder(new EmptyBorder(55, 40, 55, 40));

        add(Box.createVerticalGlue());
        add(centerLabel("GAME OVER", 42, Config.RED, Font.BOLD));
        add(Box.createVerticalStrut(22));
        add(centerLabel("YOUR SCORE", 13, Config.MUTED, Font.BOLD));
        add(Box.createVerticalStrut(5));
        add(centerLabel(String.valueOf(score), 60, Config.NAVY, Font.BOLD));
        add(Box.createVerticalStrut(8));
        add(centerLabel("Best score: " + best, 15, Config.GREEN_DARK, Font.PLAIN));
        add(Box.createVerticalStrut(35));

        ActionButton replay = new ActionButton("PLAY AGAIN");
        replay.setAlignmentX(CENTER_ALIGNMENT);
        replay.addActionListener(e -> again.run());
        add(replay);
        add(Box.createVerticalStrut(12));

        ActionButton home = new ActionButton("MAIN MENU", Config.NAVY2, Config.NAVY, Color.WHITE);
        home.setAlignmentX(CENTER_ALIGNMENT);
        home.addActionListener(e -> menu.run());
        add(home);
        add(Box.createVerticalGlue());
    }

    private JLabel centerLabel(String text, int size, Color color, int style) {
        JLabel l = new JLabel(text);
        l.setFont(Config.font(size, style));
        l.setForeground(color);
        l.setAlignmentX(CENTER_ALIGNMENT);
        return l;
    }
}

/* --------------------------- APPLICATION CONTROLLER ----------------------- */
class SnakeApplication extends JFrame {
    private final CardLayout layout = new CardLayout();
    private final JPanel screens = new JPanel(layout);
    private final GameEngine engine = new GameEngine();
    private final HighScoreStore scores = new HighScoreStore();
    private PlayView playView;

    SnakeApplication() {
        super(Config.TITLE);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(940, 720);
        setMinimumSize(new Dimension(900, 680));
        setResizable(false);
        setLocationRelativeTo(null);
        screens.setBackground(Config.BG);

        screens.add(new MenuView(
            this::showGame, this::showInstructions, this::showScores,
            this::showAbout, this::exit
        ), "MENU");

        playView = new PlayView(engine, this::showMenu, this::showGameOver);
        screens.add(playView, "PLAY");
        add(screens);
        layout.show(screens, "MENU");
    }

    private void showMenu() {
        playView.stop();
        engine.menu();
        layout.show(screens, "MENU");
    }

    private void showGame() {
        layout.show(screens, "PLAY");
        playView.start();
    }

    private void showInstructions() {
        showInfo("HOW TO PLAY", new String[] {
            "Use the arrow keys or W, A, S, D to move the snake.",
            "Collect red food to earn 10 points.",
            "Gold bonus food gives 25 points for a limited time.",
            "Do not hit the wall or the snake's own body.",
            "Press SPACE to pause or resume the game."
        });
    }

    private void showAbout() {
        showInfo("ABOUT PROJECT", new String[] {
            "PROJECT: SNAKE GAME USING JAVA",
            "STUDENT: UJJWAL KUMAR",
            "ERP ID: RU-25-11551",
            "UNIVERSITY: Rungta International Skills University",
            "SUBJECT: Data Structures and Algorithms",
            "GUIDE: SURYAMANI VERMA",
            "DATA STRUCTURE: LinkedList",
            "GRAPHICS: Java Swing and AWT"
        });
    }

    private void showScores() {
        List<String> lines = new ArrayList<>();
        lines.add("TOP SCORES SAVED ON THIS COMPUTER");
        if (scores.entries().isEmpty()) lines.add("No scores yet. Play a game first.");
        int rank = 1;
        for (String row : scores.entries()) lines.add((rank++) + ".  " + row.replace("|", "     "));
        showInfo("HIGH SCORES", lines.toArray(new String[0]));
    }

    private void showInfo(String title, String[] lines) {
        screens.add(new InfoView(title, lines, this::showMenu), title);
        layout.show(screens, title);
    }

    private void showGameOver() {
        int score = engine.scores().score();
        scores.add("Ujjwal", score);
        screens.add(new GameOverView(score, scores.best(), this::showGame, this::showMenu), "OVER");
        layout.show(screens, "OVER");
    }

    private void exit() {
        playView.stop();
        dispose();
        System.exit(0);
    }
}
