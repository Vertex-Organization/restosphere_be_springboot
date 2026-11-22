package com.vertex.restosphere.controller;

import com.vertex.restosphere.dto.request.CustomerRequest;
import com.vertex.restosphere.dto.response.CustomerResponse;
import com.vertex.restosphere.security.PublicRateLimiter;
import com.vertex.restosphere.service.CustomerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Public; declared permitAll in SecurityConfig. */
@Tag(name = "Customers - Public")
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;
    private final PublicRateLimiter limiter;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(
            @Valid @RequestBody CustomerRequest req, HttpServletRequest http) {
        limiter.check(http);
        return service.register(req);
    }
}
