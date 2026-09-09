import { useState, useEffect, useCallback } from "react";

// ── Types ──────────────────────────────────────────────────────────────────

interface Reading {
  bpm: number;
  ts: number;
}

interface Zone {
  id: string;
  label: string;
  min: number;
  max: number;
  color: string;
  light: string;
}

// ── Constants ──────────────────────────────────────────────────────────────

const ZONES: Zone[] = [
  { id: "rest",    label: "Reposo",       min: 40,  max: 60,  color: "#22c55e", light: "rgba(34,197,94,0.1)" },
  { id: "warmup",  label: "Calentamiento",min: 60,  max: 100, color: "#3b82f6", light: "rgba(59,130,246,0.1)" },
  { id: "cardio",  label: "Cardio",       min: 100, max: 140, color: "#f59e0b", light: "rgba(245,158,11,0.1)" },
  { id: "peak",    label: "Pico máximo",  min: 140, max: 220, color: "#e8293a", light: "rgba(232,41,58,0.1)" },
];

const METRICS = [
  { key: "spo2",     label: "SpO₂",    unit: "%",    color: "#3b82f6" },
  { key: "hrv",      label: "HRV",     unit: "ms",   color: "#8b5cf6" },
  { key: "calories", label: "Cal",     unit: "kcal", color: "#f59e0b" },
  { key: "steps",    label: "Pasos",   unit: "",     color: "#22c55e" },
];

// ── Helpers ────────────────────────────────────────────────────────────────

function getZone(bpm: number): Zone {
  return ZONES.find((z) => bpm >= z.min && bpm < z.max) ?? ZONES[3];
}

function lerp(a: number, b: number, t: number) {
  return a + (b - a) * t;
}

function formatTime(date: Date) {
  return date.toLocaleTimeString("es-ES", { hour: "2-digit", minute: "2-digit" });
}

function formatDate(date: Date) {
  return date.toLocaleDateString("es-ES", { weekday: "short", day: "numeric", month: "short" });
}

// ── Watch Face Component ───────────────────────────────────────────────────

function WatchFace({ bpm, zone, active }: { bpm: number; zone: Zone; active: boolean }) {
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(t);
  }, []);

  const r = 72;
  const cx = 100;
  const cy = 100;
  const pct = Math.min(1, (bpm - 40) / 180);
  const angle = -90 + pct * 270;
  const rad = (deg: number) => ((deg - 90) * Math.PI) / 180;

  function arcPath(startDeg: number, endDeg: number, radius: number) {
    const s = { x: cx + radius * Math.cos(rad(startDeg)), y: cy + radius * Math.sin(rad(startDeg)) };
    const e = { x: cx + radius * Math.cos(rad(endDeg)), y: cy + radius * Math.sin(rad(endDeg)) };
    const large = endDeg - startDeg > 180 ? 1 : 0;
    return `M ${s.x} ${s.y} A ${radius} ${radius} 0 ${large} 1 ${e.x} ${e.y}`;
  }

  const hours = now.getHours();
  const minutes = now.getMinutes();
  const seconds = now.getSeconds();
  const secAngle = (seconds / 60) * 360 - 90;
  const minAngle = ((minutes + seconds / 60) / 60) * 360 - 90;
  const hrAngle = ((hours % 12 + minutes / 60) / 12) * 360 - 90;

  function hand(deg: number, len: number, w: number, color: string) {
    const r2 = ((deg - 90) * Math.PI) / 180;
    const x = cx + len * Math.cos(r2);
    const y = cy + len * Math.sin(r2);
    return <line x1={cx} y1={cy} x2={x} y2={y} stroke={color} strokeWidth={w} strokeLinecap="round" />;
  }

  return (
    <div className="relative flex items-center justify-center">
      {/* Watch body */}
      <div className="relative" style={{
        width: 200,
        height: 220,
        borderRadius: 44,
        background: "var(--watch-bg)",
        boxShadow: "0 32px 80px rgba(13,27,42,0.45), 0 0 0 2px rgba(255,255,255,0.06)",
        padding: 8,
      }}>
        {/* Screen */}
        <div className="w-full h-full rounded-3xl overflow-hidden flex flex-col items-center justify-center"
          style={{ background: "#111827", position: "relative" }}>

          {/* Ambient glow */}
          <div className="absolute inset-0 opacity-20" style={{
            background: `radial-gradient(circle at 50% 60%, ${zone.color}, transparent 65%)`,
            transition: "all 1s ease",
          }} />

          {/* Watch clock */}
          <svg viewBox="0 0 200 200" width={184} height={184} className="relative z-10">
            {/* Hour markers */}
            {Array.from({ length: 12 }, (_, i) => {
              const a = (i / 12) * 360 - 90;
              const r3 = (a * Math.PI) / 180;
              const x1 = cx + 86 * Math.cos(r3);
              const y1 = cy + 86 * Math.sin(r3);
              const x2 = cx + 92 * Math.cos(r3);
              const y2 = cy + 92 * Math.sin(r3);
              return <line key={i} x1={x1} y1={y1} x2={x2} y2={y2} stroke="rgba(255,255,255,0.2)" strokeWidth={i % 3 === 0 ? 2 : 1} />;
            })}

            {/* Progress arc track */}
            <path d={arcPath(-135, 135, r)} fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth={6} strokeLinecap="round" />
            {pct > 0.01 && (
              <path d={arcPath(-135, lerp(-135, 135, pct), r)} fill="none" stroke={zone.color} strokeWidth={6} strokeLinecap="round"
                style={{ filter: `drop-shadow(0 0 5px ${zone.color})`, transition: "all 0.8s ease" }} />
            )}

            {/* Clock hands */}
            {hand(hrAngle, 32, 3, "rgba(255,255,255,0.85)")}
            {hand(minAngle, 46, 2, "rgba(255,255,255,0.85)")}
            {hand(secAngle, 52, 1, zone.color)}
            <circle cx={cx} cy={cy} r={3} fill={zone.color} />

            {/* BPM center display */}
            <text x={cx} y={cy - 16} textAnchor="middle" fontSize="9" fill="rgba(255,255,255,0.4)"
              style={{ fontFamily: "Space Mono, monospace", letterSpacing: 1 }}>RITMO CARDÍACO</text>
            <text x={cx} y={cy + 30} textAnchor="middle" fontSize="22" fontWeight="700" fill={zone.color}
              style={{ fontFamily: "Space Mono, monospace", transition: "fill 1s ease" }}>{bpm}</text>
            <text x={cx} y={cy + 42} textAnchor="middle" fontSize="8" fill="rgba(255,255,255,0.4)"
              style={{ fontFamily: "Space Mono, monospace" }}>BPM</text>

            {/* Status dot */}
            <circle cx={cx} cy={cy - 50} r={3} fill={active ? "#22c55e" : "#64748b"}
              style={{ filter: active ? "drop-shadow(0 0 4px #22c55e)" : "none" }} />
          </svg>
        </div>

        {/* Watch crown button */}
        <div className="absolute" style={{
          right: -6, top: "35%",
          width: 5, height: 28,
          background: "linear-gradient(to right, #1e3a5f, #2d5a8e)",
          borderRadius: "0 3px 3px 0",
        }} />
        <div className="absolute" style={{
          right: -6, top: "55%",
          width: 5, height: 18,
          background: "linear-gradient(to right, #1e3a5f, #2d5a8e)",
          borderRadius: "0 3px 3px 0",
        }} />
      </div>
    </div>
  );
}

