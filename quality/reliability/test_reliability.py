"""Tests de fiabilité (ISO 25010 – fiabilité : tolérance aux pannes, récupérabilité, pas de perte de données).

Pannes réelles provoquées sur la stack docker compose (arrêt / redémarrage de conteneurs).
Prérequis : stack démarrée + données de démo. Lancement : pytest quality/reliability -v
Les tests s'exécutent dans l'ordre du fichier et partagent l'état (offre et candidature créées).
"""
import os
import subprocess
import time
import uuid

import httpx
import pytest

GW = os.environ.get("E2E_GATEWAY", "http://localhost:8080") + "/api/v1"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PW = "Motdepasse123"
RUN = uuid.uuid4().hex[:8]
state = {}
client = httpx.Client(timeout=30)


def compose(*args):
    subprocess.run(["docker", "compose", *args], cwd=ROOT, check=True, capture_output=True)


def login(email, password=PW):
    r = client.post(f"{GW}/auth/login", json={"email": email, "password": password})
    assert r.status_code == 200, r.text
    return {"Authorization": "Bearer " + r.json()["accessToken"]}


def wait_until(fn, timeout=180):
    """Attend que fn() soit vrai : mesure le temps de reprise après une panne."""
    start = time.time()
    while time.time() - start < timeout:
        try:
            if fn():
                return time.time() - start
        except httpx.HTTPError:
            pass
        time.sleep(2)
    pytest.fail(f"pas de reprise en {timeout} s")


@pytest.fixture(scope="module", autouse=True)
def accounts():
    state["cand"] = login("candidat@smartrecruit.test")
    state["rec"] = login("recruteur@smartrecruit.test")
    yield
    compose("start", "ai-service", "application-service", "postgres")  # remet la stack en état même en cas d'échec


def test_service_ia_en_panne_creation_offre_toujours_possible():
    compose("stop", "ai-service")
    job = {"title": f"Ingenieur fiabilite {RUN}", "description": "Offre creee pendant une panne volontaire du service IA.",
           "requiredSkills": ["Java", "Docker"], "experienceLevel": "JUNIOR", "contractType": "CDI", "location": "Tunis"}
    r = client.post(f"{GW}/jobs", headers=state["rec"], json=job)
    assert r.status_code == 201, r.text  # l'analyse IA est best effort : la panne ne bloque pas le métier
    state["job"] = r.json()["id"]


def test_service_ia_en_panne_score_repond_503_propre():
    r = client.get(f"{GW}/applications/match-preview", params={"jobId": state["job"]}, headers=state["cand"])
    assert r.status_code in (502, 503), (r.status_code, r.text)  # erreur explicite, pas de 500 ni de blocage
    assert "trace" not in r.text.lower()  # pas de pile d'exécution exposée


def test_service_ia_en_panne_candidature_enregistree_sans_score():
    r = client.post(f"{GW}/applications", headers=state["cand"], json={"jobId": state["job"]})
    assert r.status_code == 201, r.text
    state["app"] = r.json()["id"]
    assert r.json().get("matchScore") is None


def test_reprise_apres_redemarrage_du_service_ia():
    compose("start", "ai-service")
    delay = wait_until(lambda: client.get(f"{GW}/applications/match-preview", params={"jobId": state["job"]},
                                          headers=state["cand"]).status_code == 200)
    print(f"reprise du score IA en {delay:.0f} s")


def test_transition_interdite_rejetee_sans_modifier_l_etat():
    r = client.patch(f"{GW}/applications/{state['app']}/status", headers=state["rec"], json={"status": "ACCEPTED"})
    assert r.status_code == 400  # RECEIVED -> ACCEPTED interdit
    apps = client.get(f"{GW}/applications", params={"jobId": state["job"]}, headers=state["rec"]).json()
    assert [a["status"] for a in apps if a["id"] == state["app"]] == ["RECEIVED"]


def test_double_candidature_rejetee_pas_de_doublon():
    r = client.post(f"{GW}/applications", headers=state["cand"], json={"jobId": state["job"]})
    assert r.status_code == 409
    apps = client.get(f"{GW}/applications", params={"jobId": state["job"]}, headers=state["rec"]).json()
    assert sum(a["id"] == state["app"] for a in apps) == 1


def test_panne_du_service_candidatures_erreur_rapide_puis_reprise_sans_perte():
    r = client.patch(f"{GW}/applications/{state['app']}/status", headers=state["rec"], json={"status": "IN_REVIEW"})
    assert r.status_code == 200
    compose("stop", "application-service")
    start = time.time()
    r = client.get(f"{GW}/applications/me", headers=state["cand"])
    assert r.status_code >= 500 and time.time() - start < 15  # échec franc et rapide, pas d'attente infinie
    compose("start", "application-service")
    delay = wait_until(lambda: client.get(f"{GW}/applications/me", headers=state["cand"]).status_code == 200)
    mine = {a["id"]: a for a in client.get(f"{GW}/applications/me", headers=state["cand"]).json()}
    assert mine[state["app"]]["status"] == "IN_REVIEW"  # l'état validé avant la panne est conservé
    print(f"reprise du service candidatures en {delay:.0f} s")


def test_redemarrage_base_de_donnees_aucune_perte():
    compose("restart", "postgres")
    delay = wait_until(lambda: client.get(f"{GW}/jobs/{state['job']}", headers=state["rec"]).status_code == 200
                       and client.get(f"{GW}/applications/me", headers=state["cand"]).status_code == 200)
    assert client.get(f"{GW}/jobs/{state['job']}", headers=state["rec"]).json()["title"].endswith(RUN)
    mine = {a["id"]: a for a in client.get(f"{GW}/applications/me", headers=state["cand"]).json()}
    assert mine[state["app"]]["status"] == "IN_REVIEW"
    print(f"reprise après redémarrage de PostgreSQL en {delay:.0f} s")
