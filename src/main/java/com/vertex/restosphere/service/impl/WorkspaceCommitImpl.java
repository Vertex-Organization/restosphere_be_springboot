package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.RestaurantSettings;
import com.vertex.restosphere.repository.RestaurantSettingsRepository;
import com.vertex.restosphere.service.ServiceWorkflow;
import com.vertex.restosphere.service.WorkspaceCommit;
import com.vertex.restosphere.service.WorkspaceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceCommitImpl implements WorkspaceCommit {

    private final RestaurantSettingsRepository settings;
    private final WorkspaceStore store;
    private final ServiceWorkflow workflow;

    @Override
    public void commit(Employee user, String commandType) {
        RestaurantSettings s = settings.findBySingletonKey(1).orElseGet(store::lock);
        s.setRevision(s.getRevision() + 1);
        workflow.audit(user, commandType, user.getId(), "WORKSPACE", commandType);
    }
}
