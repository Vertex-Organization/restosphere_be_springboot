package com.vertex.restosphere.repository;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.Employee;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Employee> findAllByOrderByCreatedAtDesc();

    long countByRoleAndEnabledTrue(Role role);

    // Nap khu phu trach cung query de lay ten khu khong bi N+1.
    @Override
    @EntityGraph(attributePaths = "assignedArea")
    List<Employee> findAll();
}
