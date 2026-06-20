package com.raspel.cardtracker.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
    List<AppUser> findAllByActiveTrue();

    @Modifying
    @Query(value = "UPDATE app_user SET last_login_at = :now WHERE username = :username", nativeQuery = true)
    void updateLastLogin(@Param("username") String username, @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = "UPDATE app_user SET failed_login_attempts = failed_login_attempts + 1, locked_until = CASE WHEN failed_login_attempts + 1 >= 5 THEN CAST(:lockedUntil AS TIMESTAMP) ELSE locked_until END WHERE username = :username", nativeQuery = true)
    void recordFailedLogin(@Param("username") String username, @Param("lockedUntil") LocalDateTime lockedUntil);

    @Modifying
    @Query(value = "UPDATE app_user SET failed_login_attempts = 0, locked_until = NULL WHERE username = :username", nativeQuery = true)
    void recordSuccessfulLogin(@Param("username") String username);
}
