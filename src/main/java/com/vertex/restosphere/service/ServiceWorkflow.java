package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.User;
import java.util.Map;
import java.util.UUID;

/** Lenh cua phuc vu, bep; thong bao va audit chung. */
public interface ServiceWorkflow {

    void waiter(String type, Map<String, Object> c, User user);

    void kitchen(String type, Map<String, Object> c, User user);

    void notify(Role role, String kind, String title, String body, String target);

    void audit(User actor, String action, UUID target, String entity, String description);

    void read(Map<String, Object> c, User user);
}
