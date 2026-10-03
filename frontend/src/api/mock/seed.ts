// Demo data — fictitious companies and people. Shapes follow DATA_MODEL.md exactly.

import type {
  Account,
  Application,
  ApplicationStatus,
  AuditLogEntry,
  CandidateProfile,
  Company,
  Job,
} from '../../types';
import { computeMatch } from './scoring';

export const DEMO_PASSWORD = 'demo1234';

export const DEMO_ACCOUNTS = {
  candidate: 'candidat@demo.tn',
  recruiter: 'recruteur@demo.tn',
  admin: 'admin@demo.tn',
} as const;

export interface MockDb {
  version: number;
  accounts: (Account & { password: string })[];
  profiles: CandidateProfile[];
  /** Candidats ayant exercé leur droit à l'oubli : seul l'id subsiste. */
  deletedCandidates: string[];
  companies: Company[];
  jobs: Job[];
  applications: Application[];
  audit: AuditLogEntry[];
  /** Nom du fichier CV par candidat (le contenu reste en mémoire pendant la session). */
  cvFiles: Record<string, string>;
}

export const DB_VERSION = 2;

const JOB_IDS = [
  '43b7a3a6-9a8d-4a03-980d-7b71d8f56413',
  '759cde66-bacf-43d0-8b1f-9163ce9ff57f',
  'ec1b8ca1-f91e-4d4c-9ff4-9b7889463e85',
  '4b0dbb41-8d52-48f1-942c-3fe860e7a113',
  'e2acf72f-9e57-4f7a-a0ee-89aed453dd32',
  '3139d32c-93cd-49bf-9c94-1cf0dc98d2c1',
  'a9488d99-0bbb-4599-91ce-5dd2b45ed1f0',
  'fc377a4c-4a15-444d-85e7-ce8a3a578a8e',
  'ddd1dfb2-3b98-4ef8-9af6-1a26146d3f31'
];
const APPLICATION_IDS = [
  '7412b293-4729-4739-a14f-f3d719db3ad0',
  '29a3b2e9-5d65-4441-9588-42dea2bc372f',
  'ab9099a4-35a2-40ae-9af3-05535ec42e08',
  'aefcfad8-efc8-4849-b3aa-7efe4458a885',
  'a28defe3-9bf0-4273-9247-6f57a5e5a5ab',
  '3eabedcb-baa8-4dd4-88bd-64072bcfbe01',
  '451b4cf3-6123-4df7-b656-af7229d4beef',
  'b02b61c4-a3d7-4628-ace6-6fa2fd5166e6',
  '5304317f-af42-412f-b838-b3268e944239',
  '0e51f30d-c6a7-4e39-84b0-32ccd7c524a5',
  'ce177b4e-0837-48a3-9261-a7ab3aa2e4f9'
];
const AUDIT_IDS = [
  '10f1bc81-448a-4a9e-a6b2-bc5b50c187fc',
  '9132b63e-f162-47e4-a9c3-49e03602f8ac',
  '366eb16f-508e-4ad7-b7c9-3acfe059a0ee',
  'e27a984d-6548-41d0-bfcd-9eb1a7cad415',
  '24933b83-7577-40a9-a491-f0b2ea1fca65',
  'beb79919-3f22-4af8-a3be-d01d43cf2fde',
  'bf3c4c06-4343-48bc-89fa-6a688fb5d27b',
  '956269f0-e5d7-4875-adad-d6c795a76d79',
  'ff50bde4-3825-47b8-9cab-cc97663f1c97',
  '7e570ddf-8270-40a8-a369-b584ff5e9ff0',
  'dc713d96-0c0f-4195-817a-f08a1745d6d8',
  '28f49481-a0a0-4dc4-a720-9bdf1c11f735'
];


const T = (iso: string) => `${iso}T09:00:00Z`;

function account(id: string, email: string, role: Account['role'], createdAt: string): Account & { password: string } {
  return { id, email, role, status: 'ACTIVE', createdAt: T(createdAt), updatedAt: T(createdAt), password: DEMO_PASSWORD };
}

function profile(p: Omit<CandidateProfile, 'deleted' | 'cvUrl' | 'cvParsedAt' | 'createdAt' | 'updatedAt'> & { since: string; cv?: boolean }): CandidateProfile {
  const { since, cv = true, ...rest } = p;
  return {
    ...rest,
    cvUrl: cv ? `/api/v1/users/candidates/${p.id}/cv` : null,
    cvParsedAt: cv ? T(since) : null,
    createdAt: T(since),
    updatedAt: T(since),
    deleted: false,
  };
}

