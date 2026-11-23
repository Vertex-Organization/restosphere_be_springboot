package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Employee;
import java.util.UUID;

/** Thông báo, audit log và đánh dấu đã đọc dùng chung giữa các luồng nghiệp vụ. */
public interface ServiceWorkflow {

    void notify(Role role, String kind, String title, String body, String target);

    void audit(Employee actor, String action, UUID target, String entity, String description);

    /** Đánh dấu đã đọc các thông báo theo {@code ids}/{@code id}; không có thì đọc tất cả. */
    void read(java.util.Map<String, Object> c, Employee user);
}
