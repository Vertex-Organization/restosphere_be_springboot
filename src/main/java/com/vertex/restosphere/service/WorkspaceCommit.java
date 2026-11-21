package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.User;

/**
 * Ghi nhận một thao tác ghi: tăng revision của nhà hàng để các workspace biết dữ liệu đã đổi và ghi
 * audit log.
 */
public interface WorkspaceCommit {

    void commit(User user, String commandType);
}
