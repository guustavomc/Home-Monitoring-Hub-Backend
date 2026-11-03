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

    // device is optional: null (or blank) means readings from all devices
    public List<ReadingResponse> getReadings(int limit, Instant from, Instant to, String device) {
        if (limit < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be at least 1");
        }
        Limit cappedLimit = Limit.of(Math.min(limit, MAX_LIMIT));
        boolean allDevices = isBlank(device);

        List<Reading> readings;
        if (from == null && to == null) {
            readings = allDevices
                    ? repository.findAllByOrderByTimestampDesc(cappedLimit)
                    : repository.findByDeviceIdOrderByTimestampDesc(device, cappedLimit);
        }
        else {
            Instant start = (from != null) ? from : Instant.EPOCH;
            // A day ahead, so readings from a device clock running slightly fast aren't cut off
            Instant end = (to != null) ? to : Instant.now().plus(1, ChronoUnit.DAYS);
            if (start.isAfter(end)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
            }
            readings = allDevices
                    ? repository.findByTimestampBetweenOrderByTimestampDesc(start, end, cappedLimit)
                    : repository.findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(device, start, end, cappedLimit);
        }

        return readings.stream().map(ReadingResponse::from).toList();
    }

    public Optional<ReadingResponse> getLatest(String device) {
        Optional<Reading> latest = isBlank(device)
                ? repository.findTopByOrderByTimestampDesc()
                : repository.findTopByDeviceIdOrderByTimestampDesc(device);
        return latest.map(ReadingResponse::from);
    }

    public List<String> getDeviceIds() {
        return repository.findDeviceIds();
    }

    private static boolean isBlank(String device) {
        return device == null || device.isBlank();
    }
}
