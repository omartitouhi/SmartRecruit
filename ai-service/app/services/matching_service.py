import json
from datetime import date

from pydantic import ConfigDict, Field

from app.core.config import settings
from app.schemas.common import CamelModel, CVParsingResult, MatchCriterion, MatchScore
from app.services.job_analysis_service import AnalyzedJob
from app.services.llm_client import OpenRouterClient
from app.services.scoring import CRITERIA_WEIGHTS, compute_global_score

CRITERIA_LABELS = {
    "skills": "Compétences techniques",
    "experience": "Expérience",
    "education": "Formation",
}
INSUFFICIENT_DATA = "Données insuffisantes"
NO_EVALUABLE_CRITERION = f"{INSUFFICIENT_DATA} : aucun critère de compatibilité n'est évaluable."

MAX_JOB_DESCRIPTION_CHARS = 3_000

SYSTEM_PROMPT = """Tu évalues l'adéquation d'un CV à une offre d'emploi, critère par critère, pour aider
un recruteur (tu ne prends aucune décision de recrutement). Tu réponds UNIQUEMENT par un objet JSON :
{"skills": {"subScore": number|null, "explanation": string},
 "experience": {"subScore": number|null, "explanation": string},
 "education": {"subScore": number|null, "explanation": string}}
- subScore : entre 0 et 100, ou null si les données sont insuffisantes pour évaluer le critère
  (explique alors ce qui manque). Ne donne jamais un score arbitraire.
- skills : compétences requises couvertes par le CV, en tenant compte des équivalences
  (ex. Postgres = PostgreSQL) et des technologies proches.
- experience : durée et pertinence de l'expérience au regard du niveau attendu
  (JUNIOR 0-2 ans, INTERMEDIATE 2-5 ans, SENIOR 5-8 ans, LEAD 8 ans et +).
- education : adéquation des diplômes au poste.
- explanation : une ou deux phrases factuelles en français, citant les éléments du CV et de l'offre.
  N'invente aucun élément absent des données fournies."""


class _Strict(CamelModel):
    model_config = ConfigDict(extra="forbid")


class CriterionEvaluation(_Strict):
    # None : le LLM juge les données insuffisantes pour ce critère (AI-MATCH-03).
    sub_score: float | None = Field(ge=0, le=100)
    explanation: str = Field(min_length=1)


class MatchEvaluation(_Strict):
    """Sortie du LLM pour AI-MATCH-01, validée par schéma strict."""

    skills: CriterionEvaluation
    experience: CriterionEvaluation
    education: CriterionEvaluation


class InsufficientDataError(Exception):
    """Aucun critère évaluable : pas de score plutôt qu'un score arbitraire -> 422."""


def _prompt(cv: CVParsingResult, job: AnalyzedJob) -> str:
    offer = {
        "title": job.title,
        **job.analysis.model_dump(mode="json", by_alias=True),
        "description": job.description[:MAX_JOB_DESCRIPTION_CHARS],
    }
    return (
        f"Date du jour : {date.today().isoformat()}\n\n"
        f"CV (JSON) :\n{cv.model_dump_json(by_alias=True)}\n\n"
        f"Offre (JSON) :\n{json.dumps(offer, ensure_ascii=False)}"
    )


async def evaluate_match(cv: CVParsingResult, job: AnalyzedJob, llm: OpenRouterClient) -> MatchEvaluation:
    """Évaluation sémantique CV/offre par le LLM, critère par critère (AI-MATCH-01).

    Une sortie non conforme (sous-score hors 0-100, critère manquant, champ inconnu)
    ou un échec OpenRouter lève LLMError (502) / LLMNotConfiguredError (503).
    """
    return await llm.complete_json(
        SYSTEM_PROMPT, _prompt(cv, job), MatchEvaluation, timeout_seconds=settings.matching_timeout_seconds
    )


def missing_data(cv: CVParsingResult, job: AnalyzedJob) -> dict[str, str]:
    """Critères impossibles à évaluer faute de données, avec la raison (AI-MATCH-03)."""
    missing = {}
    if not job.analysis.required_skills:
        missing["skills"] = "l'offre ne précise aucune compétence requise."
    elif not cv.skills:
        missing["skills"] = "aucune compétence n'a été extraite du CV."
    if not cv.experiences:
        missing["experience"] = "aucune expérience n'a été extraite du CV."
    if not cv.education:
        missing["education"] = "aucun diplôme n'a été extrait du CV."
    return missing


def explain_score(evaluation: MatchEvaluation, missing: dict[str, str] | None = None) -> MatchScore:
    """Décompose le score en critères explicables (AI-MATCH-03), au format MatchScore.

    Pour chaque critère : poids, contribution = poids × sous-score (calculée ici, pas
    par le LLM) et explication du LLM. Un critère sans données suffisantes (constat du
    code ou sous-score null du LLM) reçoit un poids et une contribution nuls et
    l'explication « Données insuffisantes » ; le score se répartit sur les autres.
    """
    missing = dict(missing or {})
    for criterion in CRITERIA_WEIGHTS:
        result = getattr(evaluation, criterion)
        if criterion not in missing and result.sub_score is None:
            missing[criterion] = result.explanation

    sub_scores = {
        criterion: getattr(evaluation, criterion).sub_score
        for criterion in CRITERIA_WEIGHTS
        if criterion not in missing
    }
    if not sub_scores:
        raise InsufficientDataError(NO_EVALUABLE_CRITERION)

    score = compute_global_score(sub_scores)
    total_weight = sum(CRITERIA_WEIGHTS[criterion] for criterion in sub_scores)
    criteria = []
    for criterion in CRITERIA_WEIGHTS:
        if criterion in missing:
            criteria.append(MatchCriterion(
                label=CRITERIA_LABELS[criterion],
                weight=0.0,
                contribution=0.0,
                explanation=f"{INSUFFICIENT_DATA} : {missing[criterion]}",
            ))
            continue
        weight = CRITERIA_WEIGHTS[criterion] / total_weight
        criteria.append(MatchCriterion(
            label=CRITERIA_LABELS[criterion],
            weight=round(weight, 4),
            contribution=round(weight * sub_scores[criterion], 2),
            explanation=getattr(evaluation, criterion).explanation,
        ))
    return MatchScore(score=score, criteria=criteria)


async def score_match(cv: CVParsingResult, job: AnalyzedJob, llm: OpenRouterClient) -> MatchScore:
    """Évaluation LLM (AI-MATCH-01) + score pondéré (AI-MATCH-02) + explication (AI-MATCH-03).

    Sans aucun critère évaluable, le LLM n'est même pas appelé : InsufficientDataError.
    """
    missing = missing_data(cv, job)
    if len(missing) == len(CRITERIA_WEIGHTS):
        raise InsufficientDataError(NO_EVALUABLE_CRITERION)
    return explain_score(await evaluate_match(cv, job, llm), missing)
