import os
import mysql.connector
from dotenv import load_dotenv

load_dotenv()

# ==========================================
# Conexión persistente a MySQL
# ==========================================
def get_db_connection():
    """
    Retorna una conexión activa a MySQL utilizando las variables de entorno.
    """
    return mysql.connector.connect(
        host=os.getenv("DB_HOST", "127.0.0.1"),
        user=os.getenv("DB_USER", "cft2026_appmoviles"),
        password=os.getenv("DB_PASSWORD", ""),
        database=os.getenv("DB_NAME", "cft2026_appmoviles"),
        port=int(os.getenv("DB_PORT", 3306))
    )

# Fallback en memoria si MySQL tuviera alguna desconexión momentánea
users_db = {}
telemetry_db = []
alerts_db = []
