package io.github.francisewais.oj.submission;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.francisewais.oj.common.ApiException;
import io.github.francisewais.oj.common.CurrentUser;
import io.github.francisewais.oj.common.PageResponse;
import io.github.francisewais.oj.problem.Problem;
import io.github.francisewais.oj.problem.ProblemRepository;
import io.github.francisewais.oj.problem.ProblemService;
import io.github.francisewais.oj.submission.SubmissionDtos.CaseResult;
import io.github.francisewais.oj.submission.SubmissionDtos.SubmissionCreated;
import io.github.francisewais.oj.submission.SubmissionDtos.SubmissionDetail;
import io.github.francisewais.oj.submission.SubmissionDtos.SubmissionSummary;
import io.github.francisewais.oj.submission.SubmissionDtos.SubmitRequest;
import io.github.francisewais.oj.user.User;
import io.github.francisewais.oj.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SubmissionService {

    /** 每个用户同时最多几个提交在排队/评测 */
    private static final int MAX_PENDING = 3;
    private static final TypeReference<List<CaseResult>> CASE_LIST = new TypeReference<>() {
    };

    private final SubmissionRepository submissions;
    private final ProblemService problemService;
    private final ProblemRepository problems;
    private final UserRepository users;
    private final JudgeDispatcher dispatcher;
    private final JsonMapper json;

    public SubmissionService(SubmissionRepository submissions, ProblemService problemService,
                             ProblemRepository problems, UserRepository users, JudgeDispatcher dispatcher,
                             JsonMapper json) {
        this.submissions = submissions;
        this.problemService = problemService;
        this.problems = problems;
        this.users = users;
        this.dispatcher = dispatcher;
        this.json = json;
    }

    /** 不加事务：保存的提交要先提交到数据库，后台线程才能查到。 */
    public SubmissionCreated submit(CurrentUser user, SubmitRequest req) {
        Language language = Language.of(req.language()).orElseThrow(() -> ApiException.badRequest("不支持的语言"));
        Problem problem = problemService.requireVisible(req.problemId(), user);
        long pending = submissions.countByUserIdAndStatusIn(user.id(),
                List.of(Submission.Status.PENDING, Submission.Status.JUDGING));
        if (pending >= MAX_PENDING) {
            throw ApiException.tooManyRequests("你还有提交正在评测，请稍等一下再提交");
        }
        Submission s = submissions.save(new Submission(user.id(), problem.getId(), language.id(), req.code()));
        dispatcher.dispatch(s.getId());
        return new SubmissionCreated(s.getId());
    }

    public SubmissionDetail get(long id, CurrentUser viewer) {
        Submission s = submissions.findById(id).orElseThrow(() -> ApiException.notFound("提交记录"));
        boolean owner = viewer != null && (viewer.id() == s.getUserId() || viewer.admin());
        List<CaseResult> cases = caseResults(s);
        if (!owner) {
            cases = cases.stream().map(CaseResult::withoutMessage).toList();
        }
        String username = users.findById(s.getUserId()).map(User::getUsername).orElse("?");
        String title = problems.findById(s.getProblemId()).map(Problem::getTitle).orElse("?");
        return new SubmissionDetail(s.getId(), s.getProblemId(), title, username, s.getLanguage(),
                s.getStatus().name(), s.getVerdict(), s.getTimeMs(), s.getMemoryKb(), s.getPassedCount(),
                s.getTotalCount(), owner ? s.getCompileMessage() : null, cases, owner ? s.getCode() : null, owner,
                s.getCreatedAt(), s.getJudgedAt());
    }

    public List<CaseResult> caseResults(Submission s) {
        String raw = s.getCaseResults();
        return raw == null || raw.isBlank() ? List.of() : json.readValue(raw, CASE_LIST);
    }

    public PageResponse<SubmissionSummary> list(CurrentUser viewer, boolean mine, Long problemId, String username,
                                                String verdict, int page, int size) {
        Long userId = null;
        if (mine) {
            if (viewer == null) {
                throw ApiException.badRequest("请先登录");
            }
            userId = viewer.id();
        } else if (username != null && !username.isBlank()) {
            userId = users.findByUsername(username.trim()).map(User::getId).orElse(-1L);
        }
        var pageable = PageRequest.of(Math.max(page, 1) - 1, Math.clamp(size, 1, 100),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<SubmissionRow> rows = submissions.search(userId, problemId,
                verdict == null || verdict.isBlank() ? null : verdict, pageable);

        Map<Long, String> names = users.findAllById(rows.stream().map(SubmissionRow::userId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        Map<Long, String> titles = problems.findAllById(rows.stream().map(SubmissionRow::problemId).distinct().toList())
                .stream().collect(Collectors.toMap(Problem::getId, Problem::getTitle, (a, b) -> a));
        List<SubmissionSummary> items = rows.stream()
                .map(r -> new SubmissionSummary(r.id(), r.problemId(), titles.getOrDefault(r.problemId(), "?"),
                        names.getOrDefault(r.userId(), "?"), r.language(), r.status().name(), r.verdict(),
                        r.timeMs(), r.memoryKb(), r.passedCount(), r.totalCount(), r.createdAt()))
                .toList();
        return PageResponse.of(rows, items);
    }
}
