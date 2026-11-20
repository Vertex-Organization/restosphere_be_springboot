package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.dto.request.TableRequest;
import com.vertex.restosphere.dto.response.ManagerTableResponse;
import com.vertex.restosphere.dto.response.TableResponse;
import java.util.List;
import java.util.UUID;

public interface TableService {

    List<ManagerTableResponse> listForManager(UUID actorId);

    List<TableResponse> list(UUID areaId, TableStatus status);

    TableResponse get(UUID id);

    TableResponse create(TableRequest req);

    TableResponse update(UUID actorId, UUID id, TableRequest req);

    TableResponse updateStatus(UUID id, TableStatus status);

    void delete(UUID id);
}
