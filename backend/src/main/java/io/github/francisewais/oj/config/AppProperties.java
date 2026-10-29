package io.github.francisewais.oj.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 里 oj.* 的配置。 */
@ConfigurationProperties(prefix = "oj")
public record AppProperties(Jwt jwt, Admin admin, Judger judger, Ai ai) {

    public record Jwt(String secret, Duration ttl) {
    }

    public record Admin(String username, String password) {
    }

    public record Judger(String url, String token, int concurrency, Duration timeout) {
    }

    public record Ai(String baseUrl, String apiKey, String model, String thinking,
                     int maxTokens, int dailyLimit, Duration timeout) {

        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
