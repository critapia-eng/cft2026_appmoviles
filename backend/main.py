import hashlib
from typing import List
from fastapi import FastAPI, Depends, HTTPException, status
from sqlalchemy.orm import Session

# 1. Importaciones corregidas arriba del todo
from backend import models, schemas
from backend.database import engine, get_db

# Crear las tablas en la BD si no existen
models.Base.metadata.create_all(bind=engine)

app = FastAPI(title="API de Alertas de Salud - Telemetría")


# --- ENDPOINTS ---

@app.post("/register", response_model=schemas.UserResponse, status_code=status.HTTP_201_CREATED)
def registrar_usuario(user: schemas.UserRegister, db: Session = Depends(get_db)):
    # 1. Cifrar el RUT con SHA-256 por privacidad
    rut_hashed = hashlib.sha256(user.rut.strip().lower().encode()).hexdigest()

    # Validar si el usuario ya existe
    db_user = db.query(models.Usuario).filter(models.Usuario.rut_hashed == rut_hashed).first()
    if db_user:
        raise HTTPException(status_code=400, detail="El usuario con este RUT ya está registrado.")

    # 2. Asignar umbrales según la condición médica del paciente
    condicion = user.condicion_medica.lower() if user.condicion_medica else "normal"
    hr_min = 60
    hr_max = 100

    if condicion == "bradicardico":
        hr_min = 45  # Tolera pulso en reposo más bajo sin disparar falsas alarmas
    elif condicion == "taquicardico":
        hr_max = 115 # Tolera pulso en reposo más alto sin disparar falsas alarmas

    # 3. Guardar en BD
    nuevo_usuario = models.Usuario(
        nombre=user.nombre,
        rut_hashed=rut_hashed,
        telefono_emergencia=user.telefono_emergencia,
        condicion_medica=condicion,
        hr_min_custom=hr_min,
        hr_max_custom=hr_max
    )
    db.add(nuevo_usuario)
    db.commit()
    db.refresh(nuevo_usuario)
    return nuevo_usuario


@app.post("/telemetry")
def recibir_telemetria(data: schemas.VitalSigns, db: Session = Depends(get_db)):
    # Buscar paciente por ID
    usuario = db.query(models.Usuario).filter(models.Usuario.id == data.usuario_id).first()
    if not usuario:
        raise HTTPException(status_code=404, detail="Usuario no encontrado.")

    # Guardar lectura de telemetría
    lectura = models.Telemetria(
        usuario_id=data.usuario_id,
        frecuencia_cardiaca=data.frecuencia_cardiaca,
        presion_sistolica=data.presion_sistolica,
        presion_diastolica=data.presion_diastolica
    )
    db.add(lectura)

    # Lógica de detección de Alerta con Rango Personalizado
    alerta_generada = None
    if data.frecuencia_cardiaca > usuario.hr_max_custom:
        alerta_generada = models.Alerta(
            usuario_id=data.usuario_id,
            tipo_alerta="TAQUICARDIA",
            descripcion=f"Frecuencia de {data.frecuencia_cardiaca} BPM supera el máximo del paciente ({usuario.hr_max_custom} BPM)."
        )
    elif data.frecuencia_cardiaca < usuario.hr_min_custom:
        alerta_generada = models.Alerta(
            usuario_id=data.usuario_id,
            tipo_alerta="BRADICARDIA",
            descripcion=f"Frecuencia de {data.frecuencia_cardiaca} BPM está bajo el mínimo del paciente ({usuario.hr_min_custom} BPM)."
        )

    if alerta_generada:
        db.add(alerta_generada)

    db.commit()

    return {
        "status": "ok",
        "mensaje": "Lectura registrada correctamente.",
        "alerta": alerta_generada.tipo_alerta if alerta_generada else "Sin anomalías"
    }


@app.get("/alerts", response_model=List[schemas.AlertResponse])
def listar_alertas(db: Session = Depends(get_db)):
    return db.query(models.Alerta).all()