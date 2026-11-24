package com.vertex.restosphere.controller;

import com.vertex.restosphere.service.PayosPayments;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public by design (payOS has no token); the request is trusted only after its signature checks.
 */
@Tag(name = "payOS webhook")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PayosWebhookController {
    private final PayosPayments payosPayments;

    @PostMapping("/payos-webhook")
    public Map<String, Object> receive(@RequestBody Map<String, Object> body) {
        payosPayments.handleWebhook(body);
        return Map.of("success", true);
    }
}
