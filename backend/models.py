import uuid
from sqlalchemy import Column, String, Integer, DateTime, ForeignKey, Float
from sqlalchemy.sql import func
from backend.database import Base

class Usuario(Base):
    __tablename__ = "usuarios"

    # Identificador único de usuario
    id = Column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    nombre = Column(String(100), nullable=False)
    
    # RUT almacenado como hash SHA-256 para mayor seguridad
    rut_hashed = Column(String(64), unique=True, nullable=False)
    telefono_emergencia = Column(String(20), nullable=False)

    # Condición especial del paciente (Ej: 'normal', 'taquicardico', 'bradicardico')
    condicion_medica = Column(String(30), default="normal")

    # Umbrales personalizados para evitar falsas alertas continuas
    # Si es bradicárdico, hr_min_custom será más bajo (ej. 45 BPM)
    # Si es taquicárdico, hr_max_custom será más alto (ej. 110 BPM)
    hr_min_custom = Column(Integer, default=60)
    hr_max_custom = Column(Integer, default=100)


class Telemetria(Base):
    __tablename__ = "telemetria"

    id = Column(Integer, primary_key=True, autoincrement=True)
    usuario_id = Column(String(36), ForeignKey("usuarios.id"), nullable=False)
    frecuencia_cardiaca = Column(Integer, nullable=False)
    presion_sistolica = Column(Integer, nullable=False)
    presion_diastolica = Column(Integer, nullable=False)
    fecha_registro = Column(DateTime(timezone=True), server_default=func.now())


class Alerta(Base):
    __tablename__ = "alertas"

    id = Column(Integer, primary_key=True, autoincrement=True)
    usuario_id = Column(String(36), ForeignKey("usuarios.id"), nullable=False)
    tipo_alerta = Column(String(50), nullable=False)  # Ej: "TAQUICARDIA", "BRADICARDIA"
    descripcion = Column(String(255), nullable=False)
    fecha_alerta = Column(DateTime(timezone=True), server_default=func.now())