// ── Spark Line ─────────────────────────────────────────────────────────────

function SparkLine({ readings, color }: { readings: Reading[]; color: string }) {
  if (readings.length < 2) return null;
  const vals = readings.map((r) => r.bpm);
  const min = Math.min(...vals) - 5;
  const max = Math.max(...vals) + 5;
  const W = 100, H = 32;

  const pts = vals.map((v, i) => {
    const x = (i / (vals.length - 1)) * W;
    const y = H - ((v - min) / (max - min)) * H;
    return [x, y] as [number, number];
  });

  const line = pts.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x},${y}`).join(" ");
  const area = `${line} L${W},${H} L0,${H} Z`;

  return (
    <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{ width: "100%", height: 32 }}>
      <defs>
        <linearGradient id={`sg-${color.replace("#","")}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.3" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#sg-${color.replace("#","")})`} />
      <path d={line} fill="none" stroke={color} strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
}

// ── Zone Pill ──────────────────────────────────────────────────────────────

function ZonePill({ zone, active, bpm }: { zone: Zone; active: boolean; bpm: number }) {
  const inZone = bpm >= zone.min && bpm < zone.max;
  const progress = inZone ? (bpm - zone.min) / (zone.max - zone.min) : 0;

  return (
    <div className="rounded-2xl p-3 relative overflow-hidden transition-all duration-500"
      style={{
        background: active ? zone.light : "rgba(13,27,42,0.03)",
        border: `1px solid ${active ? zone.color + "44" : "rgba(13,27,42,0.06)"}`,
        transform: active ? "scale(1.02)" : "scale(1)",
      }}>
      {active && (
        <div className="absolute bottom-0 left-0 h-0.5 transition-all duration-700"
          style={{ width: `${progress * 100}%`, background: zone.color, opacity: 0.6 }} />
      )}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full flex-shrink-0"
            style={{ background: zone.color, boxShadow: active ? `0 0 6px ${zone.color}` : "none" }} />
          <span className="text-sm font-semibold" style={{ color: active ? zone.color : "var(--text-secondary)" }}>
            {zone.label}
          </span>
        </div>
        <span className="text-xs font-mono" style={{ color: "var(--text-muted)", fontFamily: "Space Mono, monospace" }}>
          {zone.min}–{zone.max}
        </span>
      </div>
    </div>
  );
}

