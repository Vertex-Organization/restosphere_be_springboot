package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.Employee;
import java.util.Map;

public interface WorkspaceSnapshots {

    Map<String, Object> snapshot(String workspace, Employee user);
}
