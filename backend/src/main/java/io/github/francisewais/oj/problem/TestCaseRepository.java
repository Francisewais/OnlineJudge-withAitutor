package io.github.francisewais.oj.problem;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdOrderBySeqAsc(Long problemId);

    long countByProblemId(Long problemId);

    @Modifying
    @Query("delete from TestCase t where t.problemId = :problemId")
    void deleteByProblemId(@Param("problemId") Long problemId);
}
