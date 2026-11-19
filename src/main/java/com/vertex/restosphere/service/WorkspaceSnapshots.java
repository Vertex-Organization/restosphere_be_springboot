package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.User;
import java.util.Map;

public interface WorkspaceSnapshots {

    Map<String, Object> snapshot(String workspace, User user);
}
