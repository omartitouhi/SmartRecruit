# -*- coding: utf-8 -*-
"""Test de bout en bout de toute la plateforme via le gateway et le frontend (nginx).

Usage : python backend/e2e/e2e_full.py                   (docker compose, ports par défaut)
        E2E_TARGET=k8s python backend/e2e/e2e_full.py    (kind : les logs sont lus avec kubectl)
Prérequis : pip install -r backend/e2e/requirements.txt, et la stack démarrée avec OPENROUTER_API_KEY.
"""
import io, os, re, subprocess, sys, time, uuid
import httpx
from reportlab.pdfgen import canvas

GW = os.environ.get("E2E_GATEWAY", "http://localhost:8080") + "/api/v1"
WEB = os.environ.get("E2E_WEB", "http://localhost:5173") + "/api/v1"
TARGET = os.environ.get("E2E_TARGET", "compose")
# Racine du monorepo (docker-compose.yml) : backend/e2e/ -> ../..
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "admin@smartrecruit.local")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "Admin-Dev-2026!")
PW = "Motdepasse123"


def make_cv():
    lines = ["Sami Ben Ali", "Ingenieur logiciel - Tunis", "", "Competences", "Java, Spring Boot, PostgreSQL, Docker, React, Git", "",
             "Experience professionnelle", "Backend Developer - Acme Tech (2022-01 - Present)", "Developpement de microservices Spring Boot et API REST",
             "Stagiaire Java - Beta Soft (2021-06 - 2021-09)", "", "Formation", "Master Genie Logiciel - ESPRIT, 2025",
             "Licence Informatique - ISI, 2022", "", "Langues", "Francais, Anglais"]
    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer)
    for i, line in enumerate(lines):
        pdf.drawString(60, 800 - 20 * i, line)
    pdf.save()
    return buffer.getvalue()


CV_BYTES = make_cv()


def user_service_logs():
    if TARGET == "k8s":
        cmd = ["kubectl", "-n", "smartrecruit", "logs", "deploy/user-service", "--tail=400"]
    else:
        cmd = ["docker", "compose", "logs", "--tail", "400", "user-service"]
    return subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace").stdout


c = httpx.Client(timeout=90)
ok = fail = 0
failures = []


def check(label, cond, detail=""):
    global ok, fail
    if cond:
        ok += 1
        print(f"  OK   {label}")
    else:
        fail += 1
        failures.append(label)
        print(f"  FAIL {label}  {str(detail)[:200]}")


def section(name):
    print(f"\n== {name}")


def tag():
    return uuid.uuid4().hex[:6]


def register(role, label):
    email = f"{label}-{tag()}@e2e.test"
    r = c.post(f"{GW}/auth/register", json={"email": email, "password": PW, "role": role})
    assert r.status_code == 201, (label, r.status_code, r.text)
    return email, r.json()["id"], login(email)


def login(email, password=PW):
    r = c.post(f"{GW}/auth/login", json={"email": email, "password": password})
    return {"Authorization": "Bearer " + r.json()["accessToken"]} if r.status_code == 200 else None


def candidate_with_cv(label):
    email, cid, H = register("CANDIDATE", label)
    c.put(f"{GW}/users/candidates/me", headers=H, json={"firstName": "Sami", "lastName": label, "email": email, "phone": "+216 20 000 000",
          "location": "Tunis", "skills": ["Java"], "experiences": [], "education": [], "languages": ["Francais"]})
    c.post(f"{GW}/users/candidates/me/cv", headers=H, files={"file": ("cv.pdf", io.BytesIO(CV_BYTES), "application/pdf")})
    for _ in range(40):
        p = c.get(f"{GW}/users/candidates/me", headers=H).json()
        if p.get("cvAnalysisStatus") in ("DONE", "FAILED") or p.get("cvParsedAt"):
            break
        time.sleep(2)
    return email, cid, H, p


RUN = uuid.uuid4().hex[:8]  # rend les filtres de recherche uniques d'une exécution à l'autre
JOB = {"title": f"Developpeur Java Backend {RUN}", "description": "Nous recherchons un developpeur Java confirme (3 a 5 ans) maitrisant Spring Boot, PostgreSQL, Docker et Kubernetes.",
       "requiredSkills": ["Java", "Spring Boot", "PostgreSQL", "Docker", "Kubernetes", f"Skill{RUN}"], "experienceLevel": "INTERMEDIATE", "contractType": "CDI", "location": "Tunis"}

