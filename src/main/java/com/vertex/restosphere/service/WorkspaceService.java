package com.vertex.restosphere.service;

import static com.vertex.restosphere.service.WorkspaceStore.*;
import static com.vertex.restosphere.service.ReservationWorkflow.*;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.exception.ApiException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceService {
    private final WorkspaceStore db;
    private final WorkspaceSnapshots snapshots;
    private final ReservationWorkflow reservations;
    private final ServiceWorkflow service;
    private final WaiterService waiter;
    private final BillingWorkflow billing;
    private final ManagementWorkflow management;
    User authorize(String workspace,String subject) {
        User user;
        try {user=db.get(User.class,subject);}catch(ApiException e){throw new ApiException(HttpStatus.FORBIDDEN,"Account unavailable");}
        Set<Role> roles=switch(workspace){case "admin"->Set.of(Role.ADMIN);case "manager"->Set.of(Role.MANAGER,Role.ADMIN);case "kitchen"->Set.of(Role.KITCHEN,Role.ADMIN);case "cashier"->Set.of(Role.CASHIER,Role.ADMIN);case "staff"->Set.of(Role.RECEPTIONIST,Role.WAITER,Role.ADMIN);default->throw ApiException.notFound("Workspace");};
        if(!user.isEnabled()||!roles.contains(user.getRole()))throw new ApiException(HttpStatus.FORBIDDEN,"Workspace access denied");return user;
    }
    public Map<String,Object> load(String workspace,String subject) {db.lock();return snapshots.snapshot(workspace,authorize(workspace,subject));}
    /**
     * Entry point for the waiter REST API ({@code WaiterOrderController}): same lock, revision, audit
     * and "staff" workspace snapshot response as the generic commands endpoint.
     */
    public Map<String,Object> waiter(String subject,String action,Consumer<User> operation) {
        var settings=db.lock();var user=authorize("staff",subject);
        if(user.getRole()!=Role.ADMIN&&user.getRole()!=Role.WAITER)throw new ApiException(HttpStatus.FORBIDDEN,"Command not permitted for your role");
        operation.accept(user);
        settings.setRevision(settings.getRevision()+1);
        service.audit(user,action,user.getId(),"WORKSPACE",action);db.flush();
        return snapshots.snapshot("staff",user);
    }
    public Map<String,Object> command(String workspace,String subject,Map<String,Object> c) {
        var settings=db.lock();var user=authorize(workspace,subject);String type=required(c,"type",80);String ref=null;
        if(Set.of("read","read-notification","read-notifications","notification.read").contains(type))service.read(c,user);
        else switch(workspace) {
            case "admin" -> management.admin(type,c,user);
            case "manager" -> management.command(type,c,user);
            case "kitchen" -> service.kitchen(type,c,user);
            case "cashier" -> ref=billing.command(type,c,user);
            case "staff" -> {
                boolean reception=Set.of("create","edit","confirm","reject","cancel","no-show","assign","check-in","handover","request-exception").contains(type);
                if(user.getRole()!=Role.ADMIN&&user.getRole()!=(reception?Role.RECEPTIONIST:Role.WAITER))throw new ApiException(HttpStatus.FORBIDDEN,"Command not permitted for your role");
                if(type.equals("create")){var r=reservations.create(object(c,"input"),user,false,Boolean.TRUE.equals(c.get("walkIn")));ref=id(r);}
                else if(type.equals("request-exception")){var r=db.get(Reservation.class,required(c,"id",100));if(r.getStatus()!=Reservation.Status.PENDING)throw ApiException.conflict("Reservation is not pending");ref=id(billing.request(ApprovalRequest.Type.RESERVATION,"RESERVATION",r.getId(),BigDecimal.ZERO,required(c,"reason",1000),user));}
                else if(reception)reservations.command(type,c,user);else waiter.handle(type,c,user);
            }
        }
        settings.setRevision(settings.getRevision()+1);
        if(type.equals("approval.decide")) {
            var request=db.get(ApprovalRequest.class,required(c,"id",100));
            service.notify(request.getRequestedBy().getRole(),"REQUEST_DECIDED","Request decided",request.getStatus().name(),id(request));
        }
        if(Set.of("pay-cash","confirm-transfer").contains(type)&&ref!=null) {
            var payment=db.get(Payment.class,ref);
            if(payment.getBill().getStatus()==Bill.Status.PAID)service.notify(Role.WAITER,"PAYMENT","Payment received",payment.getBill().getCode(),id(payment.getBill().getSession()));
        }
        service.audit(user,type,user.getId(),"WORKSPACE",type);db.flush();
        var data=snapshots.snapshot(workspace,user);
        if(workspace.equals("admin")||workspace.equals("manager"))return data;
        return map("data",data,"state",workspace.equals("staff")?data:null,"summary","Command completed","ref",ref,"reservationId",workspace.equals("staff")&&type.equals("create")?ref:null);
    }
}
