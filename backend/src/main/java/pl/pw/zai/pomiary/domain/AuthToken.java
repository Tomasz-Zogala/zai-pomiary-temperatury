package pl.pw.zai.pomiary.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "auth_token")
public class AuthToken {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AuthToken() {}

    public AuthToken(String tokenHash, Long userId, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public Long getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
}
