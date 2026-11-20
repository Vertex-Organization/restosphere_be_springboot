package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.service.KitchenWriteSupport;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WorkspaceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KitchenWriteSupportImpl implements KitchenWriteSupport {

    private final RestaurantSettingsRepository settings;
    private final WorkspaceStore store;
    private final ServiceWorkflow workflow;

    @Override
    public void commit(User user, String commandType) {
        RestaurantSettings s = settings.findBySingletonKey(1).orElseGet(store::lock);
        s.setRevision(s.getRevision() + 1);
        workflow.audit(user, commandType, user.getId(), "WORKSPACE", commandType);
    }
}
