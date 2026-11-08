import json
import re
from typing import TypeVar

import httpx
from pydantic import BaseModel, ValidationError

from app.core.config import settings
from app.core.metrics import LLM_CALLS_IN_PROGRESS

T = TypeVar("T", bound=BaseModel)

_FENCE = re.compile(r"^```(?:json)?\s*|\s*```$")


class LLMNotConfiguredError(Exception):
    """Aucune clé OpenRouter n'est configurée (OPENROUTER_API_KEY) -> 503."""


class LLMError(Exception):
    """Échec de l'appel OpenRouter ou sortie du LLM non conforme au schéma -> 502.

    Jamais rattrapé pour renvoyer une valeur par défaut : aucune donnée n'est inventée.
    """


class OpenRouterClient:
    """Client minimal de l'API chat completions d'OpenRouter (compatible OpenAI).

    Toute réponse est parsée en JSON puis validée contre un schéma Pydantic strict
    (garde-fou anti-hallucination, cf. Responsible AI) avant d'être renvoyée.
    """

    def __init__(
        self,
        api_key: str | None,
        model: str,
        base_url: str,
        timeout_seconds: float,
        transport: httpx.AsyncBaseTransport | None = None,
    ):
        self.api_key = api_key
        self.model = model
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self.transport = transport

    async def complete_json(
        self, system: str, user: str, schema: type[T], timeout_seconds: float | None = None
    ) -> T:
        if not self.api_key:
            raise LLMNotConfiguredError(
                "Service LLM non configuré : définir OPENROUTER_API_KEY."
            )

        payload = {
            "model": self.model,
            "temperature": 0,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        }
        headers = {"Authorization": f"Bearer {self.api_key}", "X-Title": "SmartRecruit AI Service"}
        try:
            with LLM_CALLS_IN_PROGRESS.track_inprogress():
                content = await self._post(payload, headers, timeout_seconds)
        except httpx.TimeoutException as exc:
            raise LLMError("Le LLM n'a pas répondu dans le délai imparti.") from exc
        except httpx.HTTPStatusError as exc:
            raise LLMError(f"OpenRouter a répondu {exc.response.status_code}.") from exc
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError) as exc:
            raise LLMError("Réponse OpenRouter inexploitable.") from exc

        return parse_llm_json(content, schema)

    async def _post(self, payload: dict, headers: dict, timeout_seconds: float | None) -> str | None:
        """Appel HTTP à OpenRouter ; renvoie le contenu texte du premier choix."""
        async with httpx.AsyncClient(
            transport=self.transport, timeout=timeout_seconds or self.timeout_seconds
        ) as client:
            response = await client.post(
                f"{self.base_url}/chat/completions", json=payload, headers=headers
            )
            response.raise_for_status()
            return response.json()["choices"][0]["message"]["content"]


def parse_llm_json(content: str | None, schema: type[T]) -> T:
    """Contenu texte du LLM -> instance validée de `schema`, sinon `LLMError`."""
    if not isinstance(content, str):
        raise LLMError("Réponse du LLM vide.")
    try:
        data = json.loads(_FENCE.sub("", content.strip()))
    except json.JSONDecodeError as exc:
        raise LLMError("La réponse du LLM n'est pas un JSON valide.") from exc
    try:
        return schema.model_validate(data)
    except ValidationError as exc:
        raise LLMError(
            f"Réponse du LLM non conforme au schéma ({exc.error_count()} erreur(s))."
        ) from exc


def get_llm_client() -> OpenRouterClient:
    """Dépendance FastAPI (surchargée dans les tests)."""
    return OpenRouterClient(
        api_key=settings.openrouter_api_key,
        model=settings.openrouter_model,
        base_url=settings.openrouter_base_url,
        timeout_seconds=settings.llm_timeout_seconds,
    )
