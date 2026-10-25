package com.backend.home_monitoring_hub.reading;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/readings")
public class ReadingController {

    private final ReadingService service;

    public ReadingController(ReadingService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReadingResponse> getReadings(
            @RequestParam(defaultValue = "" + ReadingService.DEFAULT_LIMIT) int limit,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return service.getReadings(limit, from, to);
    }

    @GetMapping("/latest")
    public ResponseEntity<ReadingResponse> getLatest() {
        return service.getLatest()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
}
