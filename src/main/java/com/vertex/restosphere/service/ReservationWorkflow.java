package com.vertex.restosphere.service;

import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.entity.ReservationHistory;
import com.vertex.restosphere.entity.ReservationItem;
import com.vertex.restosphere.entity.ReservationTable;
import com.vertex.restosphere.entity.User;
import java.util.List;
import java.util.Map;

/** Dat ban: tao, tra cuu, gan ban, lich su. Helper doc input (text, required...) o ReservationWorkflowImpl. */
public interface ReservationWorkflow {

    Map<String, Object> settings();

    Map<String, Object> createPublic(Map<String, Object> input);

    Reservation create(Map<String, Object> input, User actor, boolean web, boolean walkIn);

    Map<String, Object> lookup(Map<String, Object> input);

    Map<String, Object> cancelPublic(Map<String, Object> input);

    int expireNoShows();

    void assign(Reservation r, String tableId);

    boolean active(Reservation r);

    void command(String type, Map<String, Object> c, User actor);

    ReservationTable assignment(Reservation r);

    void history(Reservation r, User actor, String action, String description);

    Map<String, Object> view(Reservation r);

    Map<String, Object> view(Reservation r, ReservationTable a, List<ReservationItem> preorder, List<ReservationHistory> history);

    Map<String, Object> publicView(Reservation r);
}
