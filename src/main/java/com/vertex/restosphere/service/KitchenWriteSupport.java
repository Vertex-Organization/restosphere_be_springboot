package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Phan chung cua moi lenh ghi cua bep (giong WorkspaceService.command): tang revision de web bep polling thay
 * thay doi, va ghi audit WORKSPACE cua lenh. Goi trong transaction da giu RestaurantLock.
 */
@Component
@RequiredArgsConstructor
class KitchenWriteSupport {

    private final RestaurantSettingsRepository settings;
    private final WorkspaceStore store;
    private final ServiceWorkflow workflow;

    void commit(User user, String commandType) {
        RestaurantSettings s = settings.findBySingletonKey(1).orElseGet(store::lock);
        s.setRevision(s.getRevision() + 1);
        workflow.audit(user, commandType, user.getId(), "WORKSPACE", commandType);
    }
}
