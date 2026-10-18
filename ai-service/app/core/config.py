from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    service_name: str = "ai-service"
    server_port: int = 8000

    db_host: str = "localhost"
    db_port: int = 5432
    db_name: str = "ai_db"
    db_user: str = "smartrecruit"
    db_password: str = "smartrecruit"

    # LLM via OpenRouter (structuration CV, analyse d'offre, matching, recommandation, skill gap).
    # Sans clé, les endpoints qui en dépendent répondent 503.
    openrouter_api_key: str | None = None
    openrouter_model: str = "anthropic/claude-haiku-5.5"
    openrouter_base_url: str = "https://openrouter.ai/api/v1"
    llm_timeout_seconds: float = 9.0

    @property
    def database_url(self) -> str:
        return (
            f"postgresql://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )


settings = Settings()
