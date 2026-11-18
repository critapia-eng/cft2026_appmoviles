import hashlib

# ==========================================
# Funciones Utilitarias y Seguridad
# ==========================================

def hash_rut(rut: str) -> str:
    """
    Limpia y hashea un RUT utilizando SHA-256 para anonimización y protección.
    """
    clean_rut = rut.replace(".", "").replace("-", "").strip().lower()
    return hashlib.sha256(clean_rut.encode()).hexdigest()
