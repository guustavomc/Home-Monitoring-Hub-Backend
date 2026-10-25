package com.backend.home_monitoring_hub.reading;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReadingRepository extends JpaRepository<Reading, Long> {

    Optional<Reading> findTopByOrderByTimestampDesc();

    List<Reading> findAllByOrderByTimestampDesc(Limit limit);

    List<Reading> findByTimestampBetweenOrderByTimestampDesc(Instant from, Instant to, Limit limit);
}