package io.github.francisewais.oj.submission;

import io.github.francisewais.oj.problem.ProblemRepository;
import io.github.francisewais.oj.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 在同一个事务里保存评测结果并更新题目、用户的统计数据。 */
@Component
public class SubmissionResultWriter {

    private final SubmissionRepository submissions;
    private final ProblemRepository problems;
    private final UserRepository users;

    public SubmissionResultWriter(SubmissionRepository submissions, ProblemRepository problems, UserRepository users) {
        this.submissions = submissions;
        this.problems = problems;
        this.users = users;
    }

    @Transactional
    public void save(Submission s) {
        submissions.save(s);
        // 系统错误不是用户的问题，不计入统计
        if (Verdicts.SE.equals(s.getVerdict())) {
            return;
        }
        boolean accepted = Verdicts.AC.equals(s.getVerdict());
        problems.incrementCounts(s.getProblemId(), accepted ? 1 : 0);
        users.incrementSubmitCount(s.getUserId());
        if (accepted) {
            // 重新统计而不是 +1，同一道题多次通过、并发评测都不会多算
            users.updateSolvedCount(s.getUserId(), (int) submissions.countSolvedProblems(s.getUserId()));
        }
    }
}
