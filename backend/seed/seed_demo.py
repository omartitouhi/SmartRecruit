"""Jeu de données de démonstration : entreprises, offres, candidats avec CV PDF, candidatures.

Passe uniquement par l'API du gateway (les CV sont analysés par le service IA, comme en usage réel).
Rejouable : un compte, une entreprise ou une offre déjà présents est réutilisé, pas dupliqué.

    pip install -r e2e/requirements.txt
    python backend/seed/seed_demo.py                 # gateway sur http://localhost:8080
    GATEWAY=http://localhost:8080 python backend/seed/seed_demo.py

Tous les comptes ont le mot de passe `Motdepasse123`.
"""

import io
import os
import random
import sys
import time

import httpx
from reportlab.pdfgen import canvas

GW = os.environ.get("GATEWAY", "http://localhost:8080") + "/api/v1"
ADMIN = (os.environ.get("ADMIN_EMAIL", "admin@smartrecruit.local"), os.environ.get("ADMIN_PASSWORD", "Admin-Dev-2026!"))
PASSWORD = "Motdepasse123"
rng = random.Random(42)
http = httpx.Client(timeout=60)


def call(method, path, **kwargs):
    """Requête avec reprise si le limiteur de débit du gateway répond 429."""
    for _ in range(8):
        response = http.request(method, f"{GW}{path}", **kwargs)
        if response.status_code != 429:
            return response
        time.sleep(int(response.headers.get("Retry-After", "5")))
    return response


def login(email, password=PASSWORD):
    response = call("POST", "/auth/login", json={"email": email, "password": password})
    return {"Authorization": "Bearer " + response.json()["accessToken"]} if response.status_code == 200 else None


def account(email, role):
    """Crée le compte (ou le retrouve) et renvoie ses en-têtes d'authentification."""
    response = call("POST", "/auth/register", json={"email": email, "password": PASSWORD, "role": role})
    if response.status_code not in (201, 409):
        sys.exit(f"inscription de {email} refusée : {response.status_code} {response.text[:200]}")
    headers = login(email)
    if headers is None:
        sys.exit(f"connexion de {email} impossible")
    return headers


# --------------------------------------------------------------------------- entreprises et offres

COMPANIES = [
    {"email": "recruteur@smartrecruit.test", "name": "Acme Tech", "sector": "Édition de logiciels",
     "description": "Éditeur de solutions de gestion pour PME, équipes produit à Tunis et Sfax."},
    {"email": "recruteur2@smartrecruit.test", "name": "Medina Systems", "sector": "Services numériques",
     "description": "Société de conseil et d'intégration, projets bancaires et télécoms."},
    {"email": "recruteur3@smartrecruit.test", "name": "Carthage Digital", "sector": "Agence web et mobile",
     "description": "Agence qui conçoit des sites, applications mobiles et plateformes e-commerce."},
    {"email": "recruteur4@smartrecruit.test", "name": "Sahara Analytics", "sector": "Données et IA",
     "description": "Cabinet spécialisé en ingénierie de données, tableaux de bord et modèles prédictifs."},
    {"email": "recruteur5@smartrecruit.test", "name": "Zitouna Cloud", "sector": "Infrastructure et cloud",
     "description": "Hébergement, automatisation et supervision pour des clients publics et privés."},
]

