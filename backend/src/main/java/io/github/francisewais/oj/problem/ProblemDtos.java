package io.github.francisewais.oj.problem;

import java.time.Instant;
import java.util.List;

import io.github.francisewais.oj.problem.Problem.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ProblemDtos {

    private ProblemDtos() {
    }

    public record Sample(String input, String output) {
    }

    /** status：solved 已通过，attempted 尝试过但没通过，null 没做过 */
    public record ProblemSummary(Long id, String title, Difficulty difficulty, List<String> tags, int submitCount,
                                 int acceptedCount, boolean visible, String status) {
    }

    public record ProblemDetail(Long id, String title, Difficulty difficulty, List<String> tags, String description,
                                String inputFormat, String outputFormat, List<Sample> samples, String hint,
                                int timeLimitMs, int memoryLimitMb, int submitCount, int acceptedCount,
                                boolean hasEditorial, String status) {
    }

    public record EditorialView(String editorial) {
    }

    public record TestCaseForm(@NotNull(message = "不能为空") String input,
                               @NotNull(message = "不能为空") String output) {
    }

    public record ProblemForm(
            @NotBlank(message = "不能为空") @Size(max = 100, message = "不能超过 100 个字") String title,
            @NotNull(message = "不能为空") Difficulty difficulty,
            List<@NotBlank String> tags,
            @NotBlank(message = "不能为空") @Size(max = 60000, message = "太长了") String description,
            @Size(max = 60000, message = "太长了") String inputFormat,
            @Size(max = 60000, message = "太长了") String outputFormat,
            List<@Valid Sample> samples,
            @Size(max = 60000, message = "太长了") String hint,
            @Size(max = 1000000, message = "太长了") String editorial,
            @Min(value = 100, message = "不能小于 100 ms") @Max(value = 10000, message = "不能超过 10000 ms") int timeLimitMs,
            @Min(value = 16, message = "不能小于 16 MB") @Max(value = 1024, message = "不能超过 1024 MB") int memoryLimitMb,
            boolean visible,
            @NotEmpty(message = "至少需要一组测试数据") @Size(max = 200, message = "最多 200 组") List<@Valid TestCaseForm> testCases) {
    }

    public record AdminProblemView(Long id, String title, Difficulty difficulty, List<String> tags,
                                   String description, String inputFormat, String outputFormat, List<Sample> samples,
                                   String hint, String editorial, int timeLimitMs, int memoryLimitMb,
                                   boolean visible, Instant updatedAt, List<TestCaseForm> testCases) {
    }

    public record Created(Long id) {
    }
}
