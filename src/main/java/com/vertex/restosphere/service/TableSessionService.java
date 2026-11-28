package com.vertex.restosphere.service;

import com.vertex.restosphere.dto.request.SplitItemRequest;
import com.vertex.restosphere.dto.response.SessionChangeResponse;
import java.util.List;
import java.util.UUID;

/** Ghép và tách phiên bàn. Mỗi bàn một phiên; ghép chỉ là chuyện thanh toán chung một hóa đơn. */
public interface TableSessionService {

    /** Gộp phiên nguồn vào phiên đích; hóa đơn cũ của phiên nguồn thành VOIDED. */
    SessionChangeResponse merge(UUID actorId, UUID targetSessionId, UUID sourceSessionId);

    /** Chuyển các món sang phiên mới ở một bàn trống khác, có hóa đơn và gọi món riêng. */
    SessionChangeResponse split(
            UUID actorId, UUID sessionId, UUID targetTableId, List<SplitItemRequest> items, int guestCount);
}
