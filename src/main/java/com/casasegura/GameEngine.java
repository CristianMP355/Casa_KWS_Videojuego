package com.casasegura;

import javax.swing.Timer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Motor del juego "Casa Segura" estilo Operador de Noche, con amenazas múltiples simultáneas.
 * <p>
 * Reglas:
 * <ul>
 *   <li>Nivel 1 (0-500 pts): 1 amenaza activa, 10.0 s para responder.</li>
 *   <li>Nivel 2 (500-1200 pts): 2 amenazas simultáneas, 8.0 s para responder.</li>
 *   <li>Nivel 3 (1200+ pts): 3 amenazas simultáneas, 6.0 s para responder.</li>
 *   <li>Los tiempos son amplios para cubrir la inferencia de audio del Arduino y la latencia BLE.</li>
 *   <li>Responder con el sector amenazado lo neutraliza (bono por velocidad y combo).</li>
 *   <li>Responder un sector sin amenaza es una falsa alarma: resta puntos y rompe el combo.</li>
 *   <li>Una ANOMALÍA congela los monitores 1.2 s y eleva temporalmente el riesgo global.</li>
 *   <li>Cada intrusión no atendida a tiempo cuesta una vida (3 vidas).</li>
 * </ul>
 * La dificultad puede ser progresiva (según el puntaje) o fijada manualmente.
 * Todo el estado se actualiza en el Event Dispatch Thread mediante un {@link Timer} de Swing,
 * por lo que los métodos públicos deben invocarse desde el EDT.
 */
public class GameEngine {

    /** Niveles de dificultad. */
    public enum Level {
        LEVEL_1(1, 0, 1, 10.0),
        LEVEL_2(2, 500, 2, 8.0),
        LEVEL_3(3, 1200, 3, 6.0);

        private final int number;
        private final int minScore;
        private final int maxSimultaneousThreats;
        private final double reactionSeconds;

        Level(int number, int minScore, int maxSimultaneousThreats, double reactionSeconds) {
            this.number = number;
            this.minScore = minScore;
            this.maxSimultaneousThreats = maxSimultaneousThreats;
            this.reactionSeconds = reactionSeconds;
        }

        public int getNumber() {
            return number;
        }

        public int getMinScore() {
            return minScore;
        }

        public int getMaxSimultaneousThreats() {
            return maxSimultaneousThreats;
        }

        public double getReactionSeconds() {
            return reactionSeconds;
        }

        /** Nivel que corresponde a un puntaje en modo progresivo. */
        public static Level forScore(int score) {
            Level result = LEVEL_1;
            for (Level level : values()) {
                if (score >= level.minScore) {
                    result = level;
                }
            }
            return result;
        }
    }

    /** Intrusión activa en un sector con su cuenta regresiva. */
    public static final class Threat {
        private final Sector sector;
        private final double totalSeconds;
        private double remainingSeconds;

        private Threat(Sector sector, double totalSeconds) {
            this.sector = sector;
            this.totalSeconds = totalSeconds;
            this.remainingSeconds = totalSeconds;
        }

        public Sector getSector() {
            return sector;
        }

        public double getTotalSeconds() {
            return totalSeconds;
        }

        public double getRemainingSeconds() {
            return Math.max(0, remainingSeconds);
        }

        /** Fracción de tiempo restante, de 1.0 (recién aparecida) a 0.0 (expirada). */
        public double getFraction() {
            return Math.max(0, Math.min(1, remainingSeconds / totalSeconds));
        }
    }

    /** Resultado de una respuesta del operador. */
    public enum CommandResult {
        NEUTRALIZED, FALSE_ALARM, BLOCKED, IGNORED
    }

    /** Oyente de eventos del motor. Todos los métodos son opcionales. */
    public interface Listener {
        /** Cambió el conjunto de amenazas activas (aparición, neutralización o expiración). */
        default void onThreatsChanged(List<Threat> activeThreats) {
        }

        /** Se llama en cada fotograma y cuando cambian puntaje, vidas, combo o riesgo. */
        default void onTick(GameEngine engine) {
        }

        default void onMessage(String message, LogType type) {
        }

        default void onGameOver(int finalScore, boolean newRecord) {
        }
    }

    public static final int MAX_LIVES = 3;
    private static final int TICK_MS = 40;
    private static final double FREEZE_SECONDS = 1.2;
    private static final int FALSE_ALARM_PENALTY = 30;
    private static final int BASE_POINTS = 100;
    private static final int MAX_SPEED_BONUS = 100;
    private static final double COMBO_STEP = 0.1;
    private static final double MAX_COMBO_MULTIPLIER = 2.0;
    /** Probabilidad de que, habiendo hueco, aparezcan varias amenazas en el mismo instante. */
    private static final double MULTI_SPAWN_PROBABILITY = 0.65;

