import json
import logging

from pydantic import BaseModel, ConfigDict

from app.schemas.common import CVParsingResult, SkillGapResult
from app.services.grounding import normalize
from app.services.job_analysis_service import AnalyzedJob
from app.services.llm_client import OpenRouterClient

log = logging.getLogger(__name__)

SYSTEM_PROMPT = """Tu identifies les compétences manquantes d'un candidat pour une offre. Tu réponds
UNIQUEMENT par un objet JSON : {"missingSkills": [string]}
- missingSkills : compétences de la liste requiredSkills de l'offre que le candidat ne possède pas,
  recopiées exactement comme dans requiredSkills.
- Tiens compte des équivalences et variantes d'écriture (ex. Postgres = PostgreSQL, JS = JavaScript,
  K8s = Kubernetes) : une compétence équivalente présente dans le profil n'est pas manquante.
- N'ajoute aucune compétence absente de requiredSkills."""


class LLMSkillGap(BaseModel):
    model_config = ConfigDict(extra="forbid")

    missingSkills: list[str]


def keep_required_skills(output: LLMSkillGap, cv: CVParsingResult, job: AnalyzedJob) -> list[str]:
    """Anti-hallucination : toute compétence qui ne figure pas dans l'offre est rejetée.

    Les compétences gardées reprennent la graphie de l'offre, sans doublon. Une compétence
    que le CV mentionne à l'identique ne peut pas être manquante.
    """
    required = {normalize(skill): skill for skill in job.analysis.required_skills}
    owned = {normalize(skill) for skill in cv.skills}
    missing: list[str] = []
    for skill in output.missingSkills:
        key = normalize(skill)
        if key not in required:
            log.warning("Compétence manquante rejetée : %r ne figure pas dans l'offre %s", skill, job.job_id)
            continue
        if key not in owned and required[key] not in missing:
            missing.append(required[key])
    return missing


async def analyze_skill_gap(cv: CVParsingResult, job: AnalyzedJob, llm: OpenRouterClient) -> SkillGapResult:
    """Compétences requises par l'offre absentes du profil, selon le LLM (AI-REC-02)."""
    if not job.analysis.required_skills:
        return SkillGapResult(missing_skills=[])
    prompt = (
        f"Compétences du candidat : {json.dumps(cv.skills, ensure_ascii=False)}\n"
        f"Expériences du candidat : {json.dumps([e.title for e in cv.experiences], ensure_ascii=False)}\n"
        f"requiredSkills de l'offre : {json.dumps(job.analysis.required_skills, ensure_ascii=False)}"
    )
    output = await llm.complete_json(SYSTEM_PROMPT, prompt, LLMSkillGap)
    return SkillGapResult(missing_skills=keep_required_skills(output, cv, job))
