# Diagrammes — SmartRecruit (Mermaid)

Diagrammes du [rapport de conception](RAPPORT_CONCEPTION.md), au format Mermaid : GitHub les affiche directement ;
pour une image, coller le code dans https://mermaid.live puis exporter en PNG ou SVG.

## 1. Cas d'utilisation par rôle

```mermaid
flowchart LR
    C(["👤 Candidat"])
    R(["👤 Recruteur"])
    A(["👤 Administrateur"])

    subgraph SR["SmartRecruit"]
        UC1["S'inscrire / se connecter"]
        UC2["Gérer son profil et déposer son CV"]
        UC3["Rechercher et filtrer les offres"]
        UC4["Voir son score de compatibilité expliqué"]
        UC5["Postuler / retirer sa candidature"]
        UC6["Gérer ses favoris"]
        UC7["Gérer son entreprise et inviter des recruteurs"]
        UC8["Créer, publier, clôturer, supprimer une offre"]
        UC9["Classer les candidats, changer le statut, planifier un entretien"]
        UC10["Annoter et noter une candidature"]
        UC11["Tableau de bord filtré par période"]
        UC12["Gérer les comptes (activer / désactiver)"]
        UC13["Modérer une offre"]
        UC14["Consulter le journal d'audit"]
    end

    C --- UC1 & UC2 & UC3 & UC4 & UC5 & UC6
    R --- UC1 & UC7 & UC8 & UC9 & UC10 & UC11
    A --- UC1 & UC11 & UC12 & UC13 & UC14
```

## 2. Architecture

```mermaid
flowchart TB
    U(["Navigateur<br/>candidat · recruteur · admin"])
    FE["<b>Frontend</b><br/>React 19 + TypeScript (Vite)<br/>servi par nginx"]
    GW["<b>API Gateway</b><br/>Spring Cloud Gateway<br/>validation JWT · rate limiting<br/>en-têtes d'identité signés HMAC"]

    U -->|HTTPS| FE
    FE -->|"/api/v1/** + JWT Bearer"| GW

    subgraph MS["Microservices Spring Boot 4 (Java 21)"]
        AUTH["auth-service<br/>comptes, JWT, RBAC"]
        USER["user-service<br/>profils, CV, entreprises"]
        JOB["job-service<br/>offres, recherche, favoris"]
        APP["application-service<br/>candidatures, notes, entretiens"]
        DASH["dashboard-service<br/>statistiques, administration, audit"]
    end
    AI["<b>ai-service</b> (FastAPI, Python 3.12)<br/>parsing CV, analyse d'offre,<br/>score de compatibilité, recommandations"]
    LLM[("LLM externe<br/>OpenRouter")]

    GW --> AUTH & USER & JOB & APP & DASH & AI

    subgraph PG["PostgreSQL 16 — une base logique par service"]
        DB1[(auth_db)]
        DB2[(user_db)]
        DB3[(job_db)]
        DB4[(application_db)]
        DB5[(dashboard_db)]
        DB6[(ai_db)]
    end

    AUTH --- DB1
    USER --- DB2
    JOB --- DB3
    APP --- DB4
    DASH --- DB5
    AI --- DB6
    AI -.->|optionnel| LLM

    USER -.->|analyse du CV| AI
    JOB -.->|analyse d'offre, recommandations| AI
    APP -.->|score de compatibilité| AI
    APP -.->|offre publiée ?| JOB
    APP -.->|profil complet ?| USER
    APP -.->|événements d'audit| DASH
    USER -.-> DASH
    DASH -.->|statistiques, administration| AUTH
    DASH -.-> USER & JOB & APP
```

## 3. Modèle de données

