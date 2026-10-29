package io.github.francisewais.oj.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Modifying
    @Query("update User u set u.submitCount = u.submitCount + 1 where u.id = :id")
    void incrementSubmitCount(@Param("id") long id);

    @Modifying
    @Query("update User u set u.solvedCount = :count where u.id = :id")
    void updateSolvedCount(@Param("id") long id, @Param("count") int count);

    @Modifying
    @Query(value = "update users u set solved_count = (select count(distinct s.problem_id) from submissions s"
            + " where s.user_id = u.id and s.verdict = 'AC')", nativeQuery = true)
    void recomputeSolvedCounts();
}
