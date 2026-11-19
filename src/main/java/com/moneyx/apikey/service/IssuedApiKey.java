package com.moneyx.apikey.service;

import com.moneyx.apikey.domain.ApiKey;

/**
 * Resultado da emissão de uma API Key: a entidade persistida + o segredo em texto puro.
 *
 * Este é o ÚNICO momento em que o segredo existe fora do cliente. Ele deve ser
 * devolvido ao comerciante na resposta HTTP e então descartado.
 */
public record IssuedApiKey(ApiKey apiKey, String plainText) {

    @Override
    public String toString() {
        return "IssuedApiKey[id=" + apiKey.getId() + ", prefix=" + apiKey.getKeyPrefix() + ", plainText=***]";
    }
}
