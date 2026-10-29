package io.github.francisewais.oj.submission;

import java.time.Instant;

/** 提交列表只需要这些字段，不查代码和测试点详情。 */
public record SubmissionRow(Long id, Long userId, Long problemId, String language, Submission.Status status,
                            String verdict, Integer timeMs, Integer memoryKb, int passedCount, int totalCount,
                            Instant createdAt) {
}
