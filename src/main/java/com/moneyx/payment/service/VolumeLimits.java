package com.moneyx.payment.service;

/**
 * Limite acumulado de volume que vale para um comerciante, em centavos.
 *
 * @param daily   por dia (fuso de moneyx.payments.volume-limit.zone)
 * @param monthly por mês
 */
public record VolumeLimits(long daily, long monthly) {
}
