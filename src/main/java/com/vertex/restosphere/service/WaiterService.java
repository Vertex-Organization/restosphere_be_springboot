package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.User;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface WaiterService {

    void handle(String type, Map<String, Object> command, User waiter);

    void accept(UUID sessionId, User waiter);

    void addDraft(UUID sessionId, UUID menuItemId, User waiter);

    void editDraft(UUID sessionId, UUID draftId, BigDecimal quantity, String note, User waiter);

    void removeDraft(UUID sessionId, UUID draftId, User waiter);

    void submit(UUID sessionId, User waiter);

    void serve(UUID sessionId, UUID itemId, User waiter);

    void requestCancel(UUID sessionId, UUID itemId, String reason, User waiter);

    void requestPriority(UUID sessionId, UUID orderId, String reason, User waiter);

    void requestPayment(UUID sessionId, User waiter);

    void withdrawPaymentRequest(UUID sessionId, User waiter);

    void guestLeft(UUID sessionId, boolean confirmed, User waiter);

    void clean(UUID sessionId, boolean confirmed, User waiter);
}
