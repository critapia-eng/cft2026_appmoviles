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
        
        # Sentencia SQL para la tabla 'usuarios' (almacena datos del adulto mayor)
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS usuarios (
            id INT AUTO_INCREMENT PRIMARY KEY,
            nombre VARCHAR(100) NOT NULL,
            rut VARCHAR(12) UNIQUE,
            telefono_emergencia VARCHAR(20)
        );
        """)
        
        # Confirma los cambios realizados en la base de datos
        conn.commit()
        print("Base de datos conectada y tabla 'usuarios' verificada.")

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
