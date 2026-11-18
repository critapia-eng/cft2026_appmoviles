import sys
from pathlib import Path

# Configurar path para permitir imports tanto directos (python main.py) como modulares
current_dir = Path(__file__).resolve().parent
if str(current_dir) not in sys.path:
    sys.path.insert(0, str(current_dir))

from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware

# Re-exportar datos y modelos para mantener 100% de retrocompatibilidad
from database import users_db, telemetry_db, alerts_db
from models import UserRegister, UserResponse, VitalSigns
from utils import hash_rut

# Importación de routers modulares
from routers import users_router, telemetry_router, alerts_router

# Inicialización de la aplicación FastAPI
app = FastAPI(
    title="API de Alertas de Salud - Telemetría",
    description="Backend modular y escalable para registro de pacientes, telemetría y alertas SOS.",
    version="2.0.0"
)

# Configuración de CORS para permitir conexiones desde la app móvil y frontend web
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Inclusión de routers modulares
app.include_router(users_router)
app.include_router(telemetry_router)
app.include_router(alerts_router)

@app.get("/")
def home():
    return {"status": "ok", "message": "Servidor backend activo"}

@app.get("/privacy", response_class=HTMLResponse)
def get_privacy_policy():
    privacy_file = Path(__file__).resolve().parent / "privacy.html"
    if privacy_file.exists():
        return HTMLResponse(content=privacy_file.read_text(encoding="utf-8"))
    return HTMLResponse(content="<h1>Política de Privacidad PulseUp - CFT Lota Arauco</h1><p>En proceso de actualización.</p>")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
