package com.moneyx.payment.service;

import com.moneyx.payment.exception.VolumeLimitExceededException;
import com.moneyx.payment.exception.VolumeLimitExceededException.Period;
import com.moneyx.payment.repository.PaymentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Confere o limite acumulado de volume (dia e mês, no fuso configurado) antes de criar um pagamento.
 *
 * Conta pagamentos CRIADOS e não cancelados (PENDING, PAID, EXPIRED), pela data de criação.
 *
 * Só é correto se o chamador já trancou a linha do comerciante na mesma transação
 * (PaymentRepository.lockMerchantForPaymentCreation): sem o lock, duas criações simultâneas leriam a mesma soma
 * e ambas passariam. Por isso exige transação (MANDATORY).
 */
@Component
class PaymentVolumeLimit {

    private final PaymentRepository paymentRepository;
    private final PaymentVolumeLimitProperties properties;

    PaymentVolumeLimit(PaymentRepository paymentRepository, PaymentVolumeLimitProperties properties) {
        this.paymentRepository = paymentRepository;
        this.properties = properties;
    }

    /**
     * @throws VolumeLimitExceededException se {@code amount} não couber no mês ou no dia
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void check(UUID merchantId, long amount, Instant now) {
        ZoneId zone = properties.zone();
        LocalDate today = LocalDate.ofInstant(now, zone);

        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant nextMonthStart = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(zone).toInstant();
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant nextDayStart = today.plusDays(1).atStartOfDay(zone).toInstant();

        // O mês primeiro: se os dois estourarem, "mensal" é a resposta útil (tentar amanhã também falharia).
        ensureFits(Period.MONTHLY, properties.monthly(),
                paymentRepository.sumVolumeCreatedSince(merchantId, monthStart), amount, nextMonthStart);
        ensureFits(Period.DAILY, properties.daily(),
                paymentRepository.sumVolumeCreatedSince(merchantId, dayStart), amount, nextDayStart);
    }

    private static void ensureFits(Period period, long limit, long used, long amount, Instant resetsAt) {
        // Math.addExact: nunca deixar uma soma estourar o long em silêncio (seria um número negativo "cabendo").
        if (Math.addExact(used, amount) > limit) {
            throw new VolumeLimitExceededException(period, limit, Math.max(0, limit - used), resetsAt);
        }
    }
}
