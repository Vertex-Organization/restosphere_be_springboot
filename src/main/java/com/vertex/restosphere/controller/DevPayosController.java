package com.vertex.restosphere.controller;

import com.vertex.restosphere.service.PayosPayments;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pretends a guest paid, without calling payOS. Exists only under the dev profile. */
@Profile("dev")
@RestController
@RequestMapping("/api/dev/payos")
@RequiredArgsConstructor
public class DevPayosController {
    private final PayosPayments payosPayments;

    @PostMapping("/fake-paid/{orderCode}")
    public Map<String, Object> fakePaid(@PathVariable long orderCode) {
        return Map.of("result", payosPayments.simulatePaid(orderCode));
    }
}