# ---------------------------------------------------------------- comptes
section("Comptes")
admin_email, admin_pw = ADMIN_EMAIL, ADMIN_PASSWORD
HA = login(admin_email, admin_pw)
check("administrateur de demarrage peut se connecter", HA is not None)
r1_email, r1_id, H1 = register("RECRUITER", "rec1")
r2_email, r2_id, H2 = register("RECRUITER", "rec2")
r3_email, r3_id, H3 = register("RECRUITER", "rec3")
r4_email, r4_id, H4 = register("RECRUITER", "rec4")
c1_email, c1_id, HC1, c1_profile = candidate_with_cv("cand1")
check("CV du candidat analyse (cvAnalysisStatus=DONE)", c1_profile.get("cvAnalysisStatus") == "DONE", c1_profile.get("cvAnalysisStatus"))
r = c.get(f"{GW}/users/candidates/me/cv-suggestions", headers=HC1)
check("suggestions du CV (200, competences + dates YYYY-MM)", r.status_code == 200 and "Java" in r.json().get("skills", []), r.text[:120])
if r.status_code == 200 and r.json().get("experiences"):
    check("dates des suggestions au format YYYY-MM", re.fullmatch(r"\d{4}-\d{2}", r.json()["experiences"][0]["startDate"]) is not None, r.json()["experiences"][0])

# ---------------------------------------------------------------- entreprises
section("Entreprises (BE-COMP)")
co = {"name": "Acme Tech", "sector": "Logiciel", "description": "Editeur de logiciels", "logoUrl": None}
r = c.post(f"{GW}/users/companies", headers=H1, json=co)
check("recruteur cree son entreprise (201)", r.status_code == 201, r.text[:150])
co1 = r.json() if r.status_code == 201 else {}
check("le createur est premier membre", r1_id in co1.get("members", []), co1)
check("seconde entreprise du meme recruteur refusee (409)", c.post(f"{GW}/users/companies", headers=H1, json=co).status_code == 409)
check("un candidat ne peut pas creer d'entreprise (403)", c.post(f"{GW}/users/companies", headers=HC1, json=co).status_code == 403)
check("creation avec champs manquants (400)", c.post(f"{GW}/users/companies", headers=H4, json={"name": ""}).status_code == 400)
check("GET /companies/me (200)", c.get(f"{GW}/users/companies/me", headers=H1).status_code == 200)
check("GET /companies/me sans entreprise (404)", c.get(f"{GW}/users/companies/me", headers=H4).status_code == 404)
check("vue publique d'une entreprise (candidat, 200)", c.get(f"{GW}/users/companies/{co1.get('id')}", headers=HC1).status_code == 200)
r = c.patch(f"{GW}/users/companies/{co1.get('id')}", headers=H1, json={"description": "Nouvelle description"})
check("modification par un membre (200)", r.status_code == 200 and r.json().get("description") == "Nouvelle description", r.text[:120])
r2 = c.post(f"{GW}/users/companies", headers=H2, json={"name": "Beta Corp", "sector": "Conseil", "description": "Conseil"})
co2 = r2.json() if r2.status_code == 201 else {}
check("modification par un non-membre (403)", c.patch(f"{GW}/users/companies/{co1.get('id')}", headers=H2, json={"name": "Hack"}).status_code == 403)

# invitation
r = c.post(f"{GW}/users/companies/{co1.get('id')}/invitations", headers=H1, json={"email": r3_email})
check("invitation creee (201, PENDING)", r.status_code == 201 and r.json().get("status") == "PENDING", r.text[:150])
check("le jeton n'est pas expose par l'API", "token" not in r.text.lower() or r.json().get("token") is None, r.text[:150])
check("invitation en doublon (409)", c.post(f"{GW}/users/companies/{co1.get('id')}/invitations", headers=H1, json={"email": r3_email}).status_code == 409)
check("invitation par un non-membre (403)", c.post(f"{GW}/users/companies/{co1.get('id')}/invitations", headers=H2, json={"email": "x@e2e.test"}).status_code == 403)
check("liste des invitations (200)", c.get(f"{GW}/users/companies/{co1.get('id')}/invitations", headers=H1).status_code == 200)
logs = user_service_logs()
m = re.findall(r"[Tt]oken[=: ]+([A-Za-z0-9_\-]{20,})", logs)
token = m[-1] if m else None
check("jeton d'invitation retrouve dans les logs (mode dev)", token is not None)
if token:
    check("acceptation avec un faux jeton (400/404/410)", c.post(f"{GW}/users/companies/invitations/accept", headers=H3, json={"token": "x" * 40}).status_code in (400, 404, 410))
    r = c.post(f"{GW}/users/companies/invitations/accept", headers=H3, json={"token": token})
    check("invitation acceptee, le recruteur est rattache (200)", r.status_code == 200 and r3_id in r.json().get("members", []), r.text[:150])
    check("le membre invite voit l'entreprise (GET me)", c.get(f"{GW}/users/companies/me", headers=H3).status_code == 200)

