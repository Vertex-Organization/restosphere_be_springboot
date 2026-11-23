package com.vertex.restosphere.service;

import com.vertex.restosphere.common.enums.DiscountMode;
import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.Employee;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.TableSession;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Hóa đơn, tính tiền và yêu cầu phê duyệt dùng chung giữa các luồng nghiệp vụ. */
public interface BillingWorkflow {

    /** Tạo hóa đơn rỗng, có mã riêng; luôn tạo trước phiên trỏ tới nó. */
    Bill open();

    Bill bill(TableSession session);

    List<OrderItem> items(Bill bill);

    /** Tính lại tạm tính, giảm giá, phí dịch vụ, thuế và tổng; bỏ qua hóa đơn đã đóng. */
    void recalculate(Bill b);

    /** Tổng đã thu thành công của hóa đơn. */
    BigDecimal received(Bill b);

    /** Báo xung đột khi phiên bản hóa đơn client giữ đã cũ. */
    void requireVersion(Bill b, long version);

    /** Chặn sửa hóa đơn đã đóng hoặc đã có thanh toán. */
    void editable(Bill b);

    /** Chuyển hóa đơn sang PAID khi đã thu đủ, ngược lại mở lại. */
    void settle(Bill b);

    void applyDiscount(Bill b, DiscountMode mode, BigDecimal value, String reason, Employee user);

    ApprovalRequest request(
            ApprovalRequest.Type type,
            String target,
            UUID targetId,
            BigDecimal amount,
            String reason,
            Employee user);

    /** Duyệt hoặc từ chối yêu cầu và áp dụng hệ quả lên hóa đơn, món, thanh toán, thực đơn. */
    void decide(UUID requestId, boolean approve, String reason, Employee user);
}
