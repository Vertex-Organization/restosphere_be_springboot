package com.vertex.restosphere.controller;

import com.vertex.restosphere.service.WorkspaceService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    private final WorkspaceService service;
    @GetMapping("/{workspace}")
    public Map<String,Object> load(@PathVariable String workspace,@AuthenticationPrincipal Jwt jwt) {return service.load(workspace,jwt.getSubject());}
    @PostMapping("/{workspace}/commands")
    public Map<String,Object> command(@PathVariable String workspace,@AuthenticationPrincipal Jwt jwt,@RequestBody Map<String,Object> command) {return service.command(workspace,jwt.getSubject(),command);}
}
