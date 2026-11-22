package com.vertex.restosphere.service.impl;

import static com.vertex.restosphere.common.util.Values.normalizePhone;
import static com.vertex.restosphere.common.util.Values.phoneVariants;

import com.vertex.restosphere.common.enums.AccountStatus;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.dto.request.CustomerRequest;
import com.vertex.restosphere.dto.response.CustomerPageResponse;
import com.vertex.restosphere.dto.response.CustomerResponse;
import com.vertex.restosphere.entity.AuditLog;
import com.vertex.restosphere.entity.Customer;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.repository.AuditLogRepository;
import com.vertex.restosphere.repository.CustomerRepository;
import com.vertex.restosphere.repository.ReservationRepository;
import com.vertex.restosphere.security.CurrentUserService;
import com.vertex.restosphere.service.CustomerService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {
    static final int MAX_PAGE_SIZE = 100;
    static final int RECENT_RESERVATIONS = 10;
    static final String DUPLICATE_PHONE = "Số điện thoại này đã là thành viên.";

    private final CustomerRepository customers;
    private final ReservationRepository reservations;
    private final AuditLogRepository auditLogs;
    private final CurrentUserService currentUser;
    private final ReservationMailer mailer;

    @Transactional
    @Override
    public CustomerResponse register(CustomerRequest req) {
        var c = new Customer();
        apply(c, req, false);
        customers.saveAndFlush(c);
        return created(c, mailer.sendWelcome(c));
    }

    @Transactional(readOnly = true)
    @Override
    public CustomerPageResponse list(
            UUID actorId, String q, AccountStatus status, int page, int size) {
        requireManager(actorId);
        if (page < 0) throw ApiException.badRequest("page", "Trang không hợp lệ.");
        if (size < 1 || size > MAX_PAGE_SIZE)
            throw ApiException.badRequest(
                    "size", "Số dòng mỗi trang từ 1 đến " + MAX_PAGE_SIZE + ".");
        var result =
                customers.findAll(
                        filter(q, status),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        var stats = statsByPhone(result.getContent());
        var items =
                result.getContent().stream()
                        .map(c -> toResponse(c, stats.getOrDefault(c.getPhone(), Stats.NONE)))
                        .toList();
        return new CustomerPageResponse(
                items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    @Override
    public CustomerResponse get(UUID actorId, UUID id) {
        requireManager(actorId);
        var c = find(id);
        var recent =
                reservations
                        .findByPhoneInOrderByStartsAtDesc(
                                phoneVariants(c.getPhone()), PageRequest.of(0, RECENT_RESERVATIONS))
                        .stream()
                        .map(
                                r ->
                                        new CustomerResponse.RecentReservation(
                                                r.getId(),
                                                r.getCode(),
                                                r.getStartsAt(),
                                                r.getGuestCount(),
                                                r.getArea().getName(),
                                                r.getStatus()))
                        .toList();
        var stats = statsByPhone(List.of(c)).getOrDefault(c.getPhone(), Stats.NONE);
        return toResponse(c, stats, recent, null);
    }

    @Transactional
    @Override
    public CustomerResponse create(UUID actorId, CustomerRequest req) {
        var actor = requireManager(actorId);
        var c = new Customer();
        apply(c, req, true);
        customers.saveAndFlush(c);
        boolean sent = mailer.sendWelcome(c);
        audit(actor, "CUSTOMER_CREATED", c, "Thêm thành viên " + c.getFullName());
        return created(c, sent);
    }

    @Transactional
    @Override
    public CustomerResponse update(UUID actorId, UUID id, CustomerRequest req) {
        var actor = requireManager(actorId);
        var c = find(id);
        apply(c, req, true);
        audit(actor, "CUSTOMER_UPDATED", c, "Cập nhật thành viên " + c.getFullName());
        return get(actorId, id);
    }

    @Transactional
    @Override
    public CustomerResponse updateStatus(UUID actorId, UUID id, AccountStatus status) {
        var actor = requireManager(actorId);
        var c = find(id);
        c.setEnabled(status == AccountStatus.ACTIVE);
        audit(
                actor,
                "CUSTOMER_STATUS_CHANGED",
                c,
                (c.isEnabled() ? "Mở khóa" : "Khóa") + " thành viên " + c.getFullName());
        return get(actorId, id);
    }

    private User requireManager(UUID actorId) {
        return currentUser.require(actorId, Role.ADMIN, Role.MANAGER);
    }

    private Customer find(UUID id) {
        return customers
                .findById(id)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy thành viên."));
    }

    // Self-registration must not let a visitor overwrite staff notes.
    private void apply(Customer c, CustomerRequest req, boolean staff) {
        String phone = normalizePhone(req.phone());
        boolean taken =
                c.getId() == null
                        ? customers.existsByPhone(phone)
                        : customers.existsByPhoneAndIdNot(phone, c.getId());
        if (taken) throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_PHONE, "phone");
        c.setFullName(req.fullName());
        c.setPhone(phone);
        c.setEmail(req.email());
        c.setBirthday(req.birthday());
        c.setMarketingConsent(req.marketingConsent());
        if (staff) c.setNote(req.note());
    }

    private static Specification<Customer> filter(String q, AccountStatus status) {
        return (root, query, cb) -> {
            var where = cb.conjunction();
            if (status != null)
                where =
                        cb.and(
                                where,
                                cb.equal(root.get("enabled"), status == AccountStatus.ACTIVE));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.strip().toLowerCase() + "%";
                String phoneLike = "%" + normalizePhone(q.strip()) + "%";
                where =
                        cb.and(
                                where,
                                cb.or(
                                        cb.like(cb.lower(root.get("fullName")), like),
                                        cb.like(cb.lower(root.get("email")), like),
                                        cb.like(root.get("phone"), phoneLike)));
            }
            return where;
        };
    }

    record Stats(long count, Instant lastVisitAt) {
        static final Stats NONE = new Stats(0, null);

        Stats plus(Stats other) {
            Instant last = lastVisitAt;
            if (last == null || (other.lastVisitAt != null && other.lastVisitAt.isAfter(last))) {
                last = other.lastVisitAt;
            }
            return new Stats(count + other.count, last);
        }
    }

    // Reservations may store either 0... or legacy +84...; fold both into the member's phone.
    private Map<String, Stats> statsByPhone(List<Customer> list) {
        Map<String, String> owner = new HashMap<>();
        for (var c : list) {
            phoneVariants(c.getPhone()).forEach(v -> owner.put(v, c.getPhone()));
        }
        Map<String, Stats> stats = new HashMap<>();
        if (owner.isEmpty()) return stats;
        for (Object[] row : reservations.summarizeByPhoneIn(owner.keySet())) {
            var found = new Stats((Long) row[1], (Instant) row[2]);
            stats.merge(owner.get((String) row[0]), found, Stats::plus);
        }
        return stats;
    }

    private void audit(User actor, String action, Customer c, String description) {
        var log = new AuditLog();
        log.setActor(actor);
        log.setAction(action);
        log.setTargetType("CUSTOMER");
        log.setTargetId(c.getId());
        log.setDescription(description);
        auditLogs.save(log);
    }

    private static CustomerResponse created(Customer c, boolean welcomeEmailSent) {
        return toResponse(c, Stats.NONE, List.of(), welcomeEmailSent);
    }

    private static CustomerResponse toResponse(Customer c, Stats stats) {
        return toResponse(c, stats, List.of(), null);
    }

    private static CustomerResponse toResponse(
            Customer c,
            Stats stats,
            List<CustomerResponse.RecentReservation> recent,
            Boolean welcomeEmailSent) {
        return new CustomerResponse(
                c.getId(),
                c.getFullName(),
                c.getPhone(),
                c.getEmail(),
                c.getBirthday(),
                c.isMarketingConsent(),
                c.getNote(),
                c.isEnabled() ? AccountStatus.ACTIVE : AccountStatus.LOCKED,
                c.getCreatedAt(),
                stats.count(),
                stats.lastVisitAt(),
                recent,
                welcomeEmailSent);
    }
}
