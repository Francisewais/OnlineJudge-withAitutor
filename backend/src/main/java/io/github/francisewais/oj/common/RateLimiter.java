package io.github.francisewais.oj.common;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 按用户的滑动窗口限流，只保存在内存里，重启后清零。 */
public class RateLimiter {

    private final int limit;
    private final Duration window;
    private final Map<Long, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    public boolean tryAcquire(long key) {
        Instant now = Instant.now();
        Deque<Instant> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(now.minus(window))) {
                queue.pollFirst();
            }
            if (queue.size() >= limit) {
                return false;
            }
            queue.addLast(now);
            return true;
        }
    }
}
