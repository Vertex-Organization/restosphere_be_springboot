package com.vertex.restosphere.repository;

import com.vertex.restosphere.entity.ApprovalRequest;
import com.vertex.restosphere.entity.Bill;
import com.vertex.restosphere.entity.MenuItem;
import com.vertex.restosphere.entity.OrderItem;
import com.vertex.restosphere.entity.Payment;
import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.TableSession;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ApprovalQueryRepository
        extends JpaRepository<ApprovalRequest, UUID>, JpaSpecificationExecutor<ApprovalRequest> {

    @Override
    @EntityGraph(attributePaths = {"requestedBy", "decidedBy"})
    List<ApprovalRequest> findAll(Specification<ApprovalRequest> spec, Sort sort);

    @Query(
            "select distinct b from Bill b join fetch b.sessions s join fetch s.table where b.id in :ids")
    List<Bill> findBillsWithTableByIdIn(Collection<UUID> ids);

    @Query(
            "select i from OrderItem i join fetch i.order o join fetch o.session s join fetch"
                    + " s.table where i.id in :ids")
    List<OrderItem> findOrderItemsWithTableByIdIn(Collection<UUID> ids);

    @Query("select s from TableSession s join fetch s.bill where s.id in :sessionIds")
    List<TableSession> findSessionsWithBillByIdIn(Collection<UUID> sessionIds);

    @Query("select p from Payment p join fetch p.bill where p.id in :ids")
    List<Payment> findPaymentsWithBillByIdIn(Collection<UUID> ids);

    @Query("select m from MenuItem m where m.id in :ids")
    List<MenuItem> findMenuItemsByIdIn(Collection<UUID> ids);

    @Query("select r from Reservation r where r.id in :ids")
    List<Reservation> findReservationsByIdIn(Collection<UUID> ids);
}
