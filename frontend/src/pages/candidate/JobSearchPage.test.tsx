import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { jobsApi } from '../../api/jobs';
import { HttpError } from '../../lib/errors';
import { makeAccount, makeJob, page } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { JobSearchPage, SEARCH_DEBOUNCE_MS } from './JobSearchPage';

vi.mock('../../api/jobs', async (original) => ({
  ...(await original<typeof import('../../api/jobs')>()),
  jobsApi: { search: vi.fn(), get: vi.fn(), recommendations: vi.fn(), create: vi.fn(), update: vi.fn(), updateStatus: vi.fn() },
}));

const search = vi.mocked(jobsApi.search);
const show = (route = '/candidate/jobs') =>
  renderAt(<JobSearchPage />, { route, path: '/candidate/jobs', account: makeAccount('CANDIDATE') });
const lastParams = () => search.mock.calls.at(-1)?.[0];

beforeEach(() => {
  vi.resetAllMocks();
  vi.useFakeTimers({ shouldAdvanceTime: true });
  search.mockResolvedValue(page([makeJob(), makeJob({ id: 'job-2', title: 'Data engineer' })]));
});
afterEach(() => vi.useRealTimers());

const typer = () => userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

describe('job search', () => {
  it('lists the published offers with a link to each detail page', async () => {
    show();
    expect(await screen.findByText('2 offres')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Développeur backend/ })).toHaveAttribute('href', '/candidate/jobs/job-1');
    expect(search).toHaveBeenCalledWith({
      keyword: '',
      location: '',
      skills: [],
      contractType: '',
      experienceLevel: '',
      workMode: '',
      minSalary: '',
      page: 0,
      size: 20,
    });
  });

  it('refreshes the list once, after the last keystroke, not on every key', async () => {
    const user = typer();
    show();
    await screen.findByText('2 offres');
    search.mockClear();

    await user.type(screen.getByLabelText('Recherche'), 'java');
    expect(search).not.toHaveBeenCalled();
    await act(() => vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS - 50));
    expect(search).not.toHaveBeenCalled();
    await act(() => vi.advanceTimersByTimeAsync(100));

    expect(search).toHaveBeenCalledTimes(1);
    expect(lastParams()).toMatchObject({ keyword: 'java', page: 0 });
  });

  it('sends the comma-separated skills as a list and the selects as filters', async () => {
    const user = typer();
    show();
    await screen.findByText('2 offres');
    await user.type(screen.getByLabelText('Compétences'), 'Java, React ,');
    await user.selectOptions(screen.getByLabelText('Contrat'), 'CDI');
    await act(() => vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 50));
    expect(lastParams()).toMatchObject({ skills: ['Java', 'React'], contractType: 'CDI' });
  });

  it('restores the filters from the URL', async () => {
    show('/candidate/jobs?keyword=react&location=Sfax&experienceLevel=SENIOR&page=1');
    await screen.findByText('2 offres');
    expect(screen.getByLabelText('Recherche')).toHaveValue('react');
    expect(screen.getByLabelText('Ville')).toHaveValue('Sfax');
    expect(lastParams()).toMatchObject({ keyword: 'react', location: 'Sfax', experienceLevel: 'SENIOR', page: 1 });
  });

  it('pages through more than 20 results', async () => {
    const user = typer();
    search.mockResolvedValue(page([makeJob()], { totalElements: 45, totalPages: 3 }));
    show();
    expect(await screen.findByText('Page 1 sur 3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Précédent' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Suivant' }));
    expect(lastParams()).toMatchObject({ page: 1 });
  });

  it('keeps showing the previous results while the next ones load', async () => {
    const user = typer();
    show();
    await screen.findByText('2 offres');
    search.mockReturnValue(new Promise(() => undefined));
    await user.type(screen.getByLabelText('Ville'), 'Sfax');
    await act(() => vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 50));
    expect(screen.getByRole('link', { name: /Développeur backend/ })).toBeInTheDocument();
    expect(screen.getByText('2 offres').parentElement).toHaveAttribute('aria-busy', 'true');
  });

  it('offers to clear the filters when nothing matches', async () => {
    const user = typer();
    search.mockResolvedValueOnce(page([]));
    show('/candidate/jobs?keyword=zzz');
    expect(await screen.findByText('Aucune offre ne correspond')).toBeInTheDocument();
    search.mockResolvedValue(page([makeJob()]));
    await user.click(screen.getByRole('button', { name: 'Effacer les filtres' }));
    expect(await screen.findByText('1 offre')).toBeInTheDocument();
    expect(lastParams()).toMatchObject({ keyword: '' });
    expect(screen.getByLabelText('Recherche')).toHaveValue('');
  });

  it('shows an error with a retry', async () => {
    const user = typer();
    search.mockRejectedValueOnce(new HttpError(503));
    show();
    expect(await screen.findByText('Service indisponible')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText('2 offres')).toBeInTheDocument();
  });
});
