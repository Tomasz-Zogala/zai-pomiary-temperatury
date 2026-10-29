package pl.pw.zai.pomiary.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.pw.zai.pomiary.domain.AppUser;
import pl.pw.zai.pomiary.repo.AppUserRepository;

// Tworzy konto administratora z ADMIN_USERNAME / ADMIN_PASSWORD przy pierwszym starcie
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AdminInitializer(AppUserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        var admin = props.admin();
        if (admin == null || isBlank(admin.username()) || isBlank(admin.password())) {
            log.warn("Brak konta administratora - ustaw ADMIN_USERNAME i ADMIN_PASSWORD i uruchom ponownie");
            return;
        }
        users.save(new AppUser(admin.username().trim(), encoder.encode(admin.password())));
        log.info("Utworzono konto administratora '{}'", admin.username().trim());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
