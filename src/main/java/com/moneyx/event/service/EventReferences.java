package com.moneyx.event.service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Implementada pelos módulos que guardam referências a eventos (ex.: as entregas de webhook apontam para o evento que
 * entregam). A retenção só apaga um evento quando NENHUMA implementação o reporta como referenciado.
 *
 * Por que uma interface: o módulo event é genérico e não conhece quem o usa (a dependência é webhook → event, nunca o
 * contrário). Quem depende de event declara aqui que ainda precisa de um evento. A chave estrangeira no banco continua
 * sendo a última barreira: se algo escapar, o DELETE falha e nada é apagado.
 */
public interface EventReferences {

    /** Dentre os ids informados, devolve os que ainda são referenciados (e por isso não podem ser apagados). */
    Set<UUID> findReferenced(Collection<UUID> eventIds);
}
