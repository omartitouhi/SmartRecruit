import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { SelectField, TextAreaField, TextField } from './Field';
import { Pager } from './Pager';
import { TagInput } from './TagInput';

describe('form fields', () => {
  it('ties the label, hint and error to the control for screen readers', () => {
    render(<TextField label="Email" hint="Visible par les recruteurs" error="Saisissez un email valide." />);
    const input = screen.getByLabelText('Email');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Saisissez un email valide.');
    expect(screen.getByRole('alert')).toHaveTextContent('Saisissez un email valide.');
  });

  it('shows the hint while there is no error', () => {
    render(<TextAreaField label="Description" hint="Quelques lignes" />);
    expect(screen.getByLabelText('Description')).toHaveAccessibleDescription('Quelques lignes');
    expect(screen.getByLabelText('Description')).not.toHaveAttribute('aria-invalid');
  });

  it('renders a select with a placeholder option', async () => {
    const onChange = vi.fn();
    render(<SelectField label="Contrat" placeholder="Tous" options={[{ value: 'CDI', label: 'CDI' }]} onChange={onChange} />);
    expect(screen.getByRole('option', { name: 'Tous' })).toHaveValue('');
    await userEvent.setup().selectOptions(screen.getByLabelText('Contrat'), 'CDI');
    expect(onChange).toHaveBeenCalled();
  });
});

describe('TagInput', () => {
  function Harness({ max }: { max?: number }) {
    const [values, setValues] = useState<string[]>(['Java']);
    return <TagInput label="Compétences" values={values} onChange={setValues} max={max} />;
  }

  it('adds on Enter or comma, ignoring blanks and duplicates (case-insensitive)', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    const input = screen.getByLabelText('Compétences');
    await user.type(input, 'React{Enter}');
    await user.type(input, 'java{Enter}');
    await user.type(input, '  ,');
    await user.type(input, 'SQL, Docker,');
    const tags = screen.getByLabelText('Compétences ajoutées');
    expect(tags).toHaveTextContent('JavaReactSQLDocker');
  });

  it('adds a pending word on blur, removes with the button and with Backspace', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.type(screen.getByLabelText('Compétences'), 'Go');
    await user.tab();
    expect(screen.getByLabelText('Compétences ajoutées')).toHaveTextContent('JavaGo');

    await user.click(screen.getByRole('button', { name: 'Retirer Java' }));
    expect(screen.getByLabelText('Compétences ajoutées')).toHaveTextContent('Go');
    await user.click(screen.getByLabelText('Compétences'));
    await user.keyboard('{Backspace}');
    expect(screen.queryByLabelText('Compétences ajoutées')).not.toBeInTheDocument();
  });

  it('stops at the maximum', async () => {
    const user = userEvent.setup();
    render(<Harness max={2} />);
    await user.type(screen.getByLabelText('Compétences'), 'A,B,C,');
    expect(screen.getByLabelText('Compétences ajoutées')).toHaveTextContent('JavaA');
  });
});

describe('Pager', () => {
  it('renders nothing for a single page', () => {
    const { container } = render(<Pager page={0} totalPages={1} onPage={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('moves one page at a time and disables the ends', async () => {
    const user = userEvent.setup();
    const onPage = vi.fn();
    const { rerender } = render(<Pager page={0} totalPages={3} onPage={onPage} />);
    expect(screen.getByText('Page 1 sur 3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Précédent' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Suivant' }));
    expect(onPage).toHaveBeenCalledWith(1);

    rerender(<Pager page={2} totalPages={3} onPage={onPage} />);
    expect(screen.getByRole('button', { name: 'Suivant' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Précédent' }));
    expect(onPage).toHaveBeenLastCalledWith(1);
  });
});
