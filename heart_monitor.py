"""
Smart Watch Heart Rate Monitor — Python / Streamlit
Instalar: pip install streamlit plotly
Ejecutar:  streamlit run heart_monitor.py
"""

import random
import time
from datetime import datetime

import plotly.graph_objects as go
import streamlit as st

# ── Configuración de página ───────────────────────────────────────────────

st.set_page_config(
    page_title="Monitor Cardíaco",
    page_icon="❤️",
    layout="centered",
)

# ── Estilos ───────────────────────────────────────────────────────────────

st.markdown("""
<style>
  @import url('https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700;800&family=Space+Mono:wght@400;700&display=swap');

  html, body, [class*="css"] { font-family: 'Outfit', sans-serif; }

  .stApp { background: #f0f2f7; }

  .watch-card {
    background: #ffffff;
    border-radius: 24px;
    padding: 28px 24px;
    box-shadow: 0 4px 24px rgba(13,27,42,0.08);
    margin-bottom: 16px;
  }
  .bpm-hero {
    text-align: center;
    font-family: 'Space Mono', monospace;
    font-size: 72px;
    font-weight: 700;
    line-height: 1;
    margin: 8px 0;
  }
  .zone-badge {
    display: inline-block;
    border-radius: 999px;
    padding: 3px 12px;
    font-size: 12px;
    font-weight: 600;
    margin-left: 8px;
  }
  .metric-card {
    background: #ffffff;
    border-radius: 18px;
    padding: 16px;
    box-shadow: 0 2px 8px rgba(13,27,42,0.05);
    text-align: center;
  }
  .metric-label {
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 1.5px;
    color: #94a3b8;
  }
  .metric-value {
    font-family: 'Space Mono', monospace;
    font-size: 28px;
    font-weight: 700;
    line-height: 1.2;
  }
  .metric-unit {
    font-size: 11px;
    color: #94a3b8;
  }
  .zone-pill {
    border-radius: 14px;
    padding: 10px 14px;
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 14px;
    font-weight: 600;
    transition: all 0.3s;
  }
  .section-title {
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 2px;
    color: #94a3b8;
    margin-bottom: 12px;
  }
</style>
""", unsafe_allow_html=True)

# ── Zonas cardíacas ───────────────────────────────────────────────────────

ZONES = [
    {"id": "rest",   "label": "Reposo",        "min": 40,  "max": 60,  "color": "#22c55e"},
    {"id": "warmup", "label": "Calentamiento",  "min": 60,  "max": 100, "color": "#3b82f6"},
    {"id": "cardio", "label": "Cardio",         "min": 100, "max": 140, "color": "#f59e0b"},
    {"id": "peak",   "label": "Pico máximo",    "min": 140, "max": 220, "color": "#e8293a"},
]


def get_zone(bpm: int) -> dict:
    for z in ZONES:
        if z["min"] <= bpm < z["max"]:
            return z
    return ZONES[-1]


# ── Estado de sesión ──────────────────────────────────────────────────────

def init_state():
    defaults = {
        "bpm": 74,
        "history": [74],
        "active": True,
        "spo2": 97,
        "hrv": 42,
        "calories": 284,
        "steps": 6230,
        "start_time": time.time(),
    }
    for k, v in defaults.items():
        if k not in st.session_state:
            st.session_state[k] = v


init_state()


def tick():
    """Avanza la simulación un segundo."""
    if not st.session_state.active:
        return

    prev = st.session_state.bpm
    new_bpm = max(55, min(190, prev + round((random.random() - 0.48) * 6)))
    st.session_state.bpm = new_bpm
    st.session_state.history = (st.session_state.history + [new_bpm])[-60:]

    if random.random() > 0.7:
        st.session_state.steps += 1
    if random.random() > 0.92:
        st.session_state.calories += 1
    if random.random() > 0.5:
        st.session_state.spo2 = max(94, min(100, st.session_state.spo2 + random.choice([-1, 1])))
    st.session_state.hrv = max(20, min(80, st.session_state.hrv + round((random.random() - 0.5) * 4)))


