"""Vérifie que toutes les briques portent la même version (BE-OPS-05).

Sources : pom.xml des 6 services, package.json, service IA, appVersion du chart Helm,
tags d'images de l'overlay prod et de envs/prod.tfvars. Sur un tag git vX.Y.Z, la version doit valoir X.Y.Z.
Usage : python deploy/scripts/check_versions.py [vX.Y.Z]
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICES = ["api-gateway", "auth-service", "user-service", "job-service", "application-service", "dashboard-service"]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def found(path: str, pattern: str) -> list[str]:
    values = re.findall(pattern, read(path), re.MULTILINE)
    if not values:
        sys.exit(f"Version introuvable dans {path}")
    return values


versions: dict[str, list[str]] = {}
for s in SERVICES:
    # Version du projet : la première <version> après </parent>.
    pom = read(f"backend/{s}/pom.xml").split("</parent>", 1)[1]
    versions[f"backend/{s}/pom.xml"] = re.findall(r"<version>([^<]+)</version>", pom)[:1]
versions["frontend/package.json"] = [json.loads(read("frontend/package.json"))["version"]]
versions["ai-service/app/main.py"] = found("ai-service/app/main.py", r'^\s*version="([^"]+)"')
versions["deploy/helm/smartrecruit/Chart.yaml"] = found("deploy/helm/smartrecruit/Chart.yaml", r'^appVersion:\s*"?([^"\s]+)')
versions["deploy/k8s/overlays/prod/kustomization.yaml"] = found(
    "deploy/k8s/overlays/prod/kustomization.yaml", r'^\s*newTag:\s*"?([^"\s]+)')
versions["deploy/terraform/envs/prod.tfvars"] = found("deploy/terraform/envs/prod.tfvars", r'^image_tag\s*=\s*"([^"]+)"')

expected = sys.argv[1].removeprefix("v") if len(sys.argv) > 1 else versions["frontend/package.json"][0]
errors = [f"{path}: {v} (attendu {expected})" for path, vs in versions.items() for v in vs if v != expected]
if errors:
    sys.exit("Versions non alignées :\n  " + "\n  ".join(errors))
print(f"Version {expected} alignée sur {len(versions)} fichiers.")
