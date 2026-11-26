"use client";

import { useSyncExternalStore } from "react";

/**
 * Lê uma media query e acompanha mudanças (ex.: o usuário liga "reduzir movimento" no sistema com a página aberta).
 * No servidor (e na 1ª renderização) devolve `serverValue`: por padrão `true` para "reduzir movimento", ou seja, o
 * caminho seguro (sem animação) até o navegador dizer o contrário.
 */
export function useMediaQuery(query: string, serverValue: boolean): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    () => window.matchMedia(query).matches,
    () => serverValue,
  );
}

export function usePrefersReducedMotion(): boolean {
  return useMediaQuery("(prefers-reduced-motion: reduce)", true);
}

/** WebGL disponível? (alguns navegadores corporativos/antigos e máquinas sem GPU não têm). */
export function hasWebGL(): boolean {
  try {
    const canvas = document.createElement("canvas");
    return Boolean(canvas.getContext("webgl2") ?? canvas.getContext("webgl"));
  } catch {
    return false;
  }
}
