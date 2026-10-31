import sys

from fastapi import FastAPI

app = FastAPI(title="edudoc-ai-service")


@app.get("/health")
def health():
    return {"service": "ai-service", "status": "UP"}


@app.get("/smoke")
def smoke():
    return {
        "service": "ai-service",
        "python": sys.version.split()[0],
        "status": "ok",
    }
