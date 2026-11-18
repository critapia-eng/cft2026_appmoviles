import uuid
from datetime import datetime
from fastapi import APIRouter
from models import VitalSigns
from database import get_db_connection, users_db, telemetry_db, alerts_db

router = APIRouter(prefix="/telemetry", tags=["Telemetría"])

@router.post("/send")
def receive_telemetry(data: VitalSigns):
    user_id_str = str(data.user_id)
    timestamp = datetime.now()

    # Evaluación clínica de peligro
    is_high_pressure = data.systolic_pressure > 140
    is_low_pressure = data.systolic_pressure < 90
    is_anomalous_hr = data.heart_rate > 120 or data.heart_rate < 50
    alert_triggered = is_high_pressure or is_low_pressure or is_anomalous_hr

    alert = None
    contact_phone = "+56900000000"

    # 1. Guardar en MySQL
    try:
        conn = get_db_connection()
        cursor = conn.cursor(dictionary=True)

        # Buscar teléfono de emergencia del usuario
        try:
            cursor.execute("SELECT telefono_emergencia FROM usuarios WHERE user_id = %s", (user_id_str,))
            row_u = cursor.fetchone()
            if row_u and row_u.get("telefono_emergencia"):
                contact_phone = row_u["telefono_emergencia"]
        except Exception:
            pass

        # Insertar en lecturas_ritmo
        try:
            cursor.execute(
                """
                INSERT INTO lecturas_ritmo (user_id, heart_rate, systolic_pressure, diastolic_pressure)
                VALUES (%s, %s, %s, %s)
                """,
                (user_id_str, data.heart_rate, data.systolic_pressure, data.diastolic_pressure)
            )
        except Exception as err_ritmo:
            print(f"[MySQL Lecturas Error] {err_ritmo}")

        # Si hubo anomalía, insertar en alertas_sos
        if alert_triggered:
            alert_id = str(uuid.uuid4())
            reason_text = f"Sistólica: {data.systolic_pressure}, BPM: {data.heart_rate}"
            try:
                cursor.execute(
                    """
                    INSERT INTO alertas_sos (alert_id, user_id, reason, contact_phone, status)
                    VALUES (%s, %s, %s, %s, %s)
                    """,
                    (alert_id, user_id_str, reason_text, contact_phone, "LLAMADA_A_HOSPITAL_INICIADA")
                )
            except Exception as err_al:
                print(f"[MySQL Alerta SOS Error] {err_al}")

            # Guardar coordenadas de la emergencia en ubicaciones_alerta (Lota, Biobío)
            try:
                cursor.execute(
                    """
                    INSERT INTO ubicaciones_alerta (alert_id, user_id, latitud, longitud, direccion_aproximada)
                    VALUES (%s, %s, %s, %s, %s)
                    """,
                    (alert_id, user_id_str, -37.0933, -73.1556, "Lota, Región del Biobío, Chile (Sector CFT Lota Arauco)")
                )
            except Exception as err_loc:
                print(f"[MySQL Ubicaciones Alerta Error] {err_loc}")

            alert = {
                "alert_id": alert_id,
                "user_id": user_id_str,
                "timestamp": timestamp,
                "reason": reason_text,
                "contact_phone": contact_phone,
                "status": "LLAMADA_A_HOSPITAL_INICIADA"
            }

        conn.commit()
        cursor.close()
        conn.close()
    except Exception as e:
        print(f"[MySQL Telemetry Warning] {e}")

    # 2. Respaldo en memoria
    telemetry_data = data.model_dump() if hasattr(data, "model_dump") else data.dict()
    telemetry_db.append({**telemetry_data, "timestamp": timestamp})

    if alert_triggered:
        if not alert:
            alert = {
                "alert_id": str(uuid.uuid4()),
                "user_id": user_id_str,
                "timestamp": timestamp,
                "reason": f"Sistólica: {data.systolic_pressure}, BPM: {data.heart_rate}",
                "contact_phone": contact_phone,
                "status": "LLAMADA_A_HOSPITAL_INICIADA"
            }
        alerts_db.append(alert)
        return {
            "alert_triggered": True,
            "message": "ANOMALÍA DETECTADA: Alerta enviada al hospital.",
            "alert_details": alert
        }

    return {"alert_triggered": False, "message": "Signos vitales estables."}
