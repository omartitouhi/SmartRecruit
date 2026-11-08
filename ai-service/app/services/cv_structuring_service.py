import logging
import re

from pydantic import BaseModel, ConfigDict, ValidationError

from app.core.config import settings
from app.schemas.common import CVParsingResult, ParsedEducation, ParsedExperience
from app.services.grounding import appears_in, dedupe
from app.services.llm_client import OpenRouterClient
from app.services.rules_cv import parse_cv_rules

log = logging.getLogger(__name__)

# Au-delà, le texte est tronqué : un CV dépasse rarement quelques pages.
MAX_CV_TEXT_CHARS = 15_000

# Nom canonique (français) -> formes acceptées dans le texte du CV.
LANGUAGE_ALIASES = {
    "Français": r"fran[cç]ais|french",
    "Anglais": r"anglais|english",
    "Arabe": r"arabe|arabic",
    "Espagnol": r"espagnol|spanish",
    "Allemand": r"allemand|german|deutsch",
    "Italien": r"italien|italian",
}

SYSTEM_PROMPT = """Tu es un extracteur de données de CV. Tu réponds UNIQUEMENT par un objet JSON :
{"skills": [string], "experiences": [{"title": string, "company": string, "startDate": "YYYY-MM-DD",
"endDate": "YYYY-MM-DD" | null, "description": string}], "education": [{"degree": string,
"institution": string, "year": integer}], "languages": [string]}
Règles :
- N'invente rien : chaque compétence, entreprise et établissement doit être recopié tel qu'il est écrit dans le CV.
- Dates au format YYYY-MM-DD avec le jour à 01 ; endDate = null si le poste est en cours.
- "year" = année d'obtention (ou de fin) du diplôme.
- Langues parlées, nommées en français (ex. "Anglais").
- Si une information est absente, renvoie une liste vide plutôt que de deviner."""


class _StrictExperience(ParsedExperience):
    model_config = ConfigDict(extra="forbid")


class _StrictEducation(ParsedEducation):
    model_config = ConfigDict(extra="forbid")


class LLMCVOutput(BaseModel):
    """Forme brute attendue du LLM. Les entrées sont revalidées une à une ensuite."""

    model_config = ConfigDict(extra="forbid")

    skills: list[str]
    experiences: list[dict]
    education: list[dict]
    languages: list[str]


def _valid_entries(model: type[BaseModel], entries: list[dict]) -> list:
    """Garde-fou anti-hallucination : toute entrée non conforme au schéma est rejetée."""
    result = []
    for entry in entries:
        try:
            result.append(model.model_validate(entry))
        except ValidationError:
            log.info("Entrée de CV rejetée (non conforme au schéma) : %s", entry)
    return result


def _language_in_text(language: str, text: str) -> bool:
    pattern = LANGUAGE_ALIASES.get(language)
    if pattern:
        return re.search(rf"\b(?:{pattern})\b", text, re.IGNORECASE) is not None
    return appears_in(language, text)


def ground_cv_output(output: LLMCVOutput, text: str) -> CVParsingResult:
    """Ne conserve que les données conformes au schéma ET présentes dans le texte source."""
    experiences = [
        e for e in _valid_entries(_StrictExperience, output.experiences)
        if e.title.strip() and appears_in(e.company, text)
    ]
    education = [
        e for e in _valid_entries(_StrictEducation, output.education)
        if e.degree.strip() and appears_in(e.institution, text)
    ]
    return CVParsingResult(
        skills=dedupe([s for s in output.skills if appears_in(s, text)]),
        experiences=[ParsedExperience(**e.model_dump()) for e in experiences],
        education=[ParsedEducation(**e.model_dump()) for e in education],
        languages=dedupe([lang for lang in output.languages if _language_in_text(lang, text)]),
    )


async def parse_cv_text(text: str, llm: OpenRouterClient) -> CVParsingResult:
    """Texte brut du CV (AI-CV-01) -> JSON structuré par le LLM (OpenRouter), validé
    par schéma strict et confronté au texte source (AI-CV-02).

    Lève `LLMNotConfiguredError` / `LLMError` : aucune donnée n'est inventée en cas d'échec.
    """
    if settings.ai_engine == "rules":
        return parse_cv_rules(text)
    output = await llm.complete_json(
        SYSTEM_PROMPT, f"Texte du CV :\n\"\"\"\n{text[:MAX_CV_TEXT_CHARS]}\n\"\"\"", LLMCVOutput
    )
    return ground_cv_output(output, text)