# ---------------------------------------------------------------- offres
section("Offres (BE-JOB)")
check("publication sans entreprise (403)", c.post(f"{GW}/jobs", headers=H4, json=JOB).status_code == 403)
check("publication par un candidat (403)", c.post(f"{GW}/jobs", headers=HC1, json=JOB).status_code == 403)
r = c.post(f"{GW}/jobs", headers=H1, json={**JOB, "title": ""})
check("publication invalide (400 + fieldErrors)", r.status_code == 400 and "fieldErrors" in r.text, r.text[:150])
r = c.post(f"{GW}/jobs", headers=H1, json=JOB)
check("publication (201, PUBLISHED)", r.status_code == 201 and r.json().get("status") == "PUBLISHED", r.text[:150])
job = r.json() if r.status_code == 201 else {}
job_id = job.get("id")
check("l'offre est liee a l'entreprise du recruteur", job.get("companyId") == co1.get("id"), job)
r = c.post(f"{GW}/jobs", headers=H3, json={**JOB, "title": f"Data Engineer Python {RUN}", "requiredSkills": ["Python", "Spark"], "location": f"Sfax-{RUN}", "contractType": "CDD"})
job2_id = r.json().get("id") if r.status_code == 201 else None
check("un autre membre de l'entreprise peut publier (201)", r.status_code == 201, r.text[:120])
time.sleep(4)  # analyse IA asynchrone apres publication
r = c.get(f"{GW}/jobs", headers=HC1)
check("recherche candidat (200, paginee)", r.status_code == 200 and "content" in r.json() and r.json().get("totalElements", 0) >= 2, r.text[:150])
r = c.get(f"{GW}/jobs", headers=HC1, params={"keyword": f"Python {RUN}"})
check("filtre keyword", r.status_code == 200 and all("Python" in (j["title"] + j["description"]) for j in r.json()["content"]) and len(r.json()["content"]) == 1, r.text[:150])
r = c.get(f"{GW}/jobs", headers=HC1, params={"location": f"Sfax-{RUN}"})
check("filtre location", r.status_code == 200 and len(r.json()["content"]) == 1)
r = c.get(f"{GW}/jobs", headers=HC1, params={"skills": f"Skill{RUN}"})
check("filtre skills", r.status_code == 200 and len(r.json()["content"]) == 1)
r = c.get(f"{GW}/jobs", headers=HC1, params={"contractType": "CDD", "keyword": RUN})
check("filtre contractType", r.status_code == 200 and len(r.json()["content"]) == 1)
r = c.get(f"{GW}/jobs", headers=HC1)
check("offres recentes d'abord", r.status_code == 200 and r.json()["content"][0]["id"] == job2_id, [j["title"] for j in r.json()["content"]])
check("nom de l'entreprise dans les resultats", r.json()["content"][0].get("companyName") == "Acme Tech", r.json()["content"][0])
check("detail d'une offre (200)", c.get(f"{GW}/jobs/{job_id}", headers=HC1).status_code == 200)
check("modification par le proprietaire (200)", c.patch(f"{GW}/jobs/{job_id}", headers=H1, json={"location": "Ariana"}).status_code == 200)
check("modification par une autre entreprise (403)", c.patch(f"{GW}/jobs/{job_id}", headers=H2, json={"location": "Hack"}).status_code == 403)
r = c.get(f"{GW}/jobs", headers=H1, params={"companyId": "me"})
check("mes offres (companyId=me) avec compteur de candidatures", r.status_code == 200 and all("applicationsCount" in j for j in r.json()["content"]) and len(r.json()["content"]) == 2, r.text[:200])
check("transition de statut interdite (400)", c.patch(f"{GW}/jobs/{job_id}/status", headers=H1, json={"status": "DRAFT"}).status_code == 400)

# ---------------------------------------------------------------- candidatures et matching
section("Candidatures et matching (BE-APP, BE-MATCH)")
r = c.get(f"{GW}/applications/match-preview", headers=HC1, params={"jobId": job_id})
check("apercu du score via le backend (200, score + criteres)", r.status_code == 200 and "score" in r.json() and len(r.json().get("criteria", [])) == 3, r.text[:150])
t0 = time.time()
r = c.get(f"{GW}/applications/match-preview", headers=HC1, params={"jobId": job_id})
check("apercu en cache (reponse rapide)", r.status_code == 200 and time.time() - t0 < 3, f"{time.time()-t0:.1f}s")
c2_email, c2_id, HC2 = register("CANDIDATE", "nocv")
check("candidature sans profil/CV (422)", c.post(f"{GW}/applications", headers=HC2, json={"jobId": job_id}).status_code == 422)
check("candidature par un recruteur (403)", c.post(f"{GW}/applications", headers=H1, json={"jobId": job_id}).status_code == 403)
check("candidature sur une offre inconnue (404)", c.post(f"{GW}/applications", headers=HC1, json={"jobId": str(uuid.uuid4())}).status_code == 404)
r = c.post(f"{GW}/applications", headers=HC1, json={"jobId": job_id})
check("candidature (201, RECEIVED)", r.status_code == 201 and r.json().get("status") == "RECEIVED", r.text[:150])
app = r.json() if r.status_code == 201 else {}
app_id = app.get("id")
check("repostuler (409)", c.post(f"{GW}/applications", headers=HC1, json={"jobId": job_id}).status_code == 409)
for _ in range(30):
    mine = c.get(f"{GW}/applications/me", headers=HC1).json()
    if mine and mine[0].get("matchScore"):
        break
    time.sleep(2)
