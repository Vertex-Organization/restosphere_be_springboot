package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.service.ReservationWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationScheduler {
    private final ReservationWorkflow reservations;

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void expireNoShows() {
        reservations.expireNoShows();
    }
}
