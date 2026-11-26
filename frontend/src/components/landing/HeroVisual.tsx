"use client";

import dynamic from "next/dynamic";
import { useEffect, useRef, useState } from "react";
import { hasWebGL, usePrefersReducedMotion } from "@/lib/use-media-query";
import styles from "./landing.module.css";

// three.js é pesado (~600 KB): só baixa quando o 3D vai mesmo aparecer, e nunca no servidor.
const HeroScene = dynamic(() => import("./HeroScene"), { ssr: false });

/**
 * Decide entre a cena 3D e o fundo estático:
 * - "reduzir movimento" ligado no sistema → estático (nem baixa o three.js);
 * - sem WebGL → estático;
 * - fora da tela → a cena para de renderizar (IntersectionObserver).
 */
export function HeroVisual() {
  const reducedMotion = usePrefersReducedMotion();
  const [webgl, setWebgl] = useState<boolean | null>(null);
  const [visible, setVisible] = useState(true);
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Só carrega o 3D quando o navegador fica OCIOSO: baixar e compilar o three.js (shaders) gerava tarefas longas de
    // até 255 ms logo na abertura, competindo com a página ficar clicável. O fundo estático cobre esse intervalo.
    const start = () => setWebgl(hasWebGL());
    if ("requestIdleCallback" in window) {
      const id = window.requestIdleCallback(start, { timeout: 1500 });
      return () => window.cancelIdleCallback(id);
    }
    const timer = setTimeout(start, 300);
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    const element = box.current;
    if (!element) return;
    const observer = new IntersectionObserver(([entry]) => setVisible(entry?.isIntersecting ?? true));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const show3d = !reducedMotion && webgl === true;

  return (
    <div ref={box} className={styles.visual} data-hero-visual={show3d ? "3d" : "static"} aria-hidden="true">
      <div className={styles.glow} />
      {show3d && <HeroScene active={visible} />}
    </div>
  );
}
