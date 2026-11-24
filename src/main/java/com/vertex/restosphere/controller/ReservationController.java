package com.vertex.restosphere.controller;

import com.vertex.restosphere.dto.request.PaymentStatusRequest;
import com.vertex.restosphere.dto.response.PaymentStatusResponse;
import com.vertex.restosphere.security.PublicRateLimiter;
import com.vertex.restosphere.service.PayosPaymentQueries;
import com.vertex.restosphere.service.ReservationWorkflow;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    private final ReservationWorkflow service;
    private final PublicRateLimiter limiter;
    private final PayosPaymentQueries queries;

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return service.settings();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @RequestBody Map<String, Object> input, HttpServletRequest req) {
        limiter.check(req);
        return service.createPublic(input);
    }

    @PostMapping("/lookup")
    public Map<String, Object> lookup(
            @RequestBody Map<String, Object> input, HttpServletRequest req) {
        limiter.check(req);
        return service.lookup(input);
    }

    // TODO(confirm): new public endpoint, required by the master prompt for /payment/result. POST
    // because it carries the guest's code and phone; rate limited like the other public calls.
    @PostMapping("/payment-status")
    public PaymentStatusResponse paymentStatus(
            @Valid @RequestBody PaymentStatusRequest input, HttpServletRequest req) {
        limiter.check(req);
        return queries.publicStatus(input.orderCode(), input.code(), input.phone());
    }

    @PostMapping("/cancel")
    public Map<String, Object> cancel(
            @RequestBody Map<String, Object> input, HttpServletRequest req) {
        limiter.check(req);
        return service.cancelPublic(input);
    }
}
