import type { components } from "./schema";

/**
 * Tipos da API, GERADOS do contrato OpenAPI do backend (npm run api:types). Nunca escrever estes tipos à mão: se a API
 * mudar, o arquivo gerado muda e o TypeScript aponta tudo o que precisa acompanhar.
 */
export type Payment = components["schemas"]["PaymentView"];
export type PaymentStatus = NonNullable<Payment["status"]>;
