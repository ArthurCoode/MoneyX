"use strict";

/**
 * Guarda do X-Forwarded-For, carregado ANTES do servidor do Next (`node -r ./server/forwarded-for-guard.cjs server.js`).
 *
 * Problema: o Next só PREENCHE o X-Forwarded-For com o IP da conexão quando ele não veio na requisição
 * (`??=` no base-server). Com o Next exposto direto (sem proxy na frente), um visitante que mandasse
 * "X-Forwarded-For: 1.2.3.4" escolheria o próprio "IP", e os limites por IP da API (que confia no Next) não valeriam.
 *
 * Solução: em toda requisição, SOBRESCREVER o header com o IP real do socket. O Next então mantém este valor e o BFF o
 * repassa à API. Só quando houver um proxy confiável NA FRENTE do Next (que já escreve o header correto) é que se liga
 * MONEYX_TRUST_FORWARDED_FOR=true para manter o header recebido.
 */

const http = require("node:http");

function install(trustForwardedFor = process.env.MONEYX_TRUST_FORWARDED_FOR === "true") {
  if (trustForwardedFor || http.Server.prototype.__moneyxForwardedForGuard) return;
  const originalEmit = http.Server.prototype.emit;
  http.Server.prototype.emit = function emit(event, request, ...rest) {
    if (event === "request" && request && request.headers) {
      const ip = request.socket && request.socket.remoteAddress;
      if (ip) request.headers["x-forwarded-for"] = ip;
      else delete request.headers["x-forwarded-for"];
    }
    return originalEmit.call(this, event, request, ...rest);
  };
  http.Server.prototype.__moneyxForwardedForGuard = true;
}

install();

module.exports = { install };