check("score IA associe a la candidature en arriere-plan", bool(mine and mine[0].get("matchScore")), mine[:1])
c3_email, c3_id, HC3, _ = candidate_with_cv("cand3")
r = c.post(f"{GW}/applications", headers=HC3, json={"jobId": job_id})
check("seconde candidature (201)", r.status_code == 201)
app3_id = r.json().get("id") if r.status_code == 201 else None
time.sleep(1)
for _ in range(30):
    lst = c.get(f"{GW}/applications", headers=H1, params={"jobId": job_id}).json()
    if len(lst) == 2 and all(a.get("matchScore") for a in lst):
        break
    time.sleep(2)
r = c.get(f"{GW}/applications", headers=H1, params={"jobId": job_id})
scores = [a["matchScore"]["score"] for a in r.json() if a.get("matchScore")]
check("candidatures recues triees par score decroissant", r.status_code == 200 and scores == sorted(scores, reverse=True) and len(scores) == 2, scores)
check("candidatures recues par une autre entreprise (403)", c.get(f"{GW}/applications", headers=H2, params={"jobId": job_id}).status_code == 403)
check("candidatures recues par un candidat (403)", c.get(f"{GW}/applications", headers=HC1, params={"jobId": job_id}).status_code == 403)
check("changement de statut IN_REVIEW (200 + historique)", (lambda x: x.status_code == 200 and x.json()["status"] == "IN_REVIEW" and len(x.json()["statusHistory"]) == 2)(c.patch(f"{GW}/applications/{app_id}/status", headers=H1, json={"status": "IN_REVIEW"})))
check("transition interdite RECEIVED->ACCEPTED (400)", c.patch(f"{GW}/applications/{app3_id}/status", headers=H1, json={"status": "ACCEPTED"}).status_code == 400)
check("statut WITHDRAWN interdit au recruteur (400/403)", c.patch(f"{GW}/applications/{app3_id}/status", headers=H1, json={"status": "WITHDRAWN"}).status_code in (400, 403))
check("changement de statut par une autre entreprise (403)", c.patch(f"{GW}/applications/{app_id}/status", headers=H2, json={"status": "INTERVIEW"}).status_code == 403)
check("INTERVIEW puis ACCEPTED", c.patch(f"{GW}/applications/{app_id}/status", headers=H1, json={"status": "INTERVIEW"}).status_code == 200 and c.patch(f"{GW}/applications/{app_id}/status", headers=H1, json={"status": "ACCEPTED"}).status_code == 200)
check("retrait d'une candidature acceptee refuse (409/400)", c.delete(f"{GW}/applications/{app_id}", headers=HC1).status_code in (400, 409))
check("retrait par un autre candidat (403/404)", c.delete(f"{GW}/applications/{app3_id}", headers=HC1).status_code in (403, 404))
check("retrait de sa candidature (204)", c.delete(f"{GW}/applications/{app3_id}", headers=HC3).status_code == 204)
r = c.get(f"{GW}/applications", headers=H1, params={"jobId": job_id})
check("candidature retiree absente de la liste du recruteur", r.status_code == 200 and all(a["id"] != app3_id for a in r.json()), r.text[:100])
r = c.get(f"{GW}/jobs", headers=H1, params={"companyId": "me"})
check("compteur de candidatures reel (1 active)", r.status_code == 200 and any(j["id"] == job_id and j["applicationsCount"] == 1 for j in r.json()["content"]), [(j["title"], j.get("applicationsCount")) for j in r.json()["content"]])
check("recommandations du candidat (200)", c.get(f"{GW}/jobs/recommendations", headers=HC1).status_code == 200)

# ---------------------------------------------------------------- cloture d'offre
section("Cloture d'une offre")
check("cloture (200)", c.patch(f"{GW}/jobs/{job2_id}/status", headers=H3, json={"status": "CLOSED"}).status_code == 200)
r = c.get(f"{GW}/jobs", headers=HC1)
check("offre cloturee absente de la recherche candidat", all(j["id"] != job2_id for j in r.json()["content"]))
check("offre cloturee introuvable pour un candidat (404)", c.get(f"{GW}/jobs/{job2_id}", headers=HC1).status_code == 404)
check("offre cloturee visible cote recruteur", any(j["id"] == job2_id for j in c.get(f"{GW}/jobs", headers=H1, params={"companyId": "me"}).json()["content"]))
check("candidature sur une offre cloturee (404/409)", c.post(f"{GW}/applications", headers=HC1, json={"jobId": job2_id}).status_code in (404, 409))
check("reouverture (200)", c.patch(f"{GW}/jobs/{job2_id}/status", headers=H3, json={"status": "PUBLISHED"}).status_code == 200)

