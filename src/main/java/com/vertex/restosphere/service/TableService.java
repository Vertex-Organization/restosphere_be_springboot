package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.TableStatus;
import com.vertex.restosphere.dto.request.TableRequest;
import com.vertex.restosphere.dto.response.TableResponse;
import com.vertex.restosphere.entity.RestaurantTable;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AreaRepository;
import com.vertex.restosphere.repository.RestaurantTableRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TableService {

    private final RestaurantTableRepository tables;
    private final AreaRepository areas;

    @Transactional(readOnly = true)
    public List<TableResponse> list(UUID areaId, TableStatus status) {
        List<RestaurantTable> result = areaId != null && status != null ? tables.findByAreaIdAndStatus(areaId, status)
                : areaId != null ? tables.findByAreaId(areaId)
                : status != null ? tables.findByStatus(status)
                : tables.findAll();
        return result.stream().map(TableResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TableResponse get(UUID id) {
        return TableResponse.from(find(id));
    }

    public TableResponse create(TableRequest req) {
        String code = req.code().trim();
        if (tables.existsByCode(code)) {
            throw ApiException.conflict("Table code already exists");
        }
        RestaurantTable table = new RestaurantTable();
        apply(table, code, req);
        return TableResponse.from(tables.save(table));
    }

    public TableResponse update(UUID id, TableRequest req) {
        RestaurantTable table = find(id);
        String code = req.code().trim();
        if (!code.equals(table.getCode()) && tables.existsByCode(code)) {
            throw ApiException.conflict("Table code already exists");
        }
        apply(table, code, req);
        return TableResponse.from(table);
    }

    public TableResponse updateStatus(UUID id, TableStatus status) {
        RestaurantTable table = find(id);
        table.setStatus(status);
        return TableResponse.from(table);
    }

    public void delete(UUID id) {
        tables.delete(find(id));
    }

    private void apply(RestaurantTable table, String code, TableRequest req) {
        table.setCode(code);
        table.setCapacity(req.capacity());
        table.setArea(areas.findById(req.areaId()).orElseThrow(() -> ApiException.notFound("Area")));
    }

    private RestaurantTable find(UUID id) {
        return tables.findById(id).orElseThrow(() -> ApiException.notFound("Table"));
    }
}
