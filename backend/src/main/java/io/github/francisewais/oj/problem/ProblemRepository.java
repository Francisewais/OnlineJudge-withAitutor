package io.github.francisewais.oj.problem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    @Query("""
            select p from Problem p
            where (:includeHidden = true or p.visible = true)
              and (:keyword = '' or lower(p.title) like concat('%', :keyword, '%')
                   or lower(p.tags) like concat('%', :keyword, '%'))
              and (:difficulty is null or p.difficulty = :difficulty)
            """)
    Page<Problem> search(@Param("includeHidden") boolean includeHidden, @Param("keyword") String keyword,
                         @Param("difficulty") Problem.Difficulty difficulty, Pageable pageable);

    @Modifying
    @Query("update Problem p set p.submitCount = p.submitCount + 1, p.acceptedCount = p.acceptedCount + :accepted"
            + " where p.id = :id")
    void incrementCounts(@Param("id") long id, @Param("accepted") int accepted);
}
