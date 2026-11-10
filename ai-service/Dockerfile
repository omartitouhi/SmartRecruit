FROM python:3.12-slim

WORKDIR /app

# Réseau lent ou instable : timeout long, réessais pip et cache pip conservé même si le build échoue.
COPY requirements.txt .
RUN --mount=type=cache,id=smartrecruit-pip,target=/root/.cache/pip \
    for i in 1 2 3 4 5; do \
      pip install --default-timeout=120 --retries=5 -r requirements.txt && exit 0; \
      echo "pip a échoué (essai $i/5), nouvelle tentative..."; sleep 10; \
    done; exit 1

# pip ne sert plus à l'exécution ; il embarque ses propres urllib3/certifi/msgpack signalés par Trivy (SCA).
RUN python -m pip uninstall -y pip

COPY app ./app

# Sans privilèges, UID fixe (requis par runAsNonRoot sous Kubernetes).
RUN useradd --system --uid 10001 --no-create-home app
USER 10001

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
