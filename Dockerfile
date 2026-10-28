FROM python:3.11-slim

WORKDIR /app

# Copiar dependencias e instalar
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copiar todo el código del backend
COPY backend/ /app/

# Exponer el puerto de FastAPI
EXPOSE 9000

# Comando para iniciar la API
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "9000"]