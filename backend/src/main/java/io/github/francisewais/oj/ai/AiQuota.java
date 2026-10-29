package io.github.francisewais.oj.ai;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.francisewais.oj.common.CurrentUser;
import io.github.francisewais.oj.config.AppProperties;
import org.springframework.stereotype.Component;

/** 每个用户每天能问 AI 的次数，管理员不限。只保存在内存里，重启后清零。 */
@Component
public class AiQuota {

    private record Usage(LocalDate day, int count) {
    }

    private final int dailyLimit;
    private final Map<Long, Usage> usage = new ConcurrentHashMap<>();

    public AiQuota(AppProperties props) {
        this.dailyLimit = props.ai().dailyLimit();
    }

    public boolean tryConsume(CurrentUser user) {
        if (user.admin()) {
            return true;
        }
        LocalDate today = LocalDate.now();
        boolean[] granted = {false};
        usage.compute(user.id(), (id, u) -> {
            int used = u == null || !u.day().equals(today) ? 0 : u.count();
            if (used >= dailyLimit) {
                return u;
            }
            granted[0] = true;
            return new Usage(today, used + 1);
        });
        return granted[0];
    }

    /** AI 服务出错时退还这次的次数 */
    public void refund(CurrentUser user) {
        LocalDate today = LocalDate.now();
        usage.computeIfPresent(user.id(), (id, u) -> u.day().equals(today) && u.count() > 0
                ? new Usage(today, u.count() - 1) : u);
    }

    /** 今天剩余次数，管理员返回 null 表示不限 */
    public Integer remaining(CurrentUser user) {
        if (user.admin()) {
            return null;
        }
        Usage u = usage.get(user.id());
        int used = u == null || !u.day().equals(LocalDate.now()) ? 0 : u.count();
        return Math.max(0, dailyLimit - used);
    }

    public int dailyLimit() {
        return dailyLimit;
    }
}
