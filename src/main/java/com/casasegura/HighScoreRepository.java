package com.casasegura;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistencia del récord y del historial de turnos en JSON mediante Gson.
 * Por defecto se guarda en {@code highscore_casa_segura.json} dentro del directorio
 * de trabajo (la raíz del proyecto al ejecutar desde IntelliJ).
 */
public class HighScoreRepository {

    public static final String DEFAULT_FILE_NAME = "highscore_casa_segura.json";
    private static final int MAX_HISTORY = 20;

    /** Resultado de un turno (partida) del operador. */
    public static final class GameEntry {
        private String date;
        private int score;
        private int level;
        private int bestStreak;
        private int neutralized;
        private String difficulty;

        public GameEntry(String date, int score, int level, int bestStreak, int neutralized, String difficulty) {
            this.date = date;
            this.score = score;
            this.level = level;
            this.bestStreak = bestStreak;
            this.neutralized = neutralized;
            this.difficulty = difficulty;
        }

        public String getDate() {
            return date;
        }

        public int getScore() {
            return score;
        }

        public int getLevel() {
            return level;
        }

        public int getBestStreak() {
            return bestStreak;
        }

        public int getNeutralized() {
            return neutralized;
        }

        public String getDifficulty() {
            return difficulty;
        }
    }

    /** Contenido completo del archivo JSON. */
    public static final class Record {
        private int highScore;
        private int bestStreak;
        private int maxLevel = 1;
        private String achievedAt = "";
        private List<GameEntry> history = new ArrayList<>();

        public int getHighScore() {
            return highScore;
        }

        public void setHighScore(int highScore) {
            this.highScore = highScore;
        }

        public int getBestStreak() {
            return bestStreak;
        }

        public void setBestStreak(int bestStreak) {
            this.bestStreak = bestStreak;
        }

        public int getMaxLevel() {
            return maxLevel;
        }

        public void setMaxLevel(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        public String getAchievedAt() {
            return achievedAt;
        }

        public void setAchievedAt(String achievedAt) {
            this.achievedAt = achievedAt;
        }

        /** Historial, del turno más reciente al más antiguo. */
        public List<GameEntry> getHistory() {
            if (history == null) {
                history = new ArrayList<>(); // JSON antiguo sin historial
            }
            return history;
        }

        public void addToHistory(GameEntry entry) {
            getHistory().add(0, entry);
            while (history.size() > MAX_HISTORY) {
                history.remove(history.size() - 1);
            }
        }
    }

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public HighScoreRepository() {
        this(Paths.get(DEFAULT_FILE_NAME));
    }

    public HighScoreRepository(Path file) {
        this.file = file;
    }

    public Path getFile() {
        return file;
    }

    /** Carga el récord; si el archivo no existe o está dañado devuelve uno vacío. */
    public Record load() {
        if (!Files.exists(file)) {
            return new Record();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Record record = gson.fromJson(reader, Record.class);
            return record != null ? record : new Record();
        } catch (IOException | JsonParseException e) {
            return new Record();
        }
    }

    public void save(Record record) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(record, writer);
        }
    }
}
