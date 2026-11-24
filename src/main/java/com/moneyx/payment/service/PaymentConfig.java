package com.moneyx.payment.service;

import com.moneyx.payment.provider.mercadopago.MercadoPagoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PaymentVolumeLimitProperties.class, MercadoPagoProperties.class})
class PaymentConfig {
}
