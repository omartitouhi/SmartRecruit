# quality/ — tests ISO/IEC 25010

| Dossier | Contenu |
|---|---|
| `run-all.sh` | Lance toutes les couches de test (ou une seule : `bash quality/run-all.sh perf`) |
| `e2e-ui/` | Tests E2E de l'interface (Playwright) : parcours des 3 rôles, multi-navigateurs, accessibilité axe-core |
| `perf/` | Test de charge k6 (20 utilisateurs simultanés, p95 < 500 ms) |
| `reliability/` | Tests de fiabilité : pannes réelles de conteneurs (IA, service candidatures, PostgreSQL) |
| `reports/` | Preuves d'exécution réelles (JUnit, couverture, rapports HTML, sorties console) |

Les tests unitaires et d'intégration restent à côté du code (`backend/*/src/test`, `ai-service/tests`,
`frontend/src/**/*.test.tsx`) et les tests API dans `backend/e2e/e2e_full.py`.

Documentation : [guide d'installation et des tests](../docs/qualite/GUIDE_INSTALLATION.md),
[matrice de traçabilité](../docs/qualite/MATRICE_TRACABILITE.md),
[rapport de conformité ISO 25010](../docs/qualite/RAPPORT_CONFORMITE_ISO25010.md).
