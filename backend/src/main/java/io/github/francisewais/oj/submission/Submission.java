package io.github.francisewais.oj.submission;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "submissions")
public class Submission {

    public enum Status {
        PENDING, JUDGING, FINISHED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long problemId;

    private String language;

    private String code;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String verdict;

    private Integer timeMs;

    private Integer memoryKb;

    private int passedCount;

    private int totalCount;

    private String compileMessage;

    /** JSON：每个测试点的结果 */
    private String caseResults;

    private Instant createdAt;

    private Instant judgedAt;

    protected Submission() {
    }

    public Submission(Long userId, Long problemId, String language, String code) {
        this.userId = userId;
        this.problemId = problemId;
        this.language = language;
        this.code = code;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void markJudging() {
        this.status = Status.JUDGING;
    }

    public void finish(String verdict, int timeMs, int memoryKb, int passedCount, int totalCount,
                       String compileMessage, String caseResults) {
        this.status = Status.FINISHED;
        this.verdict = verdict;
        this.timeMs = timeMs;
        this.memoryKb = memoryKb;
        this.passedCount = passedCount;
        this.totalCount = totalCount;
        this.compileMessage = compileMessage;
        this.caseResults = caseResults;
        this.judgedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProblemId() {
        return problemId;
    }

    public String getLanguage() {
        return language;
    }

    public String getCode() {
        return code;
    }

    public Status getStatus() {
        return status;
    }

    public String getVerdict() {
        return verdict;
    }

    public Integer getTimeMs() {
        return timeMs;
    }

    public Integer getMemoryKb() {
        return memoryKb;
    }

    public int getPassedCount() {
        return passedCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public String getCompileMessage() {
        return compileMessage;
    }

    public String getCaseResults() {
        return caseResults;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getJudgedAt() {
        return judgedAt;
    }
}
