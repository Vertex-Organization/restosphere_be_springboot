package com.vertex.restosphere.controller;

import com.vertex.restosphere.config.OpenApiConfig;
import com.vertex.restosphere.dto.request.AddDraftRequest;
import com.vertex.restosphere.dto.request.EditDraftRequest;
import com.vertex.restosphere.dto.request.ReasonRequest;
import com.vertex.restosphere.service.WaiterService;
import com.vertex.restosphere.service.WorkspaceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Waiter orders")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/waiter/sessions/{sessionId}")
@PreAuthorize("hasAnyRole('WAITER','ADMIN')")
@RequiredArgsConstructor
public class WaiterOrderController {

    private final WorkspaceService workspace;
    private final WaiterService waiter;

    @PostMapping("/accept")
    public Map<String, Object> accept(@PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "accept", u -> waiter.accept(sessionId, u));
    }

    @PostMapping("/drafts")
    public Map<String, Object> addDraft(@PathVariable UUID sessionId, @Valid @RequestBody AddDraftRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "add-draft", u -> waiter.addDraft(sessionId, req.menuItemId(), u));
    }

    @PatchMapping("/drafts/{draftId}")
    public Map<String, Object> editDraft(@PathVariable UUID sessionId, @PathVariable UUID draftId,
            @Valid @RequestBody EditDraftRequest req, @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "edit-draft",
                u -> waiter.editDraft(sessionId, draftId, req.quantity(), req.note(), u));
    }

    @DeleteMapping("/drafts/{draftId}")
    public Map<String, Object> removeDraft(@PathVariable UUID sessionId, @PathVariable UUID draftId,
            @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "remove-draft", u -> waiter.removeDraft(sessionId, draftId, u));
    }

    @PostMapping("/orders")
    public Map<String, Object> submit(@PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "submit", u -> waiter.submit(sessionId, u));
    }

    @PostMapping("/orders/{orderId}/priority")
    public Map<String, Object> requestPriority(@PathVariable UUID sessionId, @PathVariable UUID orderId,
            @Valid @RequestBody ReasonRequest req, @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "request-priority",
                u -> waiter.requestPriority(sessionId, orderId, req.reason(), u));
    }

    @PostMapping("/items/{itemId}/serve")
    public Map<String, Object> serve(@PathVariable UUID sessionId, @PathVariable UUID itemId,
            @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "serve", u -> waiter.serve(sessionId, itemId, u));
    }

    @PostMapping("/items/{itemId}/cancel-request")
    public Map<String, Object> requestCancel(@PathVariable UUID sessionId, @PathVariable UUID itemId,
            @Valid @RequestBody ReasonRequest req, @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "request-cancel",
                u -> waiter.requestCancel(sessionId, itemId, req.reason(), u));
    }

    @PostMapping("/payment-request")
    public Map<String, Object> requestPayment(@PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "request-payment", u -> waiter.requestPayment(sessionId, u));
    }

    @DeleteMapping("/payment-request")
    public Map<String, Object> withdrawPaymentRequest(@PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "withdraw-payment-request",
                u -> waiter.withdrawPaymentRequest(sessionId, u));
    }

    @PostMapping("/guest-left")
    public Map<String, Object> guestLeft(@PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "guest-left", u -> waiter.guestLeft(sessionId, true, u));
    }

    @PostMapping("/clean")
    public Map<String, Object> clean(@PathVariable UUID sessionId, @AuthenticationPrincipal Jwt jwt) {
        return workspace.waiter(jwt.getSubject(), "clean", u -> waiter.clean(sessionId, true, u));
    }
}
