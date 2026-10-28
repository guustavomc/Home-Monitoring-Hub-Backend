package com.backend.home_monitoring_hub.reading;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingServiceTest {

    @Mock
    private ReadingRepository repository;

    @InjectMocks
    private ReadingService service;

    @Test
    void capsLimitAtMaximum() {
        when(repository.findAllByOrderByTimestampDesc(any())).thenReturn(List.of());

        service.getReadings(5000, null, null);

        verify(repository).findAllByOrderByTimestampDesc(Limit.of(ReadingService.MAX_LIMIT));
    }

    @Test
    void rejectsLimitBelowOne() {
        assertThatThrownBy(() -> service.getReadings(0, null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void rejectsFromAfterTo() {
        Instant from = Instant.parse("2026-10-26T00:00:00Z");
        Instant to = Instant.parse("2026-10-25T00:00:00Z");

        assertThatThrownBy(() -> service.getReadings(10, from, to))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void usesTimeRangeQueryWhenOnlyFromIsGiven() {
        Instant from = Instant.parse("2026-10-25T20:00:00Z");
        when(repository.findByTimestampBetweenOrderByTimestampDesc(any(), any(), any())).thenReturn(List.of());

        service.getReadings(10, from, null);

        verify(repository).findByTimestampBetweenOrderByTimestampDesc(eq(from), any(), eq(Limit.of(10)));
    }
}