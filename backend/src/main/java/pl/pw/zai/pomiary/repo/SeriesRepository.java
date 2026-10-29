package pl.pw.zai.pomiary.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pw.zai.pomiary.domain.Series;

public interface SeriesRepository extends JpaRepository<Series, Long> {
}
