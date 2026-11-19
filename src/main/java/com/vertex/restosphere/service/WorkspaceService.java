package com.vertex.restosphere.service;

import java.util.Map;

/** Snapshot va command theo workspace (/api/v1/workspaces). */
public interface WorkspaceService {

    Map<String, Object> load(String workspace, String subject);

    Map<String, Object> command(String workspace, String subject, Map<String, Object> c);
}
