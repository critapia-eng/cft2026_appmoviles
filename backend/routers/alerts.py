from fastapi import APIRouter
from database import get_db_connection, alerts_db

router = APIRouter(tags=["Alertas"])

@router.get("/alerts")
def get_alerts():
    # Consultar alertas reales desde MySQL
    try:
        conn = get_db_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute("SELECT alert_id, user_id, reason, contact_phone, status, timestamp FROM alertas_sos ORDER BY timestamp DESC")
        rows = cursor.fetchall()
        cursor.close()
        conn.close()
        return {"alerts": rows}
    except Exception as e:
        print(f"[MySQL Alerts Error] {e}")
        return {"alerts": alerts_db}

@router.get("/alerts/{user_id}")
def get_alerts_by_user(user_id: str):
    try:
        conn = get_db_connection()
        cursor = conn.cursor(dictionary=True)
        cursor.execute(
            """
            SELECT alert_id, user_id, reason, contact_phone, status, timestamp 
            FROM alertas_sos 
            WHERE user_id = %s 
            ORDER BY timestamp DESC
            """, 
            (str(user_id),)
        )
        rows = cursor.fetchall()
        cursor.close()
        conn.close()
        return {
            "status": "ok",
            "count": len(rows),
            "alerts": rows
        }
    except Exception as e:
        print(f"[MySQL Alerts By User Error] {e}")
        user_alerts = [a for a in alerts_db if str(a.get("user_id")) == str(user_id)]
        return {
            "status": "ok",
            "count": len(user_alerts),
            "alerts": user_alerts
        }
