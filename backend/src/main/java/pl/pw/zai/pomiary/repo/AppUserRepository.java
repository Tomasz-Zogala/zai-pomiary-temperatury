package pl.pw.zai.pomiary.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pw.zai.pomiary.domain.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
}
