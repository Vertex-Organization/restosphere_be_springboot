package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.response.ManagerReportResponse;
import com.vertex.restosphere.entity.Payment;
import java.time.LocalDate;
import java.util.UUID;

public interface ManagerReportService {

    ManagerReportResponse report(UUID actorId, LocalDate from, LocalDate to, String shift, Payment.Method method);
}
