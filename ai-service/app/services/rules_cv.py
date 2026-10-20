"""CV -> données structurées par règles (sections, dates, catalogue de compétences), sans LLM.

Chaque valeur est lue dans le texte du CV : rien n'est inventé.
"""

import re
import unicodedata
from datetime import date

from app.schemas.common import CVParsingResult, ParsedEducation, ParsedExperience
from app.services.skills_catalog import extract_skills, skill_key

NOT_SPECIFIED = "Non précisé"

# --- Découpage du CV en sections ---------------------------------------------------------

_SECTION_TITLES = {
    "exp": r"exp[ée]riences?(?:\s+professionnelles?)?|parcours\s+professionnel|work\s+experience"
           r"|professional\s+experience|employment(?:\s+history)?",
    "edu": r"formations?(?:\s+et\s+dipl[oô]mes?)?|[ée]ducation|dipl[oô]mes?|[ée]tudes|parcours\s+(?:acad[ée]mique|universitaire)"
           r"|academic\s+background",
    "skills": r"comp[ée]tences?(?:\s+(?:cl[ée]s|techniques|informatiques))?|skills|technical\s+skills|technologies",
    "lang": r"langues?|languages?",
    "other": r"projets?|certifications?|centres?\s+d.int[ée]r[eê]ts?|loisirs|hobbies|profil|summary|contact"
             r"|r[ée]f[ée]rences?|interests|objectif",
}
_SECTION_RE = {
    kind: re.compile(rf"^(?:{titles})\s*:?$", re.IGNORECASE) for kind, titles in _SECTION_TITLES.items()
}


def _sections(text: str) -> dict[str, list[str]]:
    sections: dict[str, list[str]] = {"header": []}
    current = "header"
    for raw in text.splitlines():
        line = raw.strip()
        if not line:
            continue
        kind = next((k for k, rx in _SECTION_RE.items() if rx.match(line)), None)
        if kind:
            current = kind
            sections.setdefault(current, [])
        else:
            sections[current].append(line)
    return sections


# --- Dates --------------------------------------------------------------------------------

_MONTHS = {
    "jan": 1, "fev": 2, "feb": 2, "mar": 3, "avr": 4, "apr": 4, "mai": 5, "may": 5, "jun": 6,
    "juin": 6, "jul": 7, "juil": 7, "aou": 8, "aug": 8, "sep": 9, "oct": 10, "nov": 11, "dec": 12,
}
_MONTH_WORD = (
    r"(?:janv(?:ier)?|jan(?:uary)?|f[ée]v(?:rier)?|feb(?:ruary)?|mars?|march|avr(?:il)?|apr(?:il)?|mai|may"
    r"|juin|june?|juil(?:let)?|july?|ao[uû]t?|aug(?:ust)?|sept?(?:embre|ember)?|oct(?:obre|ober)?"
    r"|nov(?:embre|ember)?|d[ée]c(?:embre|ember)?)"
)
_DATE = rf"(?:\d{{4}}[-/.]\d{{1,2}}|\d{{1,2}}[-/.]\d{{4}}|{_MONTH_WORD}\.?\s+\d{{4}}|\d{{4}})"
_NOW = r"(?:pr[ée]sent|actuel(?:lement)?|aujourd.hui|maintenant|now|current|en\s+cours|ce\s+jour)"
_RANGE = re.compile(
    rf"(?<![\d/.-])(?P<s>{_DATE})\s*(?:-|–|—|à|au|to|jusqu.à)\s*(?P<e>{_DATE}|{_NOW})(?![\d])", re.IGNORECASE
)
_YEAR = re.compile(r"\b(?:19|20)\d{2}\b")


