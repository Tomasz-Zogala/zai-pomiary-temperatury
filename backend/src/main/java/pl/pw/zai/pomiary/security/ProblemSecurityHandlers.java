package pl.pw.zai.pomiary.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import pl.pw.zai.pomiary.web.error.GlobalExceptionHandler;

import java.io.IOException;

// 401/403 z warstwy Spring Security w formacie RFC 9457
@Component
public class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public ProblemSecurityHandlers(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
        String detail = ApiKeyFilter.isMeasurementUpload(req)
                ? "Wymagany poprawny klucz czujnika w nagłówku X-API-Key"
                : "Wymagane uwierzytelnienie tokenem Bearer";
        write(res, HttpStatus.UNAUTHORIZED, detail);
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) throws IOException {
        write(res, HttpStatus.FORBIDDEN, "Brak uprawnień do tej operacji");
    }

    private void write(HttpServletResponse res, HttpStatus status, String detail) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        if (status == HttpStatus.UNAUTHORIZED) res.setHeader("WWW-Authenticate", "Bearer");
        mapper.writeValue(res.getOutputStream(), GlobalExceptionHandler.problem(status, detail, null));
    }
}
