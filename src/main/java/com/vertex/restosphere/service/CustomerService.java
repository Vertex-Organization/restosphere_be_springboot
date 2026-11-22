package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.AccountStatus;
import com.vertex.restosphere.dto.request.CustomerRequest;
import com.vertex.restosphere.dto.response.CustomerPageResponse;
import com.vertex.restosphere.dto.response.CustomerResponse;
import java.util.UUID;

/** Thành viên: khách tự đăng ký trên web, quản lý tra cứu và cập nhật. */
public interface CustomerService {

    CustomerResponse register(CustomerRequest req);

    CustomerPageResponse list(UUID actorId, String q, AccountStatus status, int page, int size);

    CustomerResponse get(UUID actorId, UUID id);

    CustomerResponse create(UUID actorId, CustomerRequest req);

    CustomerResponse update(UUID actorId, UUID id, CustomerRequest req);

    CustomerResponse updateStatus(UUID actorId, UUID id, AccountStatus status);
}
