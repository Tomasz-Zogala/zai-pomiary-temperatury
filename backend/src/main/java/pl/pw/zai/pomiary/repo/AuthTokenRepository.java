package pl.pw.zai.pomiary.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import pl.pw.zai.pomiary.domain.AuthToken;

import java.time.Instant;

public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {

    @Modifying
    @Transactional
    @Query("delete from AuthToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);

    @Modifying
    @Transactional
    @Query("delete from AuthToken t where t.userId = :userId and t.tokenHash <> :keep")
    int deleteOthers(Long userId, String keep);
}
