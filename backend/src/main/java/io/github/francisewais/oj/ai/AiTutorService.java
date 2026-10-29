package io.github.francisewais.oj.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.francisewais.oj.ai.AiDtos.AiStatus;
import io.github.francisewais.oj.ai.AiDtos.TutorRequest;
import io.github.francisewais.oj.common.ApiException;
import io.github.francisewais.oj.common.CurrentUser;
import io.github.francisewais.oj.config.AppProperties;
import io.github.francisewais.oj.problem.Problem;
import io.github.francisewais.oj.problem.ProblemService;
import io.github.francisewais.oj.submission.Language;
import io.github.francisewais.oj.submission.Submission;
import io.github.francisewais.oj.submission.SubmissionRepository;
import io.github.francisewais.oj.submission.SubmissionService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 调用 OpenAI 兼容的 /chat/completions 接口（默认 DeepSeek），把流式回答通过 SSE 转发给前端。
 * 事件：reasoning（思考过程）、delta（回答内容）、error、done，数据都是 JSON。
 */
@Service
public class AiTutorService {

    private static final Logger log = LoggerFactory.getLogger(AiTutorService.class);

    private final AppProperties.Ai config;
    private final ProblemService problems;
    private final SubmissionRepository submissions;
    private final SubmissionService submissionService;
    private final AiQuota quota;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiTutorService(AppProperties props, ProblemService problems, SubmissionRepository submissions,
                          SubmissionService submissionService, AiQuota quota, JsonMapper json) {
        this.config = props.ai();
        this.problems = problems;
        this.submissions = submissions;
        this.submissionService = submissionService;
        this.quota = quota;
        this.json = json;
    }

    public AiStatus status(CurrentUser user) {
        return new AiStatus(config.enabled(), config.model(), quota.dailyLimit(),
                user == null ? null : quota.remaining(user));
    }

    public SseEmitter tutor(CurrentUser user, TutorRequest req) {
        if (!config.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "还没有配置 AI 助教：需要在 .env 里设置 OJ_AI_API_KEY");
        }
        if (req.mode() == TutorMode.CHAT && (req.question() == null || req.question().isBlank())) {
            throw ApiException.badRequest("请输入你的问题");
        }
        Problem problem = problems.requireVisible(req.problemId(), user);
        Language language = req.language() == null ? null : Language.of(req.language()).orElse(null);
        var info = new TutorPrompt.ProblemInfo(problem.getTitle(), problem.getDifficulty().name(),
                problem.getTimeLimitMs(), problem.getMemoryLimitMb(), problem.getDescription(),
                problem.getInputFormat(), problem.getOutputFormat(), problems.samples(problem), problem.getHint(),
                problem.getEditorial());
        List<TutorPrompt.Message> history = req.history() == null ? List.of() : req.history().stream()
                .map(m -> new TutorPrompt.Message(m.role(), m.content()))
                .toList();
        List<TutorPrompt.Message> messages = TutorPrompt.build(info, req.mode(), language, req.code(),
                latestJudge(user, problem, req), history, req.question());

        if (!quota.tryConsume(user)) {
            throw ApiException.tooManyRequests("今天的 AI 提问次数用完了（每天 " + quota.dailyLimit() + " 次），明天再来吧");
        }
        SseEmitter emitter = new SseEmitter(config.timeout().toMillis());
        AtomicBoolean closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(e -> closed.set(true));
        executor.execute(() -> stream(user, messages, emitter, closed));
        return emitter;
    }

    /** 优先用前端指定的提交，否则用这个用户在这道题上的最近一次提交。 */
    private TutorPrompt.JudgeInfo latestJudge(CurrentUser user, Problem problem, TutorRequest req) {
        Optional<Submission> found = req.submissionId() != null
                ? submissions.findById(req.submissionId())
                        .filter(s -> s.getUserId() == user.id() && s.getProblemId().equals(problem.getId()))
                : submissions.findFirstByUserIdAndProblemIdOrderByIdDesc(user.id(), problem.getId());
        return found.map(s -> new TutorPrompt.JudgeInfo(s.getStatus().name(), s.getVerdict(), s.getPassedCount(),
                        s.getTotalCount(), s.getCompileMessage(), submissionService.caseResults(s),
                        req.code() != null && !req.code().equals(s.getCode())))
                .orElse(null);
    }

    private void stream(CurrentUser user, List<TutorPrompt.Message> messages, SseEmitter emitter,
                        AtomicBoolean closed) {
        boolean received = false;
        try {
            HttpResponse<Stream<String>> response = http.send(buildRequest(messages),
                    HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() != 200) {
                    String body = lines.limit(50).collect(Collectors.joining("\n"));
                    quota.refund(user);
                    sendError(emitter, "AI 服务返回错误（HTTP " + response.statusCode() + "）：" + upstreamMessage(body));
                    return;
                }
                Iterator<String> it = lines.iterator();
                while (it.hasNext() && !closed.get()) {
                    String line = it.next();
                    // 空行和以冒号开头的注释（keep-alive）都跳过
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode chunk = json.readTree(data);
                    JsonNode error = chunk.path("error");
                    if (!error.isMissingNode() && !error.isNull()) {
                        sendError(emitter, "AI 服务出错：" + error.path("message").asString());
                        return;
                    }
                    JsonNode delta = chunk.path("choices").path(0).path("delta");
                    received |= send(emitter, "reasoning", delta.path("reasoning_content"));
                    received |= send(emitter, "delta", delta.path("content"));
                }
            }
            if (!closed.get()) {
                emitter.send(SseEmitter.event().name("done").data("{}"));
                emitter.complete();
            }
        } catch (IOException e) {
            if (closed.get()) {
                return;
            }
            log.warn("AI 请求失败: {}", e.toString());
            if (!received) {
                quota.refund(user);
            }
            sendError(emitter, "连接 AI 服务失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException e) {
            log.warn("AI 回答出错", e);
            sendError(emitter, "AI 回答出错：" + e.getMessage());
        }
    }

    private HttpRequest buildRequest(List<TutorPrompt.Message> messages) {
        ObjectNode body = json.createObjectNode();
        body.put("model", config.model());
        body.put("stream", true);
        body.put("max_tokens", config.maxTokens());
        ArrayNode list = body.putArray("messages");
        for (TutorPrompt.Message m : messages) {
            list.addObject().put("role", m.role()).put("content", m.content());
        }
        if (config.thinking() != null && !config.thinking().isBlank()) {
            body.putObject("thinking").put("type", config.thinking());
        }
        String base = config.baseUrl().endsWith("/")
                ? config.baseUrl().substring(0, config.baseUrl().length() - 1) : config.baseUrl();
        return HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                .timeout(config.timeout())
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
    }

    private boolean send(SseEmitter emitter, String event, JsonNode text) throws IOException {
        if (!text.isString() || text.asString().isEmpty()) {
            return false;
        }
        emitter.send(SseEmitter.event().name(event).data(json.writeValueAsString(Map.of("text", text.asString()))));
        return true;
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(json.writeValueAsString(Map.of("message", message))));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            // 浏览器已经断开，不用再通知
        }
    }

    private String upstreamMessage(String body) {
        try {
            JsonNode message = json.readTree(body).path("error").path("message");
            if (message.isString()) {
                return message.asString();
            }
        } catch (RuntimeException e) {
            // 不是 JSON，直接返回原文
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
