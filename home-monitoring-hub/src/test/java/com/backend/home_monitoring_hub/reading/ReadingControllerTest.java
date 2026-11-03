package com.backend.home_monitoring_hub.reading;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadingController.class)
class ReadingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReadingService service;

    private final ReadingResponse sample = new ReadingResponse(
            1L, "livingroom", Instant.parse("2026-10-25T21:40:39Z"), 24.5, null, 237.5, 1010.67, 21.5);

    @Test
    void latestReturnsTheReading() throws Exception {
        when(service.getLatest(null)).thenReturn(Optional.of(sample));

        mockMvc.perform(get("/api/readings/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").value("2026-10-25T21:40:39Z"))
                .andExpect(jsonPath("$.temperature").value(24.5))
                .andExpect(jsonPath("$.humidity").value(nullValue()));
    }

    @Test
    void latestReturns404WhenThereAreNoReadings() throws Exception {
        when(service.getLatest(null)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/readings/latest"))
                .andExpect(status().isNotFound());
    }

    @Test
    void readingsUseDefaultLimit() throws Exception {
        when(service.getReadings(ReadingService.DEFAULT_LIMIT, null, null, null)).thenReturn(List.of(sample));

        mockMvc.perform(get("/api/readings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(service).getReadings(ReadingService.DEFAULT_LIMIT, null, null, null);
    }

    @Test
    void readingsPassDeviceFilterToService() throws Exception {
        when(service.getReadings(ReadingService.DEFAULT_LIMIT, null, null, "livingroom")).thenReturn(List.of(sample));

        mockMvc.perform(get("/api/readings").param("device", "livingroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceId").value("livingroom"));
    }

    @Test
    void latestPassesDeviceFilterToService() throws Exception {
        when(service.getLatest("livingroom")).thenReturn(Optional.of(sample));

        mockMvc.perform(get("/api/readings/latest").param("device", "livingroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("livingroom"));
    }

    @Test
    void devicesListsKnownDeviceIds() throws Exception {
        when(service.getDeviceIds()).thenReturn(List.of("garage", "livingroom"));

        mockMvc.perform(get("/api/readings/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("garage"))
                .andExpect(jsonPath("$[1]").value("livingroom"));
    }

    @Test
    void invalidParametersReturn400() throws Exception {
        mockMvc.perform(get("/api/readings").param("limit", "abc"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/readings").param("from", "yesterday"))
                .andExpect(status().isBadRequest());
    }
}