package io.github.francisewais.oj.problem;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.francisewais.oj.common.ApiException;
import io.github.francisewais.oj.common.CurrentUser;
import io.github.francisewais.oj.common.PageResponse;
import io.github.francisewais.oj.problem.Problem.Difficulty;
import io.github.francisewais.oj.problem.ProblemDtos.AdminProblemView;
import io.github.francisewais.oj.problem.ProblemDtos.EditorialView;
import io.github.francisewais.oj.problem.ProblemDtos.ProblemDetail;
import io.github.francisewais.oj.problem.ProblemDtos.ProblemForm;
import io.github.francisewais.oj.problem.ProblemDtos.ProblemSummary;
import io.github.francisewais.oj.problem.ProblemDtos.Sample;
import io.github.francisewais.oj.problem.ProblemDtos.TestCaseForm;
import io.github.francisewais.oj.submission.SubmissionRepository;
import io.github.francisewais.oj.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class ProblemService {

    private static final TypeReference<List<Sample>> SAMPLE_LIST = new TypeReference<>() {
    };

    private final ProblemRepository problems;
    private final TestCaseRepository testCases;
    private final SubmissionRepository submissions;
    private final UserRepository users;
    private final JsonMapper json;

    public ProblemService(ProblemRepository problems, TestCaseRepository testCases, SubmissionRepository submissions,
                          UserRepository users, JsonMapper json) {
        this.problems = problems;
        this.testCases = testCases;
        this.submissions = submissions;
        this.users = users;
        this.json = json;
    }

    public PageResponse<ProblemSummary> list(int page, int size, String keyword, Difficulty difficulty,
                                             CurrentUser viewer, boolean includeHidden) {
        var pageable = PageRequest.of(Math.max(page, 1) - 1, Math.clamp(size, 1, 100), Sort.by("id"));
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        Page<Problem> result = problems.search(includeHidden, kw, difficulty, pageable);
        Map<Long, String> status = statusOf(viewer);
        List<ProblemSummary> items = result.getContent().stream()
                .map(p -> new ProblemSummary(p.getId(), p.getTitle(), p.getDifficulty(), tags(p), p.getSubmitCount(),
                        p.getAcceptedCount(), p.isVisible(), status.get(p.getId())))
                .toList();
        return PageResponse.of(result, items);
    }

    public ProblemDetail detail(long id, CurrentUser viewer) {
        Problem p = requireVisible(id, viewer);
        return new ProblemDetail(p.getId(), p.getTitle(), p.getDifficulty(), tags(p), p.getDescription(),
                p.getInputFormat(), p.getOutputFormat(), samples(p), p.getHint(), p.getTimeLimitMs(),
                p.getMemoryLimitMb(), p.getSubmitCount(), p.getAcceptedCount(), !p.getEditorial().isBlank(),
                statusOf(viewer).get(p.getId()));
    }

    public EditorialView editorial(long id, CurrentUser viewer) {
        return new EditorialView(requireVisible(id, viewer).getEditorial());
    }

    /** 隐藏的题目只有管理员能看到，对其他人表现为不存在。 */
    public Problem requireVisible(long id, CurrentUser viewer) {
        Problem p = problems.findById(id).orElseThrow(() -> ApiException.notFound("题目"));
        if (!p.isVisible() && (viewer == null || !viewer.admin())) {
            throw ApiException.notFound("题目");
        }
        return p;
    }

    public List<Sample> samples(Problem p) {
        return json.readValue(p.getSamples(), SAMPLE_LIST);
    }

    public static List<String> tags(Problem p) {
        return Arrays.stream(p.getTags().split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
    }

    public AdminProblemView adminGet(long id) {
        Problem p = problems.findById(id).orElseThrow(() -> ApiException.notFound("题目"));
        List<TestCaseForm> cases = testCases.findByProblemIdOrderBySeqAsc(id).stream()
                .map(t -> new TestCaseForm(t.getInputData(), t.getOutputData()))
                .toList();
        return new AdminProblemView(p.getId(), p.getTitle(), p.getDifficulty(), tags(p), p.getDescription(),
                p.getInputFormat(), p.getOutputFormat(), samples(p), p.getHint(), p.getEditorial(),
                p.getTimeLimitMs(), p.getMemoryLimitMb(), p.isVisible(), p.getUpdatedAt(), cases);
    }

    @Transactional
    public long create(ProblemForm form) {
        Problem p = Problem.create();
        apply(p, form);
        problems.save(p);
        saveTestCases(p.getId(), form.testCases());
        return p.getId();
    }

    @Transactional
    public void update(long id, ProblemForm form) {
        Problem p = problems.findById(id).orElseThrow(() -> ApiException.notFound("题目"));
        apply(p, form);
        problems.save(p);
        testCases.deleteByProblemId(id);
        saveTestCases(id, form.testCases());
    }

    /** 删除题目会连带删除它的测试数据和提交记录（外键 ON DELETE CASCADE），然后重新统计通过题数。 */
    @Transactional
    public void delete(long id) {
        if (!problems.existsById(id)) {
            throw ApiException.notFound("题目");
        }
        problems.deleteById(id);
        problems.flush();
        users.recomputeSolvedCounts();
    }

    private Map<Long, String> statusOf(CurrentUser viewer) {
        if (viewer == null) {
            return Map.of();
        }
        Map<Long, String> status = new HashMap<>();
        submissions.findAttemptedProblemIds(viewer.id()).forEach(id -> status.put(id, "attempted"));
        submissions.findSolvedProblemIds(viewer.id()).forEach(id -> status.put(id, "solved"));
        return status;
    }

    private void apply(Problem p, ProblemForm form) {
        String tags = form.tags() == null ? "" : String.join(",", form.tags().stream()
                .map(String::trim).filter(t -> !t.isEmpty()).distinct().toList());
        if (tags.length() > 255) {
            throw ApiException.badRequest("标签总长度不能超过 255 个字符");
        }
        List<Sample> samples = form.samples() == null ? List.of() : form.samples();
        p.update(form.title().trim(), form.difficulty(), tags, form.description(), nullToEmpty(form.inputFormat()),
                nullToEmpty(form.outputFormat()), json.writeValueAsString(samples), nullToEmpty(form.hint()),
                nullToEmpty(form.editorial()), form.timeLimitMs(), form.memoryLimitMb(), form.visible());
    }

    private void saveTestCases(Long problemId, List<TestCaseForm> cases) {
        for (int i = 0; i < cases.size(); i++) {
            testCases.save(new TestCase(problemId, i + 1, cases.get(i).input(), cases.get(i).output()));
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
