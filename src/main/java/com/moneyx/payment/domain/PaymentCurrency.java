package com.moneyx.payment.domain;

/**
 * Moedas aceitas (códigos ISO 4217). O Pix opera apenas em real.
 *
 * O campo já existe no modelo para que aceitar outra moeda no futuro seja uma
 * mudança de regra, e não de estrutura.
 */
public enum PaymentCurrency {
    BRL
}
