package com.backend.home_monitoring_hub.reading;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadingRepository extends JpaRepository<Reading, Long> {
}