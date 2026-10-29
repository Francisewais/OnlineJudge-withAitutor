package io.github.francisewais.oj.submission;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.francisewais.oj.config.AppProperties;
import io.github.francisewais.oj.problem.TestCase;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 调用判题机（judger/app/main.py）的 HTTP 接口。 */
@Component
public class JudgeClient {

    public static class JudgerException extends RuntimeException {
        JudgerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    record WireCase(String input, String output) {
    }

    record JudgeRequest(String language, String source,
                        @JsonProperty("time_limit_ms") int timeLimitMs,
                        @JsonProperty("memory_limit_mb") int memoryLimitMb,
                        @JsonProperty("test_cases") List<WireCase> testCases) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CaseResponse(String verdict,
                               @JsonProperty("time_ms") int timeMs,
                               @JsonProperty("memory_kb") int memoryKb,
                               @JsonProperty("exit_code") int exitCode,
                               int signal,
                               String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JudgeResponse(String verdict,
                                @JsonProperty("compile_message") String compileMessage,
                                @JsonProperty("time_ms") int timeMs,
                                @JsonProperty("memory_kb") int memoryKb,
                                List<CaseResponse> cases) {
    }

    record RunRequest(String language, String source, String input,
                      @JsonProperty("time_limit_ms") int timeLimitMs,
                      @JsonProperty("memory_limit_mb") int memoryLimitMb) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunResponse(String verdict,
                              @JsonProperty("compile_message") String compileMessage,
                              String stdout,
                              String stderr,
                              @JsonProperty("time_ms") int timeMs,
                              @JsonProperty("memory_kb") int memoryKb,
                              @JsonProperty("exit_code") int exitCode,
                              int signal,
                              String message) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final AppProperties.Judger config;
    private final JsonMapper json;

    public JudgeClient(AppProperties props, JsonMapper json) {
        this.config = props.judger();
        this.json = json;
    }

    public JudgeResponse judge(String language, String source, int timeLimitMs, int memoryLimitMb,
                               List<TestCase> cases) {
        List<WireCase> wire = cases.stream().map(c -> new WireCase(c.getInputData(), c.getOutputData())).toList();
        return post("/judge", new JudgeRequest(language, source, timeLimitMs, memoryLimitMb, wire),
                JudgeResponse.class);
    }

    public RunResponse run(String language, String source, String input, int timeLimitMs, int memoryLimitMb) {
        return post("/run", new RunRequest(language, source, input, timeLimitMs, memoryLimitMb), RunResponse.class);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.url() + path))
                .timeout(config.timeout())
                .header("Content-Type", "application/json")
                .header("X-Judge-Token", config.token())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new JudgerException("连接判题机失败：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JudgerException("评测被中断", e);
        }
        if (response.statusCode() != 200) {
            String detail = response.body().length() > 300 ? response.body().substring(0, 300) : response.body();
            throw new JudgerException("判题机返回 HTTP " + response.statusCode() + "：" + detail, null);
        }
        return json.readValue(response.body(), type);
    }
}