# (entreprise, titre, contrat, ville, niveau, compétences, description)
JOBS = [
    (0, "Développeur Java Backend", "CDI", "Tunis", "INTERMEDIATE", ["Java", "Spring Boot", "PostgreSQL", "Docker"],
     "Vous concevez des microservices Spring Boot et des API REST pour notre ERP. Environnement : Java, Spring Boot, PostgreSQL, Docker, Git. 3 ans d'expérience minimum."),
    (0, "Développeur React Frontend", "CDD", "Sfax", "INTERMEDIATE", ["React", "TypeScript", "CSS", "Git"],
     "Vous construisez l'interface de notre application web avec React et TypeScript, en lien avec les designers. Tests avec Jest. 2 ans d'expérience."),
    (0, "Ingénieur DevOps", "CDI", "Tunis", "SENIOR", ["Docker", "Kubernetes", "CI/CD", "Linux", "Terraform"],
     "Vous industrialisez nos déploiements : pipelines CI/CD, conteneurs Docker, clusters Kubernetes, infrastructure avec Terraform sur Linux. 5 ans d'expérience requis."),
    (0, "Stage PFE – Développement Full Stack", "STAGE", "Tunis", "JUNIOR", ["JavaScript", "Node.js", "React", "MongoDB"],
     "Stage de fin d'études de 6 mois : développement d'un module de reporting avec Node.js, React et MongoDB. Profil débutant accepté."),
    (0, "Testeur QA automatisation", "CDI", "Ariana", "INTERMEDIATE", ["Selenium", "Cypress", "Jira", "Postman"],
     "Vous automatisez les tests de non-régression avec Selenium et Cypress, rédigez les plans de test dans Jira et validez les API avec Postman. 3 ans d'expérience."),
    (1, "Architecte Java / Microservices", "CDI", "Tunis", "LEAD", ["Java", "Spring Boot", "Microservices", "Kafka", "Kubernetes"],
     "Vous encadrez une équipe de 6 développeurs et définissez l'architecture microservices de nos plateformes bancaires : Java, Spring Boot, Kafka, Kubernetes. 8 ans d'expérience."),
    (1, "Développeur .NET", "CDI", "Sousse", "INTERMEDIATE", [".NET", "C#", "SQL Server", "Azure"],
     "Développement d'applications métier en C# et .NET sur SQL Server, déployées sur Azure. 3 ans d'expérience en environnement Agile et Scrum."),
    (1, "Consultant Oracle / PL-SQL", "CDI", "Tunis", "SENIOR", ["Oracle", "PL/SQL", "SQL", "Linux"],
     "Vous optimisez et faites évoluer des bases Oracle de production, écrivez des procédures PL/SQL et assurez le support N3. 6 ans d'expérience."),
    (1, "Développeur Angular", "CDD", "Tunis", "INTERMEDIATE", ["Angular", "TypeScript", "REST", "Git"],
     "Intégration d'interfaces Angular consommant des API REST pour un client télécoms. 2 ans d'expérience, TypeScript indispensable."),
    (1, "Alternance – Développeur Java", "ALTERNANCE", "Tunis", "JUNIOR", ["Java", "Spring Boot", "SQL", "Git"],
     "Alternance de 12 mois : participation au développement d'une application Spring Boot, avec tutorat par un développeur senior. Profil débutant."),
    (2, "Développeur Laravel / PHP", "CDI", "Sousse", "INTERMEDIATE", ["PHP", "Laravel", "MySQL", "JavaScript"],
     "Vous développez des plateformes e-commerce en PHP et Laravel avec MySQL. 3 ans d'expérience, bonne connaissance de JavaScript."),
    (2, "Développeur Mobile Flutter", "CDI", "Monastir", "INTERMEDIATE", ["Flutter", "Android", "REST", "Git"],
     "Vous créez des applications mobiles Flutter pour Android et iOS qui consomment des API REST. 2 ans d'expérience."),
    (2, "UI Designer / Intégrateur", "FREELANCE", "Nabeul", "INTERMEDIATE", ["Figma", "HTML", "CSS", "JavaScript"],
     "Mission freelance de 4 mois : maquettes Figma puis intégration HTML, CSS et JavaScript d'un site vitrine. 3 ans d'expérience."),
    (2, "Développeur WordPress / PHP", "CDD", "Bizerte", "JUNIOR", ["PHP", "MySQL", "HTML", "CSS"],
     "Création et maintenance de sites WordPress sur mesure en PHP, MySQL, HTML et CSS. Débutant accepté, 1 an d'expérience apprécié."),
    (2, "Lead Developer Node.js", "CDI", "Tunis", "LEAD", ["Node.js", "TypeScript", "MongoDB", "AWS", "Docker"],
     "Vous pilotez l'équipe back-end : API Node.js et TypeScript, MongoDB, déploiements Docker sur AWS. Rôle de référent technique, 8 ans d'expérience."),
    (3, "Data Engineer", "CDI", "Tunis", "SENIOR", ["Python", "Spark", "SQL", "Kafka", "Airflow"],
     "Vous construisez les pipelines de données (Python, Spark, Kafka) qui alimentent nos tableaux de bord. 5 ans d'expérience en ingénierie de données."),
    (3, "Data Analyst", "CDI", "Tunis", "INTERMEDIATE", ["SQL", "Power BI", "Python", "Pandas"],
     "Analyse de données métier, requêtes SQL, tableaux de bord Power BI et scripts Python avec Pandas. 3 ans d'expérience."),
    (3, "Data Scientist Junior", "CDD", "Sfax", "JUNIOR", ["Python", "Machine Learning", "Pandas", "NumPy"],
     "Vous prototypez des modèles de Machine Learning avec Python, Pandas et NumPy. Jeune diplômé ou 1 an d'expérience."),
    (3, "Ingénieur MLOps", "CDI", "Tunis", "SENIOR", ["Python", "Docker", "Kubernetes", "TensorFlow", "CI/CD"],
     "Industrialisation des modèles : conteneurs Docker, Kubernetes, pipelines CI/CD, TensorFlow en production. 5 ans d'expérience."),
    (3, "Stage – BI & Reporting", "STAGE", "Ariana", "JUNIOR", ["SQL", "Power BI", "Excel"],
     "Stage de 4 mois : modélisation de données SQL et tableaux de bord Power BI pour un client industriel. Profil débutant."),
    (4, "Administrateur Systèmes Linux", "CDI", "Tunis", "INTERMEDIATE", ["Linux", "Bash", "Ansible", "Nginx"],
     "Administration de serveurs Linux, scripts Bash, automatisation avec Ansible, exploitation de Nginx. 3 ans d'expérience."),
    (4, "Ingénieur Cloud AWS", "CDI", "Tunis", "SENIOR", ["AWS", "Terraform", "Kubernetes", "Linux", "Docker"],
     "Architecture et exploitation de plateformes AWS avec Terraform et Kubernetes. 5 ans d'expérience, certification AWS appréciée."),
    (4, "Ingénieur Cybersécurité", "CDI", "Tunis", "SENIOR", ["Cybersécurité", "Linux", "Python", "OAuth2"],
     "Audit de sécurité, durcissement des systèmes Linux, scripts Python et intégration OAuth2 pour nos clients. 5 ans d'expérience."),
    (4, "Technicien Supervision", "CDD", "Sousse", "JUNIOR", ["Linux", "Prometheus", "Grafana"],
     "Mise en place de la supervision avec Prometheus et Grafana sur serveurs Linux. Jeune diplômé accepté."),
    (4, "Développeur Go Backend", "CDI", "Tunis", "INTERMEDIATE", ["Go", "PostgreSQL", "Docker", "REST"],
     "Développement d'outils d'infrastructure en Go, API REST, PostgreSQL et Docker. 3 ans d'expérience."),
]

