import uuid
import hashlib
from pydantic import BaseModel, Field

class UserRegisterRequest(BaseModel):
    name: str
    rut: str
    emergency_phone: str
    condition_type: str = "Ritmo Normal (60 - 100 BPM)"

class UserResponse(BaseModel):
    user_id: uuid.UUID
    name: str
    rut_hashed: str
    emergency_phone: str
    condition_type: str

class VitalSigns(BaseModel):
    user_id: uuid.UUID
    heart_rate: int = Field(..., ge=30, le=220)
    systolic_pressure: int
    diastolic_pressure: int

def hash_rut(rut: str) -> str:
    clean_rut = rut.replace(".", "").replace("-", "").upper()
    return hashlib.sha256(clean_rut.encode()).hexdigest()