# ---------------------------------------------------------------- dashboard et administration
section("Tableau de bord et administration (BE-DASH, BE-ADMIN)")
check("stats globales refusees aux non-admins (403)", c.get(f"{GW}/dashboard/stats/global", headers=H1).status_code == 403 and c.get(f"{GW}/dashboard/stats/global", headers=HC1).status_code == 403)
r = c.get(f"{GW}/dashboard/stats/global", headers=HA)
check("stats globales (admin, candidatures retirees exclues)", r.status_code == 200 and r.json().get("totalApplications", 0) >= 1 and r.json().get("activeJobs", 0) >= 2 and r.json().get("totalUsers", 0) >= 8, r.text[:250])
r = c.get(f"{GW}/dashboard/stats/company/{co1.get('id')}", headers=H1)
check("stats de mon entreprise", r.status_code == 200 and r.json().get("publishedJobs", 0) >= 2 and r.json().get("applicationsReceived", 0) >= 1, r.text[:250])
check("taux d'acceptation calcule", r.status_code == 200 and r.json().get("acceptanceRate") is not None, r.text[:200])
check("stats d'une autre entreprise refusees (403)", c.get(f"{GW}/dashboard/stats/company/{co1.get('id')}", headers=H2).status_code == 403)
check("stats entreprise par un admin (200)", c.get(f"{GW}/dashboard/stats/company/{co1.get('id')}", headers=HA).status_code == 200)
r = c.get(f"{GW}/dashboard/stats/company/{co1.get('id')}", headers=H1, params={"from": "2000-01-01", "to": "2000-12-31"})
check("filtre par periode : aucune candidature en 2000", r.status_code == 200 and r.json().get("applicationsReceived") == 0 and r.json().get("publishedJobs", 0) >= 2, r.text[:200])
check("filtre par periode incoherent (400)", c.get(f"{GW}/dashboard/stats/company/{co1.get('id')}", headers=H1, params={"from": "2026-12-01", "to": "2026-01-01"}).status_code == 400)
r = c.get(f"{GW}/dashboard/stats/global", headers=HA, params={"from": "2000-01-01", "to": "2000-12-31"})
check("stats globales filtrees par periode", r.status_code == 200 and r.json().get("totalApplications") == 0 and r.json().get("totalUsers", 0) >= 8, r.text[:200])
r = c.get(f"{GW}/dashboard/admin/users", headers=HA, params={"role": "CANDIDATE", "size": 5})
check("liste des utilisateurs filtree par role", r.status_code == 200 and all(u["role"] == "CANDIDATE" for u in r.json()["content"]) and len(r.json()["content"]) > 0, r.text[:150])
check("liste des utilisateurs refusee aux non-admins (403)", c.get(f"{GW}/dashboard/admin/users", headers=H1).status_code == 403)
check("desactivation d'un utilisateur (200)", c.patch(f"{GW}/dashboard/admin/users/{c2_id}/status", headers=HA, json={"status": "DISABLED"}).status_code == 200)
check("un utilisateur desactive ne peut plus se connecter", login(c2_email) is None)
check("reactivation puis connexion", c.patch(f"{GW}/dashboard/admin/users/{c2_id}/status", headers=HA, json={"status": "ACTIVE"}).status_code == 200 and login(c2_email) is not None)
admin_id = c.get(f"{GW}/auth/me", headers=HA).json()["id"]
check("un admin ne peut pas se desactiver (400)", c.patch(f"{GW}/dashboard/admin/users/{admin_id}/status", headers=HA, json={"status": "DISABLED"}).status_code == 400)
check("moderation d'offre (200)", c.patch(f"{GW}/dashboard/admin/jobs/{job2_id}/moderation", headers=HA, json={"reason": "Contenu non conforme"}).status_code == 200)
check("moderation sans raison (400)", c.patch(f"{GW}/dashboard/admin/jobs/{job_id}/moderation", headers=HA, json={"reason": ""}).status_code == 400)
check("moderation par un recruteur (403)", c.patch(f"{GW}/dashboard/admin/jobs/{job_id}/moderation", headers=H1, json={"reason": "x"}).status_code == 403)
check("offre moderee absente de la recherche candidat", all(j["id"] != job2_id for j in c.get(f"{GW}/jobs", headers=HC1).json()["content"]))
r = c.get(f"{GW}/jobs/{job2_id}", headers=H3)
check("le proprietaire voit la raison de moderation", r.status_code == 200 and r.json().get("status") == "MODERATED" and r.json().get("moderationReason") == "Contenu non conforme", r.text[:200])
check("offre moderee non modifiable par le recruteur (403/409)", c.patch(f"{GW}/jobs/{job2_id}/status", headers=H3, json={"status": "PUBLISHED"}).status_code in (403, 409))
c.get(f"{GW}/users/candidates/{c1_id}/cv", headers=H1)  # consultation d'un CV par un recruteur -> audit
time.sleep(3)
r = c.get(f"{GW}/dashboard/admin/audit-log", headers=HA, params={"size": 100})
actions = {e["action"] for e in r.json().get("content", [])} if r.status_code == 200 else set()
check("journal d'audit (admin)", r.status_code == 200, r.text[:150])
for a in ("CV_VIEWED", "APPLICATION_STATUS_CHANGED", "USER_STATUS_CHANGED", "JOB_MODERATED"):
    check(f"evenement d'audit {a} journalise", a in actions, sorted(actions))
