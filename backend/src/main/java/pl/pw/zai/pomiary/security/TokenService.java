package pl.pw.zai.pomiary.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pw.zai.pomiary.config.AppProperties;
import pl.pw.zai.pomiary.domain.AppUser;
import pl.pw.zai.pomiary.domain.AuthToken;
import pl.pw.zai.pomiary.repo.AppUserRepository;
import pl.pw.zai.pomiary.repo.AuthTokenRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class TokenService {

    private final AuthTokenRepository tokens;
    private final AppUserRepository users;
    private final Duration ttl;

    public TokenService(AuthTokenRepository tokens, AppUserRepository users, AppProperties props) {
        this.tokens = tokens;
        this.users = users;
        this.ttl = props.tokenTtl();
    }

    public record Issued(String token, long expiresIn) {}

    @Transactional
    public Issued issue(AppUser user) {
        String token = Secrets.randomToken();
        Instant now = Instant.now();
        tokens.save(new AuthToken(Secrets.sha256(token), user.getId(), now, now.plus(ttl)));
        return new Issued(token, ttl.toSeconds());
    }

    @Transactional(readOnly = true)
    public Optional<AuthPrincipals.Admin> resolve(String token) {
        String hash = Secrets.sha256(token);
        return tokens.findById(hash)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .flatMap(t -> users.findById(t.getUserId())
                        .map(u -> new AuthPrincipals.Admin(u.getId(), u.getUsername(), hash)));
    }

    @Transactional
    public void revoke(String tokenHash) {
        tokens.deleteById(tokenHash);
    }

    @Transactional
    public void revokeOthers(Long userId, String keepHash) {
        tokens.deleteOthers(userId, keepHash);
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void purgeExpired() {
        tokens.deleteExpired(Instant.now());
    }
}
