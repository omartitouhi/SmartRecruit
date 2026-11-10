import { SelectField } from '../ui/Field';

const OPTIONS = [
  { value: '7', label: '7 derniers jours' },
  { value: '30', label: '30 derniers jours' },
  { value: '90', label: '90 derniers jours' },
  { value: '365', label: '12 derniers mois' },
];

export function PeriodFilter({ value, onChange }: { value: string; onChange: (days: string) => void }) {
  return (
    <SelectField
      label="Période"
      hint="Filtre les candidatures selon leur date de dépôt."
      placeholder="Depuis le début"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      options={OPTIONS}
    />
  );
}
