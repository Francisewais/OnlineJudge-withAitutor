package io.github.francisewais.oj.problem;

import io.github.francisewais.oj.common.CurrentUser;
import io.github.francisewais.oj.common.PageResponse;
import io.github.francisewais.oj.problem.Problem.Difficulty;
import io.github.francisewais.oj.problem.ProblemDtos.EditorialView;
import io.github.francisewais.oj.problem.ProblemDtos.ProblemDetail;
import io.github.francisewais.oj.problem.ProblemDtos.ProblemSummary;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private final ProblemService problems;

    public ProblemController(ProblemService problems) {
        this.problems = problems;
    }

    @GetMapping
    public PageResponse<ProblemSummary> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Difficulty difficulty,
                                             @AuthenticationPrincipal Jwt jwt) {
        return problems.list(page, size, keyword, difficulty, CurrentUser.of(jwt), false);
    }

    @GetMapping("/{id}")
    public ProblemDetail detail(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return problems.detail(id, CurrentUser.of(jwt));
    }

    @GetMapping("/{id}/editorial")
    public EditorialView editorial(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return problems.editorial(id, CurrentUser.of(jwt));
    }
}
