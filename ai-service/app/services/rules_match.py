"""Matching, écart de compétences et recommandation par règles, sans LLM.

Les explications citent les éléments constatés dans le CV et l'offre ; le score global reste
calculé par `scoring.compute_global_score`, comme avec le LLM.
"""

from __future__ import annotations

import re
from datetime import date
from typing import TYPE_CHECKING

from app.schemas.common import CVParsingResult, Recommendation, RecommendationResult
from app.schemas.enums import ExperienceLevel
from app.services.rules_job import years_expected
from app.services.scoring import compute_global_score
from app.services.skills_catalog import extract_skills, skill_key

if TYPE_CHECKING:
    from app.services.job_analysis_service import AnalyzedJob

MAX_RECOMMENDATIONS = 10

_DEGREE_RANKS = [
    (8, r"doctorat|phd"),
    (5, r"master|mast[eè]re|ing[ée]nieur|mba|bac\s*\+\s*5"),
    (3, r"licence|license|bachelor|bac\s*\+\s*3|ma[iî]trise"),
    (2, r"bts|dut|deug|bac\s*\+\s*2|technicien\s+sup[ée]rieur"),
    (1, r"baccalaur[ée]at|\bbac\b"),
]
_DEGREE_NEEDED = {
    ExperienceLevel.JUNIOR: 2, ExperienceLevel.INTERMEDIATE: 3, ExperienceLevel.SENIOR: 5, ExperienceLevel.LEAD: 5,
}


def degree_rank(degree: str) -> int | None:
    return next((rank for rank, pattern in _DEGREE_RANKS if re.search(pattern, degree, re.IGNORECASE)), None)


def experience_years(cv: CVParsingResult, today: date | None = None) -> float:
    """Durée cumulée des expériences, périodes qui se chevauchent comptées une seule fois."""
    today = today or date.today()
    periods = sorted((e.start_date, e.end_date or today) for e in cv.experiences)
    months, current_end = 0, None
    for start, end in periods:
        if current_end is None or start > current_end:
            months += (end.year - start.year) * 12 + end.month - start.month
            current_end = end
        elif end > current_end:
            months += (end.year - current_end.year) * 12 + end.month - current_end.month
            current_end = end
    return max(months, 0) / 12


def cv_skill_keys(cv: CVParsingResult) -> set[str]:
    """Compétences du CV, y compris celles citées dans les intitulés et descriptions d'expérience."""
    keys = {skill_key(s) for s in cv.skills}
    for experience in cv.experiences:
        keys.update(skill_key(s) for s in extract_skills(f"{experience.title} {experience.description}"))
    return keys


def skill_gap_rules(cv: CVParsingResult, job: AnalyzedJob) -> list[str]:
    owned = cv_skill_keys(cv)
    missing: list[str] = []
    for skill in job.analysis.required_skills:
        if skill_key(skill) not in owned and skill not in missing:
            missing.append(skill)
    return missing


def _fmt_years(years: float) -> str:
    return f"{years:.1f}".replace(".", ",").removesuffix(",0")


def _list(items: list[str]) -> str:
    return ", ".join(items)


LEVEL_LABELS = {
    ExperienceLevel.JUNIOR: "junior", ExperienceLevel.INTERMEDIATE: "confirmé",
    ExperienceLevel.SENIOR: "senior", ExperienceLevel.LEAD: "lead",
}


def _years_phrase(years: float) -> str:
    return f"{_fmt_years(years)} an{'s' if years >= 2 else ''}"


def match_criteria_rules(cv: CVParsingResult, job: AnalyzedJob) -> dict[str, tuple[float | None, str]]:
    """Sous-score (0-100, None si non évaluable) et explication de chaque critère."""
    required = job.analysis.required_skills
    owned = cv_skill_keys(cv)
    covered = [s for s in required if skill_key(s) in owned]
    missing = [s for s in required if s not in covered]
    if required and (cv.skills or owned):
        plural = "s" if len(covered) > 1 else ""
        if covered:
            explanation = f"{len(covered)} compétence{plural} requise{plural} sur {len(required)} présente{plural} dans le CV"
            explanation += f" ({_list(covered)})"
        else:
            explanation = (f"Aucune des {len(required)} compétences requises n'est présente dans le CV"
                           if len(required) > 1 else "La compétence requise n'est pas présente dans le CV")
        explanation += f" ; manque : {_list(missing)}." if missing else "."
        skills = (round(100 * len(covered) / len(required), 1), explanation)
    else:
        skills = (None, "compétences non comparables.")

    level = job.analysis.experience_level
    expected = years_expected(level)
    if cv.experiences:
        years = experience_years(cv)
        score = 100.0 if years >= expected else round(100 * years / expected, 1)
        wanted = f"{_years_phrase(expected)} attendus" if expected else "aucune expérience exigée"
        experience = (score, f"{_years_phrase(years)} d'expérience cumulée pour un poste de niveau "
                             f"{LEVEL_LABELS[level]} ({wanted}).")
    else:
        experience = (None, "aucune expérience extraite du CV.")

    ranks = [r for r in (degree_rank(e.degree) for e in cv.education) if r is not None]
    if ranks:
        best = max(ranks)
        needed = _DEGREE_NEEDED[level]
        top = next(e.degree for e in cv.education if degree_rank(e.degree) == best)
        education = (min(100.0, round(100 * best / needed, 1)),
                     f"Diplôme le plus élevé : {top}, pour un poste de niveau {LEVEL_LABELS[level]}.")
    else:
        education = (None, "niveau de diplôme non identifiable dans le CV.")
    return {"skills": skills, "experience": experience, "education": education}


def recommend_rules(cv: CVParsingResult, jobs: list[AnalyzedJob]) -> RecommendationResult:
    """Offres ayant au moins une compétence requise en commun, classées par le même score que le matching."""
    owned = cv_skill_keys(cv)
    ranked = []
    for job in jobs:
        if not any(skill_key(s) in owned for s in job.analysis.required_skills):
            continue
        sub_scores = {c: s for c, (s, _) in match_criteria_rules(cv, job).items() if s is not None}
        ranked.append((compute_global_score(sub_scores), job))
    ranked.sort(key=lambda pair: pair[0], reverse=True)
    return RecommendationResult(
        recommendations=[Recommendation(job_id=job.job_id, score=score) for score, job in ranked[:MAX_RECOMMENDATIONS]]
    )
