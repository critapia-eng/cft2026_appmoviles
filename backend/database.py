import os
import pymysql
from fastapi import HTTPException, status
from dotenv import load_dotenv

load_dotenv()

def get_db_connection():
    try:
        connection = pymysql.connect(
            host=os.getenv("DB_HOST", "localhost"),
            user=os.getenv("DB_USER", "root"),
            password=os.getenv("DB_PASSWORD", ""),
            database=os.getenv("DB_NAME", "cft2026_appmoviles"),
            port=int(os.getenv("DB_PORT", 3306)),
            cursorclass=pymysql.cursors.DictCursor
        )
        return connection
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Error conectando a la base de datos: {str(e)}"
        )

def init_db():
    try:
        host = os.getenv("DB_HOST", "localhost")
        user = os.getenv("DB_USER", "root")
        password = os.getenv("DB_PASSWORD", "")
        db_name = os.getenv("DB_NAME", "cft2026_appmoviles")
        port = int(os.getenv("DB_PORT", 3306))

        conn_init = pymysql.connect(host=host, user=user, password=password, port=port)
        with conn_init.cursor() as c:
            c.execute(f"CREATE DATABASE IF NOT EXISTS `{db_name}`")
        conn_init.close()

        conn = get_db_connection()
        with conn.cursor() as cursor:
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS usuarios (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id VARCHAR(36) UNIQUE,
                    nombre VARCHAR(100) NOT NULL,
                    rut_hashed VARCHAR(64),
                    telefono_emergencia VARCHAR(20),
                    condition_type VARCHAR(100) DEFAULT 'Ritmo Normal (60 - 100 BPM)'
                );
            """)
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS lecturas_ritmo (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id VARCHAR(36) NOT NULL,
                    heart_rate INT NOT NULL,
                    systolic_pressure INT NOT NULL,
                    diastolic_pressure INT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """)
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS alertas_sos (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    user_id VARCHAR(36) NOT NULL,
                    reason VARCHAR(255) NOT NULL,
                    contact_phone VARCHAR(20) NOT NULL,
                    status VARCHAR(20) DEFAULT 'PENDIENTE',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
            """)
            conn.commit()
        conn.close()
        print(">> [FastAPI] Base de datos y tablas inicializadas correctamente.")
    except Exception as e:
        print(f">> [FastAPI] Nota sobre conexión a BD en startup: {e}")