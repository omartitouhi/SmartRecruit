import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { jobsApi } from '../../api/jobs';
import { HttpError } from '../../lib/errors';
import { makeAccount } from '../../test/fixtures';
import { renderAt } from '../../test/render';
import { SaveJobButton } from './SaveJobButton';

vi.mock('../../api/jobs', async (original) => ({
  ...(await original<typeof import('../../api/jobs')>()),
  jobsApi: { save: vi.fn(), unsave: vi.fn() },
}));

const api = vi.mocked(jobsApi);
const show = (saved: boolean, onChange = vi.fn()) => {
  renderAt(<SaveJobButton jobId="job-1" saved={saved} title="Data engineer" onChange={onChange} compact />, {
    route: '/candidate/jobs',
    account: makeAccount('CANDIDATE'),
  });
  return onChange;
};

beforeEach(() => vi.resetAllMocks());

describe('favourite toggle (EXT-SAVED-01)', () => {
  it('saves the offer, says so and reports the new state', async () => {
    const user = userEvent.setup();
    api.save.mockResolvedValue();
    const onChange = show(false);
    const button = screen.getByRole('button', { name: 'Ajouter aux favoris : Data engineer' });
    expect(button).toHaveAttribute('aria-pressed', 'false');
    await user.click(button);

    expect(api.save).toHaveBeenCalledWith('job-1');
    expect(await screen.findByText('Offre ajoutée à vos favoris.', { selector: '.toast' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retirer des favoris : Data engineer' })).toHaveAttribute('aria-pressed', 'true');
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it('removes a saved offer', async () => {
    const user = userEvent.setup();
    api.unsave.mockResolvedValue();
    const onChange = show(true);
    await user.click(screen.getByRole('button', { name: 'Retirer des favoris : Data engineer' }));
    expect(api.unsave).toHaveBeenCalledWith('job-1');
    expect(onChange).toHaveBeenCalledWith(false);
  });

  it('goes back to the previous state when the API refuses', async () => {
    const user = userEvent.setup();
    api.save.mockRejectedValue(new HttpError(404, 'Offre introuvable.'));
    const onChange = show(false);
    await user.click(screen.getByRole('button', { name: 'Ajouter aux favoris : Data engineer' }));
    expect(await screen.findByText('Offre introuvable.', { selector: '.toast' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ajouter aux favoris : Data engineer' })).toHaveAttribute('aria-pressed', 'false');
    expect(onChange).not.toHaveBeenCalled();
  });
});
