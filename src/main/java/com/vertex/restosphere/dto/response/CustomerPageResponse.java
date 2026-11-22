package com.vertex.restosphere.dto.response;

import java.util.List;

public record CustomerPageResponse(
        List<CustomerResponse> items, int page, int size, long totalItems, int totalPages) {}