def _fold(value: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFKD", value.lower()) if not unicodedata.combining(c))


def _parse_date(token: str, end: bool = False) -> date | None:
    token = token.strip()
    if re.fullmatch(_NOW, token, re.IGNORECASE):
        return None
    try:
        if m := re.fullmatch(r"(\d{4})[-/.](\d{1,2})", token):
            return date(int(m[1]), int(m[2]), 1)
        if m := re.fullmatch(r"(\d{1,2})[-/.](\d{4})", token):
            return date(int(m[2]), int(m[1]), 1)
        if m := re.fullmatch(r"(\d{4})", token):
            return date(int(m[1]), 12 if end else 1, 1)
        if m := re.fullmatch(rf"({_MONTH_WORD})\.?\s+(\d{{4}})", token, re.IGNORECASE):
            month = next(v for k, v in _MONTHS.items() if _fold(m[1]).startswith(k))
            return date(int(m[2]), month, 1)
    except ValueError:
        return None
    return None


# --- CV -----------------------------------------------------------------------------------

_JOB_WORDS = re.compile(
    r"d[ée]veloppeu|developer|ing[ée]nieur|engineer|stagiaire|intern|analyste|analyst|manager|chef|lead|architecte"
    r"|consultant|designer|devops|data|responsable|administrateur|technicien|testeur|tester|freelance|assistant",
    re.IGNORECASE,
)
_SPLIT = re.compile(r"\s+(?:chez|at|@)\s+|\s*[-–—|]\s+|\s*,\s+|\s+\(", re.IGNORECASE)
_BULLET = re.compile(r"^[\s•·▪*>\-–—]+")
_SCHOOL = re.compile(
    r"[ée]cole|ecole|universit|facult|institut|iset|esprit|insat|enit|isi\b|ihec|polytechn|isg|issat|supcom|sesame"
    r"|university|school|college|lyc[ée]e|centre",
    re.IGNORECASE,
)
_DEGREE = re.compile(
    r"master|mast[eè]re|licence|license|bachelor|ing[ée]nieur|doctorat|phd|mba|bts|dut|dipl[oô]me|baccalaur[ée]at"
    r"|bac\s*\+\s*\d|\bbac\b|technicien\s+sup[ée]rieur|deug|ma[iî]trise|cycle\s+pr[ée]paratoire",
    re.IGNORECASE,
)
_LANGUAGES = {
    "Français": r"fran[cç]ais|french",
    "Anglais": r"anglais|english",
    "Arabe": r"arabe|arabic",
    "Espagnol": r"espagnol|spanish",
    "Allemand": r"allemand|german|deutsch",
    "Italien": r"italien|italian",
}


def _clean(value: str) -> str:
    return re.sub(r"\s+", " ", _BULLET.sub("", value)).strip(" ()[]-–—|,:;·•")


def _split_head(head: str) -> tuple[str, str]:
    """« Titre - Entreprise » (ou « Titre chez Entreprise », « Titre, Entreprise ») -> (titre, entreprise)."""
    parts = [p for p in (_clean(p) for p in _SPLIT.split(head, maxsplit=1)) if p]
    if not parts:
        return "", ""
    if len(parts) == 1:
        return parts[0], ""
    first, second = parts
    if _JOB_WORDS.search(second) and not _JOB_WORDS.search(first):
        first, second = second, first
    return first, second


def _experiences(lines: list[str]) -> list[ParsedExperience]:
    heads = []  # (ligne de la période, période, texte d'en-tête, ligne où commence l'entrée)
    for i, line in enumerate(lines):
        if not (m := _RANGE.search(line)):
            continue
        head = _clean(line[: m.start()] + " " + line[m.end():])
        first = i
        if not head and i > 0 and not _RANGE.search(lines[i - 1]):
            head, first = _clean(lines[i - 1]), i - 1  # « Titre - Entreprise » écrit au-dessus des dates
        heads.append((i, m, head, first))

    experiences = []
    for n, (index, match, head, _) in enumerate(heads):
        start = _parse_date(match["s"])
        end = _parse_date(match["e"], end=True)
        title, company = _split_head(head)
        if start is None or not title or (end and end < start):
            continue
        stop = heads[n + 1][3] if n + 1 < len(heads) else len(lines)
        description = " ".join(_clean(x) for x in lines[index + 1:stop])[:500]
        experiences.append(ParsedExperience(
            title=title, company=company or NOT_SPECIFIED, start_date=start, end_date=end, description=description,
        ))
    return experiences


def _education(lines: list[str]) -> list[ParsedEducation]:
    entries = []
    for i, line in enumerate(lines):
        if not _DEGREE.search(line):
            continue
        # L'année est celle de la ligne (fin de période si c'en est une), à défaut celle de la ligne suivante.
        years = next((_YEAR.findall(window) for window in (line, *lines[i + 1:i + 2]) if _YEAR.search(window)), [])
        if not years:
            continue
        year = int(years[-1])
        text = _clean(_YEAR.sub("", _RANGE.sub("", line)))
        parts = [p for p in (_clean(p) for p in _SPLIT.split(text)) if p]
        if not parts:
            continue
        degree = next((p for p in parts if _DEGREE.search(p)), parts[0])
        institution = next((p for p in parts if p != degree and _SCHOOL.search(p)), "")
        if not institution:
            institution = next((p for p in parts if p != degree), "")
        if not institution and i + 1 < len(lines) and not _DEGREE.search(lines[i + 1]):
            institution = _clean(_YEAR.sub("", _RANGE.sub("", lines[i + 1])))
        entries.append(ParsedEducation(degree=degree, institution=institution or NOT_SPECIFIED, year=year))
    return entries


def _listed_skills(lines: list[str]) -> list[str]:
    """Éléments d'une rubrique « Compétences » (« Langages : Java, Go ») absents du catalogue."""
    skills = []
    for line in lines:
        label, _, content = line.partition(":")
        if not content:
            content = label
        elif re.fullmatch(r"\s*(?:langues?|languages?)\s*", label, re.IGNORECASE):
            continue  # « Langues : Arabe, Français » est une rubrique de langues, pas de compétences
        for token in re.split(r"[,;•|·/\n]", content):
            token = _clean(token)
            if 2 <= len(token) <= 30 and len(token.split()) <= 3 and re.search(r"[A-Za-z]", token):
                skills.append(token)
    return skills


def parse_cv_rules(text: str) -> CVParsingResult:
    """Texte brut du CV -> données structurées, par règles (sections, dates, catalogue de compétences)."""
    sections = _sections(text)
    skill_lines = sections.get("skills", [])
    by_key: dict[str, str] = {}
    for skill in [*extract_skills(text), *_listed_skills(skill_lines)]:
        by_key.setdefault(skill_key(skill), skill)  # « Postgres » et « PostgreSQL » ne comptent qu'une fois
    skills = list(by_key.values())

    exp_lines = sections.get("exp") or [
        line for kind in ("header", "other") for line in sections.get(kind, [])
    ]
    edu_lines = sections.get("edu") or [
        line for kind in ("header", "other") for line in sections.get(kind, [])
    ]
    lang_source = "\n".join(sections.get("lang") or [text])
    languages = [
        name for name, pattern in _LANGUAGES.items() if re.search(rf"\b(?:{pattern})\b", lang_source, re.IGNORECASE)
    ]
    return CVParsingResult(
        skills=skills,
        experiences=_experiences(exp_lines),
        education=_education(edu_lines),
        languages=languages,
    )
