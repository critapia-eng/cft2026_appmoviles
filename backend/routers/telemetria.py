from fastapi import APIRouter, HTTPException
from database import get_db_connection
from models.schemas import VitalSigns

router = APIRouter(tags=["Telemetría y Alertas"])

@router.post("/telemetry/send")
def receive_telemetry(data: VitalSigns):
    conn = get_db_connection()
    user_str_id = str(data.user_id)
    alert_triggered = False
    alert_message = "Signos vitales dentro del rango normal."

    try:
        with conn.cursor() as cursor:
            cursor.execute("SELECT telefono_emergencia AS emergency_phone, condition_type FROM usuarios WHERE user_id = %s", (user_str_id,))
            user = cursor.fetchone()

            if not user:
                raise HTTPException(status_code=404, detail="Usuario no encontrado")

            emergency_phone = user.get("emergency_phone", "Sin contacto")
            condition = user.get("condition_type", "Ritmo Normal")

            sql_telemetry = """
                INSERT INTO lecturas_ritmo (user_id, heart_rate, systolic_pressure, diastolic_pressure)
                VALUES (%s, %s, %s, %s)
            """
            cursor.execute(sql_telemetry, (user_str_id, data.heart_rate, data.systolic_pressure, data.diastolic_pressure))

            if "Bradicardia" in condition:
                if data.heart_rate < 40 or data.heart_rate > 120:
                    alert_triggered = True
                    alert_message = f"ALERTA SOS: Ritmo crítico detectado ({data.heart_rate} BPM) para paciente bradicárdico."
            elif "Taquicardia" in condition:
                if data.heart_rate > 150:
                    alert_triggered = True
                    alert_message = f"ALERTA SOS: Ritmo muy elevado ({data.heart_rate} BPM) para paciente taquicárdico."
            else:
                if data.heart_rate < 50 or data.heart_rate > 130:
                    alert_triggered = True
                    alert_message = f"ALERTA SOS: Ritmo fuera de rango normal ({data.heart_rate} BPM)."

            if alert_triggered:
                sql_alert = """
                    INSERT INTO alertas_sos (user_id, reason, contact_phone, status)
                    VALUES (%s, %s, %s, 'PENDIENTE')
                """
                cursor.execute(sql_alert, (user_str_id, alert_message, emergency_phone))

            conn.commit()

        return {
            "alert_triggered": alert_triggered,
            "message": alert_message,
            "emergency_contact": emergency_phone
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error procesando telemetría: {str(e)}")
    finally:
        conn.close()

@router.get("/alerts")
def get_alerts():
    conn = get_db_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute("SELECT * FROM alertas_sos ORDER BY id DESC")
            alerts = cursor.fetchall()
            return {"total": len(alerts), "alerts": alerts}
    finally:
        conn.close()

@router.get("/telemetry")
def get_telemetry():
    conn = get_db_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute("SELECT * FROM lecturas_ritmo ORDER BY id DESC")
            readings = cursor.fetchall()
            return {"total": len(readings), "readings": readings}
    finally:
        conn.close()