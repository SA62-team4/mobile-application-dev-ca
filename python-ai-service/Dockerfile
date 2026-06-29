# python:3.12-slim is Debian-based but strips non-essential packages, giving us
# a much smaller image than the full python:3.12 image (~50 MB vs ~1 GB).
FROM python:3.12-slim AS runtime
WORKDIR /app

# These two environment variables improve Python behaviour inside containers:
# PYTHONDONTWRITEBYTECODE=1 : stops Python from writing .pyc cache files to disk
# PYTHONUNBUFFERED=1        : forces stdout/stderr to be flushed immediately so
#                             logs appear in real time in `docker logs`
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# build-essential is required to compile native extensions in chromadb and
# some of its dependencies (e.g. hnswlib). We combine all apt steps into one
# RUN instruction to minimise layer count, and clean the cache afterwards.
RUN apt-get update && \
    apt-get install -y --no-install-recommends build-essential && \
    rm -rf /var/lib/apt/lists/*

# Security best practice: run the application as a non-root user.
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup --no-create-home --shell /usr/sbin/nologin appuser

# ChromaDB persists its local vector index under /data/chroma in Compose. Create
# the mount point before dropping privileges so Docker's empty named volume is
# initialized with appuser ownership.
RUN mkdir -p /data/chroma && \
    chown -R appuser:appgroup /data

# Copy and install Python dependencies before copying source code. This way
# the pip install layer is cached and only invalidated when requirements.txt
# changes, not on every source code change.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application source code. --chown ensures the non-root user can read it.
COPY --chown=appuser:appgroup app ./app

# Drop root privileges permanently.
USER appuser

# Document the port. The actual binding is in docker-compose.yml.
EXPOSE 8000

# HEALTHCHECK uses Python's built-in urllib — no extra packages needed.
# --start-period=30s gives FastAPI time to load ChromaDB and its index.
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

# uvicorn is the ASGI server that runs our FastAPI application.
# --host 0.0.0.0 : listen on all network interfaces inside the container
#                  (not just localhost) so Docker can route traffic to it.
# --port 8000    : the port FastAPI listens on.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
