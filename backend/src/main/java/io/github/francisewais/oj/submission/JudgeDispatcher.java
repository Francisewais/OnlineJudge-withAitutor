package io.github.francisewais.oj.submission;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.francisewais.oj.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 后台评测线程池；启动时把上次没评完的提交重新排队。 */
@Component
public class JudgeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(JudgeDispatcher.class);

    private final ExecutorService executor;
    private final JudgeService judgeService;
    private final SubmissionRepository submissions;

    public JudgeDispatcher(AppProperties props, JudgeService judgeService, SubmissionRepository submissions) {
        this.executor = Executors.newFixedThreadPool(Math.max(1, props.judger().concurrency()),
                Thread.ofPlatform().name("judge-", 1).factory());
        this.judgeService = judgeService;
        this.submissions = submissions;
    }

    public void dispatch(long submissionId) {
        executor.execute(() -> judgeService.judge(submissionId));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void requeueUnfinished() {
        List<Submission> unfinished = submissions.findByStatusInOrderByIdAsc(
                List.of(Submission.Status.PENDING, Submission.Status.JUDGING));
        if (!unfinished.isEmpty()) {
            log.info("重新评测 {} 个未完成的提交", unfinished.size());
        }
        unfinished.forEach(s -> dispatch(s.getId()));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
