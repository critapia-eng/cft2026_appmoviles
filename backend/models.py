import uuid
from typing import Optional
from pydantic import BaseModel, Field

# ==========================================
# Modelos de datos (Pydantic Schemas)
# ==========================================

class UserRegister(BaseModel):
    name: str
    rut: str
    emergency_phone: str
    condition_type: Optional[str] = None
    medical_history: Optional[str] = None

class UserResponse(BaseModel):
    user_id: uuid.UUID
    name: str
    rut_hashed: str
    emergency_phone: str
    condition_type: Optional[str] = None

class VitalSigns(BaseModel):
    user_id: uuid.UUID
    heart_rate: int = Field(..., ge=30, le=220)
    systolic_pressure: int
    diastolic_pressure: int