# --------------------------------------------------------------------------- candidats

# (prénom, nom, ville, titre, compétences, [(poste, entreprise, début AAAA-MM, fin|None, mission)], [(diplôme, école, année)], langues)
CANDIDATES = [
    ("Sami", "Ben Ali", "Tunis", "Ingénieur logiciel Java", ["Java", "Spring Boot", "PostgreSQL", "Docker", "Git", "REST"],
     [("Backend Developer", "Acme Tech", "2022-01", None, "Développement de microservices Spring Boot et API REST"),
      ("Stagiaire Java", "Beta Soft", "2021-06", "2021-09", "Développement d'un module de facturation Java")],
     [("Master Génie Logiciel", "ESPRIT", 2021), ("Licence Informatique", "ISI", 2019)], ["Français", "Anglais"]),
    ("Yasmine", "Trabelsi", "Sfax", "Développeuse front-end", ["React", "TypeScript", "JavaScript", "CSS", "HTML", "Git", "Jest"],
     [("Développeuse Front-end", "Carthage Digital", "2021-03", None, "Interfaces React et TypeScript pour des sites e-commerce"),
      ("Intégratrice web", "Pixel Studio", "2019-09", "2021-02", "Intégration HTML, CSS et JavaScript de maquettes Figma")],
     [("Licence Informatique", "ISET Sfax", 2019)], ["Français", "Anglais", "Arabe"]),
    ("Mehdi", "Jebali", "Tunis", "Ingénieur DevOps", ["Docker", "Kubernetes", "CI/CD", "Linux", "Terraform", "Jenkins", "AWS"],
     [("Ingénieur DevOps", "Zitouna Cloud", "2019-02", None, "Pipelines CI/CD Jenkins, clusters Kubernetes et infrastructure Terraform sur AWS"),
      ("Administrateur Linux", "Telnet", "2016-09", "2019-01", "Administration de serveurs Linux et scripts Bash")],
     [("Diplôme d'ingénieur en informatique", "INSAT", 2016)], ["Français", "Anglais"]),
    ("Ines", "Gharbi", "Tunis", "Data engineer", ["Python", "Spark", "SQL", "Kafka", "Airflow", "Pandas", "Docker"],
     [("Data Engineer", "Sahara Analytics", "2020-05", None, "Pipelines Python et Spark, ingestion Kafka vers l'entrepôt de données"),
      ("Ingénieure BI", "Medina Systems", "2018-01", "2020-04", "Requêtes SQL et tableaux de bord Power BI")],
     [("Master Data Science", "ENIT", 2017)], ["Français", "Anglais"]),
    ("Amine", "Khelifi", "Sousse", "Développeur PHP Laravel", ["PHP", "Laravel", "MySQL", "JavaScript", "HTML", "CSS", "Git"],
     [("Développeur PHP", "Carthage Digital", "2020-10", None, "Plateformes e-commerce en PHP et Laravel avec MySQL"),
      ("Développeur WordPress", "Webline", "2019-01", "2020-09", "Sites WordPress sur mesure en PHP")],
     [("Licence Informatique", "ISSAT Sousse", 2018)], ["Français", "Arabe"]),
    ("Rania", "Mansouri", "Tunis", "Développeuse mobile", ["Flutter", "Android", "Kotlin", "REST", "Git", "Figma"],
     [("Développeuse Mobile", "Carthage Digital", "2021-09", None, "Applications Flutter pour Android et iOS consommant des API REST"),
      ("Développeuse Android", "Appwork", "2020-01", "2021-08", "Application Android en Kotlin")],
     [("Master Génie Logiciel", "ESPRIT", 2019)], ["Français", "Anglais"]),
    ("Karim", "Chaabane", "Tunis", "Architecte logiciel", ["Java", "Spring Boot", "Microservices", "Kafka", "Kubernetes", "AWS", "Docker", "Oracle"],
     [("Architecte logiciel", "Medina Systems", "2017-03", None, "Encadrement d'une équipe de 6 développeurs, architecture microservices Java et Kafka"),
      ("Développeur Java senior", "Banque de Tunisie", "2012-09", "2017-02", "Applications bancaires Java et Oracle")],
     [("Diplôme d'ingénieur en informatique", "ENSI", 2012)], ["Français", "Anglais", "Allemand"]),
    ("Salma", "Ben Youssef", "Ariana", "Testeuse QA", ["Selenium", "Cypress", "Jira", "Postman", "JUnit", "Agile"],
     [("Ingénieure QA", "Acme Tech", "2020-02", None, "Automatisation des tests avec Selenium et Cypress, plans de test Jira"),
      ("Testeuse junior", "Vermeg", "2018-09", "2020-01", "Tests manuels et API avec Postman")],
     [("Licence Informatique", "ISI", 2018)], ["Français", "Anglais"]),
    ("Walid", "Haddad", "Monastir", "Étudiant ingénieur", ["JavaScript", "Node.js", "React", "MongoDB", "Git"],
     [("Stagiaire développeur web", "Webline", "2025-07", "2025-09", "Développement d'une application React avec une API Node.js")],
     [("Cycle ingénieur informatique", "ENIM", 2026)], ["Français", "Anglais", "Arabe"]),
    ("Nour", "Zouari", "Tunis", "Data analyst", ["SQL", "Power BI", "Python", "Pandas", "Excel"],
     [("Data Analyst", "Sahara Analytics", "2021-06", None, "Tableaux de bord Power BI et analyses SQL pour des clients industriels"),
      ("Stagiaire BI", "Medina Systems", "2021-01", "2021-05", "Modélisation de données SQL")],
     [("Master Business Intelligence", "IHEC Carthage", 2021)], ["Français", "Anglais"]),
    ("Hatem", "Bouzid", "Tunis", "Développeur .NET", [".NET", "C#", "SQL Server", "Azure", "Scrum", "Git"],
     [("Développeur .NET", "Medina Systems", "2019-04", None, "Applications métier C# et .NET sur SQL Server, déploiement Azure"),
      ("Développeur C#", "Soft Engineering", "2017-10", "2019-03", "Applications desktop C#")],
     [("Licence Informatique", "ISG Tunis", 2017)], ["Français", "Anglais"]),
    ("Emna", "Dridi", "Sfax", "Développeuse Angular", ["Angular", "TypeScript", "JavaScript", "REST", "Git", "Sass"],
     [("Développeuse Angular", "Medina Systems", "2022-02", None, "Interfaces Angular consommant des API REST pour un client télécoms")],
     [("Licence Informatique", "FSEGS Sfax", 2021)], ["Français", "Anglais"]),
    ("Oussama", "Lakhal", "Tunis", "Administrateur systèmes", ["Linux", "Bash", "Ansible", "Nginx", "Prometheus", "Grafana", "Docker"],
     [("Administrateur systèmes", "Zitouna Cloud", "2021-01", None, "Exploitation Linux, automatisation Ansible, supervision Prometheus et Grafana"),
      ("Technicien support", "Tunisie Telecom", "2018-10", "2020-12", "Support N2 et administration Linux")],
     [("Licence Réseaux", "ISET Com", 2018)], ["Français", "Anglais", "Arabe"]),
    ("Aya", "Ferchichi", "Tunis", "Jeune diplômée Python", ["Python", "Machine Learning", "Pandas", "NumPy", "SQL", "TensorFlow"],
     [("Stagiaire Data Science", "Sahara Analytics", "2025-02", "2025-07", "Modèle de prédiction avec Python, Pandas et NumPy")],
     [("Master Data Science", "ESPRIT", 2025)], ["Français", "Anglais"]),
    ("Fares", "Marzouki", "Tunis", "Développeur Node.js senior", ["Node.js", "TypeScript", "MongoDB", "AWS", "Docker", "REST", "GraphQL", "Redis"],
     [("Lead Developer Node.js", "Zitouna Cloud", "2018-06", None, "Encadrement de l'équipe back-end, API Node.js et TypeScript déployées sur AWS"),
      ("Développeur Node.js", "Carthage Digital", "2014-09", "2018-05", "API Node.js et MongoDB")],
     [("Diplôme d'ingénieur en informatique", "INSAT", 2014)], ["Français", "Anglais"]),
    ("Chaima", "Rekik", "Bizerte", "Développeuse web", ["PHP", "MySQL", "HTML", "CSS", "JavaScript", "WordPress"],
     [("Développeuse web", "Webline", "2024-09", None, "Sites WordPress sur mesure en PHP et MySQL")],
     [("Licence Informatique", "ISSAT Mateur", 2024)], ["Français", "Arabe"]),
    ("Bilel", "Saidi", "Tunis", "Développeur Go", ["Go", "PostgreSQL", "Docker", "REST", "Linux", "Git"],
     [("Développeur Go", "Zitouna Cloud", "2021-11", None, "Outils d'infrastructure en Go, API REST et PostgreSQL"),
      ("Développeur backend", "Appwork", "2019-09", "2021-10", "API Python et PostgreSQL")],
     [("Master Génie Logiciel", "ESPRIT", 2019)], ["Français", "Anglais"]),
    ("Lamia", "Ben Salah", "Tunis", "Ingénieure cybersécurité", ["Cybersécurité", "Linux", "Python", "OAuth2", "Docker", "Bash"],
     [("Ingénieure cybersécurité", "Medina Systems", "2019-09", None, "Audits de sécurité, durcissement Linux et intégration OAuth2"),
      ("Analyste SOC", "Tunisie Telecom", "2017-02", "2019-08", "Supervision de la sécurité et réponse aux incidents")],
     [("Diplôme d'ingénieur en télécommunications", "SUP'COM", 2017)], ["Français", "Anglais"]),
    ("Taher", "Slimani", "Sousse", "Consultant Oracle", ["Oracle", "PL/SQL", "SQL", "Linux", "Java"],
     [("Consultant Oracle", "Medina Systems", "2016-03", None, "Optimisation de bases Oracle et procédures PL/SQL en production"),
      ("Développeur PL/SQL", "Poulina", "2013-10", "2016-02", "Procédures PL/SQL pour l'ERP interne")],
     [("Licence Informatique", "ISIMS", 2013)], ["Français", "Anglais"]),
]


