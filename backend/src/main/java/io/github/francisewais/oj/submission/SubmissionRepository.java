package io.github.francisewais.oj.submission;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query("select distinct s.problemId from Submission s where s.userId = :userId and s.verdict = 'AC'")
    List<Long> findSolvedProblemIds(@Param("userId") long userId);

    @Query("select distinct s.problemId from Submission s where s.userId = :userId")
    List<Long> findAttemptedProblemIds(@Param("userId") long userId);

    @Query("select count(distinct s.problemId) from Submission s where s.userId = :userId and s.verdict = 'AC'")
    long countSolvedProblems(@Param("userId") long userId);

    long countByUserIdAndStatusIn(Long userId, Collection<Submission.Status> statuses);

    List<Submission> findByStatusInOrderByIdAsc(Collection<Submission.Status> statuses);

    Optional<Submission> findFirstByUserIdAndProblemIdOrderByIdDesc(Long userId, Long problemId);

    @Query(value = """
            select new io.github.francisewais.oj.submission.SubmissionRow(
                s.id, s.userId, s.problemId, s.language, s.status, s.verdict, s.timeMs, s.memoryKb,
                s.passedCount, s.totalCount, s.createdAt)
            from Submission s
            where (:userId is null or s.userId = :userId)
              and (:problemId is null or s.problemId = :problemId)
              and (:verdict is null or s.verdict = :verdict)
            """,
            countQuery = """
                    select count(s) from Submission s
                    where (:userId is null or s.userId = :userId)
                      and (:problemId is null or s.problemId = :problemId)
                      and (:verdict is null or s.verdict = :verdict)
                    """)
    Page<SubmissionRow> search(@Param("userId") Long userId, @Param("problemId") Long problemId,
                     @Param("verdict") String verdict, Pageable pageable);
}
