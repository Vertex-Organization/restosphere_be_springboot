package com.vertex.restosphere.controller;

import com.vertex.restosphere.service.WorkspaceService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService service;

    @GetMapping("/{workspace}")
    public Map<String, Object> load(
            @PathVariable String workspace, @AuthenticationPrincipal Jwt jwt) {
        return service.load(workspace, jwt.getSubject());
    }

    @PostMapping("/staff/commands")
    public Map<String, Object> command(
            @AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> command) {
        return service.command(jwt.getSubject(), command);
    }
}
