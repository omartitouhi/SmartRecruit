#!/usr/bin/env bash
# Exécute les couches de test ISO 25010 et écrit les preuves dans quality/reports/.
# Usage : bash quality/run-all.sh [étape...]      sans argument : toutes les étapes, dans l'ordre
# Étapes : install unit api ui perf reliability sast sca dast maintainability
# Prérequis : Docker, Java 21+, Node 20+, Python 3.10+ (Linux, macOS ou Git Bash sous Windows).
set -u
cd "$(dirname "$0")/.."
ROOT_HOST=$(pwd -W 2>/dev/null || pwd)   # chemin vu par Docker (Git Bash : C:/...)
export MSYS_NO_PATHCONV=1
R=quality/reports
PY=${PYTHON:-python}
STEPS=${*:-install unit api ui perf reliability sast sca dast maintainability}
status=0
step() { echo; echo "=================== $1 ==================="; }
run() { "$@" || { echo "!! échec : $*"; status=1; }; }
wait_http() { until [ "$(curl -s -o /dev/null -w '%{http_code}' "$1")" = "$2" ]; do sleep 3; done; }

for s in $STEPS; do case $s in
install)  # Portabilité : installation reproductible depuis le dépôt
  step "Installation (docker compose) + données de démo"
  [ -f .env ] || cp .env.example .env
  run docker compose up -d --build
  wait_http http://localhost:8080/api/v1/jobs 401; wait_http http://localhost:5173/ 200
  run $PY -m pip install -q -r backend/e2e/requirements.txt
  run $PY backend/seed/seed_demo.py ;;
unit)  # Aptitude fonctionnelle + maintenabilité : tests unitaires/intégration et couverture
  step "Tests unitaires et d'intégration + couverture"
  mkdir -p $R/coverage $R/unit
  for svc in api-gateway auth-service user-service job-service application-service dashboard-service; do
    (cd backend/$svc && chmod +x mvnw && ./mvnw -q -B -ntp org.jacoco:jacoco-maven-plugin:0.8.14:prepare-agent test \
      org.jacoco:jacoco-maven-plugin:0.8.14:report) || { echo "!! échec : $svc"; status=1; }
    rm -rf "${R:?}/coverage/${svc:?}" "${R:?}/unit/${svc:?}"; mkdir -p $R/unit/$svc
    cp -r backend/$svc/target/site/jacoco $R/coverage/$svc; cp backend/$svc/target/surefire-reports/TEST-*.xml $R/unit/$svc/
  done
  (cd ai-service && $PY -m pip install -q -r requirements-dev.txt && $PY -m pytest -q -p no:cacheprovider --cov=app --cov-branch \
    --cov-report=term --cov-report=html:../$R/coverage/ai-service --junitxml=../$R/unit/ai-service-junit.xml) || status=1
  rm -f $R/coverage/ai-service/.gitignore   # pytest-cov y écrit "*" : le rapport doit rester versionné
  (cd frontend && npm ci --no-audit --no-fund && npm run test:coverage -- --reporter=default --reporter=junit \
    --outputFile.junit=../$R/unit/frontend-junit.xml) || status=1 ;;
api)  # Aptitude fonctionnelle + compatibilité : contrat d'API de bout en bout
  step "Tests API de bout en bout (gateway + nginx)"
  mkdir -p $R/api
  # La campagne crée ~15 comptes : la limite anti-force brute (10 / 5 min / IP) est relevée le temps du test, puis rétablie.
  RATE_LIMIT_MAX_REQUESTS=1000 docker compose up -d api-gateway; wait_http http://localhost:8080/api/v1/jobs 401
  PYTHONIOENCODING=utf-8 $PY backend/e2e/e2e_full.py > $R/api/e2e-console.txt 2>&1 || status=1
  tail -2 $R/api/e2e-console.txt
  docker compose up -d api-gateway; wait_http http://localhost:8080/api/v1/jobs 401 ;;
ui)  # Aptitude fonctionnelle, facilité d'utilisation (axe), compatibilité (navigateurs)
  step "Tests E2E de l'interface (Playwright)"
  (cd quality/e2e-ui && npm ci --no-audit --no-fund && npx playwright install chromium webkit && npx playwright test) || status=1 ;;