export function createSeed(): MockDb {
  const accounts = [
    account('bdd640fb-0667-4ad1-9c80-317fa3b1799d', DEMO_ACCOUNTS.candidate, 'CANDIDATE', '2026-09-02'),
    account('6b65a6a4-8b81-48f6-b38a-088ca65ed389', DEMO_ACCOUNTS.recruiter, 'RECRUITER', '2026-08-20'),
    account('c241330b-01a9-471f-9e8a-774bcf36d58b', DEMO_ACCOUNTS.admin, 'ADMIN', '2026-08-01'),
    account('23b8c1e9-3924-46de-beb1-3b9046685257', 'skander.jaziri@demo.tn', 'CANDIDATE', '2026-09-10'),
    account('bd9c66b3-ad3c-4d6d-9a3d-1fa7bc8960a9', 'nour.benamor@demo.tn', 'CANDIDATE', '2026-09-12'),
    account('972a8469-1641-4f82-8b9d-2434e465e150', 'malek.gharbi@demo.tn', 'CANDIDATE', '2026-09-14'),
    account('17fc695a-07a0-4a6e-8822-e8f36c031199', 'ines.chaabane@demo.tn', 'CANDIDATE', '2026-09-18'),
    account('9a1de644-815e-46d1-bb8f-aa1837f8a88b', 'yassine.mabrouk@demo.tn', 'CANDIDATE', '2026-09-03'),
    account('47378190-96da-4dac-b2ff-5d2a386ecbe0', 'rh@kerkennah-data.demo.tn', 'RECRUITER', '2026-08-22'),
  ];
  const deletedId = 'b74d0fb1-32e7-4629-8fad-c1a606cb0fb3';
  accounts.push({ ...account(deletedId, `deleted+${deletedId}@invalid`, 'CANDIDATE', '2026-09-05'), status: 'DELETED' });

  const amira = accounts[0];
  const recruiter = accounts[1];
  const kerkennahRecruiter = accounts[8];

  const profiles: CandidateProfile[] = [
    profile({
      id: amira.id,
      firstName: 'Amira',
      lastName: 'Trabelsi',
      email: 'amira.trabelsi@demo.tn',
      phone: '+216 22 418 730',
      location: 'Tunis',
      skills: ['Java', 'Spring Boot', 'PostgreSQL', 'Docker', 'REST', 'Git', 'JUnit', 'Maven', 'Python', 'SQL'],
      experiences: [
        { title: 'Développeuse backend', company: 'Medina Soft', startDate: '2022-01', endDate: null, description: 'API Spring Boot pour la facturation de 40 agences, migration vers PostgreSQL 15.' },
        { title: 'Stagiaire Java', company: 'Byrsa Lab', startDate: '2021-02', endDate: '2021-08', description: 'Outil interne de suivi des tickets, tests JUnit.' },
      ],
      education: [{ degree: 'Diplôme d’ingénieur en informatique', institution: 'ESPRIT', year: 2021 }],
      languages: ['Arabe', 'Français', 'Anglais'],
      since: '2026-09-02',
    }),
    profile({
      id: accounts[3].id,
      firstName: 'Skander',
      lastName: 'Jaziri',
      email: 'skander.jaziri@demo.tn',
      phone: '+216 98 102 554',
      location: 'Tunis',
      skills: ['Java', 'Spring Boot', 'PostgreSQL', 'Docker', 'REST', 'Git', 'JUnit', 'Maven', 'Kubernetes', 'Kafka'],
      experiences: [
        { title: 'Ingénieur backend', company: 'Cap Bon Logistics', startDate: '2020-03', endDate: null, description: 'Suivi des conteneurs en temps réel, 2 millions d’événements par jour.' },
        { title: 'Développeur Java', company: 'Sfax Telecom', startDate: '2019-01', endDate: '2020-02', description: '' },
      ],
      education: [{ degree: 'Master Génie Logiciel', institution: 'INSAT', year: 2018 }],
      languages: ['Arabe', 'Français', 'Anglais', 'Allemand'],
      since: '2026-09-10',
    }),
    profile({
      id: accounts[4].id,
      firstName: 'Nour',
      lastName: 'Ben Amor',
      email: 'nour.benamor@demo.tn',
      phone: null,
      location: 'Ariana',
      skills: ['Java', 'Spring Boot', 'PostgreSQL', 'Docker', 'REST', 'Git', 'JUnit', 'Maven', 'Kubernetes', 'React'],
      experiences: [
        { title: 'Développeuse full-stack', company: 'Hannibal Systems', startDate: '2021-09', endDate: null, description: 'Portail client React + Spring Boot.' },
      ],
      education: [{ degree: 'Master Génie Logiciel', institution: 'ESPRIT', year: 2021 }],
      languages: ['Arabe', 'Français'],
      since: '2026-09-12',
    }),
    profile({
      id: accounts[5].id,
      firstName: 'Malek',
      lastName: 'Gharbi',
      email: 'malek.gharbi@demo.tn',
      phone: '+216 55 870 213',
      location: 'Sousse',
      skills: ['Java', 'Spring Boot', 'MySQL', 'Docker', 'REST', 'Git', 'JUnit'],
      experiences: [
        { title: 'Développeur Java junior', company: 'Sahel Info', startDate: '2024-06', endDate: null, description: 'Maintenance d’une application de gestion des stocks.' },
      ],
      education: [{ degree: 'Licence en informatique de gestion', institution: 'ISG Sousse', year: 2023 }],
      languages: ['Arabe', 'Français', 'Anglais'],
      since: '2026-09-14',
    }),
    profile({
      id: accounts[6].id,
      firstName: 'Ines',
      lastName: 'Chaabane',
      email: 'ines.chaabane@demo.tn',
      phone: null,
      location: 'Tunis',
      skills: ['React', 'TypeScript', 'CSS', 'Git', 'REST', 'Java', 'Docker'],
      experiences: [
        { title: 'Développeuse front-end', company: 'Atlas Mobile', startDate: '2023-02', endDate: null, description: 'Interfaces React pour une application bancaire.' },
      ],
      education: [{ degree: 'Diplôme d’ingénieur en informatique', institution: 'ENSI', year: 2022 }],
      languages: ['Arabe', 'Français'],
      since: '2026-09-18',
    }),
    profile({
      id: accounts[7].id,
      firstName: 'Yassine',
      lastName: 'Mabrouk',
      email: 'yassine.mabrouk@demo.tn',
      phone: '+216 20 334 981',
      location: 'Tunis',
      skills: ['Java', 'Spring Boot', 'PostgreSQL', 'Docker', 'REST', 'Git', 'JUnit', 'Maven', 'GraphQL'],
      experiences: [
        { title: 'Développeur backend', company: 'Zitouna Pay', startDate: '2021-04', endDate: null, description: 'Paiement mobile, Spring Boot + PostgreSQL en production.' },
      ],
      education: [{ degree: 'Licence fondamentale en informatique', institution: 'FST', year: 2020 }],
      languages: ['Arabe', 'Français', 'Anglais'],
      since: '2026-09-03',
    }),
  ];

  const companies: Company[] = [
    {
      id: '6c307511-b2b9-437a-a8df-6ec4ce4a2bbd',
      name: 'Carthage Fret',
      sector: 'Transport et logistique',
      description: 'Transitaire basé aux Berges du Lac. Nous suivons 1 200 conteneurs par mois entre Radès, Marseille et Hambourg, et nous construisons nos propres outils de suivi.',
      logoUrl: null,
      members: [recruiter.id],
    },
    {
      id: '371ecd7b-27cd-4130-8722-9389571aa876',
      name: 'Kerkennah Data',
      sector: 'Données et IA',
      description: 'Cabinet de data engineering à Sfax, au service des industriels du Sahel.',
      logoUrl: null,
      members: [kerkennahRecruiter.id],
    },
    {
      id: '1a2a73ed-562b-4f79-8374-59eef50bea63',
      name: 'Sahel Énergie',
      sector: 'Énergie',
      description: 'Exploitation de centrales solaires dans le Sahel tunisien.',
      logoUrl: null,
      members: [],
    },
    {
      id: '5be6128e-18c2-4797-a142-ea7d17be3111',
      name: 'Atlas Mobile',
      sector: 'Applications mobiles',
      description: 'Studio d’applications mobiles pour la banque et l’assurance.',
      logoUrl: null,
      members: [],
    },
  ];
  const [fret, kerkennah, sahel, atlas] = companies;

  const job = (
    n: number,
    company: Company,
    title: string,
    experienceLevel: Job['experienceLevel'],
    contractType: Job['contractType'],
    location: string,
    status: Job['status'],
    publishedAt: string | null,
    requiredSkills: string[],
    description: string,
  ): Job => ({
    id: JOB_IDS[n - 1],
    companyId: company.id,
    title,
    description,
    requiredSkills,
    experienceLevel,
    contractType,
    location,
    status,
    publishedAt: publishedAt ? T(publishedAt) : null,
  });

  const jobs: Job[] = [
    job(1, fret, 'Développeur·se backend Java', 'SENIOR', 'CDI', 'Tunis, Les Berges du Lac', 'PUBLISHED', '2026-10-01',
      ['Java', 'Spring Boot', 'PostgreSQL', 'Docker', 'REST', 'Git', 'JUnit', 'Maven', 'Kubernetes', 'GraphQL'],
      'Vous rejoignez l’équipe de quatre personnes qui construit notre outil de suivi des conteneurs. Vous reprenez l’API de réservation (Spring Boot, PostgreSQL), la découpez en services et la déployez sur Kubernetes. Échanges réguliers avec notre bureau de Hambourg.'),
    job(2, fret, 'Développeur·se front-end React', 'INTERMEDIATE', 'CDI', 'Tunis, Les Berges du Lac', 'PUBLISHED', '2026-10-06',
      ['React', 'TypeScript', 'CSS', 'Git', 'REST', 'Vite'],
      'Vous construisez l’écran de suivi que nos clients consultent chaque jour : carte des conteneurs, alertes de retard, documents douaniers.'),
    job(3, fret, 'Stage data analyst', 'JUNIOR', 'STAGE', 'Tunis, Les Berges du Lac', 'DRAFT', null,
      ['Python', 'SQL', 'Power BI', 'Excel'],
      'Stage de six mois pour mesurer les délais de dédouanement et les présenter à la direction.'),
    job(4, fret, 'Responsable exploitation', 'LEAD', 'CDI', 'Radès', 'CLOSED', '2026-08-25',
      ['Gestion d’équipe', 'Logistique', 'Excel', 'SAP'],
      'Encadrement de l’équipe de quai de Radès (12 personnes).'),
    job(5, kerkennah, 'Data engineer', 'SENIOR', 'CDI', 'Sfax', 'PUBLISHED', '2026-09-28',
      ['Python', 'SQL', 'Spark', 'Airflow', 'Docker', 'Kafka', 'PostgreSQL'],
      'Pipelines de données pour trois industriels de l’huile d’olive : collecte, nettoyage, tableaux de bord de production.'),
    job(6, sahel, 'Ingénieur·e DevOps', 'INTERMEDIATE', 'CDD', 'Sousse', 'PUBLISHED', '2026-10-03',
      ['Docker', 'Kubernetes', 'Terraform', 'Linux', 'Git', 'CI/CD'],
      'Mise en place de l’infrastructure de supervision de nos 14 centrales solaires. CDD de 12 mois.'),
    job(7, atlas, 'Développeur·se Android', 'JUNIOR', 'FREELANCE', 'Tunis', 'PUBLISHED', '2026-09-20',
      ['Kotlin', 'Android', 'Jetpack Compose', 'Git', 'REST'],
      'Mission de quatre mois sur l’application mobile d’un assureur.'),
    job(8, kerkennah, 'Alternance ingénieur·e machine learning', 'JUNIOR', 'ALTERNANCE', 'Sfax', 'PUBLISHED', '2026-10-09',
      ['Python', 'PyTorch', 'SQL', 'Docker', 'Git'],
      'Alternance de deux ans : modèles de prévision de rendement pour les huileries.'),
    job(9, sahel, 'Chef·fe de projet SI', 'LEAD', 'CDI', 'Sousse', 'PUBLISHED', '2026-09-30',
      ['Gestion de projet', 'Agile', 'Scrum', 'SQL', 'Java'],
      'Pilotage du remplacement de notre ERP, en lien avec trois prestataires.'),
  ];
  const [backend, frontend, , , dataEng, devops, android] = jobs;

  const apps: Application[] = [];
  const PATHS: Record<ApplicationStatus, ApplicationStatus[]> = {
    RECEIVED: ['RECEIVED'],
    IN_REVIEW: ['RECEIVED', 'IN_REVIEW'],
    INTERVIEW: ['RECEIVED', 'IN_REVIEW', 'INTERVIEW'],
    ACCEPTED: ['RECEIVED', 'IN_REVIEW', 'INTERVIEW', 'ACCEPTED'],
    REJECTED: ['RECEIVED', 'IN_REVIEW', 'INTERVIEW', 'REJECTED'],
    WITHDRAWN: ['RECEIVED', 'WITHDRAWN'],
  };
  const apply = (n: number, candidateId: string, j: Job, status: ApplicationStatus, appliedAt: string, actor: string) => {
    const p = profiles.find((x) => x.id === candidateId);
    const start = new Date(T(appliedAt)).getTime();
    apps.push({
      id: APPLICATION_IDS[n - 1],
      jobId: j.id,
      candidateId,
      status,
      matchScore: p ? computeMatch(p, j) : null,
      appliedAt: T(appliedAt),
      statusHistory: PATHS[status].map((s, i) => ({
        status: s,
        changedAt: new Date(start + i * 2.5 * 86400000).toISOString(),
        changedBy: i === 0 || s === 'WITHDRAWN' ? candidateId : actor,
      })),
    });
  };

  const [, skander, nour, malek, ines, yassine] = profiles.map((p) => p.id);
  apply(1, skander, backend, 'INTERVIEW', '2026-10-02', recruiter.id);
  apply(2, nour, backend, 'IN_REVIEW', '2026-10-03', recruiter.id);
  apply(3, malek, backend, 'IN_REVIEW', '2026-10-04', recruiter.id);
  apply(4, ines, backend, 'RECEIVED', '2026-10-08', recruiter.id);
  apply(5, yassine, backend, 'ACCEPTED', '2026-10-01', recruiter.id);
  apply(6, deletedId, backend, 'RECEIVED', '2026-10-05', recruiter.id);
  apply(7, ines, frontend, 'IN_REVIEW', '2026-10-07', recruiter.id);
  apply(8, nour, frontend, 'RECEIVED', '2026-10-09', recruiter.id);
  apply(9, amira.id, dataEng, 'INTERVIEW', '2026-09-29', kerkennahRecruiter.id);
  apply(10, amira.id, devops, 'RECEIVED', '2026-10-11', recruiter.id);
  apply(11, amira.id, android, 'REJECTED', '2026-09-21', recruiter.id);

  const audit: AuditLogEntry[] = [
    ['USER_REGISTERED', 'Account', amira.id, amira.id, '2026-09-02T08:14:00Z'],
    ['CV_UPLOADED', 'CandidateProfile', amira.id, amira.id, '2026-09-02T08:21:00Z'],
    ['JOB_PUBLISHED', 'Job', backend.id, recruiter.id, '2026-10-01T09:02:00Z'],
    ['APPLICATION_CREATED', 'Application', apps[4].id, yassine, '2026-10-01T10:40:00Z'],
    ['CV_VIEWED', 'CandidateProfile', skander, recruiter.id, '2026-10-03T13:05:00Z'],
    ['APPLICATION_STATUS_CHANGED', 'Application', apps[0].id, recruiter.id, '2026-10-07T09:30:00Z'],
    ['JOB_PUBLISHED', 'Job', frontend.id, recruiter.id, '2026-10-06T08:00:00Z'],
    ['USER_DELETED', 'Account', deletedId, deletedId, '2026-10-10T17:48:00Z'],
    ['CV_VIEWED', 'CandidateProfile', nour, recruiter.id, '2026-10-12T11:12:00Z'],
    ['APPLICATION_STATUS_CHANGED', 'Application', apps[4].id, recruiter.id, '2026-10-13T15:20:00Z'],
    ['JOB_MODERATED', 'Job', jobs[3].id, accounts[2].id, '2026-10-14T10:00:00Z'],
    ['USER_DISABLED', 'Account', accounts[5].id, accounts[2].id, '2026-10-15T16:31:00Z'],
  ].map(([action, resourceType, resourceId, actorId, timestamp], i) => ({
    id: AUDIT_IDS[i],
    action,
    resourceType,
    resourceId,
    actorId,
    timestamp,
  }));

  return {
    version: DB_VERSION,
    accounts,
    profiles,
    deletedCandidates: [deletedId],
    companies,
    jobs,
    applications: apps,
    audit,
    cvFiles: Object.fromEntries(profiles.map((p) => [p.id, `CV-${p.firstName}-${p.lastName}.pdf`])),
  };
}