```mermaid
erDiagram
    ACCOUNT ||--o| CANDIDATE_PROFILE : "possède (même id)"
    ACCOUNT }o--o| COMPANY : "recruteur membre de"
    COMPANY ||--o{ JOB : "publie"
    COMPANY ||--o{ COMPANY_INVITATION : "envoie"
    CANDIDATE_PROFILE ||--o{ APPLICATION : "dépose"
    JOB ||--o{ APPLICATION : "reçoit"
    APPLICATION ||--|{ STATUS_HISTORY : "historise"
    APPLICATION ||--o{ APPLICATION_NOTE : "est annotée"
    ACCOUNT ||--o{ APPLICATION_NOTE : "rédige"
    CANDIDATE_PROFILE ||--o{ SAVED_JOB : "met en favori"
    JOB ||--o{ SAVED_JOB : "est mise en favori"
    ACCOUNT ||--o{ AUDIT_LOG_ENTRY : "est l'acteur de"

    ACCOUNT {
        UUID id PK
        string email UK
        string passwordHash
        Role role "CANDIDATE, RECRUITER, ADMIN"
        AccountStatus status
        datetime createdAt
    }
    CANDIDATE_PROFILE {
        UUID id PK "= Account.id"
        string firstName
        string lastName
        string email
        string location
        list skills
        string cvUrl "fichier chiffré"
        enum cvAnalysisStatus "NONE, PENDING, DONE, FAILED"
    }
    COMPANY {
        UUID id PK
        string name
        string sector
        list members "accountId des recruteurs"
    }
    COMPANY_INVITATION {
        UUID id PK
        UUID companyId FK
        string email
        string tokenHash
        InvitationStatus status
        datetime expiresAt
    }
    JOB {
        UUID id PK
        UUID companyId FK
        string title
        list requiredSkills
        ContractType contractType
        ExperienceLevel experienceLevel
        WorkMode workMode
        string location
        int salaryMin
        int salaryMax
        date applicationDeadline
        int positions "1 à 100"
        JobStatus status
    }
    APPLICATION {
        UUID id PK
        UUID jobId FK
        UUID candidateId FK
        ApplicationStatus status
        MatchScore matchScore "null si IA indisponible"
        string coverLetter
        Interview interview
        datetime appliedAt
    }
    STATUS_HISTORY {
        ApplicationStatus status
        datetime changedAt
        UUID changedBy
        string message
    }
    APPLICATION_NOTE {
        UUID id PK
        UUID applicationId FK
        UUID authorId FK
        string content
        int rating
        datetime createdAt
    }
    SAVED_JOB {
        UUID id PK
        UUID candidateId FK "unique avec jobId"
        UUID jobId FK
        datetime savedAt
    }
    AUDIT_LOG_ENTRY {
        UUID id PK
        UUID actorId FK
        string action "CV_VIEWED, JOB_MODERATED..."
        string resourceType
        UUID resourceId
        datetime timestamp
    }
```

Entre microservices, les clés étrangères sont **logiques** (chaque service possède sa base).

## 4. Workflow d'une candidature (RG-03)

```mermaid
stateDiagram-v2
    [*] --> RECEIVED : le candidat postule
    RECEIVED --> IN_REVIEW : le recruteur examine
    IN_REVIEW --> INTERVIEW : entretien planifié
    INTERVIEW --> ACCEPTED : décision
    INTERVIEW --> REJECTED : décision
    RECEIVED --> WITHDRAWN : retrait (candidat)
    IN_REVIEW --> WITHDRAWN : retrait (candidat)
    ACCEPTED --> [*]
    REJECTED --> [*]
    WITHDRAWN --> [*]
    note right of RECEIVED
        Toute autre transition → 400, état inchangé.
        Chaque changement ajoute une entrée à statusHistory
        et un événement d'audit APPLICATION_STATUS_CHANGED.
    end note
```

## 5. Workflow d'une offre (RG-08, RG-09)

```mermaid
stateDiagram-v2
    [*] --> DRAFT : création / duplication
    DRAFT --> PUBLISHED : publication
    DRAFT --> [*] : suppression (brouillon uniquement)
    PUBLISHED --> CLOSED : clôture manuelle ou lendemain de la date limite
    CLOSED --> PUBLISHED : réouverture (date limite non dépassée)
    DRAFT --> MODERATED : retrait par un admin
    PUBLISHED --> MODERATED : retrait par un admin
    CLOSED --> MODERATED : retrait par un admin
    note right of MODERATED
        État final : l'offre n'est plus
        modifiable par le recruteur (403 / 409).
    end note
```

## 6. Séquence : postuler à une offre

