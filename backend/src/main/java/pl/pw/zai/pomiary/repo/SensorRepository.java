package pl.pw.zai.pomiary.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pl.pw.zai.pomiary.domain.Sensor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SensorRepository extends JpaRepository<Sensor, Long> {

    Optional<Sensor> findByApiKeyHashAndRevokedAtIsNull(String apiKeyHash);

    Optional<Sensor> findByIdAndRevokedAtIsNull(Long id);

    List<Sensor> findByRevokedAtIsNullOrderByIdAsc();

    @Modifying
    @Query("update Sensor s set s.lastMeasurementAt = :at where s.id = :id and (s.lastMeasurementAt is null or s.lastMeasurementAt < :at)")
    void touch(Long id, Instant at);
}