perf)  # Efficacité de performance : 20 utilisateurs simultanés, p95 < 500 ms
  step "Test de charge (k6)"
  mkdir -p $R/perf
  docker run --rm -v "$ROOT_HOST/quality/perf:/scripts:ro" -v "$ROOT_HOST/$R/perf:/reports" grafana/k6:1.0.0 run --quiet \
    /scripts/load-test.js 2>&1 | tee $R/perf/k6-console.txt; [ "${PIPESTATUS[0]}" = 0 ] || status=1 ;;
reliability)  # Fiabilité : pannes réelles de conteneurs
  step "Tests de fiabilité (pannes provoquées)"
  mkdir -p $R/reliability
  $PY -m pip install -q pytest httpx
  PYTHONIOENCODING=utf-8 $PY -m pytest quality/reliability -v -s -p no:cacheprovider --junitxml=$R/reliability/junit.xml 2>&1 \
    | tee $R/reliability/pytest-console.txt; [ "${PIPESTATUS[0]}" = 0 ] || status=1 ;;
sast)  # Sécurité : analyse statique
  step "SAST (Bandit, Semgrep)"
  mkdir -p $R/security
  $PY -m pip install -q bandit==1.8.6
  run $PY -m bandit -r ai-service/app -f txt -o $R/security/bandit.txt
  run docker run --rm -v "$ROOT_HOST:/src" -w /src semgrep/semgrep:1.140.0 semgrep scan --metrics=off --error \
    --config p/owasp-top-ten --config p/java --config p/typescript --config p/python --config p/secrets \
    --exclude '**/test/**' --exclude '**/tests/**' --exclude '*.test.ts' --exclude '*.test.tsx' \
    --json-output $R/security/semgrep.json backend ai-service frontend/src ;;
sca)  # Sécurité : dépendances vulnérables (CVE)
  step "SCA (pip-audit, npm audit, Trivy sur les images)"
  mkdir -p $R/security/trivy
  $PY -m pip install -q pip-audit==2.9.0
  run $PY -m pip_audit -r ai-service/requirements.txt -f columns -o $R/security/pip-audit.txt
  (cd frontend && npm audit --omit=dev > ../$R/security/npm-audit.txt) || status=1
  for img in api-gateway auth-service user-service job-service application-service dashboard-service ai-service frontend; do
    run docker run --rm -v //var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache -v "$ROOT_HOST/$R/security/trivy:/out" \
      aquasec/trivy:0.58.1 image --quiet --scanners vuln --pkg-types library --exit-code 1 --severity HIGH,CRITICAL \
      --format table --output /out/$img.txt smartrecruit-$img:latest
  done ;;
dast)  # Sécurité : scan dynamique de l'application en cours d'exécution
  step "DAST (OWASP ZAP baseline)"
  mkdir -p $R/security/zap
  run docker run --rm -v "$ROOT_HOST/$R/security/zap:/zap/wrk:rw" ghcr.io/zaproxy/zaproxy:2.16.1 zap-baseline.py \
    -t http://host.docker.internal:5173 -r zap-frontend.html -J zap-frontend.json -I ;;  # -I : échec sur FAIL seulement
maintainability)  # Maintenabilité : complexité et lint
  step "Maintenabilité (lizard, ruff, oxlint)"
  mkdir -p $R/maintainability
  $PY -m pip install -q lizard==1.17.31 ruff==0.6.9
  $PY -m lizard backend/*/src/main ai-service/app frontend/src -x "*.test.*" -x "*/test/*" -l java -l python -l typescript \
    --CCN 15 > $R/maintainability/lizard-complexite.txt; tail -4 $R/maintainability/lizard-complexite.txt
  (cd ai-service && $PY -m ruff check .) > $R/maintainability/ruff.txt 2>&1 || status=1
  (cd frontend && npx oxlint && echo "oxlint : aucun avertissement ni erreur") > $R/maintainability/oxlint.txt 2>&1 || status=1 ;;
*) echo "étape inconnue : $s"; status=1 ;;
esac; done
echo; [ $status = 0 ] && echo "TOUTES LES ÉTAPES RÉUSSIES" || echo "AU MOINS UNE ÉTAPE A ÉCHOUÉ (voir ci-dessus)"
exit $status
