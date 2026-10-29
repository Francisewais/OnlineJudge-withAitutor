package io.github.francisewais.oj.submission;

import java.time.Duration;

import io.github.francisewais.oj.common.ApiException;
import io.github.francisewais.oj.common.CurrentUser;
import io.github.francisewais.oj.common.RateLimiter;
import io.github.francisewais.oj.problem.Problem;
import io.github.francisewais.oj.problem.ProblemService;
import io.github.francisewais.oj.submission.SubmissionDtos.RunRequest;
import io.github.francisewais.oj.submission.SubmissionDtos.RunResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 自测运行：用自己给的输入跑一次代码，不计入提交记录。 */
@RestController
public class RunController {

    private static final int DEFAULT_TIME_LIMIT_MS = 2000;
    private static final int DEFAULT_MEMORY_LIMIT_MB = 256;

    private final JudgeClient judger;
    private final ProblemService problems;
    private final RateLimiter limiter = new RateLimiter(10, Duration.ofMinutes(1));

    public RunController(JudgeClient judger, ProblemService problems) {
        this.judger = judger;
        this.problems = problems;
    }

    @PostMapping("/api/run")
    public RunResult run(@Valid @RequestBody RunRequest req, @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.required(jwt);
        Language language = Language.of(req.language()).orElseThrow(() -> ApiException.badRequest("不支持的语言"));
        int timeLimit = DEFAULT_TIME_LIMIT_MS;
        int memoryLimit = DEFAULT_MEMORY_LIMIT_MB;
        if (req.problemId() != null) {
            Problem p = problems.requireVisible(req.problemId(), user);
            timeLimit = p.getTimeLimitMs();
            memoryLimit = p.getMemoryLimitMb();
        }
        if (!user.admin() && !limiter.tryAcquire(user.id())) {
            throw ApiException.tooManyRequests("运行太频繁了，请稍后再试（每分钟最多 10 次）");
        }
        JudgeClient.RunResponse r;
        try {
            r = judger.run(language.id(), req.code(), req.input() == null ? "" : req.input(), timeLimit, memoryLimit);
        } catch (JudgeClient.JudgerException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "判题机暂时不可用：" + e.getMessage());
        }
        return new RunResult(r.verdict(), r.stdout(), r.stderr(), r.compileMessage(), r.timeMs(), r.memoryKb(),
                r.exitCode(), r.signal(), r.message());
    }
}
