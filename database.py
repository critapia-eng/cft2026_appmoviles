import os
import mysql.connector
from dotenv import load_dotenv

load_dotenv()

def get_connection():
    return mysql.connector.connect(
        host=os.getenv("DB_HOST", "localhost"),
        user=os.getenv("DB_USER", "root"),
        password=os.getenv("DB_PASSWORD", ""),
        database=os.getenv("DB_NAME", "cft2026_appmoviles"),
        port=int(os.getenv("DB_PORT", 3306))
    )

def init_db():
    try:
        conn = get_connection()
        cursor = conn.cursor()
        
        # Tabla de prueba para la app de adultos mayores
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS usuarios (
            id INT AUTO_INCREMENT PRIMARY KEY,
            nombre VARCHAR(100) NOT NULL,
            rut VARCHAR(12) UNIQUE,
            telefono_emergencia VARCHAR(20)
        );
        """)
        conn.commit()
        print("Base de datos conectada y tabla 'usuarios' verificada.")
    except mysql.connector.Error as err:
        print(f"Error al conectar con MySQL: {err}")
    finally:
        if 'conn' in locals() and conn.is_connected():
            cursor.close()
            conn.close()

if __name__ == "__main__":
    init_db()
