from pydantic import BaseModel


class MatchCriterion(BaseModel):
    label: str
    weight: float
    contribution: float
    explanation: str


class MatchScore(BaseModel):
    score: float
    criteria: list[MatchCriterion]
