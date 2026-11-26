"use client";

import { useEffect } from "react";
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { usePrefersReducedMotion } from "@/lib/use-media-query";

/**
 * Revela na rolagem os elementos marcados com `data-reveal` (GSAP ScrollTrigger, em lotes).
 *
 * O conteúdo nasce VISÍVEL no HTML: sem JavaScript, ou com "reduzir movimento", nada fica escondido. Só quando o
 * GSAP roda é que ele esconde e anima (`gsap.from`). `gsap.context` + `revert()` desfaz tudo ao sair da página.
 */
export function ScrollReveal() {
  const reducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    if (reducedMotion) return;
    gsap.registerPlugin(ScrollTrigger);
    const context = gsap.context(() => {
      ScrollTrigger.batch("[data-reveal]", {
        start: "top 88%",
        once: true,
        onEnter: (elements) =>
          gsap.from(elements, { opacity: 0, y: 28, duration: 0.8, ease: "power3.out", stagger: 0.08 }),
      });
    });
    return () => context.revert();
  }, [reducedMotion]);

  return null;
}
