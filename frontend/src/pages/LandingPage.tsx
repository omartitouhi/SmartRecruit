import { useMemo, useRef, useState, type CSSProperties } from 'react';
import tunisPhoto from '../assets/tunis-avenue-habib-bourguiba.jpg';
import { HOME_BY_ROLE, useAuth } from '../auth/context';
import { ButtonLink } from '../components/ui/Button';
import { ScoreBreakdown, ScoreTotal } from '../components/ui/ScoreBreakdown';
import { ApplicationStatusTag, Tag } from '../components/ui/Tag';
import { formatScore } from '../lib/format';
import { useDocumentTitle } from '../lib/useAsync';
import { useReveal } from '../lib/useReveal';
import type { ApplicationStatus, MatchScore } from '../types';

// Sample explained score, same shape as POST /ai/matching/score (DATA_MODEL.md §4.1).
const BASE: MatchScore = {
  score: 79,
  criteria: [
    {
      label: 'Compétences techniques',
      weight: 0.5,
      contribution: 40,
      explanation: '8 des 10 compétences requises sont dans le profil : Java, Spring Boot, PostgreSQL, Docker, REST, Git, JUnit, Maven.',
    },
    { label: 'Expérience', weight: 0.3, contribution: 24, explanation: '2 ans et demi d’expérience pour un niveau Senior (3 à 5 ans demandés).' },
    { label: 'Formation', weight: 0.1, contribution: 10, explanation: 'Diplôme d’ingénieur en informatique, ESPRIT, 2023. Bac+5 demandé.' },
    { label: 'Langues', weight: 0.1, contribution: 5, explanation: 'Français et anglais présents. L’offre demande aussi l’allemand.' },
  ],
};

const MISSING = [
  { name: 'Kubernetes', criterion: 0, points: 5 },
  { name: 'GraphQL', criterion: 0, points: 5 },
  { name: 'Allemand', criterion: 3, points: 5 },
];

const RANKING: { name: string; score: number; status: ApplicationStatus }[] = [
  { name: 'Skander Jaziri', score: 91, status: 'INTERVIEW' },
  { name: 'Nour Ben Amor', score: 84.5, status: 'IN_REVIEW' },
  { name: 'Amira Trabelsi', score: 79, status: 'RECEIVED' },
  { name: 'Malek Gharbi', score: 66, status: 'IN_REVIEW' },
];

