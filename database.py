import os
import mysql.connector
from dotenv import load_dotenv

# Carga las variables de entorno desde el archivo .env (credenciales de la BD)
load_dotenv()

def get_connection():
    """
    Establece y retorna una conexión activa con la base de datos MySQL 
    utilizando los datos de acceso configurados en el archivo .env.
    """
    return mysql.connector.connect(
        host=os.getenv("DB_HOST", "localhost"),
        user=os.getenv("DB_USER", "root"),
        password=os.getenv("DB_PASSWORD", ""),
        database=os.getenv("DB_NAME", "cft2026_appmoviles"),
        port=int(os.getenv("DB_PORT", 3306))
    )

def init_db():
    """
    Inicializa la base de datos comprobando la conexión y creando 
    la tabla 'usuarios' de forma automática si aún no existe.
    """
    try:
        # Abre la conexión y crea el cursor para ejecutar sentencias SQL
        conn = get_connection()
        cursor = conn.cursor()
        
        # 1. Tabla usuarios (con campos user_id, nombre, rut_hashed, telefono_emergencia, condition_type)
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

        # Asegurar columnas si la tabla ya existía previamente con esquema antiguo
        cursor.execute("SHOW COLUMNS FROM usuarios LIKE 'user_id'")
        if not cursor.fetchone():
            cursor.execute("ALTER TABLE usuarios ADD COLUMN user_id VARCHAR(36) UNIQUE")

        cursor.execute("SHOW COLUMNS FROM usuarios LIKE 'rut_hashed'")
        if not cursor.fetchone():
            cursor.execute("ALTER TABLE usuarios ADD COLUMN rut_hashed VARCHAR(64)")

        cursor.execute("SHOW COLUMNS FROM usuarios LIKE 'condition_type'")
        if not cursor.fetchone():
            cursor.execute("ALTER TABLE usuarios ADD COLUMN condition_type VARCHAR(100) DEFAULT 'Ritmo Normal (60 - 100 BPM)'")

        # 2. Tabla lecturas_ritmo
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

        # 3. Tabla alertas_sos
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

        # Confirma los cambios realizados en la base de datos
        conn.commit()
        print("Base de datos conectada y tablas verificadas/actualizadas correctamente.")

    except mysql.connector.Error as err:
        # Captura y muestra cualquier error relacionado con MySQL (conexión, permisos, etc.)
        print(f"Error al conectar con MySQL: {err}")

    finally:
        # Garantiza el cierre del cursor y la conexión para no dejar recursos colgados
        if 'conn' in locals() and conn.is_connected():
            cursor.close()
            conn.close()

# Permite ejecutar directamente este script para probar la base de datos (python database.py)
if __name__ == "__main__":
    init_db()
