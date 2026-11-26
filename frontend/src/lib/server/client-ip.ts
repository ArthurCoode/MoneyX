/**
 * IP do visitante a partir do `X-Forwarded-For` que chega ao Next.
 *
 * O Next só PREENCHE esse header com o IP da conexão quando ele não veio na requisição (`??=` no base-server). Por isso
 * pegamos o ÚLTIMO item da lista: é o que foi acrescentado pelo proxy mais próximo (o confiável), e não o que o
 * visitante pode ter escrito à mão no começo.
 *
 * Limite conhecido: com o Next exposto DIRETO na internet (sem proxy na frente), um visitante que mande o header sozinho
 * escolhe o "IP". Em produção o Next fica atrás de um proxy que sobrescreve o header; o limite por e-mail da API
 * continua valendo de qualquer forma.
 */
export function clientIpFrom(forwardedFor: string | null): string | undefined {
  if (!forwardedFor) return undefined;
  const last = forwardedFor.split(",").at(-1)?.trim();
  // Só aceita algo com cara de IP (v4 ou v6); qualquer outra coisa é descartada em vez de repassada.
  if (!last || !/^[0-9a-fA-F:.]{2,45}$/.test(last)) return undefined;
  return last;
}
