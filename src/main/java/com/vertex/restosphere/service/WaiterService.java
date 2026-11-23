package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.Employee;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface WaiterService {

    void handle(String type, Map<String, Object> command, Employee waiter);

    void accept(UUID sessionId, Employee waiter);

    void addDraft(UUID sessionId, UUID menuItemId, Employee waiter);

    void editDraft(UUID sessionId, UUID draftId, BigDecimal quantity, String note, Employee waiter);

    void removeDraft(UUID sessionId, UUID draftId, Employee waiter);

    void submit(UUID sessionId, Employee waiter);

    void serve(UUID sessionId, UUID itemId, Employee waiter);

    void requestCancel(UUID sessionId, UUID itemId, String reason, Employee waiter);

    void requestPriority(UUID sessionId, UUID orderId, String reason, Employee waiter);

    void requestPayment(UUID sessionId, Employee waiter);

    void withdrawPaymentRequest(UUID sessionId, Employee waiter);

    void guestLeft(UUID sessionId, boolean confirmed, Employee waiter);

    void clean(UUID sessionId, boolean confirmed, Employee waiter);
}
