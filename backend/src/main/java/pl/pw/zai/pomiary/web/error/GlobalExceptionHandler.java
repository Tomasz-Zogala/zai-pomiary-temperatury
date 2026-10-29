package pl.pw.zai.pomiary.web.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static ProblemDetail problem(HttpStatus status, String detail, List<ApiException.FieldError> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        if (errors != null && !errors.isEmpty()) pd.setProperty("errors", errors);
        return pd;
    }

    private static ResponseEntity<Object> respond(ProblemDetail pd) {
        return ResponseEntity.status(pd.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(pd);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex) {
        return respond(problem(ex.getStatus(), ex.getMessage(), ex.getErrors()));
    }

    // np. /api/series/abc - taki zasób nie istnieje
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(problem(HttpStatus.NOT_FOUND, "Zasób nie istnieje", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleOther(Exception ex) {
        log.error("Nieobsłużony błąd", ex);
        return respond(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Wewnętrzny błąd serwera", null));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<ApiException.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiException.FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return respond(problem(HttpStatus.BAD_REQUEST, "Niepoprawne dane wejściowe", errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        String detail = "Niepoprawny format JSON";
        List<ApiException.FieldError> errors = List.of();
        if (ex.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
            String field = mie.getPath().getLast().getFieldName();
            String msg = mie instanceof InvalidFormatException ? "Niepoprawna wartość lub typ" : "Niepoprawny typ";
            detail = "Niepoprawna wartość pola " + field;
            errors = List.of(new ApiException.FieldError(field, msg));
        }
        return respond(problem(HttpStatus.BAD_REQUEST, detail, errors));
    }

    // Klient nie przyjmuje JSON - odpowiadamy 406 bez treści
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(@NonNull HttpMediaTypeNotAcceptableException ex,
            @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @Override
    @NonNull
    protected ResponseEntity<Object> createResponseEntity(Object body, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode, @NonNull WebRequest request) {
        if (body instanceof ProblemDetail pd && pd.getTitle() == null) {
            HttpStatus st = HttpStatus.resolve(statusCode.value());
            if (st != null) pd.setTitle(st.getReasonPhrase());
        }
        HttpHeaders h = new HttpHeaders();
        h.putAll(headers);
        h.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(body, h, statusCode);
    }
}
