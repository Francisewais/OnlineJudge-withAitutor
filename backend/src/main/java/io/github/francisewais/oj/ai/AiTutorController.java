package io.github.francisewais.oj.ai;

import io.github.francisewais.oj.ai.AiDtos.AiStatus;
import io.github.francisewais.oj.ai.AiDtos.TutorRequest;
import io.github.francisewais.oj.common.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai")
public class AiTutorController {

    private final AiTutorService tutor;

    public AiTutorController(AiTutorService tutor) {
        this.tutor = tutor;
    }

    @GetMapping("/status")
    public AiStatus status(@AuthenticationPrincipal Jwt jwt) {
        return tutor.status(CurrentUser.of(jwt));
    }

    /** 返回 text/event-stream；参数错误、次数用完等在开始推送前以普通 JSON 错误返回。 */
    @PostMapping("/tutor")
    public SseEmitter ask(@Valid @RequestBody TutorRequest req, @AuthenticationPrincipal Jwt jwt,
                          HttpServletResponse response) {
        SseEmitter emitter = tutor.tutor(CurrentUser.required(jwt), req);
        // 让 nginx 不要缓冲，回答才能一个字一个字地出来
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return emitter;
    }
}
