import re
from datetime import date

from pydantic import BaseModel, ValidationError

from app.schemas.common import CVParsingResult, ParsedEducation, ParsedExperience

# shortcut: extraction par règles + vocabulaire fermé, sans LLM/spaCy ; à remplacer
# par un vrai NER quand le corpus de test montrera les limites (voir AI-CV-02).
SKILLS = [
    "Java", "Spring Boot", "Kotlin", "Python", "FastAPI", "Django", "Flask",
    "JavaScript", "TypeScript", "React", "Angular", "Vue.js", "Node.js", "HTML", "CSS",
    "C#", ".NET", "C++", "PHP", "Laravel", "Go", "Rust", "SQL", "PostgreSQL", "MySQL",
    "MongoDB", "Redis", "Docker", "Kubernetes", "Git", "Jenkins", "CI/CD", "Linux",
    "AWS", "Azure", "GCP", "Terraform", "Maven", "Hibernate", "GraphQL", "REST",
    "Microservices", "Kafka", "RabbitMQ", "TensorFlow", "PyTorch", "scikit-learn",
    "Pandas", "NumPy", "spaCy", "Machine Learning", "Deep Learning", "NLP", "Scrum", "Agile",
]
LANGUAGES = {
    "Français": r"fran[cç]ais|french",
    "Anglais": r"anglais|english",
    "Arabe": r"arabe|arabic",
    "Espagnol": r"espagnol|spanish",
    "Allemand": r"allemand|german",
    "Italien": r"italien|italian",
}
HEADINGS = {
    "skills": r"comp[eé]tences|skills",
    "experiences": r"exp[eé]riences?|work experience",
    "education": r"formations?|[eé]ducation|dipl[oô]mes",
    "languages": r"langues|languages",
}

_TOKEN = r"(?:\d{1,2}[/.-]\d{4}|(?:[A-Za-zéûÉ]{3,9}\.?\s+)?\d{4}(?:[-/]\d{1,2})?)"
_RANGE = re.compile(
    rf"(?P<s>{_TOKEN})\s*(?:-|–|—|to|à)\s*(?P<e>{_TOKEN}|pr[eé]sent|present|aujourd'hui|current|now)",
    re.IGNORECASE,
)
_YEAR = re.compile(r"(?:19|20)\d{2}")
_SPLIT = re.compile(r"\s+(?:-|–|—|@|chez|at)\s+|\s*[,|]\s*")
_MONTHS = {"jan": 1, "feb": 2, "fev": 2, "fév": 2, "mar": 3, "apr": 4, "avr": 4, "mai": 5,
           "may": 5, "jun": 6, "jul": 7, "aug": 8, "aou": 8, "aoû": 8, "sep": 9, "oct": 10,
           "nov": 11, "dec": 12, "déc": 12}


def _to_date(token: str) -> date | None:
    token = token.strip()
    if m := re.fullmatch(r"(\d{1,2})[/.-](\d{4})", token):
        return _safe_date(int(m[2]), int(m[1]))
    if m := re.fullmatch(r"(\d{4})[-/](\d{1,2})", token):
        return _safe_date(int(m[1]), int(m[2]))
    if m := re.fullmatch(r"([A-Za-zéûÉ]{3,9})\.?\s+(\d{4})", token):
        name = m[1].lower()
        month = 7 if name.startswith("juil") else 6 if name.startswith("jui") else _MONTHS.get(name[:3])
        return _safe_date(int(m[2]), month) if month else None
    return _safe_date(int(token), 1) if re.fullmatch(r"\d{4}", token) else None


def _safe_date(year: int, month: int) -> date | None:
    try:
        return date(year, month, 1)
    except ValueError:
        return None


def _split_sections(text: str) -> dict[str, list[str]]:
    sections: dict[str, list[str]] = {key: [] for key in HEADINGS}
    current = None
    for raw in text.splitlines():
        line = raw.strip()
        heading = line.rstrip(":").strip().lower()
        key = next((k for k, p in HEADINGS.items() if len(line) < 40 and re.match(rf"(?:{p})\b", heading)), None)
        if key:
            current = key
        elif line and current:
            sections[current].append(line)
    return sections


def _clean(value: str) -> str:
    return value.strip(" -–—|,()[]•*·")


def _valid(model: type[BaseModel], **fields):
    """Garde-fou anti-hallucination : toute entrée non conforme au schéma est rejetée."""
    try:
        return model(**fields)
    except ValidationError:
        return None


def _skills(text: str) -> list[str]:
    found = {s: re.search(rf"(?<![\w+#.]){re.escape(s)}(?![\w+#])", text, re.IGNORECASE) for s in SKILLS}
    return sorted((s for s, m in found.items() if m), key=lambda s: found[s].start())


def _languages(lines: list[str]) -> list[str]:
    text = " ".join(lines)
    return [name for name, pattern in LANGUAGES.items() if re.search(rf"\b(?:{pattern})\b", text, re.IGNORECASE)]


def _experiences(lines: list[str]) -> list[ParsedExperience]:
    entries: list[dict] = []
    for line in lines:
        rng = _RANGE.search(line)
        if rng:
            start, end = _to_date(rng["s"]), _to_date(rng["e"])
            parts = _SPLIT.split(_clean(line[: rng.start()] + " " + line[rng.end():]), maxsplit=1)
            entries.append({
                "title": _clean(parts[0]),
                "company": _clean(parts[1]) if len(parts) > 1 else "",
                "start_date": start, "end_date": end, "description": [],
            })
        elif entries:
            entries[-1]["description"].append(_clean(line))
    result = []
    for e in entries:
        if not e["title"] or not e["company"]:
            continue
        item = _valid(ParsedExperience, **{**e, "description": " ".join(e["description"])})
        if item:
            result.append(item)
    return result


def _education(lines: list[str]) -> list[ParsedEducation]:
    result = []
    for line in lines:
        years = _YEAR.findall(line)
        if not years:
            continue
        parts = _SPLIT.split(_clean(_YEAR.sub("", line)), maxsplit=1)
        if len(parts) < 2:
            continue
        item = _valid(ParsedEducation, degree=_clean(parts[0]), institution=_clean(parts[1]), year=int(years[-1]))
        if item and item.degree and item.institution:
            result.append(item)
    return result


def parse_cv_text(text: str) -> CVParsingResult:
    """Texte brut du CV (AI-CV-01) -> JSON structuré validé par le schéma strict (AI-CV-02)."""
    sections = _split_sections(text)
    return CVParsingResult(
        skills=_skills(text),
        experiences=_experiences(sections["experiences"]),
        education=_education(sections["education"]),
        languages=_languages(sections["languages"]),
    )
