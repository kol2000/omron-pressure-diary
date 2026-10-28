FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd -m scanner && mkdir /data && chown scanner /data
COPY *.py ./
COPY static ./static
USER scanner
CMD ["gunicorn", "--bind", "0.0.0.0:8000", "--workers", "1", "--threads", "4", "--timeout", "210", "app:app"]
