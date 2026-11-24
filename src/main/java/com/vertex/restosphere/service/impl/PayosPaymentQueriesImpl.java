package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.common.util.Values.normalizePhone;
import static com.vertex.restosphere.common.util.Values.phoneVariants;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.response.CashierPaymentResponse;
import com.vertex.restosphere.dto.response.PaymentStatusResponse;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.PaymentRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.PayosPaymentQueries;
import com.vertex.restosphere.service.PayosPayments;
import com.vertex.restosphere.service.RestaurantLock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PayosPaymentQueriesImpl implements PayosPaymentQueries {
    private static final Role[] STAFF = {
        Role.ADMIN, Role.MANAGER, Role.CASHIER, Role.RECEPTIONIST, Role.WAITER
    };

    private final CurrentUserService currentUser;
    private final PaymentRepository payments;
    private final PayosPayments payosPayments;
    private final RestaurantLock restaurantLock;

    @Override
    public CashierPaymentResponse refresh(UUID actorId, UUID paymentId) {
        currentUser.require(actorId, STAFF);
        restaurantLock.acquire();
        Payment payment =
                payments.findById(paymentId).orElseThrow(() -> ApiException.notFound("Payment"));
        if (payment.getMethod() != Payment.Method.PAYOS) {
            throw ApiException.conflict("Chỉ kiểm tra lại được thanh toán payOS.");
        }
        payosPayments.refresh(payment);
        return CashierPaymentResponse.from(payment);
    }

    @Override
    public PaymentStatusResponse publicStatus(
            long orderCode, String reservationCode, String phone) {
        Payment payment =
                payments.findByPayosOrderCode(orderCode)
                        .filter(p -> p.getReservation() != null)
                        .filter(p -> ownedBy(p, reservationCode, phone))
                        .orElseThrow(() -> ApiException.notFound("Payment"));
        if (payment.getStatus() == Payment.Status.PENDING) {
            restaurantLock.acquire();
            payosPayments.refresh(payment);
        }
        return PaymentStatusResponse.from(payment);
    }

    // Anyone can type an order code; only the booking's own code + phone may read its status.
    private static boolean ownedBy(Payment payment, String reservationCode, String phone) {
        var reservation = payment.getReservation();
        return reservationCode != null
                && reservation.getCode().equalsIgnoreCase(reservationCode.trim())
                && phoneVariants(normalizePhone(phone)).contains(reservation.getPhone());
    }
}