r = c.get(f"{GW}/dashboard/admin/audit-log", headers=HA, params={"action": "JOB_MODERATED"})
check("filtre du journal par action", r.status_code == 200 and r.json()["content"] and all(e["action"] == "JOB_MODERATED" for e in r.json()["content"]), r.text[:150])
r = c.get(f"{GW}/dashboard/admin/audit-log", headers=HA, params={"actorId": admin_id})
check("filtre du journal par acteur", r.status_code == 200 and all(e["actorId"] == admin_id for e in r.json()["content"]))
check("journal d'audit refuse aux non-admins (403)", c.get(f"{GW}/dashboard/admin/audit-log", headers=H1).status_code == 403)

# ---------------------------------------------------------------- enrichissements metier (hors backlog)
section("Offres enrichies et favoris (EXT-JOB-01, EXT-SAVED-01)")
in_3_days = time.strftime("%Y-%m-%d", time.gmtime(time.time() + 3 * 86400))
yesterday = time.strftime("%Y-%m-%d", time.gmtime(time.time() - 86400))
JOB3 = {**JOB, "title": f"Ingenieur Data {RUN}", "requiredSkills": ["Python", "SQL", f"Data{RUN}"], "workMode": "HYBRID",
        "salaryMin": 2500, "salaryMax": 3500, "applicationDeadline": in_3_days, "positions": 2}
r = c.post(f"{GW}/jobs", headers=H1, json=JOB3)
job3 = r.json() if r.status_code == 201 else {}
job3_id = job3.get("id")
check("publication avec mode, salaire, date limite et postes (201)",
      r.status_code == 201 and job3.get("workMode") == "HYBRID" and job3.get("salaryMax") == 3500
      and job3.get("applicationDeadline") == in_3_days and job3.get("positions") == 2, r.text[:200])
check("offre sans nouveaux champs : valeurs par defaut (ON_SITE, 1 poste)", job.get("workMode") == "ON_SITE" and job.get("positions") == 1, job)
r = c.post(f"{GW}/jobs", headers=H1, json={**JOB3, "salaryMin": 4000, "salaryMax": 3000})
check("salaire minimum superieur au maximum (400 salaryMax)", r.status_code == 400 and "salaryMax" in r.json().get("fieldErrors", {}), r.text[:200])
r = c.post(f"{GW}/jobs", headers=H1, json={**JOB3, "applicationDeadline": yesterday})
check("date limite passee refusee (400 applicationDeadline)", r.status_code == 400 and "applicationDeadline" in r.json().get("fieldErrors", {}), r.text[:200])
r = c.get(f"{GW}/jobs", headers=HC1, params={"keyword": RUN, "workMode": "HYBRID"})
check("filtre workMode", r.status_code == 200 and [j["id"] for j in r.json()["content"]] == [job3_id], r.text[:200])
r = c.get(f"{GW}/jobs", headers=HC1, params={"keyword": RUN, "minSalary": 3000})
check("filtre minSalary (offres sans salaire exclues)", r.status_code == 200 and [j["id"] for j in r.json()["content"]] == [job3_id], r.text[:200])
r = c.patch(f"{GW}/jobs/{job3_id}", headers=H1, json={"salaryMin": None, "salaryMax": None})
check("suppression du salaire en PATCH (200)", r.status_code == 200 and r.json().get("salaryMin") is None, r.text[:200])
r = c.post(f"{GW}/jobs/{job_id}/duplicate", headers=H1)
dup = r.json() if r.status_code == 201 else {}
check("duplication d'une offre (201, DRAFT, titre (copie))", r.status_code == 201 and dup.get("status") == "DRAFT" and dup.get("title", "").endswith("(copie)"), r.text[:200])
check("duplication par une autre entreprise (403)", c.post(f"{GW}/jobs/{job_id}/duplicate", headers=H2).status_code == 403)
check("duplication d'une offre moderee (409)", c.post(f"{GW}/jobs/{job2_id}/duplicate", headers=H3).status_code == 409)
check("mise en favori (204)", c.put(f"{GW}/jobs/{job3_id}/saved", headers=HC1).status_code == 204)
check("mise en favori idempotente (204)", c.put(f"{GW}/jobs/{job3_id}/saved", headers=HC1).status_code == 204)
check("favori sur un brouillon (404)", c.put(f"{GW}/jobs/{dup.get('id')}/saved", headers=HC1).status_code == 404)
check("suppression d'une offre publiee refusee (409)", c.delete(f"{GW}/jobs/{job3_id}", headers=H1).status_code == 409)
draft = c.post(f"{GW}/jobs/{job_id}/duplicate", headers=H1).json()
check("suppression du brouillon par une autre entreprise (403)", c.delete(f"{GW}/jobs/{draft.get('id')}", headers=H2).status_code == 403)
check("suppression d'un brouillon (204)", c.delete(f"{GW}/jobs/{draft.get('id')}", headers=H1).status_code == 204)
check("brouillon supprime introuvable (404)", c.get(f"{GW}/jobs/{draft.get('id')}", headers=H1).status_code == 404)
check("favori par un recruteur (403)", c.put(f"{GW}/jobs/{job3_id}/saved", headers=H1).status_code == 403)
r = c.get(f"{GW}/jobs/saved", headers=HC1)
check("liste des favoris", r.status_code == 200 and [j["id"] for j in r.json()] == [job3_id] and r.json()[0].get("saved") is True, r.text[:200])
r = c.get(f"{GW}/jobs/{job3_id}", headers=HC1)
check("indicateur saved sur le detail (candidat)", r.status_code == 200 and r.json().get("saved") is True, r.text[:150])
check("indicateur saved absent pour un recruteur", c.get(f"{GW}/jobs/{job3_id}", headers=H1).json().get("saved") is None)
check("retrait du favori (204)", c.delete(f"{GW}/jobs/{job3_id}/saved", headers=HC1).status_code == 204)
check("favoris vides apres retrait", c.get(f"{GW}/jobs/saved", headers=HC1).json() == [])
check("publication du brouillon duplique (200)",
      c.patch(f"{GW}/jobs/{dup.get('id')}/status", headers=H1, json={"status": "PUBLISHED"}).status_code == 200)

