import { describe, expect, it } from "vitest";
import { formatCents, formatDateTime, parseReaisToCents, shortId } from "@/lib/format";
import { WEBHOOK_EVENT_TYPES, eventLabel } from "@/lib/webhook-events";

// O Intl usa espaço não separável entre "R$" e o número.
const normalize = (text: string) => text.replace(/\s/g, " ");

describe("parseReaisToCents (sem float)", () => {
  it.each([
    ["25,90", 2590],
    ["10", 1000],
    ["0,5", 50],
    ["0,05", 5],
    ["1.234,56", 123456],
    ["100.000,00", 10000000],
    ["R$ 12,34", 1234],
    ["  7,00 ", 700],
    // 0,1 + 0,2 em float daria 0,30000000000000004: aqui é texto → inteiro, sem arredondamento.
    ["0,30", 30],
    ["1.000.000", 100000000],
  ])("%s → %d centavos", (input, cents) => {
    expect(parseReaisToCents(input)).toBe(cents);
  });

  it.each(["", "abc", "1,234", "12.5", "1.23,00", "-5", "1,2,3", "12,", ",50", "1e3", "１０"])(
    "rejeita %j",
    (input) => {
      expect(parseReaisToCents(input)).toBeNull();
    },
  );

  it("rejeita valores grandes demais para um inteiro seguro", () => {
    expect(parseReaisToCents("999999999999999999")).toBeNull();
  });
});

describe("formatCents", () => {
  it("formata centavos em reais no padrão brasileiro", () => {
    expect(normalize(formatCents(123456))).toBe("R$ 1.234,56");
    expect(normalize(formatCents(5))).toBe("R$ 0,05");
    expect(formatCents(undefined)).toBe("—");
  });
});

describe("formatDateTime", () => {
  it("usa o horário de Brasília, não o do servidor", () => {
    // 02:30 UTC = 23:30 do dia anterior em São Paulo (UTC-3).
    expect(normalize(formatDateTime("2026-11-27T02:30:00Z"))).toBe("26/11/2026, 23:30");
  });

  it("devolve traço para ausente ou inválido", () => {
    expect(formatDateTime(undefined)).toBe("—");
    expect(formatDateTime("not a date")).toBe("—");
  });
});

describe("shortId e rótulos de eventos", () => {
  it("encurta ids", () => {
    expect(shortId("0f8fad5b-d9cb-469f-a165-70867728950e")).toBe("0f8fad5b");
  });

  it("tem rótulo em português para todo tipo do contrato", () => {
    for (const type of WEBHOOK_EVENT_TYPES) expect(eventLabel(type)).not.toBe(type);
    expect(eventLabel("payment.unknown")).toBe("payment.unknown");
  });
});