export function LandingPage() {
  useDocumentTitle('');
  const { account } = useAuth();
  const [added, setAdded] = useState<string[]>([]);
  const rootRef = useRef<HTMLDivElement>(null);
  useReveal(rootRef);

  const match = useMemo<MatchScore>(() => {
    const criteria = BASE.criteria.map((c, i) => {
      const gained = MISSING.filter((m) => m.criterion === i && added.includes(m.name)).reduce((n, m) => n + m.points, 0);
      if (!gained) return c;
      const names = MISSING.filter((m) => m.criterion === i && added.includes(m.name)).map((m) => m.name);
      return { ...c, contribution: c.contribution + gained, explanation: `${c.explanation} Ajouté : ${names.join(', ')}.` };
    });
    return { score: criteria.reduce((n, c) => n + c.contribution, 0), criteria };
  }, [added]);

  const toggle = (name: string) => setAdded((list) => (list.includes(name) ? list.filter((n) => n !== name) : [...list, name]));
  const startHref = account ? HOME_BY_ROLE[account.role] : '/register';

  return (
    <div className="landing" ref={rootRef}>
      <section className="hero">
        <div className="hero__text">
          <h1 className="hero__title serif reveal">Des scores qui se justifient.</h1>
          <p className="hero__lead reveal" style={{ '--i': 1 } as CSSProperties}>
            SmartRecruit lit les CV, les compare aux offres et classe les candidatures. Chaque note arrive avec ses raisons,
            pour le candidat comme pour le recruteur.
          </p>
          <div className="hero__actions reveal" style={{ '--i': 2 } as CSSProperties}>
            <ButtonLink to={account ? startHref : '/register'} size="lg">
              {account ? 'Aller à mon espace' : 'Déposer mon CV'}
            </ButtonLink>
            {!account && (
              <ButtonLink to="/register?role=recruiter" variant="ghost" size="lg">
                Publier une offre
              </ButtonLink>
            )}
          </div>
        </div>

        <figure className="window hero__window reveal" style={{ '--i': 2 } as CSSProperties} data-ui>
          <div className="window__top" aria-hidden="true">
            <i />
            <i />
            <i />
          </div>
          <div className="window__body">
            <p className="window__title">
              Développeur·se backend Java <Tag>37 candidatures</Tag>
            </p>
            <table className="table">
              <thead>
                <tr>
                  <th>Candidat·e</th>
                  <th>Score</th>
                  <th>Statut</th>
                </tr>
              </thead>
              <tbody>
                {RANKING.map((r, i) => (
                  <tr key={r.name} aria-selected={i === 2}>
                    <td>{r.name}</td>
                    <td className="cell-num">{formatScore(r.score)}</td>
                    <td>
                      <ApplicationStatusTag status={r.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <figcaption className="visually-hidden">Exemple de classement des candidatures pour une offre.</figcaption>
        </figure>
      </section>

      <section className="explain" id="score" aria-labelledby="score-title">
        <div className="explain__head">
          <h2 className="section-heading serif" id="score-title" data-reveal>
            Quatre critères, chacun expliqué
          </h2>
          <p className="section-lead" data-reveal>
            Compétences, expérience, formation, langues. Chaque critère a un poids, une note et une phrase qui dit
            pourquoi. Essayez : ajoutez une compétence manquante à ce profil.
          </p>
        </div>
        <div className="panel explain__doc" data-ui data-reveal>
          <p className="explain__job">
            <span className="cell-strong">Développeur·se backend Java</span>
            <span className="muted"> · Carthage Fret · Tunis · CDI · Senior</span>
          </p>
          <ScoreTotal
            score={match.score}
            label="compatibilité"
            action={
              <span className="explain__try">
                <span className="muted">Ajouter au profil :</span>
                {MISSING.map((m) => (
                  <button
                    key={m.name}
                    type="button"
                    className="chip"
                    aria-pressed={added.includes(m.name)}
                    onClick={() => toggle(m.name)}
                  >
                    {added.includes(m.name) ? '' : '+ '}
                    {m.name}
                  </button>
                ))}
              </span>
            }
          />
          <ScoreBreakdown match={match} />
        </div>
      </section>

      <section className="audience" id="candidats" aria-labelledby="cand-title">
        <div className="audience__text">
          <h2 className="section-heading serif" id="cand-title" data-reveal>
            Candidats : votre score avant de postuler
          </h2>
          <p className="section-lead" data-reveal>
            Déposez votre CV une fois. Pour chaque offre, vous voyez votre score, les compétences qui vous manquent, et
            où en est votre candidature après l’envoi.
          </p>
          <ul className="plain-list" data-reveal>
            <li>Le CV est analysé en quelques secondes, vous corrigez ce que l’IA a mal lu.</li>
            <li>Les offres recommandées sont triées par compatibilité.</li>
            <li>L’explication du score est jointe à votre candidature.</li>
          </ul>
          <ButtonLink to={startHref} variant="ghost">
            Créer mon profil
          </ButtonLink>
        </div>
        <figure className="panel audience__fragment" data-ui data-reveal>
          <p className="window__title">Mes candidatures</p>
          <ol className="timeline">
            <li>
              <span className="timeline__dot" aria-hidden="true" />
              <span>
                <span className="cell-strong">Reçue</span>
                <span className="muted"> · 2 oct., 09:14</span>
              </span>
            </li>
            <li>
              <span className="timeline__dot" aria-hidden="true" />
              <span>
                <span className="cell-strong">En revue</span>
                <span className="muted"> · 4 oct., 16:02</span>
              </span>
            </li>
            <li>
              <span className="timeline__dot" aria-hidden="true" />
              <span>
                <span className="cell-strong">Entretien</span>
                <span className="muted"> · 7 oct., 10:30</span>
              </span>
            </li>
          </ol>
          <p className="fragment__note">
            <span className="muted">Data engineer · Kerkennah Data · Sfax</span>
            <ApplicationStatusTag status="INTERVIEW" />
          </p>
          <figcaption className="visually-hidden">Exemple de suivi d’une candidature.</figcaption>
        </figure>
      </section>

      <section className="recruiters" id="recruteurs" aria-labelledby="rec-title">
        <h2 className="section-heading serif" id="rec-title" data-reveal>
          Recruteurs : un classement que vous pouvez défendre
        </h2>
        <dl className="facts">
          <div data-reveal>
            <dt>Le classement</dt>
            <dd>Les candidatures arrivent triées par score. Vous lisez la même explication que le candidat.</dd>
          </div>
          <div data-reveal>
            <dt>Le suivi</dt>
            <dd>Reçue, en revue, entretien, décision. Chaque changement est daté et journalisé.</dd>
          </div>
          <div data-reveal>
            <dt>La décision</dt>
            <dd>Le score aide à trier ; c’est vous qui décidez, et le candidat sait sur quels critères.</dd>
          </div>
        </dl>
        <div className="recruiters__foot">
          <ButtonLink to={account ? startHref : '/register?role=recruiter'}>Publier une offre</ButtonLink>
          <figure className="panel decision" data-ui data-reveal>
            <div className="decision__who">
              <span className="cell-strong">Skander Jaziri</span>
              <span className="muted">Score 92,0 / 100</span>
              <ApplicationStatusTag status="INTERVIEW" />
            </div>
            <div className="decision__actions" aria-hidden="true">
              <span className="btn">Accepter</span>
              <span className="btn btn--ghost">Ne pas retenir</span>
              <span className="btn btn--ghost">Voir le CV</span>
            </div>
            <figcaption className="visually-hidden">Exemple de décision sur une candidature en entretien.</figcaption>
          </figure>
        </div>
      </section>

      <figure className="city">
        <div className="city__frame">
          <img
            src={tunisPhoto}
            width={1600}
            height={1200}
            loading="lazy"
            decoding="async"
            alt="L’avenue Habib-Bourguiba à Tunis vue d’en haut : la double rangée d’arbres taillés, les immeubles blancs, une tour de verre et le lac de Tunis au fond."
          />
        </div>
        <figcaption className="city__caption" data-reveal>
          <span>Une plateforme pensée pour le marché de l’emploi tunisien.</span>
          <span className="muted">
            Avenue Habib-Bourguiba, Tunis. Photo : Dacoslett, CC0, via{' '}
            <a className="text-link" href="https://commons.wikimedia.org/w/index.php?curid=13616433">
              Wikimedia Commons
            </a>
          </span>
        </figcaption>
      </figure>

      <section className="privacy" id="confidentialite" aria-labelledby="privacy-title">
        <h2 className="section-heading serif" id="privacy-title" data-reveal>
          Vos données restent les vôtres
        </h2>
        <div className="privacy__grid">
          <p data-reveal>
            <span className="cell-strong">CV chiffrés.</span> Le fichier n’est jamais exposé directement : il se télécharge
            par un lien contrôlé, réservé à vous et aux recruteurs.
          </p>
          <p data-reveal>
            <span className="cell-strong">Suppression immédiate.</span> Supprimer son compte efface le profil, le CV, les
            scores et les analyses, conformément au RGPD.
          </p>
          <p id="conditions" data-reveal>
            <span className="cell-strong">Chaque consultation tracée.</span> Toute ouverture de CV et tout changement de
            statut figurent dans un journal d’audit.
          </p>
        </div>
      </section>

      <section className="closing" aria-labelledby="closing-title">
        <h2 className="closing__title serif" id="closing-title" data-reveal>
          Voyez votre premier score aujourd’hui.
        </h2>
        <ButtonLink to={startHref} size="lg">
          {account ? 'Aller à mon espace' : 'Créer un compte'}
        </ButtonLink>
      </section>
    </div>
  );
}
