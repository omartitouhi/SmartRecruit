import { ButtonLink } from '../ui/Button';
import { EmptyState } from '../ui/States';

/** Un recruteur sans entreprise ne peut ni publier ni consulter de candidatures. */
export function NoCompany() {
  return (
    <EmptyState title="Présentez d’abord votre entreprise" action={<ButtonLink to="/recruiter/company">Renseigner l’entreprise</ButtonLink>}>
      Chaque offre est rattachée à une entreprise. Il ne vous faut que son nom, son secteur et quelques lignes de
      présentation.
    </EmptyState>
  );
}
