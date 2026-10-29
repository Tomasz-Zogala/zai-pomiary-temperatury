package pl.pw.zai.pomiary.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import pl.pw.zai.pomiary.domain.Measurement;
import pl.pw.zai.pomiary.domain.Sensor;
import pl.pw.zai.pomiary.domain.Series;

import java.time.Instant;
import java.time.OffsetDateTime;

public final class Dtos {

    private Dtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}

    public record PasswordChange(@NotBlank String currentPassword,
                                 @NotNull @Size(min = 8, max = 72, message = "Hasło musi mieć od 8 do 72 znaków") String newPassword) {}

    public record SeriesInput(
            @NotBlank @Size(max = 100) String name,
            @NotNull Double minValue,
            @NotNull Double maxValue,
            @NotNull @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Kolor musi mieć format #RRGGBB") String color,
            @Size(max = 50) String icon,
            @Size(max = 20) String unit) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SeriesDto(Long id, String name, double minValue, double maxValue, String color, String icon, String unit) {
        public static SeriesDto of(Series s) {
            return new SeriesDto(s.getId(), s.getName(), s.getMinValue(), s.getMaxValue(), s.getColor(), s.getIcon(), s.getUnit());
        }
    }

    public record MeasurementInput(@NotNull Double value, OffsetDateTime timestamp) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MeasurementDto(Long id, Long seriesId, Long sensorId, double value, Instant timestamp) {
        public static MeasurementDto of(Measurement m) {
            return new MeasurementDto(m.getId(), m.getSeriesId(), m.getSensorId(), m.getValue(), m.getTimestamp());
        }
    }

    public record SensorInput(@NotBlank @Size(max = 100) String name, @NotNull Long seriesId) {}

    public record SensorDto(Long id, String name, Long seriesId, Instant createdAt, Instant lastMeasurementAt) {
        public static SensorDto of(Sensor s) {
            return new SensorDto(s.getId(), s.getName(), s.getSeriesId(), s.getCreatedAt(), s.getLastMeasurementAt());
        }
    }

    public record SensorCreatedDto(Long id, String name, Long seriesId, Instant createdAt, Instant lastMeasurementAt, String apiKey) {}
}
