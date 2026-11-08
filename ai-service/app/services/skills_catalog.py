"""Catalogue de compétences : nom canonique -> variantes d'écriture.

Sert au moteur à règles (réponse instantanée, sans LLM) : une compétence n'est reconnue
que si l'une de ses variantes figure dans le texte source, donc rien n'est inventé.
"""

import re

from app.services.grounding import normalize

CATALOG: dict[str, tuple[str, ...]] = {
    # Langages
    "Java": ("java", "jdk", "j2ee", "jee"),
    "JavaScript": ("javascript", "JS", "ecmascript", "es6"),
    "TypeScript": ("typescript", "TS"),
    "Python": ("python", "python3"),
    "C++": ("c++", "cpp"),
    "C#": ("c#", "csharp"),
    "PHP": ("php",),
    "Go": ("golang", "Go"),
    "Kotlin": ("kotlin",),
    "Swift": ("swift",),
    "Rust": ("rust",),
    "Ruby": ("ruby",),
    "SQL": ("sql",),
    "PL/SQL": ("pl/sql", "plsql"),
    "HTML": ("html", "html5"),
    "CSS": ("css", "css3"),
    "Bash": ("bash", "shell"),
    # Web / backend
    "Spring Boot": ("spring boot", "springboot"),
    "Spring": ("spring", "spring framework", "spring mvc"),
    "Hibernate": ("hibernate", "jpa"),
    "Node.js": ("node.js", "nodejs", "node js", "Node"),
    "Express": ("express.js", "expressjs", "express"),
    "NestJS": ("nestjs", "nest.js"),
    "Django": ("django",),
    "Flask": ("flask",),
    "FastAPI": ("fastapi",),
    "Laravel": ("laravel",),
    "Symfony": ("symfony",),
    ".NET": (".net", "dotnet", "asp.net"),
    "REST": ("REST", "restful", "api rest", "rest api"),
    "GraphQL": ("graphql",),
    "Microservices": ("microservices", "microservice", "microservices architecture"),
    # Front
    "React": ("react", "react.js", "reactjs"),
    "Angular": ("angular", "angularjs"),
    "Vue.js": ("vue.js", "vuejs", "vue"),
    "Next.js": ("next.js", "nextjs"),
    "Redux": ("redux",),
    "Tailwind CSS": ("tailwind", "tailwindcss", "tailwind css"),
    "Sass": ("sass", "scss"),
    "Flutter": ("flutter",),
    "React Native": ("react native",),
    "Android": ("android",),
    # Données
    "PostgreSQL": ("postgresql", "postgres", "psql"),
    "MySQL": ("mysql", "mariadb"),
    "MongoDB": ("mongodb", "mongo"),
    "Oracle": ("oracle",),
    "SQL Server": ("sql server", "mssql"),
    "Redis": ("redis",),
    "Elasticsearch": ("elasticsearch", "elastic search", "elk"),
    "Kafka": ("kafka", "apache kafka"),
    "RabbitMQ": ("rabbitmq",),
    "Pandas": ("pandas",),
    "NumPy": ("numpy",),
    "Machine Learning": ("machine learning", "apprentissage automatique", "ML"),
    "Deep Learning": ("deep learning", "apprentissage profond"),
    "TensorFlow": ("tensorflow",),
    "PyTorch": ("pytorch",),
    "Power BI": ("power bi", "powerbi"),
    "Talend": ("talend",),
    "Spark": ("apache spark", "spark", "pyspark"),
    # DevOps / cloud
    "Docker": ("docker", "dockerfile", "docker compose"),
    "Kubernetes": ("kubernetes", "k8s"),
    "Git": ("git", "github", "gitlab"),
    "CI/CD": ("ci/cd", "cicd", "ci cd", "integration continue", "intégration continue"),
    "Jenkins": ("jenkins",),
    "GitHub Actions": ("github actions",),
    "Terraform": ("terraform",),
    "Ansible": ("ansible",),
    "Linux": ("linux", "unix", "ubuntu", "debian"),
    "AWS": ("aws", "amazon web services"),
    "Azure": ("azure",),
    "GCP": ("gcp", "google cloud"),
    "Nginx": ("nginx",),
    "Maven": ("maven",),
    "Gradle": ("gradle",),
    "Prometheus": ("prometheus",),
    "Grafana": ("grafana",),
    # Qualité / méthodes
    "JUnit": ("junit",),
    "Selenium": ("selenium",),
    "Cypress": ("cypress",),
    "Jest": ("jest",),
    "Postman": ("postman",),
    "SonarQube": ("sonarqube", "sonar"),
    "Scrum": ("scrum",),
    "Agile": ("agile", "méthodes agiles", "methodes agiles"),
    "Jira": ("jira",),
    "UML": ("uml",),
    "Figma": ("figma",),
    "Cybersécurité": ("cybersécurité", "cybersecurite", "cyber sécurité", "sécurité informatique"),
    "OAuth2": ("oauth2", "oauth"),
    "JWT": ("jwt",),
}

_BOUNDARY_BEFORE = r"(?<![0-9A-Za-zÀ-ÿ+#.])"
_BOUNDARY_AFTER = r"(?![0-9A-Za-zÀ-ÿ+#])"


def _pattern(alias: str) -> re.Pattern:
    body = re.escape(alias).replace(r"\ ", r"\s+")
    # Une variante écrite avec une majuscule (« Go », « JS », « REST ») n'est reconnue qu'à l'identique.
    flags = 0 if alias != alias.lower() else re.IGNORECASE
    return re.compile(f"{_BOUNDARY_BEFORE}{body}{_BOUNDARY_AFTER}", flags)


# (canonique, motif) trié par variante la plus longue d'abord, pour que « Spring Boot »
# soit reconnu avant « Spring ».
_PATTERNS: list[tuple[str, re.Pattern]] = [
    (canonical, _pattern(alias))
    for canonical, aliases in CATALOG.items()
    for alias in sorted(aliases, key=len, reverse=True)
]

_KEY_TO_CANONICAL: dict[str, str] = {
    normalize(alias): canonical for canonical, aliases in CATALOG.items() for alias in (canonical, *aliases)
}


def skill_key(skill: str) -> str:
    """Clé de comparaison d'une compétence : « Postgres » et « PostgreSQL » donnent la même."""
    key = normalize(skill)
    canonical = _KEY_TO_CANONICAL.get(key)
    return normalize(canonical) if canonical else key


def extract_skills(text: str) -> list[str]:
    """Compétences du catalogue présentes dans `text`, dans l'ordre d'apparition, sans doublon.

    Une compétence incluse dans une autre déjà reconnue au même endroit (« Spring » dans
    « Spring Boot ») n'est pas comptée à part.
    """
    found: list[tuple[int, int, str]] = []
    for canonical, pattern in _PATTERNS:
        for match in pattern.finditer(text):
            found.append((match.start(), match.end(), canonical))
    found.sort(key=lambda item: (item[0], -(item[1] - item[0])))

    result: list[str] = []
    last_end = -1
    for start, end, canonical in found:
        if start < last_end:
            continue
        last_end = end
        if canonical not in result:
            result.append(canonical)
    return result
