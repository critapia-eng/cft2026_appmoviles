from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime

# Esquema para registrar un usuario
class UserRegister(BaseModel):
    nombre: str
    rut: str
    telefono_emergencia: str
    # 'normal', 'taquicardico', 'bradicardico'
    condicion_medica: Optional[str] = "normal"


# Esquema para responder los datos de un usuario registrado
class UserResponse(BaseModel):
    id: str
    nombre: str
    rut_hashed: str
    telefono_emergencia: str
    condicion_medica: str
    hr_min_custom: int
    hr_max_custom: int

    class Config:
        from_attributes = True


# Esquema para recibir mediciones de pulso y presión
class VitalSigns(BaseModel):
    usuario_id: str
    frecuencia_cardiaca: int = Field(..., ge=30, le=220)
    presion_sistolica: int
    presion_diastolica: int


# Esquema para mostrar alertas generadas
class AlertResponse(BaseModel):
    id: int
    usuario_id: str
    tipo_alerta: str
    descripcion: str
    fecha_alerta: datetime

    class Config:
        from_attributes = True