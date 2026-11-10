"""Jeu de données de volume : des milliers d'offres publiées, pour la recherche « à fort volume » et le test de charge.

À lancer après seed_demo.py (il réutilise ses 5 recruteurs et leurs entreprises). Passe par l'API du gateway.
Rejouable : il compte les offres de volume déjà présentes et ne crée que celles qui manquent.

    python backend/seed/seed_volume.py            # 3000 offres
    VOLUME=10000 python backend/seed/seed_volume.py
"""

import os
import random
import sys
from concurrent.futures import ThreadPoolExecutor

import httpx

GW = os.environ.get("GATEWAY", "http://localhost:8080") + "/api/v1"
TARGET = int(os.environ.get("VOLUME", "3000"))
MARKER = "jeuvolume"  # mot présent dans chaque description : sert à compter les offres déjà créées
PASSWORD = "Motdepasse123"
RECRUITERS = ["recruteur@smartrecruit.test"] + [f"recruteur{i}@smartrecruit.test" for i in range(2, 6)]

TITLES = ["Développeur Java", "Développeur React", "Ingénieur DevOps", "Data Analyst", "Data Scientist",
          "Développeur Python", "Chef de projet IT", "Administrateur systèmes", "Ingénieur QA", "Product Owner",
          "Développeur Full Stack", "Architecte logiciel", "Ingénieur cybersécurité", "UX Designer", "Développeur mobile"]
SKILLS = ["Java", "Spring Boot", "React", "TypeScript", "Python", "Django", "Docker", "Kubernetes", "PostgreSQL",
          "AWS", "Azure", "Git", "Selenium", "Figma", "Flutter", "Kotlin", "SQL", "Linux", "Jenkins", "Kafka"]
CITIES = ["Tunis", "Sfax", "Sousse", "Monastir", "Nabeul", "Bizerte", "Ariana", "Gabès"]
CONTRACTS = ["CDI", "CDD", "STAGE", "ALTERNANCE", "FREELANCE"]
LEVELS = ["JUNIOR", "INTERMEDIATE", "SENIOR", "LEAD"]
MODES = ["ON_SITE", "HYBRID", "REMOTE"]


def login(client, email):
    response = client.post(f"{GW}/auth/login", json={"email": email, "password": PASSWORD})
    if response.status_code != 200:
        sys.exit(f"connexion de {email} impossible ({response.status_code}) : lancer d'abord seed_demo.py")
    return {"Authorization": "Bearer " + response.json()["accessToken"]}


def offer(n, rng):
    title = rng.choice(TITLES)
    skills = rng.sample(SKILLS, rng.randint(2, 6))
    salary = rng.choice([None, rng.randrange(1200, 6000, 100)])
    return {
        "title": f"{title} #{n}",
        "description": f"{title} pour un projet {rng.choice(['bancaire', 'e-commerce', 'santé', 'télécom', 'industriel'])}. "
                       f"Compétences attendues : {', '.join(skills)}. Offre générée ({MARKER}).",
        "requiredSkills": skills,
        "experienceLevel": rng.choice(LEVELS),
        "contractType": rng.choice(CONTRACTS),
        "location": rng.choice(CITIES),
        "workMode": rng.choice(MODES),
        "salaryMin": salary,
        "salaryMax": None if salary is None else salary + rng.randrange(0, 1500, 100),
        "positions": rng.randint(1, 5),
    }


def main():
    client = httpx.Client(timeout=60)
    headers = [login(client, email) for email in RECRUITERS]
    existing = client.get(f"{GW}/jobs", params={"keyword": MARKER, "size": 1}, headers=headers[0]).json()["totalElements"]
    missing = max(0, TARGET - existing)
    print(f"{existing} offres de volume déjà présentes, {missing} à créer")

    def create(n):
        rng = random.Random(n)  # même offre pour le même numéro, d'une exécution à l'autre
        response = client.post(f"{GW}/jobs", json=offer(n, rng), headers=headers[n % len(headers)])
        return response.status_code

    with ThreadPoolExecutor(max_workers=8) as pool:
        codes = list(pool.map(create, range(existing, existing + missing)))
    failed = [c for c in codes if c != 201]
    total = client.get(f"{GW}/jobs", params={"size": 1}, headers=headers[0]).json()["totalElements"]
    print(f"{len(codes) - len(failed)} créées, {len(failed)} en échec ; {total} offres publiées au total")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
