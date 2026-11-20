package com.moneyx.webhook.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WebhookDeliveryProperties.class)
class WebhookConfig {
}