section("Candidature motivee, entretiens et notes (EXT-APP-01..03)")
letter = "Bonjour,\nVotre offre correspond a mon parcours data. Disponible rapidement."
r = c.post(f"{GW}/applications", headers=HC1, json={"jobId": job3_id, "coverLetter": letter})
app_x = r.json() if r.status_code == 201 else {}
check("candidature avec lettre de motivation (201)", r.status_code == 201 and app_x.get("coverLetter") == letter, r.text[:200])
check("le candidat ne voit pas les notes (notesCount null)", app_x.get("notesCount") is None and app_x.get("averageRating") is None, app_x)
check("lettre trop longue (400)", c.post(f"{GW}/applications", headers=HC3, json={"jobId": job3_id, "coverLetter": "x" * 5001}).status_code == 400)
r = c.patch(f"{GW}/applications/{app_x.get('id')}/status", headers=H1, json={"status": "IN_REVIEW", "message": "Profil retenu pour etude"})
check("decision avec message au candidat (200)", r.status_code == 200 and r.json()["statusHistory"][-1].get("message") == "Profil retenu pour etude", r.text[:200])
slot = time.strftime("%Y-%m-%dT10:00:00Z", time.gmtime(time.time() + 5 * 86400))
interview = {"scheduledAt": slot, "durationMinutes": 45, "mode": "VIDEO", "location": "https://meet.e2e.test/abc"}
check("entretien avec un autre statut refuse (400)",
      c.patch(f"{GW}/applications/{app_x.get('id')}/status", headers=H1, json={"status": "IN_REVIEW", "interview": interview}).status_code == 400)
r = c.patch(f"{GW}/applications/{app_x.get('id')}/status", headers=H1, json={"status": "INTERVIEW", "interview": interview})
check("passage en entretien avec planification (200)", r.status_code == 200 and (r.json().get("interview") or {}).get("mode") == "VIDEO", r.text[:200])
past = {**interview, "scheduledAt": "2020-01-01T10:00:00Z"}
check("entretien dans le passe refuse (400)", c.put(f"{GW}/applications/{app_x.get('id')}/interview", headers=H1, json=past).status_code == 400)
r = c.put(f"{GW}/applications/{app_x.get('id')}/interview", headers=H1, json={**interview, "mode": "ON_SITE", "location": "Siege, Tunis"})
check("replanification (200)", r.status_code == 200 and r.json()["interview"]["mode"] == "ON_SITE", r.text[:200])
check("replanification par une autre entreprise (403)", c.put(f"{GW}/applications/{app_x.get('id')}/interview", headers=H2, json=interview).status_code == 403)
r = c.get(f"{GW}/applications/interviews/upcoming", headers=H1)
check("entretiens a venir du recruteur", r.status_code == 200 and any(i["applicationId"] == app_x.get("id") for i in r.json()), r.text[:200])
check("entretiens a venir d'une autre entreprise (vide)", all(i["applicationId"] != app_x.get("id") for i in c.get(f"{GW}/applications/interviews/upcoming", headers=H2).json()))
mine = c.get(f"{GW}/applications/me", headers=HC1).json()
mine_x = next((a for a in mine if a["id"] == app_x.get("id")), {})
check("le candidat voit son entretien et le message", (mine_x.get("interview") or {}).get("mode") == "ON_SITE" and any(h.get("message") for h in mine_x.get("statusHistory", [])), mine_x)
r = c.post(f"{GW}/applications/{app_x.get('id')}/notes", headers=H1, json={"content": "Bon niveau SQL", "rating": 4})
note = r.json() if r.status_code == 201 else {}
check("note d'equipe (201)", r.status_code == 201 and note.get("rating") == 4, r.text[:200])
if token:
    r = c.get(f"{GW}/applications/{app_x.get('id')}/notes", headers=H3)
    check("notes partagees avec un autre membre de l'entreprise", r.status_code == 200 and len(r.json()) == 1, r.text[:150])
