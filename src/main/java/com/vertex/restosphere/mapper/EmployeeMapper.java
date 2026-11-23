package com.vertex.restosphere.mapper;

import com.vertex.restosphere.dto.response.EmployeeResponse;
import com.vertex.restosphere.entity.Employee;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public EmployeeResponse toResponse(Employee user) {
        return new EmployeeResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