// ── Main App ───────────────────────────────────────────────────────────────

export default function App() {
  const [bpm, setBpm] = useState(74);
  const [active, setActive] = useState(true);
  const [readings, setReadings] = useState<Reading[]>([{ bpm: 74, ts: Date.now() }]);
  const [metrics, setMetrics] = useState({ spo2: 97, hrv: 42, calories: 284, steps: 6230 });
  const [beat, setBeat] = useState(false);

  const zone = getZone(bpm);

  // Simulate BPM updates
  useEffect(() => {
    if (!active) return;
    const t = setInterval(() => {
      setBpm((prev) => {
        const next = Math.max(55, Math.min(190, prev + Math.round((Math.random() - 0.48) * 6)));
        setReadings((r) => [...r.slice(-59), { bpm: next, ts: Date.now() }]);
        return next;
      });
    }, 1000);
    return () => clearInterval(t);
  }, [active]);

  // Heartbeat pulse
  useEffect(() => {
    if (!active) return;
    const interval = (60 / bpm) * 1000;
    const t = setInterval(() => {
      setBeat(true);
      setTimeout(() => setBeat(false), 100);
      setMetrics((m) => ({
        ...m,
        steps: m.steps + (Math.random() > 0.7 ? 1 : 0),
        calories: m.calories + (Math.random() > 0.92 ? 1 : 0),
      }));
    }, interval);
    return () => clearInterval(t);
  }, [bpm, active]);

  // SpO2 / HRV drift
  useEffect(() => {
    if (!active) return;
    const t = setInterval(() => {
      setMetrics((m) => ({
        ...m,
        spo2: Math.max(94, Math.min(100, m.spo2 + (Math.random() > 0.5 ? 1 : -1))),
        hrv: Math.max(20, Math.min(80, m.hrv + Math.round((Math.random() - 0.5) * 4))),
      }));
    }, 3000);
    return () => clearInterval(t);
  }, [active]);

  const maxBpm = readings.length > 0 ? Math.max(...readings.map((r) => r.bpm)) : bpm;
  const minBpm = readings.length > 0 ? Math.min(...readings.map((r) => r.bpm)) : bpm;
  const avgBpm = readings.length > 0 ? Math.round(readings.reduce((s, r) => s + r.bpm, 0) / readings.length) : bpm;

  const metricValues: Record<string, string | number> = {
    spo2: metrics.spo2,
    hrv: metrics.hrv,
    calories: metrics.calories,
    steps: metrics.steps.toLocaleString("es-ES"),
  };

  return (
    <div className="min-h-full w-full" style={{ background: "var(--bg)" }}>
      <style>{`
        @keyframes heartbeat {
          0% { transform: scale(1); }
          30% { transform: scale(1.22); }
          60% { transform: scale(1); }
          80% { transform: scale(1.1); }
          100% { transform: scale(1); }
        }
        @keyframes fadeSlide {
          from { opacity: 0; transform: translateY(8px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>

      <div className="max-w-sm mx-auto px-4 py-8 flex flex-col gap-6">

        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs font-medium tracking-widest uppercase" style={{ color: "var(--text-muted)" }}>Monitor Cardíaco</p>
            <p className="text-xl font-bold mt-0.5" style={{ color: "var(--text-primary)" }}>
              {formatDate(new Date())} · {formatTime(new Date())}
            </p>
          </div>
          <button
            onClick={() => setActive((a) => !a)}
            className="flex items-center gap-2 px-4 py-2 rounded-full text-sm font-semibold transition-all duration-200 active:scale-95"
            style={{
              background: active ? "var(--navy)" : "var(--surface)",
              color: active ? "#fff" : "var(--text-secondary)",
              boxShadow: active ? "0 4px 16px rgba(13,27,42,0.25)" : "0 1px 4px rgba(13,27,42,0.1)",
            }}>
            <div className="w-1.5 h-1.5 rounded-full" style={{ background: active ? "#22c55e" : "#94a3b8" }} />
            {active ? "En vivo" : "Pausado"}
          </button>
        </div>

        {/* Watch + BPM hero */}
        <div className="rounded-3xl p-6 flex flex-col items-center gap-6"
          style={{ background: "var(--surface)", boxShadow: "0 4px 24px rgba(13,27,42,0.08)" }}>

          <WatchFace bpm={bpm} zone={zone} active={active} />

          {/* Live BPM readout */}
          <div className="flex items-center gap-4 w-full justify-center">
            <div className="flex items-center gap-3">
              <svg
                width={28} height={28} viewBox="0 0 24 24" fill={zone.color}
                style={{
                  animation: beat && active ? "heartbeat 0.4s ease" : "none",
                  filter: `drop-shadow(0 0 6px ${zone.color}60)`,
                  transition: "fill 0.8s ease",
                }}>
                <path d="M12 21.593c-5.63-5.539-11-10.297-11-14.402 0-3.791 3.068-5.191 5.281-5.191 1.312 0 4.151.501 5.719 4.457 1.59-3.968 4.464-4.447 5.726-4.447 2.54 0 5.274 1.621 5.274 5.181 0 4.069-5.136 8.625-11 14.402z" />
              </svg>
              <span className="text-6xl font-extrabold tracking-tight transition-all duration-500"
                style={{ color: zone.color, fontFamily: "Space Mono, monospace" }}>{bpm}</span>
              <div className="flex flex-col gap-0.5">
                <span className="text-xs font-bold uppercase tracking-widest" style={{ color: "var(--text-muted)" }}>BPM</span>
                <span className="text-xs font-semibold px-2 py-0.5 rounded-full"
                  style={{ background: zone.light, color: zone.color }}>{zone.label}</span>
              </div>
            </div>
          </div>

          {/* Min / Avg / Max */}
          <div className="w-full grid grid-cols-3 divide-x" style={{ borderTop: "1px solid var(--border)", paddingTop: 16, borderColor: "var(--border)" }}>
            {[
              { label: "Mín", value: minBpm },
              { label: "Prom", value: avgBpm },
              { label: "Máx", value: maxBpm },
            ].map((s) => (
              <div key={s.label} className="flex flex-col items-center gap-0.5" style={{ borderColor: "var(--border)" }}>
                <span className="text-xs uppercase tracking-widest" style={{ color: "var(--text-muted)" }}>{s.label}</span>
                <span className="text-lg font-bold" style={{ fontFamily: "Space Mono, monospace", color: "var(--text-primary)" }}>{s.value}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Sparkline chart */}
        <div className="rounded-2xl p-5" style={{ background: "var(--surface)", boxShadow: "0 2px 12px rgba(13,27,42,0.06)" }}>
          <div className="flex items-center justify-between mb-4">
            <p className="text-sm font-semibold uppercase tracking-widest" style={{ color: "var(--text-muted)" }}>
              Historial en vivo
            </p>
            <span className="text-xs font-mono" style={{ color: zone.color, fontFamily: "Space Mono, monospace" }}>
              {readings.length} lecturas
            </span>
          </div>
          <SparkLine readings={readings} color={zone.color} />
        </div>

        {/* Biometric metrics */}
        <div className="grid grid-cols-2 gap-3">
          {METRICS.map((m) => (
            <div key={m.key} className="rounded-2xl p-4" style={{ background: "var(--surface)", boxShadow: "0 2px 8px rgba(13,27,42,0.05)" }}>
              <p className="text-xs uppercase tracking-widest font-medium" style={{ color: "var(--text-muted)" }}>{m.label}</p>
              <div className="flex items-end gap-1 mt-2">
                <span className="text-2xl font-bold" style={{ color: m.color, fontFamily: "Space Mono, monospace" }}>
                  {metricValues[m.key]}
                </span>
                {m.unit && <span className="text-xs pb-1" style={{ color: "var(--text-muted)" }}>{m.unit}</span>}
              </div>
              <div className="mt-2 h-1 rounded-full overflow-hidden" style={{ background: "var(--border)" }}>
                <div className="h-full rounded-full transition-all duration-700"
                  style={{
                    width: m.key === "spo2" ? `${((metrics.spo2 - 90) / 10) * 100}%`
                      : m.key === "hrv" ? `${(metrics.hrv / 80) * 100}%`
                      : m.key === "calories" ? `${Math.min(100, (metrics.calories / 500) * 100)}%`
                      : `${Math.min(100, (metrics.steps / 10000) * 100)}%`,
                    background: m.color,
                  }} />
              </div>
            </div>
          ))}
        </div>

        {/* Zones */}
        <div className="rounded-2xl p-5" style={{ background: "var(--surface)", boxShadow: "0 2px 12px rgba(13,27,42,0.06)" }}>
          <p className="text-sm font-semibold uppercase tracking-widest mb-3" style={{ color: "var(--text-muted)" }}>
            Zonas de frecuencia
          </p>
          <div className="flex flex-col gap-2">
            {ZONES.map((z) => (
              <ZonePill key={z.id} zone={z} active={z.id === zone.id} bpm={bpm} />
            ))}
          </div>
        </div>

        {/* Footer */}
        <p className="text-center text-xs pb-2" style={{ color: "var(--text-muted)" }}>
          Sensor óptico · Actualización cada segundo
        </p>
      </div>
    </div>
  );
}
