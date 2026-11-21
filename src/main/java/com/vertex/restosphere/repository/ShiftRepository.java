package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.Shift;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ShiftRepository
        extends JpaRepository<Shift, UUID>, JpaSpecificationExecutor<Shift> {}
