import uuid
from fastapi import APIRouter, HTTPException, status
from database import get_db_connection
from models.schemas import UserRegisterRequest, UserResponse, hash_rut

router = APIRouter(tags=["Usuarios"])

@router.post("/users/register", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
def register_user(user: UserRegisterRequest):
    user_id = str(uuid.uuid4())
    rut_protected = hash_rut(user.rut)

    conn = get_db_connection()
    try:
        with conn.cursor() as cursor:
            sql = """
                INSERT INTO usuarios (user_id, nombre, rut_hashed, telefono_emergencia, condition_type)
                VALUES (%s, %s, %s, %s, %s)
            """
            cursor.execute(sql, (
                user_id,
                user.name,
                rut_protected,
                user.emergency_phone,
                user.condition_type
            ))
            conn.commit()

        return {
            "user_id": user_id,
            "name": user.name,
            "rut_hashed": rut_protected,
            "emergency_phone": user.emergency_phone,
            "condition_type": user.condition_type
        }
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"BD Error: {str(e)}"
        )
    finally:
        conn.close()

@router.get("/users")
def get_users():
    conn = get_db_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute("SELECT id, user_id, nombre, rut_hashed, telefono_emergencia, condition_type FROM usuarios ORDER BY id DESC")
            users = cursor.fetchall()
            return {"total": len(users), "users": users}
    finally:
        conn.close()