# Comptes de démonstration faciles à retenir (les autres sont prenom.nom@smartrecruit.test).
DEMO_EMAILS = {("Sami", "Ben Ali"): "candidat@smartrecruit.test"}


def month_label(value):
    year, month = value.split("-")
    names = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]
    return f"{names[int(month) - 1].capitalize()} {year}"


def cv_pdf(first, last, city, title, skills, experiences, education, languages) -> bytes:
    slug = f"{first}.{last}".lower().replace(" ", "")  # adresse du CV, indépendante du compte
    lines = [f"{first} {last}", f"{title} - {city}", f"{slug}@smartrecruit.test", "",
             "Compétences", f"Techniques : {', '.join(skills)}", "", "Expérience professionnelle"]
    for role, company, start, end, mission in experiences:
        lines += [f"{role} - {company}", f"{month_label(start)} - {month_label(end) if end else 'Présent'}", f"- {mission}"]
    lines += ["", "Formation"] + [f"{degree} - {school}, {year}" for degree, school, year in education]
    lines += ["", "Langues", ", ".join(languages)]
    buffer = io.BytesIO()
    pdf = canvas.Canvas(buffer)
    y = 800
    for line in lines:
        pdf.setFont("Helvetica-Bold" if line in ("Compétences", "Expérience professionnelle", "Formation", "Langues") else "Helvetica", 11)
        pdf.drawString(60, y, line)
        y -= 20
    pdf.save()
    return buffer.getvalue()


