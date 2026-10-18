import math

# Poids des critères (somme = 1), fixés dans le code : le LLM ne choisit jamais le
# score global (AI-MATCH-02). Toute modification doit être reportée dans DATA_MODEL.md §4.1.
CRITERIA_WEIGHTS: dict[str, float] = {
    "skills": 0.5,
    "experience": 0.3,
    "education": 0.2,
}


class InvalidScoreError(ValueError):
    """Sous-score ou poids invalide : rejeté plutôt que corrigé silencieusement."""


def _check_sub_score(criterion: str, value: float) -> None:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise InvalidScoreError(f"Sous-score du critère « {criterion} » invalide : {value!r}.")
    if not 0 <= value <= 100:
        raise InvalidScoreError(f"Sous-score du critère « {criterion} » hors de l'intervalle 0-100 : {value}.")


def compute_global_score(
    sub_scores: dict[str, float], weights: dict[str, float] = CRITERIA_WEIGHTS
) -> float:
    """Moyenne pondérée des sous-scores (0-100) -> score global 0-100, arrondi à 0,1.

    Déterministe : mêmes entrées, même score. Les poids sont renormalisés sur les
    critères fournis, ce qui garde le score dans 0-100 même si un critère est écarté.
    """
    if not sub_scores:
        raise InvalidScoreError("Aucun sous-score à agréger.")
    unknown = set(sub_scores) - set(weights)
    if unknown:
        raise InvalidScoreError(f"Critère(s) sans poids : {', '.join(sorted(unknown))}.")
    for criterion, value in sub_scores.items():
        _check_sub_score(criterion, value)

    total_weight = sum(weights[criterion] for criterion in sub_scores)
    if total_weight <= 0:
        raise InvalidScoreError("La somme des poids doit être strictement positive.")
    weighted = sum(weights[criterion] * value for criterion, value in sub_scores.items())
    return round(weighted / total_weight, 1)
