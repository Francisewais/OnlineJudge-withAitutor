package io.github.francisewais.oj.submission;

import java.util.List;

import io.github.francisewais.oj.problem.Problem;
import io.github.francisewais.oj.problem.ProblemRepository;
import io.github.francisewais.oj.problem.TestCase;
import io.github.francisewais.oj.problem.TestCaseRepository;
import io.github.francisewais.oj.submission.SubmissionDtos.CaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** 评测一个提交：把代码和测试数据发给判题机，保存结果。由 JudgeDispatcher 在后台线程调用。 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final SubmissionRepository submissions;
    private final ProblemRepository problems;
    private final TestCaseRepository testCases;
    private final JudgeClient judger;
    private final SubmissionResultWriter writer;
    private final JsonMapper json;

    public JudgeService(SubmissionRepository submissions, ProblemRepository problems, TestCaseRepository testCases,
                        JudgeClient judger, SubmissionResultWriter writer, JsonMapper json) {
        this.submissions = submissions;
        this.problems = problems;
        this.testCases = testCases;
        this.judger = judger;
        this.writer = writer;
        this.json = json;
    }

    public void judge(long submissionId) {
        Submission s = submissions.findById(submissionId).orElse(null);
        if (s == null || s.getStatus() == Submission.Status.FINISHED) {
            return;
        }
        s.markJudging();
        submissions.save(s);
        try {
            Problem problem = problems.findById(s.getProblemId()).orElseThrow();
            List<TestCase> cases = testCases.findByProblemIdOrderBySeqAsc(problem.getId());
            if (cases.isEmpty()) {
                s.finish(Verdicts.SE, 0, 0, 0, 0, "这道题还没有测试数据", "[]");
            } else {
                var result = judger.judge(s.getLanguage(), s.getCode(), problem.getTimeLimitMs(),
                        problem.getMemoryLimitMb(), cases);
                List<CaseResult> caseResults = result.cases().stream()
                        .map(c -> new CaseResult(c.verdict(), c.timeMs(), c.memoryKb(), c.exitCode(), c.signal(),
                                c.message()))
                        .toList();
                int passed = (int) caseResults.stream().filter(c -> Verdicts.AC.equals(c.verdict())).count();
                s.finish(result.verdict(), result.timeMs(), result.memoryKb(), passed, cases.size(),
                        result.compileMessage(), json.writeValueAsString(caseResults));
            }
        } catch (RuntimeException e) {
            log.error("评测提交 {} 失败", submissionId, e);
            s.finish(Verdicts.SE, 0, 0, 0, s.getTotalCount(), "系统错误：" + e.getMessage(), "[]");
        }
        writer.save(s);
        log.info("submission {} -> {}", submissionId, s.getVerdict());
    }
}
