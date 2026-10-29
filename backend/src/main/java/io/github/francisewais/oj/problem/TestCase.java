package io.github.francisewais.oj.problem;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long problemId;

    private int seq;

    private String inputData;

    private String outputData;

    protected TestCase() {
    }

    public TestCase(Long problemId, int seq, String inputData, String outputData) {
        this.problemId = problemId;
        this.seq = seq;
        this.inputData = inputData;
        this.outputData = outputData;
    }

    public Long getId() {
        return id;
    }

    public Long getProblemId() {
        return problemId;
    }

    public int getSeq() {
        return seq;
    }

    public String getInputData() {
        return inputData;
    }

    public String getOutputData() {
        return outputData;
    }
}