```mermaid
sequenceDiagram
    autonumber
    actor C as Candidat
    participant FE as Frontend
    participant GW as API Gateway
    participant APP as application-service
    participant JOB as job-service
    participant USER as user-service
    participant AI as ai-service
    participant DB as application_db

    C->>FE: clique sur « Postuler »
    FE->>GW: POST /api/v1/applications (Bearer JWT)
    GW->>GW: valide le JWT (sinon 401)
    GW->>APP: requête + en-têtes d'identité signés HMAC
    APP->>APP: vérifie la signature et le rôle CANDIDATE (sinon 401 / 403)
    APP->>JOB: l'offre existe et est publiée ?
    alt offre non publiée
        APP-->>FE: 409 (RG-05)
    end
    APP->>USER: profil complet et CV déposé ?
    alt profil incomplet
        APP-->>FE: 422 (RG-05)
    end
    APP->>DB: candidature active pour cette offre ?
    alt doublon
        APP-->>FE: 409 (RG-04)
    end
    APP->>DB: INSERT candidature RECEIVED
    APP-->>FE: 201 Created
    FE-->>C: confirmation
    APP-)AI: calcul du score (asynchrone)
    alt IA disponible
        AI-->>APP: MatchScore (score global + critères)
        APP->>DB: enregistre le score
    else IA indisponible
        Note over APP,AI: best effort : la candidature reste valide, score = null
    end
```

## 7. Stratégie de test : couches → outils → caractéristiques ISO/IEC 25010

```mermaid
flowchart LR
    subgraph L["Couches de test (§7)"]
        T1["Unitaires + intégration<br/>1 233 tests"]
        T2["API<br/>155 vérifications"]
        T3["E2E interface<br/>62 tests, 4 navigateurs"]
        T4["Performance<br/>20 utilisateurs, 1 min"]
        T5["Fiabilité<br/>8 pannes réelles"]
        T6["SAST / SCA / DAST"]
        T7["Qualité du code"]
        T8["Installation + CI"]
    end
    subgraph O["Outils"]
        O1["JUnit 5, MockMvc, pytest, Vitest"]
        O2["httpx (e2e_full.py)"]
        O3["Playwright + axe-core"]
        O4["k6"]
        O5["pytest + docker compose stop"]
        O6["Bandit, Semgrep, pip-audit,<br/>npm audit, Trivy, OWASP ZAP"]
        O7["JaCoCo, pytest-cov, Vitest v8,<br/>lizard, ruff, oxlint"]
        O8["Docker Compose, GitHub Actions,<br/>Kubernetes sur VM Azure"]
    end
    subgraph Q["ISO/IEC 25010"]
        Q1["Aptitude fonctionnelle"]
        Q2["Efficacité de performance"]
        Q3["Compatibilité"]
        Q4["Facilité d'utilisation"]
        Q5["Fiabilité"]
        Q6["Sécurité"]
        Q7["Maintenabilité"]
        Q8["Portabilité"]
    end
    T1 --> O1 --> Q1
    T2 --> O2 --> Q1
    O2 --> Q6
    T3 --> O3 --> Q1
    O3 --> Q3
    O3 --> Q4
    T4 --> O4 --> Q2
    T5 --> O5 --> Q5
    T6 --> O6 --> Q6
    T7 --> O7 --> Q7
    T8 --> O8 --> Q8
```

## 8. Pipeline CI/CD (GitHub Actions)

```mermaid
flowchart LR
    P(["push / pull request"]) --> S1
    subgraph S1["1 · Tests"]
        B["Backend ×6<br/>JUnit + JaCoCo"]
        F["Frontend<br/>Vitest + couverture, npm audit"]
        I["Service IA<br/>pytest-cov, Bandit, pip-audit"]
    end
    subgraph S2["2 · Validation de l'infrastructure"]
        K["Kustomize"]
        H["Helm"]
        TF["Terraform"]
        AN["Ansible"]
    end
    subgraph S3["3 · Images"]
        IM["8 images Docker"]
    end
    subgraph S4["4 · Déploiement (main uniquement)"]
        D["VM Azure<br/>Kubernetes kind via Ansible"]
    end
    subgraph S5["5 · Tests de fumée"]
        SM["Smoke tests sur la VM"]
    end
    S1 --> S2 --> S3 --> S4 --> S5
```
