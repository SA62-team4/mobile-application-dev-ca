# Small Debian-based Python runtime.
FROM python:3.12-slim AS runtime
WORKDIR /app

# No .pyc files; flush logs immediately.
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# Build ChromaDB native dependencies.
RUN apt-get update && \
    apt-get install -y --no-install-recommends build-essential && \
    rm -rf /var/lib/apt/lists/*

# Non-root runtime user.
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup --no-create-home --shell /usr/sbin/nologin appuser

# Chroma mount owned by appuser.
RUN mkdir -p /data/chroma && \
    chown -R appuser:appgroup /data

# Cache dependency installs.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# App source.
COPY --chown=appuser:appgroup app ./app

# Non-root runtime.
USER appuser

# Compose binds the port.
EXPOSE 8000

# FastAPI health probe.
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

# Run FastAPI.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
