# Dossier qualité ISO/IEC 25010 — livrables

| Livrable (cahier des charges §8) | Document |
|---|---|
| Code source versionné | ce dépôt Git (historique complet) |
| Rapport de conception | [RAPPORT_CONCEPTION.md](RAPPORT_CONCEPTION.md), diagrammes Mermaid : [DIAGRAMMES.md](DIAGRAMMES.md) |
| Matrice de traçabilité | [MATRICE_TRACABILITE.md](MATRICE_TRACABILITE.md) |
| Jeux de tests exécutables + rapports générés | [`quality/`](../../quality/) et `quality/reports/` |
| Rapport de conformité ISO 25010 | [RAPPORT_CONFORMITE_ISO25010.md](RAPPORT_CONFORMITE_ISO25010.md) |
| Guide d'installation et d'exécution | [GUIDE_INSTALLATION.md](GUIDE_INSTALLATION.md) |
| Soutenance (démo live + 2 couches de test) | [SOUTENANCE.md](SOUTENANCE.md) |
| Répartition des tests entre les 4 membres | [REPARTITION_TESTS.md](REPARTITION_TESTS.md) |
| Fiches d'étude (préparation interne, une par membre) | [Membre 1 — lead, logique métier, API](FICHE_MEMBRE_1.md) · [Membre 2 — interface](FICHE_MEMBRE_2.md) · [Membre 3 — performance, fiabilité, portabilité](FICHE_MEMBRE_3.md) · [Membre 4 — sécurité, qualité de code](FICHE_MEMBRE_4.md) |

Versions Word de ces documents : [`word/`](word/). Les fichiers Markdown restent la source : après une modification,
régénérer avec `bash docs/qualite/word/generer.sh` (Docker requis).

Versions LaTeX (`.tex` compilables + PDF, diagrammes Mermaid en PNG) : [`latex/`](latex/). Régénérer avec
`bash docs/qualite/latex/generer.sh` (Docker requis).
