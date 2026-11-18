package com.vertex.restosphere.controller;

import com.vertex.restosphere.service.ReservationWorkflow;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    private final ReservationWorkflow service;
    @GetMapping("/settings") public Map<String,Object> settings(){return service.settings();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@RequestBody Map<String,Object> input){return service.createPublic(input);}
    @PostMapping("/lookup") public Map<String,Object> lookup(@RequestBody Map<String,Object> input){return service.lookup(input);}
    @PostMapping("/cancel") public Map<String,Object> cancel(@RequestBody Map<String,Object> input){return service.cancelPublic(input);}
}