c.post(f"{GW}/applications/{app_x.get('id')}/notes", headers=H1, json={"content": "A confirmer en entretien", "rating": 5})
check("note hors bornes (400)", c.post(f"{GW}/applications/{app_x.get('id')}/notes", headers=H1, json={"content": "x", "rating": 6}).status_code == 400)
check("notes refusees au candidat (403)", c.get(f"{GW}/applications/{app_x.get('id')}/notes", headers=HC1).status_code == 403)
check("notes refusees a une autre entreprise (403)", c.get(f"{GW}/applications/{app_x.get('id')}/notes", headers=H2).status_code == 403)
r = c.get(f"{GW}/applications", headers=H1, params={"jobId": job3_id})
row = next((a for a in r.json() if a["id"] == app_x.get("id")), {}) if r.status_code == 200 else {}
check("compteur et moyenne des notes cote recruteur", row.get("notesCount") == 2 and row.get("averageRating") == 4.5, row)
r = c.put(f"{GW}/applications/{app_x.get('id')}/notes/{note.get('id')}", headers=H1, json={"content": "Tres bon niveau SQL", "rating": 5})
check("modification d'une note par son auteur (200)", r.status_code == 200 and r.json().get("content") == "Tres bon niveau SQL" and r.json().get("rating") == 5, r.text[:200])
if token:
    check("modification de la note d'un collegue refusee (403)", c.put(f"{GW}/applications/{app_x.get('id')}/notes/{note.get('id')}", headers=H3, json={"content": "Hack"}).status_code == 403)
check("suppression d'une note par son auteur (204)", c.delete(f"{GW}/applications/{app_x.get('id')}/notes/{note.get('id')}", headers=H1).status_code == 204)
r = c.patch(f"{GW}/applications/{app_x.get('id')}/status", headers=H1, json={"status": "REJECTED", "message": "Nous avons retenu un autre profil."})
check("refus motive (200)", r.status_code == 200 and r.json()["statusHistory"][-1].get("message") == "Nous avons retenu un autre profil.", r.text[:200])

section("Pilotage enrichi (EXT-DASH-01)")
r = c.get(f"{GW}/dashboard/stats/company/{co1.get('id')}", headers=H1)
s = r.json() if r.status_code == 200 else {}
check("entonnoir par statut (entreprise)", isinstance(s.get("applicationsByStatus"), dict) and s["applicationsByStatus"].get("REJECTED", 0) >= 1, s)
check("delai moyen de decision (entreprise)", s.get("averageDecisionDays") is not None, s)
check("offres bientot cloturees (entreprise)", (s.get("jobsClosingSoon") or 0) >= 1, s)
check("compteur d'entretiens a venir present", "upcomingInterviews" in s, s)
r = c.get(f"{GW}/dashboard/stats/global", headers=HA)
g = r.json() if r.status_code == 200 else {}
check("entonnoir et delai de decision (global)", isinstance(g.get("applicationsByStatus"), dict) and g.get("averageDecisionDays") is not None and "jobsClosingSoon" in g, g)
time.sleep(2)
r = c.get(f"{GW}/dashboard/admin/audit-log", headers=HA, params={"action": "INTERVIEW_SCHEDULED"})
check("evenement d'audit INTERVIEW_SCHEDULED journalise", r.status_code == 200 and len(r.json().get("content", [])) >= 1, r.text[:150])

# ---------------------------------------------------------------- securite
section("Securite et acces")
check("endpoints internes non exposes par le gateway", c.get(f"{GW.replace('/api/v1','')}/internal/v1/accounts/stats", headers=HA).status_code in (401, 403, 404))
check("sans token (401)", c.get(f"{GW}/jobs").status_code == 401)
check("via nginx du frontend : recherche d'offres (200)", c.get(f"{WEB}/jobs", headers=HC1).status_code == 200)
check("IA : lecture du CV d'un autre candidat (403)", c.get(f"{GW}/ai/cv-parsing/candidates/{c3_id}", headers=HC1).status_code == 403)

print(f"\n{'='*60}\nRESULTAT : {ok} OK, {fail} echecs")
for f in failures:
    print("  - " + f)
sys.exit(1 if fail else 0)
