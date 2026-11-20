package com.moneyx.shared.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Id de correlação: liga todas as linhas de log de uma mesma requisição (ou execução de job).
 *
 * Fica no MDC (Mapped Diagnostic Context) do SLF4J: um "bolso" por thread de onde o logger copia o valor para cada
 * linha. Por isso precisa ser SEMPRE removido no fim (as threads são reaproveitadas pelo servidor).
 */
public final class RequestIds {

    public static final String MDC_KEY = "requestId";
    public static final String HEADER = "X-Request-Id";
    /** Atributo da requisição: sobrevive ao fim do filtro (o erro 500 é montado depois que o MDC já foi limpo). */
    public static final String REQUEST_ATTRIBUTE = RequestIds.class.getName() + ".requestId";

    /**
     * O id enviado pelo cliente só é aceito neste formato. Sem essa validação, um id com quebra de linha permitiria
     * FORJAR linhas no log (log injection), e um id gigante encheria os logs.
     */
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private RequestIds() {
    }

    /** O id do cliente, se for seguro; senão, um novo. */
    public static String acceptOrGenerate(String fromClient) {
        return fromClient != null && SAFE.matcher(fromClient).matches() ? fromClient : UUID.randomUUID().toString();
    }

    /** O id da requisição/execução atual (null fora de uma). */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /**
     * Executa com um id próprio (ex.: cada execução de um job agendado) e o remove no fim, mesmo com exceção.
     * O prefixo ajuda a distinguir no log o que veio de requisição e o que veio de job.
     */
    public static <T> T runWithNewId(String prefix, Supplier<T> work) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        try {
            return work.get();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
