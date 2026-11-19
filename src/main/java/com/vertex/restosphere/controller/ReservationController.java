package com.vertex.restosphere.controller;

import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.ReservationWorkflow;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    static final int LIMIT_PER_MINUTE=20;
    private final ReservationWorkflow service;
    private final Map<String,long[]> hits=new ConcurrentHashMap<>();
    @GetMapping("/settings") public Map<String,Object> settings(){return service.settings();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@RequestBody Map<String,Object> input,HttpServletRequest req){limit(req);return service.createPublic(input);}
    @PostMapping("/lookup") public Map<String,Object> lookup(@RequestBody Map<String,Object> input,HttpServletRequest req){limit(req);return service.lookup(input);}
    @PostMapping("/cancel") public Map<String,Object> cancel(@RequestBody Map<String,Object> input,HttpServletRequest req){limit(req);return service.cancelPublic(input);}
    // In-memory per-IP counter (single instance); behind a proxy set server.forward-headers-strategy.
    void limit(HttpServletRequest req) {
        long minute=System.currentTimeMillis()/60000;
        var h=hits.compute(req.getRemoteAddr(),(k,v)->v==null||v[0]!=minute?new long[]{minute,1}:new long[]{minute,v[1]+1});
        if(hits.size()>10000)hits.values().removeIf(v->v[0]!=minute);
        if(h[1]>LIMIT_PER_MINUTE)throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Bạn thao tác quá nhanh. Vui lòng thử lại sau 1 phút.");
    }
}