tick()

# ── Datos actuales ────────────────────────────────────────────────────────

bpm = st.session_state.bpm
zone = get_zone(bpm)
history = st.session_state.history
max_bpm = max(history)
min_bpm = min(history)
avg_bpm = round(sum(history) / len(history))
elapsed = int(time.time() - st.session_state.start_time)
elapsed_str = f"{elapsed // 60:02d}:{elapsed % 60:02d}"

# ── Header ────────────────────────────────────────────────────────────────

col_title, col_btn = st.columns([3, 1])
with col_title:
    now = datetime.now()
    st.markdown(f"""
        <p style='font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:2px;color:#94a3b8;margin:0'>
            Monitor Cardíaco
        </p>
        <p style='font-size:18px;font-weight:700;color:#0d1b2a;margin:0'>
            {now.strftime('%a %d %b')} · {now.strftime('%H:%M')}
            <span style='font-size:13px;color:#94a3b8;margin-left:8px;font-family:Space Mono,monospace'>
                {elapsed_str}
            </span>
        </p>
    """, unsafe_allow_html=True)

with col_btn:
    status = "⏸ Pausar" if st.session_state.active else "▶ Reanudar"
    if st.button(status, use_container_width=True):
        st.session_state.active = not st.session_state.active
        st.rerun()

st.markdown("<div style='height:8px'/>", unsafe_allow_html=True)

# ── BPM Hero ──────────────────────────────────────────────────────────────

st.markdown(f"""
<div class="watch-card" style="text-align:center">
    <p class="section-title">Ritmo cardíaco en tiempo real</p>
    <div class="bpm-hero" style="color:{zone['color']}">
        {bpm}
        <span style="font-size:20px;color:#94a3b8;font-family:Outfit,sans-serif;font-weight:400"> BPM</span>
    </div>
    <span class="zone-badge" style="background:{zone['color']}22;color:{zone['color']}">
        ♥ {zone['label']}
    </span>
    <div style="display:flex;justify-content:space-around;margin-top:20px;
                border-top:1px solid rgba(13,27,42,0.06);padding-top:16px">
        <div>
            <div style="font-size:11px;text-transform:uppercase;letter-spacing:1.5px;color:#94a3b8">Mín</div>
            <div style="font-family:'Space Mono',monospace;font-size:20px;font-weight:700;color:#0d1b2a">{min_bpm}</div>
        </div>
        <div>
            <div style="font-size:11px;text-transform:uppercase;letter-spacing:1.5px;color:#94a3b8">Prom</div>
            <div style="font-family:'Space Mono',monospace;font-size:20px;font-weight:700;color:#0d1b2a">{avg_bpm}</div>
        </div>
        <div>
            <div style="font-size:11px;text-transform:uppercase;letter-spacing:1.5px;color:#94a3b8">Máx</div>
            <div style="font-family:'Space Mono',monospace;font-size:20px;font-weight:700;color:#0d1b2a">{max_bpm}</div>
        </div>
    </div>
</div>
""", unsafe_allow_html=True)

# ── Gráfico de historial ──────────────────────────────────────────────────

fig = go.Figure()

fig.add_trace(go.Scatter(
    y=history,
    mode="lines",
    fill="tozeroy",
    line=dict(color=zone["color"], width=2.5, shape="spline", smoothing=0.8),
    fillcolor=f"{zone['color']}22",
    hovertemplate="%{y} BPM<extra></extra>",
))

fig.update_layout(
    height=120,
    margin=dict(l=0, r=0, t=0, b=0),
    paper_bgcolor="white",
    plot_bgcolor="white",
    showlegend=False,
    xaxis=dict(visible=False),
    yaxis=dict(visible=False, range=[40, 200]),
)

st.markdown('<div class="watch-card" style="padding-bottom:8px">', unsafe_allow_html=True)
st.markdown('<p class="section-title">Historial en vivo</p>', unsafe_allow_html=True)
st.plotly_chart(fig, use_container_width=True, config={"displayModeBar": False})
st.markdown('</div>', unsafe_allow_html=True)

