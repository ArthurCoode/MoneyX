import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { StatusBadge } from "@/components/payment/StatusBadge";
import type { PaymentStatus } from "@/lib/api/types";

afterEach(cleanup);

describe("StatusBadge", () => {
  const cases: [PaymentStatus, string][] = [
    ["CREATING", "Criando"],
    ["PENDING", "Aguardando"],
    ["PAID", "Pago"],
    ["CANCELED", "Cancelado"],
    ["EXPIRED", "Expirado"],
  ];

  it.each(cases)("%s aparece como texto, não só como cor", (status, label) => {
    render(<StatusBadge status={status} />);
    // O nome acessível é o texto: o ícone fica escondido dos leitores de tela (aria-hidden).
    expect(screen.getByText(label)).toBeTruthy();
    expect(document.querySelector(`[data-status="${status}"] [aria-hidden="true"]`)).not.toBeNull();
  });

  it("estados finais negativos e o pago usam tons diferentes", () => {
    const { container } = render(
      <>
        <StatusBadge status="PAID" />
        <StatusBadge status="EXPIRED" />
      </>,
    );
    const [paid, expired] = Array.from(container.querySelectorAll("[data-status]"));
    expect(paid?.className).not.toEqual(expired?.className);
  });
});
