package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.User;
import java.util.Map;
import java.util.function.Consumer;

/** Snapshot va command theo workspace (/api/v1/workspaces). */
public interface WorkspaceService {

    Map<String, Object> load(String workspace, String subject);

    Map<String, Object> waiter(String subject, String action, Consumer<User> operation);

    Map<String, Object> command(String workspace, String subject, Map<String, Object> c);
}
