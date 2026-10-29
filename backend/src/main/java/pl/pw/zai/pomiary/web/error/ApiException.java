package pl.pw.zai.pomiary.web.error;

import org.springframework.http.HttpStatus;

import java.util.List;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final List<FieldError> errors;

    public record FieldError(String field, String message) {}

    public ApiException(HttpStatus status, String detail) {
        this(status, detail, List.of());
    }

    public ApiException(HttpStatus status, String detail, List<FieldError> errors) {
        super(detail);
        this.status = status;
        this.errors = errors;
    }

    public HttpStatus getStatus() { return status; }
    public List<FieldError> getErrors() { return errors; }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " nie istnieje");
    }

    public static ApiException unprocessable(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message, List.of(new FieldError(field, message)));
    }

    public static ApiException badRequest(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, List.of(new FieldError(field, message)));
    }
}