    // Barra de riesgo de seguridad (0-100 %)
    private static final double RISK_PER_THREAT = 18;
    private static final double RISK_PER_THREAT_URGENCY = 22;
    private static final double RISK_PER_LOST_LIFE = 10;
    private static final double ANOMALY_RISK_BOOST = 35;
    private static final double MAX_ANOMALY_RISK = 60;
    private static final double ANOMALY_RISK_DECAY_PER_SECOND = 8;
    private static final double CRITICAL_RISK = 80;

    private final HighScoreRepository highScoreRepository;
    private final HighScoreRepository.Record highScoreRecord;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Sector, Threat> activeThreats = new EnumMap<>(Sector.class);
    private final Random random = new Random();
    private final Timer timer;

    /** {@code null} = dificultad progresiva automática. */
    private Level manualLevel;
    private boolean running;
    private int score;
    private int lives = MAX_LIVES;
    private int combo;
    private int bestCombo;
    private int neutralized;
    private Level level = Level.LEVEL_1;
    private double frozenRemaining;
    private double spawnCooldown;
    private double anomalyRisk;
    private double risk;
    private boolean criticalWarned;
    private long lastTickNanos;

    public GameEngine(HighScoreRepository highScoreRepository) {
        this.highScoreRepository = highScoreRepository;
        this.highScoreRecord = highScoreRepository.load();
        this.timer = new Timer(TICK_MS, e -> tick());
        this.timer.setCoalesce(true);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    // ------------------------------------------------------------------ Dificultad

    /**
     * Fija la dificultad manualmente, o la vuelve progresiva con {@code null}.
     * Puede cambiarse en mitad de un turno; las amenazas nuevas usan el nivel nuevo.
     */
    public void setManualLevel(Level manualLevel) {
        this.manualLevel = manualLevel;
        if (manualLevel != null) {
            level = manualLevel;
        } else {
            level = running ? Level.forScore(score) : Level.LEVEL_1;
        }
        message("Dificultad: " + describeDifficulty() + ". " + describeLevel(level), LogType.INFO);
        fireTick();
    }

    public Level getManualLevel() {
        return manualLevel;
    }

    public String describeDifficulty() {
        return manualLevel == null ? "Progresiva" : "Manual (Nivel " + manualLevel.getNumber() + ")";
    }

    // ------------------------------------------------------------------ Ciclo de turno

    /** Inicia (o reinicia) un turno de vigilancia. */
    public void start() {
        activeThreats.clear();
        score = 0;
        lives = MAX_LIVES;
        combo = 0;
        bestCombo = 0;
        neutralized = 0;
        level = manualLevel != null ? manualLevel : Level.LEVEL_1;
        frozenRemaining = 0;
        anomalyRisk = 0;
        risk = 0;
        criticalWarned = false;
        spawnCooldown = 1.2;
        running = true;
        lastTickNanos = System.nanoTime();
        timer.start();

        message("Turno de vigilancia iniciado (" + describeDifficulty() + "). " + describeLevel(level), LogType.GAME);
        fireThreatsChanged();
        fireTick();
    }

    /** Termina el turno en curso a petición del operador. */
    public void stop() {
        if (running) {
            finishGame("Turno terminado por el operador.");
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void finishGame(String reason) {
        running = false;
        timer.stop();
        activeThreats.clear();
        frozenRemaining = 0;
        anomalyRisk = 0;
        risk = 0;

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        boolean newRecord = score > highScoreRecord.getHighScore();
        if (newRecord) {
            highScoreRecord.setHighScore(score);
            highScoreRecord.setAchievedAt(now);
        }
        highScoreRecord.setBestStreak(Math.max(highScoreRecord.getBestStreak(), bestCombo));
        highScoreRecord.setMaxLevel(Math.max(highScoreRecord.getMaxLevel(), level.getNumber()));
        highScoreRecord.addToHistory(new HighScoreRepository.GameEntry(
                now, score, level.getNumber(), bestCombo, neutralized, describeDifficulty()));
        try {
            highScoreRepository.save(highScoreRecord);
        } catch (IOException e) {
            message("No se pudo guardar el récord: " + e.getMessage(), LogType.ERROR);
        }

        message(reason + String.format(" Puntaje final: %d | Intrusiones neutralizadas: %d | Mejor combo: x%d",
                score, neutralized, bestCombo), LogType.GAME);
        if (newRecord) {
            message("¡NUEVO HIGH SCORE: " + score + " pts!", LogType.SUCCESS);
        }

        fireThreatsChanged();
        fireTick();
        for (Listener l : listeners) {
            l.onGameOver(score, newRecord);
        }
    }

    // ------------------------------------------------------------------ Entradas del operador

    /** Procesa la respuesta del operador sobre un sector (tecla emulada por el puente BLE). */
    public CommandResult handleCommand(Sector sector) {
        if (!running) {
            return CommandResult.IGNORED;
        }
        if (isFrozen()) {
            message(String.format(Locale.US, "Monitores CONGELADOS (%.1f s): respuesta %s ignorada.",
                    frozenRemaining, sector.name()), LogType.WARNING);
            return CommandResult.BLOCKED;
        }

        Threat threat = activeThreats.remove(sector);
        if (threat != null) {
            combo++;
            neutralized++;
            bestCombo = Math.max(bestCombo, combo);
            double multiplier = Math.min(MAX_COMBO_MULTIPLIER, 1.0 + (combo - 1) * COMBO_STEP);
            int points = (int) Math.round((BASE_POINTS + MAX_SPEED_BONUS * threat.getFraction()) * multiplier);
            message(String.format(Locale.US, "Intrusión neutralizada en %s: +%d pts (%.1f s restantes, combo x%d)",
                    sector.name(), points, threat.getRemainingSeconds(), combo), LogType.SUCCESS);
            if (combo % 5 == 0) {
                message("¡COMBO x" + combo + "! Multiplicador de puntos " + String.format(Locale.US, "%.1f", multiplier)
                        + "x", LogType.GAME);
            }
            addScore(points);
            // Breve respiro antes de la siguiente oleada.
            spawnCooldown = randomBetween(0.4, 0.9);
            updateRisk(0);
            fireThreatsChanged();
            fireTick();
            return CommandResult.NEUTRALIZED;
        }

        combo = 0;
        message(String.format("Falsa alarma: no hay intrusión en %s (-%d pts, combo perdido).",
                sector.name(), FALSE_ALARM_PENALTY), LogType.WARNING);
        addScore(-FALSE_ALARM_PENALTY);
        fireTick();
        return CommandResult.FALSE_ALARM;
    }

    /** Evento de anomalía (tecla X): congela los monitores y eleva el riesgo global. */
    public void handleAnomaly() {
        if (!running) {
            return;
        }
        frozenRemaining = FREEZE_SECONDS;
        combo = 0;
        anomalyRisk = Math.min(MAX_ANOMALY_RISK, anomalyRisk + ANOMALY_RISK_BOOST);
        message(String.format(Locale.US,
                "ANOMALÍA: monitores congelados %.1f s, riesgo global +%.0f%%, combo perdido.",
                FREEZE_SECONDS, ANOMALY_RISK_BOOST), LogType.ANOMALY);
        updateRisk(0);
        fireTick();
    }

    // ------------------------------------------------------------------ Bucle principal

    private void tick() {
        if (!running) {
            return;
        }
        long now = System.nanoTime();
        double dt = Math.min(0.25, (now - lastTickNanos) / 1_000_000_000.0);
        lastTickNanos = now;

        if (frozenRemaining > 0) {
            frozenRemaining -= dt;
            if (frozenRemaining <= 0) {
                frozenRemaining = 0;
                message("Monitores restablecidos.", LogType.INFO);
            }
        }

        boolean threatsChanged = false;

        // Cuenta regresiva de cada amenaza (sigue corriendo aunque los monitores estén congelados).
        List<Sector> expired = new ArrayList<>();
        for (Threat threat : activeThreats.values()) {
            threat.remainingSeconds -= dt;
            if (threat.remainingSeconds <= 0) {
                expired.add(threat.getSector());
            }
        }
        for (Sector sector : expired) {
            activeThreats.remove(sector);
            threatsChanged = true;
            loseLife(sector);
            if (!running) {
                return; // Game over dentro de loseLife
            }
        }

        // Aparición de nuevas amenazas.
        spawnCooldown -= dt;
        if (spawnCooldown <= 0 && activeThreats.size() < level.getMaxSimultaneousThreats()) {
            spawnThreats();
            threatsChanged = true;
        }

        updateRisk(dt);
        if (threatsChanged) {
            fireThreatsChanged();
        }
        fireTick();
    }

    /** Recalcula la barra de riesgo: amenazas activas y su urgencia, vidas perdidas y anomalías. */
    private void updateRisk(double dt) {
        anomalyRisk = Math.max(0, anomalyRisk - ANOMALY_RISK_DECAY_PER_SECOND * dt);
        double threatRisk = 0;
        for (Threat threat : activeThreats.values()) {
            threatRisk += RISK_PER_THREAT + RISK_PER_THREAT_URGENCY * (1 - threat.getFraction());
        }
        double livesRisk = (MAX_LIVES - lives) * RISK_PER_LOST_LIFE;
        risk = Math.max(0, Math.min(100, threatRisk + livesRisk + anomalyRisk));

        if (risk >= CRITICAL_RISK && !criticalWarned) {
            criticalWarned = true;
            message(String.format(Locale.US, "RIESGO DE SEGURIDAD CRÍTICO: %.0f%%", risk), LogType.ERROR);
        } else if (risk < CRITICAL_RISK - 20) {
            criticalWarned = false;
        }
    }

    private void loseLife(Sector sector) {
        lives--;
        combo = 0;
        message(String.format("¡El intruso entró por %s! Pierdes una vida (%d/%d).",
                sector.name(), Math.max(0, lives), MAX_LIVES), LogType.ERROR);
        if (lives <= 0) {
            finishGame("GAME OVER: la casa fue comprometida.");
        }
    }

    /** Genera una o varias amenazas simultáneas en sectores libres. */
    private void spawnThreats() {
        int free = level.getMaxSimultaneousThreats() - activeThreats.size();
        int count = 1;
        if (free > 1) {
            count = random.nextDouble() < MULTI_SPAWN_PROBABILITY ? free : 1 + random.nextInt(free);
        }

        List<Sector> candidates = Arrays.stream(Sector.values())
                .filter(s -> !activeThreats.containsKey(s))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates, random);
        count = Math.min(count, candidates.size());

        List<Sector> spawned = new ArrayList<>(candidates.subList(0, count));
        for (Sector sector : spawned) {
            activeThreats.put(sector, new Threat(sector, level.getReactionSeconds()));
        }

        String reaction = String.format(Locale.US, "%.1f s", level.getReactionSeconds());
        if (spawned.size() == 1) {
            message("¡Intrusión en " + spawned.get(0).name() + "! Tienes " + reaction + ".", LogType.GAME);
        } else {
            message("¡ALERTA MÚLTIPLE! " + joinSimultaneous(spawned) + ". Tienes " + reaction
                    + " para cada una.", LogType.GAME);
        }

        double levelFactor = switch (level) {
            case LEVEL_1 -> 1.0;
            case LEVEL_2 -> 0.8;
            case LEVEL_3 -> 0.6;
        };
        spawnCooldown = randomBetween(0.6, 1.4) * levelFactor;
    }

    /** "Intrusión en PUERTA y al mismo tiempo en VENTANAS". */
    private static String joinSimultaneous(List<Sector> sectors) {
        StringBuilder sb = new StringBuilder("Intrusión en ").append(sectors.get(0).name());
        for (int i = 1; i < sectors.size(); i++) {
            sb.append(i == sectors.size() - 1 ? " y al mismo tiempo en " : ", en ")
                    .append(sectors.get(i).name());
        }
        return sb.toString();
    }

    /** Suma (o resta) puntos. En modo progresivo el nivel solo sube durante un turno. */
    private void addScore(int delta) {
        score = Math.max(0, score + delta);
        if (manualLevel != null) {
            return;
        }
        Level reached = Level.forScore(score);
        if (reached.ordinal() > level.ordinal()) {
            level = reached;
            message("¡SUBISTE DE NIVEL! " + describeLevel(level), LogType.GAME);
        }
    }

    public static String describeLevel(Level level) {
        return String.format(Locale.US, "Nivel %d: %d amenaza(s) simultánea(s), %.1f s para responder.",
                level.getNumber(), level.getMaxSimultaneousThreats(), level.getReactionSeconds());
    }

    private double randomBetween(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    // ------------------------------------------------------------------ Notificaciones

    private void message(String text, LogType type) {
        for (Listener l : listeners) {
            l.onMessage(text, type);
        }
    }

    private void fireThreatsChanged() {
        List<Threat> snapshot = getActiveThreats();
        for (Listener l : listeners) {
            l.onThreatsChanged(snapshot);
        }
    }

    private void fireTick() {
        for (Listener l : listeners) {
            l.onTick(this);
        }
    }

    // ------------------------------------------------------------------ Consultas

    public List<Threat> getActiveThreats() {
        return Collections.unmodifiableList(new ArrayList<>(activeThreats.values()));
    }

    /** Amenaza activa en el sector, o {@code null}. */
    public Threat getThreat(Sector sector) {
        return activeThreats.get(sector);
    }

    public int getScore() {
        return score;
    }

    public int getLives() {
        return lives;
    }

    public int getCombo() {
        return combo;
    }

    public int getBestCombo() {
        return bestCombo;
    }

    public Level getLevel() {
        return level;
    }

    /** Riesgo de seguridad global, 0-100 %. */
    public double getRiskPercent() {
        return risk;
    }

    public int getHighScore() {
        return Math.max(highScoreRecord.getHighScore(), running ? score : 0);
    }

    public HighScoreRepository.Record getHighScoreRecord() {
        return highScoreRecord;
    }

    public boolean isFrozen() {
        return frozenRemaining > 0;
    }

    public double getFrozenRemaining() {
        return Math.max(0, frozenRemaining);
    }
}
