import uuid
from fastapi import APIRouter, HTTPException
from models import UserRegister, UserResponse
from database import get_db_connection, users_db
from utils import hash_rut

router = APIRouter(prefix="/users", tags=["Usuarios"])

@router.get("/by-rut/{rut}", response_model=UserResponse)
def get_user_by_rut(rut: str):
    """
    Verifica si un paciente ya está registrado mediante su RUT.
    Permite el acceso a familiares o médicos únicamente si el paciente existe previamente.
    """
    rut_protected = hash_rut(rut)
    try:
        conn = get_db_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            """
            SELECT user_id, nombre, rut_hashed, telefono_emergencia, condition_type 
            FROM usuarios 
            WHERE rut_hashed = %s 
            LIMIT 1
            """,
            (rut_protected,)
        )
        existing_user = cursor.fetchone()
        cursor.close()
        conn.close()

        if existing_user:
            return {
                "user_id": str(existing_user["user_id"]),
                "name": existing_user.get("nombre", "Paciente"),
                "rut_hashed": rut_protected,
                "emergency_phone": existing_user.get("telefono_emergencia", ""),
                "condition_type": existing_user.get("condition_type", "Ritmo Normal")
            }
    except Exception as e:
        print(f"[MySQL by-rut error] {e}")

    # Fallback en memoria si MySQL estuviera offline
    for uid, udata in users_db.items():
        if udata.get("rut_hashed") == rut_protected:
            return udata

    raise HTTPException(
        status_code=404,
        detail="El paciente no se encuentra registrado en el sistema. El paciente debe registrarse primero antes de que un familiar pueda monitorearlo."
    )

@router.post("/register", response_model=UserResponse)
def register_user(user: UserRegister):
    user_id = str(uuid.uuid4())
    rut_protected = hash_rut(user.rut)
    
    # 1. Guardar o Recuperar en Base de Datos MySQL
    try:
        conn = get_db_connection()
        cursor = conn.cursor(dictionary=True)

        # Verificar si el usuario ya existe por su RUT anonimizado
        cursor.execute(
            "SELECT user_id, nombre, rut_hashed, telefono_emergencia FROM usuarios WHERE rut_hashed = %s LIMIT 1",
            (rut_protected,)
        )
        existing_user = cursor.fetchone()

        if existing_user:
            user_id = str(existing_user["user_id"])
            cursor.close()
            conn.close()
            print(f"[MySQL Success] Usuario existente autenticado por RUT: {existing_user.get('nombre')} ({user_id})")
            return {
                "user_id": user_id,
                "name": existing_user.get("nombre", user.name),
                "rut_hashed": rut_protected,
                "emergency_phone": existing_user.get("telefono_emergencia", user.emergency_phone),
                "condition_type": user.condition_type or "Ritmo Normal"
            }

        cursor.close()
        cursor = conn.cursor()

        # Intento 1: Inserción directa en columnas estándar que existen sí o sí
        try:
            cursor.execute(
                """
                INSERT INTO usuarios (user_id, nombre, rut_hashed, telefono_emergencia)
                VALUES (%s, %s, %s, %s)
                """,
                (user_id, user.name, rut_protected, user.emergency_phone)
            )
        except Exception:
            # Si tiene condition_type
            cursor.execute(
                """
                INSERT INTO usuarios (user_id, nombre, rut_hashed, telefono_emergencia, condition_type)
                VALUES (%s, %s, %s, %s, %s)
                """,
                (user_id, user.name, rut_protected, user.emergency_phone, user.condition_type or "Ritmo Normal")
            )

        # Intento de guardar en contactos_emergencia si la tabla existe
        try:
            cursor.execute(
                """
                INSERT INTO contactos_emergencia (user_id, telefono_familiar)
                VALUES (%s, %s)
                """,
                (user_id, user.emergency_phone)
            )
        except Exception:
            pass

        # Intento seguro de guardar en ficha_medica
        try:
            cursor.execute(
                """
                INSERT INTO ficha_medica (user_id, antecedentes_medicos)
                VALUES (%s, %s)
                """,
                (user_id, user.medical_history if user.medical_history else "Sin antecedentes reportados")
            )
        except Exception as e_fm:
            print(f"[MySQL Ficha Médica Warning] {e_fm}")

        conn.commit()
        cursor.close()
        conn.close()
        print(f"[MySQL Success] Usuario registrado en BD: {user.name} ({user_id})")
    except Exception as e:
        print(f"[MySQL Warning] Fallo guardado en BD, usando memoria: {e}")

    # 2. Respaldo en memoria
    user_data = {
        "user_id": user_id,
        "name": user.name,
        "rut_hashed": rut_protected,
        "emergency_phone": user.emergency_phone,
        "condition_type": user.condition_type
    }
    users_db[user_id] = user_data
    return user_data
