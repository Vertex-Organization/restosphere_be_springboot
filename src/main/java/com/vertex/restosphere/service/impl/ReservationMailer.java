package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.entity.Reservation;
import com.vertex.restosphere.service.WorkspaceStore;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationMailer {
    private final ObjectProvider<JavaMailSender> sender;
    private final WorkspaceStore db;

    @Value("${app.mail.from:}")
    String from;

    public void send(Reservation r, String subject, String body) {
        var mail = sender.getIfAvailable();
        var s = db.lock();
        if (mail == null
                || !s.isEmailNotifications()
                || r.getEmail() == null
                || r.getEmail().isBlank()) return;
        var when =
                r.getStartsAt()
                        .atZone(ZoneId.of(s.getTimezone()))
                        .format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
        var msg = new SimpleMailMessage();
        if (!from.isBlank()) msg.setFrom(from);
        msg.setTo(r.getEmail());
        msg.setSubject("[" + s.getName() + "] " + subject + " - " + r.getCode());
        msg.setText(
                "Xin chào "
                        + r.getCustomerName()
                        + ",\n\n"
                        + body
                        + "\n\nMã đặt bàn: "
                        + r.getCode()
                        + "\nThời gian: "
                        + when
                        + "\nSố khách: "
                        + r.getGuestCount()
                        + "\n\n"
                        + s.getName());
        Runnable task =
                () -> {
                    try {
                        mail.send(msg);
                    } catch (Exception e) {
                        log.warn("Reservation email to {} failed: {}", r.getCode(), e.getMessage());
                    }
                };
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            task.run();
                        }
                    });
        else task.run();
    }
}
