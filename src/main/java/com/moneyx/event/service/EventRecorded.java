package com.moneyx.event.service;

import com.moneyx.event.domain.Event;

/**
 * Aviso interno (evento do Spring, não do domínio público): "um evento acabou de ser gravado".
 *
 * Padrão Observer: o EventService anuncia sem saber quem escuta. Assim o módulo webhook reage aos eventos sem que
 * o módulo event dependa dele (a dependência continua só webhook -> event, sem ciclo).
 *
 * É publicado DENTRO da transação que gravou o evento. Quem escuta com @EventListener roda nessa mesma transação
 * (tudo é confirmado ou desfeito junto). NÃO usar @TransactionalEventListener(AFTER_COMMIT) para algo que precisa
 * ser atômico com o evento: se a aplicação cair logo após o commit, o ouvinte nunca roda.
 */
public record EventRecorded(Event event) {
}
