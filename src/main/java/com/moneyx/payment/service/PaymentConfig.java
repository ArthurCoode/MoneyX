package com.moneyx.payment.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymentVolumeLimitProperties.class)
class PaymentConfig {
}
