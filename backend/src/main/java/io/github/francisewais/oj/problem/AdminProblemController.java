package io.github.francisewais.oj.problem;

import io.github.francisewais.oj.common.CurrentUser;
import io.github.francisewais.oj.common.PageResponse;
import io.github.francisewais.oj.problem.ProblemDtos.AdminProblemView;
import io.github.francisewais.oj.problem.ProblemDtos.Created;
import io.github.francisewais.oj.problem.ProblemDtos.ProblemForm;
import io.github.francisewais.oj.problem.ProblemDtos.ProblemSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 题目管理，/api/admin/** 只允许管理员访问（见 SecurityConfig）。 */
@RestController
@RequestMapping("/api/admin/problems")
public class AdminProblemController {

    private final ProblemService problems;

    public AdminProblemController(ProblemService problems) {
        this.problems = problems;
    }

    @GetMapping
    public PageResponse<ProblemSummary> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String keyword,
                                             @AuthenticationPrincipal Jwt jwt) {
        return problems.list(page, size, keyword, null, CurrentUser.of(jwt), true);
    }

    @GetMapping("/{id}")
    public AdminProblemView get(@PathVariable long id) {
        return problems.adminGet(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Created create(@Valid @RequestBody ProblemForm form) {
        return new Created(problems.create(form));
    }

    @PutMapping("/{id}")
    public void update(@PathVariable long id, @Valid @RequestBody ProblemForm form) {
        problems.update(id, form);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        problems.delete(id);
    }
}
