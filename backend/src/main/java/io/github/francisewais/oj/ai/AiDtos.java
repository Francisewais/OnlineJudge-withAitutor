package io.github.francisewais.oj.ai;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AiDtos {

    private AiDtos() {
    }

    public record ChatMessage(
            @NotNull @Pattern(regexp = "user|assistant", message = "只能是 user 或 assistant") String role,
            @NotNull @Size(max = 8000, message = "太长了") String content) {
    }

    public record TutorRequest(
            @NotNull(message = "不能为空") Long problemId,
            @NotNull(message = "不能为空") TutorMode mode,
            String language,
            @Size(max = 65536, message = "代码不能超过 64 KB") String code,
            Long submissionId,
            @Size(max = 20, message = "对话太长了，请清空后重新开始") List<@Valid ChatMessage> history,
            @Size(max = 2000, message = "问题不能超过 2000 字") String question) {
    }

    /** remaining 为 null 表示不限次数（管理员）或未登录 */
    public record AiStatus(boolean enabled, String model, int dailyLimit, Integer remaining) {
    }
}
