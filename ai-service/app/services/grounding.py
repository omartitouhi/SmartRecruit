import re
import unicodedata


def _fold(value: str) -> str:
    decomposed = unicodedata.normalize("NFKD", value.lower())
    return "".join(c for c in decomposed if not unicodedata.combining(c))


def normalize(value: str) -> str:
    """Forme de comparaison : minuscules, sans accents ni ponctuation (garde + et #, ex. C++, C#)."""
    return re.sub(r"[^0-9a-z+#]", "", _fold(value))


def appears_in(value: str, text: str) -> bool:
    """Vrai si `value` figure (à la casse/ponctuation près) dans `text`.

    Garde-fou anti-hallucination : une valeur produite par le LLM qui n'apparaît
    pas dans la source n'est pas conservée. Les valeurs très courtes (Go, R, C#)
    doivent apparaître comme un mot entier pour ne pas matcher « Google » ou « React ».
    """
    needle = normalize(value)
    if not needle:
        return False
    if len(needle) <= 3:
        return re.search(rf"(?<![0-9a-z]){re.escape(needle)}(?![0-9a-z+#])", _fold(text)) is not None
    return needle in normalize(text)


def dedupe(values: list[str]) -> list[str]:
    """Supprime les doublons (comparaison normalisée) en gardant l'ordre et la 1re graphie."""
    seen: set[str] = set()
    result = []
    for value in values:
        key = normalize(value)
        if key and key not in seen:
            seen.add(key)
            result.append(value.strip())
    return result
