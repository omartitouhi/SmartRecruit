import io

from pypdf import PdfReader
from pypdf.errors import PdfReadError

MAX_CV_SIZE_BYTES = 5 * 1024 * 1024  # DATA_MODEL.md §1 : taille max CV = 5 Mo

# En dessous de ce seuil de caractères extraits, on considère qu'il n'y a pas de
# couche de texte exploitable (cas typique d'un CV scanné/image sans OCR).
MIN_EXTRACTED_CHARS = 20


class CVTextExtractionError(Exception):
    """Le texte n'a pas pu être extrait du PDF (fichier corrompu, protégé, ou
    sans couche de texte exploitable — ex. un scan/image)."""


def extract_text_from_pdf(data: bytes) -> str:
    """Extrait le texte brut d'un CV PDF (AI-CV-01).

    Sert de base à l'extraction structurée (compétences, expériences, diplômes)
    réalisée par AI-CV-02. Ne renvoie jamais de résultat partiel silencieux :
    toute PDF illisible ou sans texte lève `CVTextExtractionError`.
    """
    try:
        reader = PdfReader(io.BytesIO(data))
    except PdfReadError as exc:
        raise CVTextExtractionError("Fichier PDF invalide ou corrompu.") from exc

    if reader.is_encrypted:
        raise CVTextExtractionError("Le PDF est protégé par mot de passe.")

    pages_text: list[str] = []
    for page in reader.pages:
        try:
            pages_text.append(page.extract_text() or "")
        except Exception as exc:
            raise CVTextExtractionError("Échec de l'extraction du texte du PDF.") from exc

    text = "\n".join(pages_text).strip()
    if len(text) < MIN_EXTRACTED_CHARS:
        raise CVTextExtractionError(
            "Aucun texte exploitable trouvé dans le PDF "
            "(probablement un scan/image sans couche de texte)."
        )
    return text
