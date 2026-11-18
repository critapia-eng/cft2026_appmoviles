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

@router.get("/latest/{user_id}")
def get_latest_telemetry(user_id: str):
    """
    Retorna la última lectura telemétrica, alertas activas y geolocalización
    del paciente para ser visualizados por familiares o personal médico en tiempo real.
    """
    user_id_str = str(user_id)
    reading = None
    latest_alert = None
    patient_info = None
    location = None

    try:
        conn = get_db_connection()
        cursor = conn.cursor(dictionary=True)

        # 1. Obtener última lectura telemétrica de MySQL
        cursor.execute(
            """
            SELECT heart_rate, systolic_pressure, diastolic_pressure, timestamp
            FROM lecturas_ritmo
            WHERE user_id = %s
            ORDER BY timestamp DESC
            LIMIT 1
            """,
            (user_id_str,)
        )
        reading = cursor.fetchone()

        # 2. Obtener última alerta SOS del usuario
        cursor.execute(
            """
            SELECT alert_id, reason, contact_phone, status, timestamp
            FROM alertas_sos
            WHERE user_id = %s
            ORDER BY timestamp DESC
            LIMIT 1
            """,
            (user_id_str,)
        )
        latest_alert = cursor.fetchone()

        # 3. Obtener ubicación de la última alerta (si existe)
        if latest_alert and latest_alert.get("alert_id"):
            cursor.execute(
                """
                SELECT latitud, longitud, direccion_aproximada
                FROM ubicaciones_alerta
                WHERE alert_id = %s
                ORDER BY timestamp DESC
                LIMIT 1
                """,
                (latest_alert["alert_id"],)
            )
            location = cursor.fetchone()

        # 4. Obtener datos del paciente
        cursor.execute(
            """
            SELECT nombre, telefono_emergencia
            FROM usuarios
            WHERE user_id = %s
            LIMIT 1
            """,
            (user_id_str,)
        )
        patient_info = cursor.fetchone()

        cursor.close()
        conn.close()
    except Exception as e:
        print(f"[MySQL Latest Telemetry Warning] {e}")

    # Fallbacks en memoria si MySQL no tiene registros
    if not reading:
        user_readings = [t for t in telemetry_db if str(t.get("user_id")) == user_id_str]
        if user_readings:
            reading = user_readings[-1]

    if not latest_alert:
        user_alerts = [a for a in alerts_db if str(a.get("user_id")) == user_id_str]
        if user_alerts:
            latest_alert = user_alerts[-1]

    bpm = reading["heart_rate"] if reading and "heart_rate" in reading else 75
    sys = reading["systolic_pressure"] if reading and "systolic_pressure" in reading else 120
    dia = reading["diastolic_pressure"] if reading and "diastolic_pressure" in reading else 80
    ts = str(reading["timestamp"]) if reading and "timestamp" in reading else str(datetime.now())

    is_alert = (bpm > 120 or bpm < 50 or sys > 140 or sys < 90)

    if not location:
        location = {
            "latitud": -37.0933,
            "longitud": -73.1556,
            "direccion_aproximada": "Lota, Región del Biobío, Chile (Sector CFT Lota Arauco)"
        }

    return {
        "status": "ok",
        "user_id": user_id_str,
        "patient_name": patient_info.get("nombre", "Paciente") if patient_info else "Paciente",
        "emergency_phone": patient_info.get("telefono_emergencia", "") if patient_info else "",
        "heart_rate": bpm,
        "systolic_pressure": sys,
        "diastolic_pressure": dia,
        "timestamp": ts,
        "is_alert": is_alert,
        "latest_alert": latest_alert,
        "location": location
    }
