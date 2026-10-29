package io.github.francisewais.oj.problem;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "problems")
public class Problem {

    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    /** 逗号分隔 */
    private String tags;

    private String description;

    private String inputFormat;

    private String outputFormat;

    /** JSON：[{"input": "...", "output": "..."}] */
    private String samples;

    private String hint;

    private String editorial;

    private int timeLimitMs;

    private int memoryLimitMb;

    private boolean visible;

    private int submitCount;

    private int acceptedCount;

    private Instant createdAt;

    private Instant updatedAt;

    protected Problem() {
    }

    public static Problem create() {
        Problem p = new Problem();
        p.createdAt = Instant.now();
        return p;
    }

    public void update(String title, Difficulty difficulty, String tags, String description, String inputFormat,
                       String outputFormat, String samples, String hint, String editorial, int timeLimitMs,
                       int memoryLimitMb, boolean visible) {
        this.title = title;
        this.difficulty = difficulty;
        this.tags = tags;
        this.description = description;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
        this.samples = samples;
        this.hint = hint;
        this.editorial = editorial;
        this.timeLimitMs = timeLimitMs;
        this.memoryLimitMb = memoryLimitMb;
        this.visible = visible;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getTags() {
        return tags;
    }

    public String getDescription() {
        return description;
    }

    public String getInputFormat() {
        return inputFormat;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public String getSamples() {
        return samples;
    }

    public String getHint() {
        return hint;
    }

    public String getEditorial() {
        return editorial;
    }

    public int getTimeLimitMs() {
        return timeLimitMs;
    }

    public int getMemoryLimitMb() {
        return memoryLimitMb;
    }

    public boolean isVisible() {
        return visible;
    }

    public int getSubmitCount() {
        return submitCount;
    }

    public int getAcceptedCount() {
        return acceptedCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
