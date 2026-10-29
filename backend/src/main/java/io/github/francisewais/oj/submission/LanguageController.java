package io.github.francisewais.oj.submission;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.github.francisewais.oj.submission.SubmissionDtos.LanguageView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LanguageController {

    @GetMapping("/api/languages")
    public List<LanguageView> languages() {
        return Arrays.stream(Language.values())
                .map(l -> new LanguageView(l.id(), l.displayName(), l.timeFactor()))
                .toList();
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
