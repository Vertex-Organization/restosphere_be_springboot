package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.AreaRequest;
import com.vertex.restosphere.dto.response.AreaResponse;
import com.vertex.restosphere.entity.Area;
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
public class AreaService {

    private final AreaRepository areas;
    private final RestaurantTableRepository tables;

    @Transactional(readOnly = true)
    public List<AreaResponse> list() {
        return areas.findAll().stream().map(AreaResponse::from).toList();
    }

    public AreaResponse create(AreaRequest req) {
        String name = req.name().trim();
        if (areas.existsByName(name)) {
            throw ApiException.conflict("Area name already exists");
        }
        Area area = new Area();
        area.setName(name);
        return AreaResponse.from(areas.save(area));
    }

    public AreaResponse update(UUID id, AreaRequest req) {
        Area area = find(id);
        String name = req.name().trim();
        if (!name.equals(area.getName()) && areas.existsByName(name)) {
            throw ApiException.conflict("Area name already exists");
        }
        area.setName(name);
        return AreaResponse.from(area);
    }

    public void delete(UUID id) {
        Area area = find(id);
        if (tables.existsByAreaId(id)) {
            throw ApiException.conflict("Area still has tables");
        }
        areas.delete(area);
    }

    private Area find(UUID id) {
        return areas.findById(id).orElseThrow(() -> ApiException.notFound("Area"));
    }
}