# ── Métricas ──────────────────────────────────────────────────────────────

st.markdown('<p class="section-title">Métricas biométricas</p>', unsafe_allow_html=True)

metrics = [
    {"label": "SpO₂",   "value": st.session_state.spo2,                    "unit": "%",    "color": "#3b82f6",
     "pct": (st.session_state.spo2 - 90) / 10 * 100},
    {"label": "HRV",    "value": st.session_state.hrv,                     "unit": "ms",   "color": "#8b5cf6",
     "pct": st.session_state.hrv / 80 * 100},
    {"label": "Cal",    "value": st.session_state.calories,                "unit": "kcal", "color": "#f59e0b",
     "pct": min(100, st.session_state.calories / 500 * 100)},
    {"label": "Pasos",  "value": f"{st.session_state.steps:,}".replace(",", "."), "unit": "", "color": "#22c55e",
     "pct": min(100, st.session_state.steps / 10000 * 100)},
]

cols = st.columns(4)
for col, m in zip(cols, metrics):
    with col:
        st.markdown(f"""
        <div class="metric-card">
            <div class="metric-label">{m['label']}</div>
            <div class="metric-value" style="color:{m['color']}">{m['value']}</div>
            <div class="metric-unit">{m['unit']}</div>
            <div style="margin-top:8px;height:4px;border-radius:4px;background:#f0f2f7;overflow:hidden">
                <div style="height:100%;width:{m['pct']:.0f}%;background:{m['color']};border-radius:4px;
                            transition:width 0.6s ease"></div>
            </div>
        </div>
        """, unsafe_allow_html=True)

# ── Zonas ─────────────────────────────────────────────────────────────────

st.markdown("<div style='height:16px'/>", unsafe_allow_html=True)
st.markdown(f"""
<div class="watch-card">
    <p class="section-title">Zonas de frecuencia</p>
""", unsafe_allow_html=True)

for z in ZONES:
    active_zone = z["id"] == zone["id"]
    bg = f"{z['color']}18" if active_zone else "rgba(13,27,42,0.03)"
    border = f"1px solid {z['color']}44" if active_zone else "1px solid rgba(13,27,42,0.06)"
    text_color = z["color"] if active_zone else "#64748b"
    dot_shadow = f"0 0 6px {z['color']}" if active_zone else "none"

    progress_pct = 0
    if active_zone:
        progress_pct = max(0, min(100, (bpm - z["min"]) / max(1, z["max"] - z["min"]) * 100))

    st.markdown(f"""
    <div class="zone-pill" style="background:{bg};border:{border};flex-direction:column;align-items:stretch">
        <div style="display:flex;justify-content:space-between;align-items:center">
            <div style="display:flex;align-items:center;gap:8px">
                <div style="width:8px;height:8px;border-radius:50%;background:{z['color']};
                            box-shadow:{dot_shadow}"></div>
                <span style="color:{text_color}">{z['label']}</span>
            </div>
            <span style="font-family:'Space Mono',monospace;font-size:12px;color:#94a3b8">
                {z['min']}–{z['max']} bpm
            </span>
        </div>
        {"" if not active_zone else f'''
        <div style="margin-top:8px;height:2px;border-radius:2px;background:rgba(13,27,42,0.06);overflow:hidden">
            <div style="height:100%;width:{progress_pct:.0f}%;background:{z['color']};border-radius:2px"></div>
        </div>
        '''}
    </div>
    """, unsafe_allow_html=True)

st.markdown("</div>", unsafe_allow_html=True)

# ── Pie ───────────────────────────────────────────────────────────────────

st.markdown("""
<p style='text-align:center;font-size:12px;color:#94a3b8;margin-top:8px;padding-bottom:16px'>
    Sensor óptico · Actualización cada segundo
</p>
""", unsafe_allow_html=True)

# ── Auto-refresco ─────────────────────────────────────────────────────────

if st.session_state.active:
    time.sleep(1)
    st.rerun()
