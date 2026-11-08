import asyncio
import json

import httpx
import pytest
from pydantic import BaseModel

from app.services.llm_client import LLMError, LLMNotConfiguredError, OpenRouterClient


class Answer(BaseModel):
    value: int


def _client(handler, api_key="sk-test") -> OpenRouterClient:
    return OpenRouterClient(
        api_key=api_key,
        model="test/model",
        base_url="https://openrouter.test/api/v1",
        timeout_seconds=1,
        transport=httpx.MockTransport(handler),
    )


def _completion(content: str) -> httpx.Response:
    return httpx.Response(200, json={"choices": [{"message": {"content": content}}]})


def _ask(client: OpenRouterClient) -> Answer:
    return asyncio.run(client.complete_json("system", "user", Answer))


def test_valid_json_answer_is_validated_against_the_schema():
    requests = []

    def handler(request: httpx.Request):
        requests.append(request)
        return _completion('{"value": 42}')

    assert _ask(_client(handler)).value == 42
    body = json.loads(requests[0].content)
    assert requests[0].url == "https://openrouter.test/api/v1/chat/completions"
    assert requests[0].headers["Authorization"] == "Bearer sk-test"
    assert body["model"] == "test/model" and body["temperature"] == 0


def test_markdown_fenced_json_is_accepted():
    assert _ask(_client(lambda _: _completion('```json\n{"value": 1}\n```'))).value == 1


def test_missing_api_key_raises_not_configured():
    with pytest.raises(LLMNotConfiguredError):
        _ask(_client(lambda _: _completion('{"value": 1}'), api_key=None))


@pytest.mark.parametrize(
    "response",
    [
        httpx.Response(500, json={"error": "boom"}),
        httpx.Response(200, json={"unexpected": True}),
        _completion("pas du json"),
        _completion('{"value": "quarante-deux"}'),
        _completion('{"other": 1}'),
    ],
)
def test_failures_and_non_conforming_answers_raise_llm_error(response):
    with pytest.raises(LLMError):
        _ask(_client(lambda _: response))


def test_timeout_raises_llm_error():
    def handler(request):
        raise httpx.ReadTimeout("timeout", request=request)

    with pytest.raises(LLMError):
        _ask(_client(handler))
