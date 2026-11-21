package com.vertex.restosphere.service.impl;

import com.vertex.restosphere.common.enums.Role;
import com.vertex.restosphere.entity.User;
import com.vertex.restosphere.service.AccountMailService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccountMailServiceImpl implements AccountMailService {

    static final String SUBJECT = "[RestoSphere] Tài khoản nhân viên của bạn";

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String from;
    private final String loginUrl;

    public AccountMailServiceImpl(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.login-url:}") String loginUrl) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        this.loginUrl = loginUrl;
    }

    @Override
    public boolean isEnabled() {
        return enabled && StringUtils.hasText(from) && mailSender.getIfAvailable() != null;
    }

    @Override
    public void sendTemporaryPassword(User user, String temporaryPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject(SUBJECT);
        message.setText(
                """
                Xin chào %s,

                Quản trị viên đã tạo tài khoản RestoSphere cho bạn.
                Email đăng nhập: %s
                Vai trò: %s
                Mật khẩu tạm: %s

                Đăng nhập tại: %s
                Vui lòng không chia sẻ mật khẩu này cho người khác.
                """
                        .formatted(
                                user.getFullName(),
                                user.getEmail(),
                                roleLabel(user.getRole()),
                                temporaryPassword,
                                loginUrl));
        mailSender.getObject().send(message);
    }

    private static String roleLabel(Role role) {
        return switch (role) {
            case RECEPTIONIST -> "Lễ tân";
            case WAITER -> "Phục vụ";
            case KITCHEN -> "Bếp";
            case CASHIER -> "Thu ngân";
            case MANAGER -> "Quản lý";
            case ADMIN -> "Quản trị";
            default -> role.name();
        };
    }
}
