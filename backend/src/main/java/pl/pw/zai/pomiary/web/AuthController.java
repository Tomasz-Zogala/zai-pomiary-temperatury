package pl.pw.zai.pomiary.web;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.pw.zai.pomiary.domain.AppUser;
import pl.pw.zai.pomiary.repo.AppUserRepository;
import pl.pw.zai.pomiary.security.AuthPrincipals;
import pl.pw.zai.pomiary.security.TokenService;
import pl.pw.zai.pomiary.web.dto.Dtos.LoginRequest;
import pl.pw.zai.pomiary.web.dto.Dtos.LoginResponse;
import pl.pw.zai.pomiary.web.dto.Dtos.PasswordChange;
import pl.pw.zai.pomiary.web.error.ApiException;

@RestController
@RequestMapping(value = "/api/auth", produces = "application/json")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    // porównanie z fikcyjnym skrótem, żeby czas odpowiedzi nie zdradzał istnienia loginu
    private final String dummyHash;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, TokenService tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    @PostMapping(value = "/login", consumes = "application/json")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        AppUser user = users.findByUsername(req.username()).orElse(null);
        boolean ok = encoder.matches(req.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !ok) {
            log.warn("Nieudane logowanie dla użytkownika '{}'", req.username());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Niepoprawny login lub hasło");
        }
        TokenService.Issued t = tokens.issue(user);
        return new LoginResponse(t.token(), "Bearer", t.expiresIn());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipals.Admin admin) {
        tokens.revoke(admin.tokenHash());
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/password", consumes = "application/json")
    @Transactional
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthPrincipals.Admin admin,
                                               @Valid @RequestBody PasswordChange req) {
        AppUser user = users.findById(admin.userId()).orElseThrow(() -> ApiException.notFound("Użytkownik"));
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bieżące hasło jest niepoprawne");
        }
        if (req.currentPassword().equals(req.newPassword())) {
            throw ApiException.unprocessable("newPassword", "Nowe hasło musi różnić się od bieżącego");
        }
        user.setPasswordHash(encoder.encode(req.newPassword()));
        tokens.revokeOthers(user.getId(), admin.tokenHash());
        return ResponseEntity.noContent().build();
    }
}
