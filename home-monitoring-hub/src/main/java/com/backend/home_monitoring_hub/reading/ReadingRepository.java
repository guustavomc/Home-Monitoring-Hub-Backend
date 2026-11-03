package com.backend.home_monitoring_hub.reading;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReadingRepository extends JpaRepository<Reading, Long> {

    Optional<Reading> findTopByOrderByTimestampDesc();

    List<Reading> findAllByOrderByTimestampDesc(Limit limit);

    List<Reading> findByTimestampBetweenOrderByTimestampDesc(Instant from, Instant to, Limit limit);

    Optional<Reading> findTopByDeviceIdOrderByTimestampDesc(String deviceId);

    List<Reading> findByDeviceIdOrderByTimestampDesc(String deviceId, Limit limit);
    
    List<Reading> findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(String deviceId, Instant from, Instant to, Limit limit);

    @Query("SELECT DISTINCT r.deviceId FROM Reading r ORDER BY r.deviceId")
    List<String> findDeviceIds();
}