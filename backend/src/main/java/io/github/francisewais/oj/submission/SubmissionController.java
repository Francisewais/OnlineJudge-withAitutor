package io.github.francisewais.oj.submission;

import io.github.francisewais.oj.common.CurrentUser;
import io.github.francisewais.oj.common.PageResponse;
import io.github.francisewais.oj.submission.SubmissionDtos.SubmissionCreated;
import io.github.francisewais.oj.submission.SubmissionDtos.SubmissionDetail;
import io.github.francisewais.oj.submission.SubmissionDtos.SubmissionSummary;
import io.github.francisewais.oj.submission.SubmissionDtos.SubmitRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService submissions;

    public SubmissionController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @PostMapping
    public SubmissionCreated submit(@Valid @RequestBody SubmitRequest req, @AuthenticationPrincipal Jwt jwt) {
        return submissions.submit(CurrentUser.required(jwt), req);
    }

    @GetMapping("/{id}")
    public SubmissionDetail get(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return submissions.get(id, CurrentUser.of(jwt));
    }

    @GetMapping
    public PageResponse<SubmissionSummary> list(@RequestParam(defaultValue = "false") boolean mine,
                                                @RequestParam(required = false) Long problemId,
                                                @RequestParam(required = false) String username,
                                                @RequestParam(required = false) String verdict,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @AuthenticationPrincipal Jwt jwt) {
        return submissions.list(CurrentUser.of(jwt), mine, problemId, username, verdict, page, size);
    }
}
