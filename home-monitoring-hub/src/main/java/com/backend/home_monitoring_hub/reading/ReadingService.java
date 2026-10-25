package com.backend.home_monitoring_hub.reading;

import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ReadingService {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 1000;

    private final ReadingRepository repository;

    public ReadingService(ReadingRepository repository) {
        this.repository = repository;
    }

    public List<ReadingResponse> getReadings(int limit, Instant from, Instant to) {
        if (limit < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be at least 1");
        }
        Limit cappedLimit = Limit.of(Math.min(limit, MAX_LIMIT));

        List<Reading> readings;
        if (from == null && to == null) {
            readings = repository.findAllByOrderByTimestampDesc(cappedLimit);
        } 
        else {
            Instant start = (from != null) ? from : Instant.EPOCH;
            // A day ahead, so readings from a device clock running slightly fast aren't cut off
            Instant end = (to != null) ? to : Instant.now().plus(1, ChronoUnit.DAYS);
            if (start.isAfter(end)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
            }
            readings = repository.findByTimestampBetweenOrderByTimestampDesc(start, end, cappedLimit);
        }

        return readings.stream().map(ReadingResponse::from).toList();
    }

    public Optional<ReadingResponse> getLatest() {
        return repository.findTopByOrderByTimestampDesc().map(ReadingResponse::from);
    }
}