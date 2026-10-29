package io.github.francisewais.oj.submission;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SubmissionDtos {

    private SubmissionDtos() {
    }

    public record SubmitRequest(
            @NotNull(message = "不能为空") Long problemId,
            @NotBlank(message = "不能为空") String language,
            @NotBlank(message = "代码不能为空") @Size(max = 65536, message = "代码不能超过 64 KB") String code) {
    }

    public record SubmissionCreated(Long id) {
    }

    /** message：运行出错时的原因和 stderr 片段，只有提交者本人和管理员能看到 */
    public record CaseResult(String verdict, int timeMs, int memoryKb, int exitCode, int signal, String message) {

        public CaseResult withoutMessage() {
            return new CaseResult(verdict, timeMs, memoryKb, exitCode, signal, "");
        }
    }

    public record SubmissionSummary(Long id, Long problemId, String problemTitle, String username, String language,
                                    String status, String verdict, Integer timeMs, Integer memoryKb,
                                    int passedCount, int totalCount, Instant createdAt) {
    }

    /** code、compileMessage 只对提交者本人和管理员返回，其他人为 null */
    public record SubmissionDetail(Long id, Long problemId, String problemTitle, String username, String language,
                                   String status, String verdict, Integer timeMs, Integer memoryKb,
                                   int passedCount, int totalCount, String compileMessage, List<CaseResult> cases,
                                   String code, boolean owner, Instant createdAt, Instant judgedAt) {
    }

    public record RunRequest(
            Long problemId,
            @NotBlank(message = "不能为空") String language,
            @NotBlank(message = "代码不能为空") @Size(max = 65536, message = "代码不能超过 64 KB") String code,
            @Size(max = 1048576, message = "输入不能超过 1 MB") String input) {
    }

    public record RunResult(String verdict, String stdout, String stderr, String compileMessage, int timeMs,
                            int memoryKb, int exitCode, int signal, String message) {
    }

    public record LanguageView(String id, String name, int timeFactor) {
    }
}
