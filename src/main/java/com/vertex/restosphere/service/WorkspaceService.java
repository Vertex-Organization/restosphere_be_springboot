package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.Employee;
import java.util.Map;
import java.util.function.Consumer;

/** Snapshot và lệnh theo workspace (/api/v1/workspaces) dùng cho bếp, thu ngân, lễ tân, phục vụ. */
public interface WorkspaceService {

    Map<String, Object> load(String workspace, String subject);

    /** Chạy thao tác của phục vụ rồi trả lại snapshot staff. */
    Map<String, Object> waiter(String subject, String action, Consumer<Employee> operation);

    /** Lệnh của lễ tân và phục vụ trên workspace staff. */
    Map<String, Object> command(String subject, Map<String, Object> c);
}