def seed_companies_and_jobs():
    owners = []
    for spec in COMPANIES:
        headers = account(spec["email"], "RECRUITER")
        mine = call("GET", "/users/companies/me", headers=headers)
        if mine.status_code != 200:
            mine = call("POST", "/users/companies", headers=headers,
                        json={k: spec[k] for k in ("name", "sector", "description")} | {"logoUrl": None})
            assert mine.status_code == 201, mine.text
        owners.append((headers, mine.json()))

    existing = {}
    for index, (headers, _) in enumerate(owners):
        page = call("GET", "/jobs", headers=headers, params={"companyId": "me", "size": 100}).json()
        existing[index] = {job["title"]: job["id"] for job in page.get("content", [])}

    job_ids = {}
    for company, title, contract, city, level, skills, description in JOBS:
        if title in existing[company]:
            job_ids[title] = existing[company][title]
            continue
        response = call("POST", "/jobs", headers=owners[company][0], json={
            "title": title, "description": description, "requiredSkills": skills, "experienceLevel": level,
            "contractType": contract, "location": city})
        assert response.status_code == 201, (title, response.text[:200])
        job_ids[title] = response.json()["id"]
    return owners, job_ids


def seed_candidates():
    result = []
    for first, last, city, title, skills, experiences, education, languages in CANDIDATES:
        email = DEMO_EMAILS.get((first, last)) or f"{first}.{last}".lower().replace(" ", "") + "@smartrecruit.test"
        headers = account(email, "CANDIDATE")
        profile = call("GET", "/users/candidates/me", headers=headers).json()
        if profile.get("cvAnalysisStatus") != "DONE":
            pdf = cv_pdf(first, last, city, title, skills, experiences, education, languages)
            call("PUT", "/users/candidates/me", headers=headers, json={
                "firstName": first, "lastName": last, "email": email, "phone": f"+216 {rng.randint(20, 99)} {rng.randint(100, 999)} {rng.randint(100, 999)}",
                "location": city, "skills": skills[:3], "experiences": [], "education": [], "languages": languages})
            call("POST", "/users/candidates/me/cv", headers=headers, files={"file": ("cv.pdf", io.BytesIO(pdf), "application/pdf")})
            for _ in range(40):
                profile = call("GET", "/users/candidates/me", headers=headers).json()
                if profile.get("cvAnalysisStatus") in ("DONE", "FAILED"):
                    break
                time.sleep(1)
            suggestions = call("GET", "/users/candidates/me/cv-suggestions", headers=headers)
            if suggestions.status_code == 200:
                s = suggestions.json()
                call("PUT", "/users/candidates/me", headers=headers, json={
                    "firstName": first, "lastName": last, "email": email, "phone": profile.get("phone") or "+216 20 000 000",
                    "location": city, "skills": s["skills"], "experiences": s["experiences"],
                    "education": s["education"], "languages": s["languages"]})
        result.append((email, headers, set(skills)))
    return result


