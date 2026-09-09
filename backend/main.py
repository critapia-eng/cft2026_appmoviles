import uuid
import hashlib
from datetime import datetime
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="API de Alertas de Salud - Telemetría")

# Bases de datos temporales en memoria
users_db = {}
telemetry_db = []
alerts_db = []

# Modelos de datos
class UserRegister(BaseModel):
    name: str
    rut: str
    emergency_phone: str

class UserResponse(BaseModel):
    user_id: uuid.UUID
    name: str
    rut_hashed: str
    emergency_phone: str

class VitalSigns(BaseModel):
    user_id: uuid.UUID
    heart_rate: int = Field(..., ge=30, le=220)
    systolic_pressure: int
    diastolic_pressure: int

def hash_rut(rut: str) -> str:
    clean_rut = rut.replace(".", "").replace("-", "").strip().lower()
    return hashlib.sha256(clean_rut.encode()).hexdigest()

@app.get("/")
def home():
    return {"status": "ok", "message": "Servidor backend activo"}

@app.post("/users/register", response_model=UserResponse)
def register_user(user: UserRegister):
    user_id = uuid.uuid4()
    rut_protected = hash_rut(user.rut)
    
    user_data = {
        "user_id": user_id,
        "name": user.name,
        "rut_hashed": rut_protected,
        "emergency_phone": user.emergency_phone
    }
    users_db[user_id] = user_data
    return user_data

@app.post("/telemetry/send")
def receive_telemetry(data: VitalSigns):
    if data.user_id not in users_db:
        raise HTTPException(status_code=404, detail="Usuario no encontrado")

    timestamp = datetime.now()
    record = {**data.dict(), "timestamp": timestamp}
    telemetry_db.append(record)

    # Evaluación de rangos de peligro
    is_high_pressure = data.systolic_pressure > 140
    is_low_pressure = data.systolic_pressure < 90
    is_anomalous_hr = data.heart_rate > 120 or data.heart_rate < 50

    if is_high_pressure or is_low_pressure or is_anomalous_hr:
        alert = {
            "alert_id": uuid.uuid4(),
            "user_id": data.user_id,
            "timestamp": timestamp,
            "reason": f"Sistólica: {data.systolic_pressure}, BPM: {data.heart_rate}",
            "contact_phone": users_db[data.user_id]["emergency_phone"],
            "status": "LLAMADA_A_HOSPITAL_INICIADA"
        }
        alerts_db.append(alert)
        return {"alert_triggered": True, "message": "ANOMALÍA DETECTADA: Alerta enviada al hospital.", "alert_details": alert}

    return {"alert_triggered": False, "message": "Signos vitales estables."}

@app.get("/alerts")
def get_alerts():
    return {"alerts": alerts_db}