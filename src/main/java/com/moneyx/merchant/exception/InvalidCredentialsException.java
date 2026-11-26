package com.moneyx.merchant.exception;

/**
 * Login recusado (401 invalid_credentials). A MESMA resposta para e-mail inexistente, conta sem senha e senha errada:
 * a API não revela quem tem conta.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
