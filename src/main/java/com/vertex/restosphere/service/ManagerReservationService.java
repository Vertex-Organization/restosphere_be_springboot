package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.ChangeReservationTableRequest;
import com.vertex.restosphere.dto.response.ManagerReservationResponse;
import com.vertex.restosphere.entity.Reservation;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ManagerReservationService {

    List<ManagerReservationResponse> list(UUID actorId, LocalDate from, LocalDate to, Reservation.Status status);

    ManagerReservationResponse changeTable(UUID actorId, UUID id, ChangeReservationTableRequest req);
}
