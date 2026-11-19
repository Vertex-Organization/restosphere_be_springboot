package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.User;
import java.util.Map;

/** Lenh cu cua Manager/Admin trong workspace. */
public interface ManagementWorkflow {

    void command(String type, Map<String, Object> c, User user);

    void admin(String type, Map<String, Object> c, User actor);
}
