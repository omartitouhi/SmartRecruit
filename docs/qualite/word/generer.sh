#!/usr/bin/env bash
# Régénère les versions Word des documents qualité à partir des fichiers Markdown (sources de référence).
# Prérequis : Docker. Usage, depuis la racine du dépôt : bash docs/qualite/word/generer.sh
set -euo pipefail
cd "$(dirname "$0")"
DIR="$(pwd -W 2>/dev/null || pwd)"

for doc in RAPPORT_CONCEPTION MATRICE_TRACABILITE RAPPORT_CONFORMITE_ISO25010 GUIDE_INSTALLATION SOUTENANCE; do
  modele=modele.docx
  [ "$doc" = MATRICE_TRACABILITE ] && modele=modele-paysage.docx # tableaux à 7 colonnes
  # Les liens entre documents pointent vers les versions Word (les ancres ne survivent pas à la conversion) ;
  # trait d'union insécable dans les identifiants (EF-01, RG-03…) pour qu'ils ne se coupent pas dans les tableaux.
  sed -E 's/\.md#[^)]*\)/.docx)/g; s/\.md\)/.docx)/g; s/\b(EF|RG|EQ|EXT)-([0-9])/\1‑\2/g' "../$doc.md" |
    MSYS_NO_PATHCONV=1 docker run --rm -i -v "$DIR:/out" -v "$DIR/../images:/images:ro" pandoc/core:3.5 \
      -f gfm -t docx --resource-path=/ --reference-doc="/out/$modele" -o "/out/$doc.docx"
  echo "docs/qualite/word/$doc.docx"
done
