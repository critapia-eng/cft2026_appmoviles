import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base
from dotenv import load_dotenv

load_dotenv()

# 1. URL Dinámica: Usa SQLite por defecto para desarrollo local
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./salud_app.db")

# 2. Configuración del motor según el tipo de BD
connect_args = {"check_same_thread": False} if "sqlite" in DATABASE_URL else {}

engine = create_engine(
    DATABASE_URL, 
    connect_args=connect_args
)

# 3. Sesión local e interfaz base para modelos
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

# 4. Inyector de sesión para la API
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

if __name__ == "__main__":
    import models
    Base.metadata.create_all(bind=engine)
    print("Tablas 'usuarios', 'telemetria' y 'alertas' creadas correctamente.")