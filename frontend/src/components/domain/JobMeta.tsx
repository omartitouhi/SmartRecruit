import { CONTRACT_LABEL, formatSalary, LEVEL_LABEL, WORK_MODE_LABEL } from '../../lib/format';
import type { ContractType, ExperienceLevel, WorkMode } from '../../types';
import { Tag } from '../ui/Tag';

/** « Carthage Fret · Tunis » suivi du contrat, du niveau, du mode de travail et du salaire quand ils sont connus. */
export function JobMeta({ companyName, location, contractType, experienceLevel, workMode, salaryMin, salaryMax }: {
  companyName: string | null;
  location: string;
  contractType: ContractType;
  experienceLevel: ExperienceLevel;
  workMode?: WorkMode;
  salaryMin?: number | null;
  salaryMax?: number | null;
}) {
  const salary = formatSalary(salaryMin, salaryMax);
  return (
    <div className="job-meta">
      <span>
        {companyName ?? 'Entreprise'} · {location}
      </span>
      <span className="job-meta__tags">
        <Tag tone="blue">{CONTRACT_LABEL[contractType]}</Tag>
        <Tag>{LEVEL_LABEL[experienceLevel]}</Tag>
        {workMode && workMode !== 'ON_SITE' && <Tag>{WORK_MODE_LABEL[workMode]}</Tag>}
      </span>
      {salary && <span className="job-meta__salary">{salary}</span>}
    </div>
  );
}