def seed_applications(job_ids, candidates, owners):
    jobs_by_id = {}
    for company, title, _, _, _, skills, _ in JOBS:
        jobs_by_id[job_ids[title]] = (company, set(skills))

    applications = []  # (headers du recruteur, id candidature)
    for email, headers, skills in candidates:
        if call("GET", "/applications/me", headers=headers).json():
            continue  # déjà candidaté lors d'une exécution précédente
        ranked = sorted(jobs_by_id.items(), key=lambda item: (-len(item[1][1] & skills), rng.random()))
        for job_id, (company, _) in [pair for pair in ranked if pair[1][1] & skills][: rng.randint(2, 4)]:
            response = call("POST", "/applications", headers=headers, json={"jobId": job_id})
            if response.status_code == 201:
                applications.append((owners[company][0], response.json()["id"], headers))

    # Avancement des dossiers : répartition réaliste des statuts
    paths = [[], [], [], ["IN_REVIEW"], ["IN_REVIEW"], ["IN_REVIEW", "INTERVIEW"],
             ["IN_REVIEW", "INTERVIEW", "ACCEPTED"], ["IN_REVIEW", "INTERVIEW", "REJECTED"], ["WITHDRAW"]]
    for recruiter, application_id, candidate in applications:
        for step in rng.choice(paths):
            if step == "WITHDRAW":
                call("DELETE", f"/applications/{application_id}", headers=candidate)
            else:
                call("PATCH", f"/applications/{application_id}/status", headers=recruiter, json={"status": step})
    return len(applications)


def seed_admin_actions(owners, job_ids):
    admin = login(*ADMIN)
    if admin is None:
        return
    title = "Technicien Supervision"
    reason = "Annonce signalée : description trop vague sur le périmètre du poste."
    call("PATCH", f"/dashboard/admin/jobs/{job_ids[title]}/moderation", headers=admin, json={"reason": reason})
    # Une offre clôturée côté recruteur
    call("PATCH", f"/jobs/{job_ids['Développeur WordPress / PHP']}/status", headers=owners[2][0], json={"status": "CLOSED"})


if __name__ == "__main__":
    owners, job_ids = seed_companies_and_jobs()
    print(f"{len(owners)} entreprises, {len(job_ids)} offres")
    candidates = seed_candidates()
    print(f"{len(candidates)} candidats avec CV analysé")
    print(f"{seed_applications(job_ids, candidates, owners)} candidatures créées")
    seed_admin_actions(owners, job_ids)
    print(f"Comptes : candidat@ et recruteur[2-5]@ smartrecruit.test, prenom.nom@smartrecruit.test ; mot de passe {PASSWORD}")
