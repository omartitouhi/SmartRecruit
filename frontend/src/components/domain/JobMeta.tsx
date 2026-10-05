import { CONTRACT_LABEL, LEVEL_LABEL } from '../../lib/format';
import type { ContractType, ExperienceLevel } from '../../types';
import { Tag } from '../ui/Tag';

/** « Carthage Fret · Tunis » suivi du contrat et du niveau. */
export function JobMeta({ companyName, location, contractType, experienceLevel }: {
  companyName: string | null;
  location: string;
  contractType: ContractType;
  experienceLevel: ExperienceLevel;
}) {
  return (
    <div className="job-meta">
      <span>
        {companyName ?? 'Entreprise'} · {location}
      </span>
      <span className="job-meta__tags">
        <Tag tone="blue">{CONTRACT_LABEL[contractType]}</Tag>
        <Tag>{LEVEL_LABEL[experienceLevel]}</Tag>
      </span>
    </div>
  );
}
