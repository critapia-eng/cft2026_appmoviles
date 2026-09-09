import requests
import time

BASE_URL = "http://127.0.0.1:8000"

def ejecutar_simulacion():
    print("=== INICIANDO SIMULACIÓN DE PRUEBAS ===")
    
    # 1. Registro de usuario
    res_user = requests.post(f"{BASE_URL}/users/register", json={
        "name": "Paciente Prueba", 
        "rut": "12.345.678-9", 
        "emergency_phone": "+56912345678"
    })
    
    if res_user.status_code != 200:
        print("Error en registro:", res_user.text)
        return

    user = res_user.json()
    user_id = user["user_id"]
    print(f"Usuario registrado -> ID: {user_id} | RUT Cifrado: {user['rut_hashed']}\n")

    # 2. Mediciones simuladas
    mediciones = [
        {"sys": 120, "dia": 80, "hr": 72, "desc": "Normal"},
        {"sys": 150, "dia": 95, "hr": 80, "desc": "Presión Alta"},
        {"sys": 118, "dia": 75, "hr": 130, "desc": "Taquicardia"}
    ]

    for m in mediciones:
        print(f"Enviando medición ({m['desc']})...")
        res_tel = requests.post(f"{BASE_URL}/telemetry/send", json={
            "user_id": user_id, 
            "heart_rate": m["hr"], 
            "systolic_pressure": m["sys"], 
            "diastolic_pressure": m["dia"]
        })
        print(f"Respuesta: {res_tel.json()['message']}\n")
        time.sleep(1)

    # 3. Historial de Alertas
    res_alerts = requests.get(f"{BASE_URL}/alerts")
    print(f"Total de alertas registradas: {len(res_alerts.json()['alerts'])}")

if __name__ == "__main__":
    ejecutar_simulacion()