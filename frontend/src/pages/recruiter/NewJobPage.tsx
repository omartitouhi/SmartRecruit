import { Link, useNavigate } from 'react-router-dom';
import { jobsApi } from '../../api/jobs';
import { JobForm } from '../../components/domain/JobForm';
import { NoCompany } from '../../components/domain/NoCompany';
import { ErrorState, PageSkeleton } from '../../components/ui/States';
import { useToast } from '../../components/ui/toast';
import { useDocumentTitle } from '../../lib/useAsync';
import { useMyCompany } from '../../lib/useCompany';

export function NewJobPage() {
  useDocumentTitle('Publier une offre');
  const navigate = useNavigate();
  const notify = useToast();
  const company = useMyCompany();

  if (company.error) {
    return (
      <div className="page page--doc">
        <ErrorState error={company.error} onRetry={company.reload} />
      </div>
    );
  }
  if (company.loading) return <PageSkeleton />;
  if (!company.data) {
    return (
      <div className="page page--doc">
        <NoCompany />
      </div>
    );
  }

  return (
    <div className="page page--doc">
      <nav className="crumbs" aria-label="Fil d’Ariane">
        <Link to="/recruiter/jobs">{company.data.name}</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">Nouvelle offre</span>
      </nav>
      <header className="page-head">
        <div>
          <h1 className="page-head__title">Nouvelle offre</h1>
          <p className="page-head__text">
            Les compétences demandées servent à calculer le score de chaque candidat. Soyez précis : « PostgreSQL » plutôt
            que « bases de données ».
          </p>
        </div>
      </header>

      <JobForm
        submitLabel="Publier l’offre"
        cancelTo="/recruiter/jobs"
        onSubmit={async (values) => {
          await jobsApi.create(values);
          notify('Offre publiée.');
          navigate('/recruiter/jobs');
        }}
      />
    </div>
  );
}
