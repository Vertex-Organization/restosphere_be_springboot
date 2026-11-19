package com.vertex.restosphere.service;

import static com.vertex.restosphere.service.WorkspaceStore.*;
import static com.vertex.restosphere.service.ReservationWorkflow.*;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.exception.ApiException;
import java.math.BigDecimal;
import java.util.*;
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
    private final BillingWorkflow billing;
    private final ManagementWorkflow management;
    User authorize(String workspace,String subject) {
        User user;
        try {user=db.get(User.class,subject);}catch(ApiException e){throw new ApiException(HttpStatus.FORBIDDEN,"Account unavailable");}
        Set<Role> roles=switch(workspace){case "admin"->Set.of(Role.ADMIN);case "manager"->Set.of(Role.MANAGER,Role.ADMIN);case "kitchen"->Set.of(Role.KITCHEN,Role.ADMIN);case "cashier"->Set.of(Role.CASHIER,Role.ADMIN);case "staff"->Set.of(Role.RECEPTIONIST,Role.WAITER,Role.ADMIN);default->throw ApiException.notFound("Workspace");};
        if(!user.isEnabled()||!roles.contains(user.getRole()))throw new ApiException(HttpStatus.FORBIDDEN,"Workspace access denied");return user;
    }
    public Map<String,Object> load(String workspace,String subject) {db.lock();return snapshots.snapshot(workspace,authorize(workspace,subject));}
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
                else if(type.equals("request-exception")){
                    var r=db.get(Reservation.class,required(c,"id",100));String reason=required(c,"reason",200);
                    if(reason.length()<5)throw invalid("reason must be 5-200 characters");
                    if(!Set.of(Reservation.Status.PENDING,Reservation.Status.CONFIRMED,Reservation.Status.ARRIVED).contains(r.getStatus()))throw ApiException.conflict("Reservation cannot request an exception");
                    var request=billing.request(ApprovalRequest.Type.RESERVATION,"RESERVATION",r.getId(),BigDecimal.ZERO,reason,user);ref=id(r);
                    reservations.history(r,user,"request-exception","Gửi yêu cầu phê duyệt tới quản lý: "+reason);
                    service.notify(Role.MANAGER,"APPROVAL","Yêu cầu phê duyệt đặt bàn "+r.getCode(),reason,id(request));
                }
                else if(reception)reservations.command(type,c,user);else service.waiter(type,c,user);
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
