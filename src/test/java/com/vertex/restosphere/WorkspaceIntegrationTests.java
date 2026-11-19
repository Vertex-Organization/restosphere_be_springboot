package com.vertex.restosphere;

import static org.junit.jupiter.api.Assertions.*;
import static com.vertex.restosphere.service.WorkspaceStore.*;
import com.vertex.restosphere.common.enums.*;
import com.vertex.restosphere.entity.*;
import com.vertex.restosphere.exception.ApiException;
import com.vertex.restosphere.service.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class WorkspaceIntegrationTests {
    @Autowired WorkspaceStore db;
    @Autowired WorkspaceService workspace;
    @Autowired ReservationWorkflow reservations;
    @Autowired BillingWorkflow billing;
    @Autowired ServiceWorkflow service;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired javax.sql.DataSource dataSource;
    @Test void legacyEnumMigrationIsRepeatableAndPreservesRows() throws Exception {
        var customer=user(Role.CUSTOMER);db.flush();
        var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
        try {
            try(var sql=connection.createStatement()) {
                sql.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
                sql.execute("ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN','MANAGER','RECEPTIONIST','WAITER','KITCHEN','CASHIER')) NOT VALID");
            }
            var script=new org.springframework.core.io.ClassPathResource("migrations/workspace-legacy-constraints.sql");
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,script);
            try(var sql=connection.createStatement();var rows=sql.executeQuery("SELECT count(*) FROM pg_constraint WHERE conname IN ('users_role_check','notifications_audience_role_check','reservations_status_check','reservation_histories_previous_status_check','reservation_histories_new_status_check','order_items_status_check','payments_status_check') AND convalidated")) {
                assertTrue(rows.next());assertEquals(7,rows.getInt(1));
            }
            assertEquals(Role.CUSTOMER,db.get(User.class,id(customer)).getRole());
            completeWorkflowAndPaymentReplay();
        } finally {org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection,dataSource);}
    }
    User user(Role role) {var u=new User();u.setEmail(UUID.randomUUID()+"@test.example");u.setFullName(role.name());u.setPasswordHash("unused");u.setRole(role);return db.save(u);}
    @SuppressWarnings("unchecked") Map<String,Object> data(Map<String,Object> result){return (Map<String,Object>)result.get("data");}
    @SuppressWarnings("unchecked") List<Map<String,Object>> list(Map<String,Object> state,String key){return (List<Map<String,Object>>)state.get(key);}
    Map<String,Object> run(String role,User user,Object... args){return workspace.command(role,id(user),map(args));}
    @Test void completeWorkflowAndPaymentReplay() {
        var reception=user(Role.RECEPTIONIST);var waiter=user(Role.WAITER);var cook=user(Role.KITCHEN);var cashier=user(Role.CASHIER);
        var area=new Area();area.setName("Workflow "+UUID.randomUUID());db.save(area);
        var table=new RestaurantTable();table.setArea(area);table.setCode("T-"+UUID.randomUUID().toString().substring(0,8));table.setCapacity(4);table.setStatus(TableStatus.AVAILABLE);db.save(table);
        var cat=new MenuCategory();cat.setName("Test "+UUID.randomUUID());db.save(cat);
        var menu=new MenuItem();menu.setName("Dish");menu.setCategory(cat);menu.setPrice(new BigDecimal("100001"));db.save(menu);
        var input=map("customer",map("name","Guest","phone","0901234567"),"startsAt",Instant.now().plusSeconds(3600).toString(),"guests",2,"duration",120,"areaId",id(area),"tableId",id(table),"notes","");
        var created=run("staff",reception,"type","create","input",input);String reservation=created.get("reservationId").toString();
        run("staff",reception,"type","confirm","id",reservation);
        run("staff",reception,"type","check-in","id",reservation,"actualGuests",2);
        var state=data(run("staff",reception,"type","handover","id",reservation));String session=list(state,"tableSessions").stream().filter(s->reservation.equals(s.get("reservationId"))).findFirst().orElseThrow().get("id").toString();
        assertThrows(ApiException.class,()->run("staff",reception,"type","accept","sessionId",session));
        run("staff",waiter,"type","accept","sessionId",session);
        var other=user(Role.WAITER);assertThrows(ApiException.class,()->run("staff",other,"type","add-draft","sessionId",session,"menuItemId",id(menu)));
        run("staff",waiter,"type","add-draft","sessionId",session,"menuItemId",id(menu));
        state=data(run("staff",waiter,"type","submit","sessionId",session));var item=list(state,"orderItems").getFirst();String itemId=item.get("id").toString();
        assertThrows(ApiException.class,()->run("staff",waiter,"type","request-payment","sessionId",session));
        run("kitchen",cook,"type","start","items",List.of(map("id",itemId,"version",item.get("version"))));
        var current=db.get(OrderItem.class,itemId);
        assertThrows(ApiException.class,()->run("kitchen",cook,"type","complete","items",List.of(map("id",itemId,"version",-1))));
        run("kitchen",cook,"type","complete","items",List.of(map("id",itemId,"version",current.getVersion())));
        run("staff",waiter,"type","serve","sessionId",session,"itemId",itemId);
        state=data(run("staff",waiter,"type","request-payment","sessionId",session));var bill=list(state,"bills").stream().filter(b->session.equals(b.get("sessionId"))).findFirst().orElseThrow();
        run("cashier",cashier,"type","open-shift","openingFloat",100000);
        var payment=map("type","pay-cash","billId",bill.get("id"),"billVersion",bill.get("version"),"amount",100001,"tendered",110000,"idempotencyKey",UUID.randomUUID().toString());
        var paid=workspace.command("cashier",id(cashier),payment);var replay=workspace.command("cashier",id(cashier),payment);assertEquals(paid.get("ref"),replay.get("ref"));
        assertEquals(TableStatus.OCCUPIED,table.getStatus());assertEquals(1,db.all(Payment.class).size());
        payment.put("amount",100000);assertThrows(ApiException.class,()->workspace.command("cashier",id(cashier),payment));
        run("staff",waiter,"type","guest-left","sessionId",session,"confirmed",true);assertEquals(TableStatus.CLEANING,table.getStatus());
        run("staff",waiter,"type","clean","sessionId",session,"confirmed",true);assertEquals(TableStatus.AVAILABLE,table.getStatus());
    }
    @Test void waiterAreaPriorityAndCancelRules() {
        var reception=user(Role.RECEPTIONIST);var waiter=user(Role.WAITER);var cook=user(Role.KITCHEN);
        var area=new Area();area.setName("Rules "+UUID.randomUUID());db.save(area);
        var elsewhere=new Area();elsewhere.setName("Elsewhere "+UUID.randomUUID());db.save(elsewhere);
        var table=new RestaurantTable();table.setArea(area);table.setCode("R-"+UUID.randomUUID().toString().substring(0,8));table.setCapacity(4);table.setStatus(TableStatus.AVAILABLE);db.save(table);
        var cat=new MenuCategory();cat.setName("Rules "+UUID.randomUUID());db.save(cat);
        var menu=new MenuItem();menu.setName("Dish");menu.setCategory(cat);menu.setPrice(new BigDecimal("50000"));db.save(menu);
        var input=map("customer",map("name","Guest","phone","0901234567"),"startsAt",Instant.now().plusSeconds(3600).toString(),"guests",2,"duration",120,"areaId",id(area),"tableId",id(table),"notes","");
        String reservation=run("staff",reception,"type","create","input",input).get("reservationId").toString();
        run("staff",reception,"type","confirm","id",reservation);
        run("staff",reception,"type","check-in","id",reservation,"actualGuests",2);
        var state=data(run("staff",reception,"type","handover","id",reservation));String session=list(state,"tableSessions").stream().filter(s->reservation.equals(s.get("reservationId"))).findFirst().orElseThrow().get("id").toString();

        var outsider=user(Role.WAITER);outsider.setAssignedArea(elsewhere);
        assertThrows(ApiException.class,()->run("staff",outsider,"type","accept","sessionId",session));
        waiter.setAssignedArea(area);
        run("staff",waiter,"type","accept","sessionId",session);
        run("staff",waiter,"type","add-draft","sessionId",session,"menuItemId",id(menu));
        state=data(run("staff",waiter,"type","submit","sessionId",session));
        var item=list(state,"orderItems").getFirst();String itemId=item.get("id").toString();String orderId=item.get("orderId").toString();

        run("staff",waiter,"type","request-priority","sessionId",session,"orderId",orderId,"reason","Guests in a hurry");
        assertEquals(waiter.getId(),db.get(Order.class,orderId).getPriorityBy().getId());

        assertThrows(ApiException.class,()->run("staff",waiter,"type","request-cancel","sessionId",session,"itemId",itemId,"reason","Changed mind"));
        run("kitchen",cook,"type","start","items",List.of(map("id",itemId,"version",item.get("version"))));
        run("staff",waiter,"type","request-cancel","sessionId",session,"itemId",itemId,"reason","Changed mind");
        assertThrows(ApiException.class,()->run("staff",waiter,"type","request-cancel","sessionId",session,"itemId",itemId,"reason","Again"));
    }
    @Test void currentDatabaseRoleAndEnabledStateOverrideTokenClaims() {
        var u=user(Role.KITCHEN);assertThrows(ApiException.class,()->workspace.load("admin",id(u)));
        u.setEnabled(false);assertThrows(ApiException.class,()->workspace.load("kitchen",id(u)));
    }
    @Test void snapshotsKeepRoleSpecificRequestsAndNotificationTargets() {
        var cook=user(Role.KITCHEN);var cashier=user(Role.CASHIER);var manager=user(Role.MANAGER);
        billing.request(ApprovalRequest.Type.MENU_STATUS,"MENU_ITEM",UUID.randomUUID(),BigDecimal.ZERO,"Unavailable",cook);
        billing.request(ApprovalRequest.Type.RESERVATION,"RESERVATION",UUID.randomUUID(),BigDecimal.ZERO,"Exception",user(Role.RECEPTIONIST));
        assertTrue(list(workspace.load("cashier",id(cashier)),"requests").isEmpty());
        String requestId=UUID.randomUUID().toString(),incidentId=UUID.randomUUID().toString();
        service.notify(Role.CASHIER,"REQUEST_DECIDED","Decision","Approved",requestId);
        service.notify(Role.MANAGER,"INCIDENT","Incident","Equipment",incidentId);
        service.notify(Role.KITCHEN,"INCIDENT_REPLY","Reply","Resolved",incidentId);
        var notification=list(workspace.load("cashier",id(cashier)),"notifications").getFirst();
        assertEquals(requestId,notification.get("requestId"));assertNull(notification.get("billId"));
        assertEquals("/staff/manager/incidents?id="+incidentId,list(workspace.load("manager",id(manager)),"notifications").getFirst().get("path"));
        assertNull(list(workspace.load("kitchen",id(cook)),"notifications").getFirst().get("orderId"));
    }
    @Test void moneyAndQuantityRejectFractionalDongAndPortions() {
        assertThrows(ApiException.class,()->BillingWorkflow.money(map("amount",1.5),"amount"));
        var item=new MenuItem();assertThrows(ApiException.class,()->ReservationWorkflow.validateQuantity(item,new BigDecimal("1.5")));
        item.setPriceUnit("kg");assertDoesNotThrow(()->ReservationWorkflow.validateQuantity(item,new BigDecimal("1.125")));
        assertEquals(new BigDecimal("125001"),BillingWorkflow.whole(new BigDecimal("100001").multiply(new BigDecimal("1.25"))));
    }
    @Test void reservationLookupRequiresPhoneAndOverlappingAssignmentsFail() {
        var staff=user(Role.RECEPTIONIST);var area=new Area();area.setName("Bookings "+UUID.randomUUID());db.save(area);
        var table=new RestaurantTable();table.setArea(area);table.setCode("B-"+UUID.randomUUID().toString().substring(0,8));table.setCapacity(4);table.setStatus(TableStatus.AVAILABLE);db.save(table);
        var input=map("name","Guest","phone","0901234567","startsAt",Instant.now().plusSeconds(7200).toString(),"guests",2,"areaId",id(area),"occasion","none","items",List.of());
        var booking=reservations.createPublic(input);String code=booking.get("code").toString();
        assertEquals(code,reservations.lookup(map("code",code,"phone","0901234567")).get("code"));
        assertThrows(ApiException.class,()->reservations.lookup(map("code",code,"phone","0907654321")));
        var r=db.all(Reservation.class).stream().filter(x->x.getCode().equals(code)).findFirst().orElseThrow();reservations.assign(r,id(table));
        var second=reservations.create(map("customer",map("name","Second","phone","0907654321"),"startsAt",r.getStartsAt().toString(),"guests",2,"duration",120,"areaId",id(area)),staff,false,false);
        assertThrows(ApiException.class,()->reservations.assign(second,id(table)));
        assertFalse(workspace.load("kitchen",id(user(Role.KITCHEN))).containsKey("reservations"));
    }
    @Test void waiterRestEndpointsDriveOrderFlow() throws Exception {
        var reception=user(Role.RECEPTIONIST);var waiter=user(Role.WAITER);var cook=user(Role.KITCHEN);
        var area=new Area();area.setName("Rest "+UUID.randomUUID());db.save(area);
        var table=new RestaurantTable();table.setArea(area);table.setCode("W-"+UUID.randomUUID().toString().substring(0,8));table.setCapacity(4);table.setStatus(TableStatus.AVAILABLE);db.save(table);
        var cat=new MenuCategory();cat.setName("Rest "+UUID.randomUUID());db.save(cat);
        var menu=new MenuItem();menu.setName("Dish");menu.setCategory(cat);menu.setPrice(new BigDecimal("50000"));db.save(menu);
        var input=map("customer",map("name","Guest","phone","0901234567"),"startsAt",Instant.now().plusSeconds(3600).toString(),"guests",2,"duration",120,"areaId",id(area),"tableId",id(table),"notes","");
        String reservation=run("staff",reception,"type","create","input",input).get("reservationId").toString();
        run("staff",reception,"type","confirm","id",reservation);
        run("staff",reception,"type","check-in","id",reservation,"actualGuests",2);
        var state=data(run("staff",reception,"type","handover","id",reservation));String session=list(state,"tableSessions").stream().filter(s->reservation.equals(s.get("reservationId"))).findFirst().orElseThrow().get("id").toString();
        db.flush();
        String base="/api/v1/waiter/sessions/"+session;
        var asWaiter=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(j->j.subject(id(waiter))).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_WAITER"));
        var asCook=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(j->j.subject(id(cook))).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_KITCHEN"));
        var json=org.springframework.http.MediaType.APPLICATION_JSON;
        var status=org.springframework.test.web.servlet.result.MockMvcResultMatchers.status();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base+"/accept").with(asCook)).andExpect(status.isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base+"/accept").with(asWaiter)).andExpect(status.isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base+"/drafts").with(asWaiter).contentType(json).content("{}")).andExpect(status.isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base+"/drafts").with(asWaiter).contentType(json).content("{\"menuItemId\":\""+id(menu)+"\"}")).andExpect(status.isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base+"/orders").with(asWaiter))
                .andExpect(status.isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.orderItems[?(@.menuItemId=='"+id(menu)+"')].status").value("SUBMITTED"));
    }
    @Test void httpBoundaryRequiresAuthenticationAndCurrentRole() throws Exception {
        var cook=user(Role.KITCHEN);db.flush();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/workspaces/kitchen"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/workspaces/admin")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().jwt(j->j.subject(id(cook)))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/reservations/settings"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.areas").isArray());
    }
}
