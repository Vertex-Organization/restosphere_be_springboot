package com.vertex.restosphere.config;

import com.vertex.restosphere.service.PayosGateway;
import com.vertex.restosphere.service.impl.FakePayosGateway;
import com.vertex.restosphere.service.impl.PayosGatewayImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PayosProperties.class)
public class PayosConfig {

    @Bean
    PayosGateway payosGateway(PayosProperties properties) {
        return properties.fake() ? new FakePayosGateway() : new PayosGatewayImpl(properties);
    }
}
