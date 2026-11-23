#!/usr/bin/env bash
# Régénère les versions LaTeX (.tex + .pdf) des documents qualité à partir des fichiers Markdown (sources de référence).
# Prérequis : Docker. Usage, depuis la racine du dépôt : bash docs/qualite/latex/generer.sh
set -euo pipefail
cd "$(dirname "$0")"
DIR="$(pwd -W 2>/dev/null || pwd)"
QDIR="$(cd .. && (pwd -W 2>/dev/null || pwd))" # docs/qualite : les images y sont partagées avec le Markdown et le Word
export MSYS_NO_PATHCONV=1

# Diagrammes Mermaid -> docs/qualite/images/diagramme-N.png (utilisées aussi par RAPPORT_CONCEPTION.md)
docker run --rm -u root -v "$QDIR:/data" minlag/mermaid-cli:11.4.2 -i /data/DIAGRAMMES.md -o /data/images/_diag.md -e png -s 3 -b white >/dev/null 2>&1
for f in ../images/_diag-*.png; do mv "$f" "../images/diagramme-${f#../images/_diag-}"; done
sed -E 's/!\[diagram\]\(\.\/_diag-([0-9]+)\.png\)/![](images\/diagramme-\1.png)/' ../images/_diag.md > _DIAGRAMMES.md
rm -f ../images/_diag.md

for doc in RAPPORT_CONCEPTION MATRICE_TRACABILITE RAPPORT_CONFORMITE_ISO25010 GUIDE_INSTALLATION SOUTENANCE REPARTITION_TESTS FICHE_MEMBRE_1 FICHE_MEMBRE_2 FICHE_MEMBRE_3 FICHE_MEMBRE_4 DIAGRAMMES; do
  src="../$doc.md"; [ "$doc" = DIAGRAMMES ] && src=_DIAGRAMMES.md
  geo=(-V geometry:margin=2cm)
  [ "$doc" = MATRICE_TRACABILITE ] && geo=(-V geometry:landscape -V geometry:margin=1.5cm) # tableaux à 7 colonnes
  # Liens entre documents -> versions PDF ; le titre H1 devient le titre du document ;
  # trait d'union insécable dans les identifiants (EF-01, RG-03…) pour qu'ils ne se coupent pas dans les tableaux ;
  # images relatives à docs/qualite -> ../images vu depuis latex/.
  sed -E 's/\.md#[^)]*\)/.pdf)/g; s/\.md\)/.pdf)/g; s/\b(EF|RG|EQ|EXT)-([0-9])/\1‑\2/g; s/\]\(images\//](..\/images\//g' "$src" |
    docker run --rm -i -v "$DIR:/out" pandoc/core:3.5 \
      -f gfm -t latex -s --shift-heading-level-by=-1 -H /out/preambule.tex --lua-filter=/out/largeurs.lua "${geo[@]}" \
      -V lang=fr -V fontsize=11pt -V colorlinks -V toc -V toc-title="Table des matières" \
      -V monofont=DejaVuSansMono.ttf -V monofontoptions=Scale=0.8 -V monofontoptions=BoldFont=DejaVuSansMono-Bold.ttf -o "/out/$doc.tex"
  # Deux passes pour la table des matières
  docker run --rm -v "$QDIR:/q" -w /q/latex texlive/texlive:latest sh -c \
    "xelatex -interaction=nonstopmode -halt-on-error $doc.tex >/dev/null && xelatex -interaction=nonstopmode -halt-on-error $doc.tex >/dev/null" ||
    { echo "Échec de compilation : voir docs/qualite/latex/$doc.log" >&2; exit 1; }
  rm -f "$doc".{aux,log,out,toc}
  echo "docs/qualite/latex/$doc.tex -> $doc.pdf"
done
rm -f _DIAGRAMMES.md
