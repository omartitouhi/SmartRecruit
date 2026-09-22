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

    @property
    def database_url(self) -> str:
        return (
            f"postgresql://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )


settings = Settings()
