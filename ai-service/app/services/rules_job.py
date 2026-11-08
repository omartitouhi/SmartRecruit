"""Offre d'emploi -> compétences et niveau par règles, sans LLM."""

import re

from app.schemas.common import JobAnalysisResult
from app.schemas.enums import ExperienceLevel
from app.services.skills_catalog import extract_skills

_YEARS = re.compile(r"(\d{1,2})\s*(?:\+|à\s*\d{1,2})?\s*(?:ans?|years?|yrs?)\b", re.IGNORECASE)
_LEVEL_WORDS = [
    (ExperienceLevel.LEAD, r"\blead\b|tech\s*lead|principal|architecte|head\s+of|responsable\s+(?:d.[ée]quipe|technique)"
                           r"|encadr(?:er|ez|ement)|manager\s+(?:une|des|d.une)\s+[ée]quipe|r[ée]f[ée]rent"),
    (ExperienceLevel.SENIOR, r"senior|confirm[ée]|exp[ée]riment[ée]"),
    (ExperienceLevel.JUNIOR, r"junior|d[ée]butant|stagiaire|stage\b|alternan|internship|graduate|fresh"),
]
_LEVEL_RANGES = [  # (niveau, années minimales)
    (ExperienceLevel.JUNIOR, 0), (ExperienceLevel.INTERMEDIATE, 2), (ExperienceLevel.SENIOR, 5), (ExperienceLevel.LEAD, 8),
]


def level_for_years(years: float) -> ExperienceLevel:
    return [level for level, minimum in _LEVEL_RANGES if years >= minimum][-1]


def years_expected(level: ExperienceLevel) -> int:
    return dict(_LEVEL_RANGES)[level]


def analyze_job_rules(description: str, title: str | None = None) -> JobAnalysisResult:
    """Offre en texte libre -> compétences du catalogue citées, niveau déduit des années et des mots-clés."""
    source = f"{title or ''}\n{description}"
    keyword = next(
        (level for level, pattern in _LEVEL_WORDS if re.search(pattern, source, re.IGNORECASE)), None
    )
    years = [int(m[1]) for m in _YEARS.finditer(source) if int(m[1]) <= 40]
    if years:
        experience = level_for_years(min(years))
    else:
        experience = keyword or ExperienceLevel.INTERMEDIATE
    seniority = ExperienceLevel.LEAD if keyword == ExperienceLevel.LEAD else (keyword or experience)
    return JobAnalysisResult(
        required_skills=extract_skills(source), experience_level=experience, seniority=seniority
    )
