"use client";

import { Canvas, useFrame } from "@react-three/fiber";
import { Float, Sparkles } from "@react-three/drei";
import { useMemo, useRef } from "react";
import { AdditiveBlending, BufferAttribute, BufferGeometry, Color, type Group, type Points } from "three";

/**
 * Cena 3D do herói: três anéis orbitando um núcleo e uma espiral de partículas subindo, nas cores da paleta
 * (esmeralda → azul → violeta). A ideia é o "fluxo" de um pagamento: entra, gira, confirma.
 *
 * Só é carregada no navegador (import dinâmico em HeroVisual) e só quando o visitante NÃO pediu menos movimento.
 * `frameloop` pausa a renderização quando a cena sai da tela (economia de bateria/GPU).
 */

// Cores repetidas dos tokens: o WebGL não lê variáveis CSS. Fonte da verdade: src/styles/tokens.css.
const PRIMARY = "#12d6a0";
const INFO = "#22b8f0";
const ACCENT = "#7c5cff";

const PARTICLES = 900;

function FlowParticles() {
  const points = useRef<Points>(null);

  const geometry = useMemo(() => {
    const positions = new Float32Array(PARTICLES * 3);
    const colors = new Float32Array(PARTICLES * 3);
    const gradient = [new Color(PRIMARY), new Color(INFO), new Color(ACCENT)];
    // Gerador determinístico (sem Math.random): a mesma cena em toda renderização.
    let seed = 7;
    const random = () => {
      seed = (seed * 16807) % 2147483647;
      return seed / 2147483647;
    };
    for (let i = 0; i < PARTICLES; i++) {
      const t = i / PARTICLES;
      const angle = t * Math.PI * 14;
      const radius = 1.6 + random() * 0.9;
      positions[i * 3] = Math.cos(angle) * radius;
      positions[i * 3 + 1] = (t - 0.5) * 5.5 + (random() - 0.5) * 0.3;
      positions[i * 3 + 2] = Math.sin(angle) * radius;
      const from = gradient[Math.min(1, Math.floor(t * 2))]!;
      const to = gradient[Math.min(2, Math.floor(t * 2) + 1)]!;
      const color = from.clone().lerp(to, (t * 2) % 1);
      colors.set([color.r, color.g, color.b], i * 3);
    }
    const buffer = new BufferGeometry();
    buffer.setAttribute("position", new BufferAttribute(positions, 3));
    buffer.setAttribute("color", new BufferAttribute(colors, 3));
    return buffer;
  }, []);

  useFrame((_, delta) => {
    if (points.current) points.current.rotation.y += delta * 0.18;
  });

  return (
    <points ref={points} geometry={geometry}>
      <pointsMaterial
        size={0.045}
        vertexColors
        transparent
        opacity={0.85}
        depthWrite={false}
        blending={AdditiveBlending}
        sizeAttenuation
      />
    </points>
  );
}

function Rings() {
  const group = useRef<Group>(null);

  useFrame((state, delta) => {
    const g = group.current;
    if (!g) return;
    g.rotation.y += delta * 0.25;
    // Paralaxe leve seguindo o ponteiro.
    g.rotation.x += (state.pointer.y * 0.25 - g.rotation.x) * 0.05;
    g.rotation.z += (-state.pointer.x * 0.2 - g.rotation.z) * 0.05;
  });

  return (
    <group ref={group}>
      {[
        { color: PRIMARY, radius: 1.35, tilt: [1.1, 0, 0.2] as const },
        { color: INFO, radius: 1.75, tilt: [0.4, 0.9, 0] as const },
        { color: ACCENT, radius: 2.15, tilt: [-0.6, 0.3, 0.8] as const },
      ].map(({ color, radius, tilt }) => (
        <mesh key={color} rotation={[...tilt]}>
          <torusGeometry args={[radius, 0.018, 16, 160]} />
          <meshBasicMaterial color={color} transparent opacity={0.9} />
        </mesh>
      ))}
      <Float speed={2} rotationIntensity={0.6} floatIntensity={0.4}>
        <mesh>
          <icosahedronGeometry args={[0.62, 1]} />
          <meshStandardMaterial color={PRIMARY} emissive={PRIMARY} emissiveIntensity={0.35} flatShading metalness={0.3} roughness={0.35} />
        </mesh>
        <mesh scale={1.18}>
          <icosahedronGeometry args={[0.62, 1]} />
          <meshBasicMaterial color={INFO} wireframe transparent opacity={0.35} />
        </mesh>
      </Float>
    </group>
  );
}

export default function HeroScene({ active }: { active: boolean }) {
  return (
    <Canvas
      camera={{ position: [0, 0, 6.2], fov: 45 }}
      dpr={[1, 2]}
      gl={{ antialias: true, alpha: true, powerPreference: "low-power" }}
      frameloop={active ? "always" : "never"}
      aria-hidden="true"
    >
      <ambientLight intensity={0.5} />
      <pointLight position={[4, 4, 4]} intensity={40} color={INFO} />
      <pointLight position={[-4, -2, 3]} intensity={30} color={ACCENT} />
      <Rings />
      <FlowParticles />
      <Sparkles count={40} scale={6} size={2} speed={0.3} color={ACCENT} opacity={0.6} />
    </Canvas>
  );
}
