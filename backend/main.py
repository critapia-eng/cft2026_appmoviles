from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from database import init_db
from routers import usuarios, telemetria

# Inicializamos la aplicación FastAPI
app = FastAPI(
    title="API de Alertas de Salud - Telemetría",
    version="2.0"
)

# Permitir solicitudes CORS para que se conecte con tu app móvil o frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Evento de inicio: Crea la base de datos y las tablas automáticamente al arrancar
@app.on_event("startup")
def startup_event():
    init_db()

# Incluimos los routers (los módulos ordenados que creamos)
app.include_router(usuarios.router)
app.include_router(telemetria.router)

# Ruta raíz para verificar que el servidor responde
@app.get("/")
def home():
    return {"status": "ok", "message": "Servidor backend activo y modularizado de forma escalable"}