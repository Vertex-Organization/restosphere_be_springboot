package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.ReservationTable;
import java.util.List;
import java.util.Map;

/** Đặt bàn: tạo, tra cứu, gán bàn, đổi trạng thái và lịch sử. */
public interface ReservationWorkflow {

    Map<String, Object> settings();

    Map<String, Object> createPublic(Map<String, Object> input);

    Reservation create(Map<String, Object> input, Employee actor, boolean web, boolean walkIn);

    Map<String, Object> lookup(Map<String, Object> input);

    Map<String, Object> cancelPublic(Map<String, Object> input);

    int expireNoShows();

    /**
     * Xác nhận đặt bàn ngay khi cọc đã trả đủ (PENDING -> CONFIRMED), tự xếp bàn nếu chưa có. Không
     * ném lỗi khi hết bàn: đặt bàn giữ PENDING và lễ tân được báo xếp tay.
     */
    boolean confirmAfterDeposit(Reservation r);

    void assign(Reservation r, String tableId);

    /** Gán một hoặc nhiều bàn (đặt bàn đông người); thay toàn bộ bàn đang giữ. */
    void assign(Reservation r, List<String> tableIds);

    List<ReservationTable> assignments(Reservation r);

    boolean active(Reservation r);

    void command(String type, Map<String, Object> c, Employee actor);

    ReservationTable assignment(Reservation r);

    void history(Reservation r, Employee actor, String action, String description);

    Map<String, Object> publicView(Reservation r);
}
