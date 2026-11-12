import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import { FormDialog } from '@/components/common/FormDialog';
import { REASON_RULE, BANK_REFERENCE_RULE } from '@/lib/refund';
import en from '../../messages/en.json';
import km from '../../messages/km.json';

function renderDialog(props: Partial<React.ComponentProps<typeof FormDialog>>, locale: 'en' | 'km' = 'en') {
  const onSubmit = vi.fn();
  const onOpenChange = vi.fn();
  render(
    <NextIntlClientProvider locale={locale} messages={locale === 'en' ? en : km}>
      <FormDialog
        open
        onOpenChange={onOpenChange}
        title="Title"
        fields={[]}
        submitLabel="Send"
        cancelLabel="Cancel"
        onSubmit={onSubmit}
        {...props}
      />
    </NextIntlClientProvider>,
  );
  return { onSubmit, onOpenChange };
}

describe('FormDialog validation', () => {
  it('blocks an empty reason and shows an accessible error', () => {
    const { onSubmit } = renderDialog({
      fields: [{ name: 'reason', label: 'Reason', multiline: true, ...REASON_RULE }],
    });
    fireEvent.click(screen.getByText('Send'));
    expect(onSubmit).not.toHaveBeenCalled();
    const field = screen.getByLabelText(/Reason/);
    expect(field).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('This field is required.');
  });

  it('submits a trimmed reason', () => {
    const { onSubmit } = renderDialog({
      fields: [{ name: 'reason', label: 'Reason', multiline: true, ...REASON_RULE }],
    });
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: '  changed my mind  ' } });
    fireEvent.click(screen.getByText('Send'));
    expect(onSubmit).toHaveBeenCalledWith({ reason: 'changed my mind' });
  });

  it('requires the bank reference (min 3) but not the note', () => {
    const fields = [
      { name: 'bankReference', label: 'Bank reference', ...BANK_REFERENCE_RULE },
      { name: 'note', label: 'Note', multiline: true, maxLength: 500 },
    ];
    const { onSubmit } = renderDialog({ fields });
    fireEvent.click(screen.getByText('Send'));
    expect(onSubmit).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText(/Bank reference/), { target: { value: 'ab' } });
    fireEvent.click(screen.getByText('Send'));
    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('at least 3');
    fireEvent.change(screen.getByLabelText(/Bank reference/), { target: { value: 'FT123456' } });
    fireEvent.click(screen.getByText('Send'));
    expect(onSubmit).toHaveBeenCalledWith({ bankReference: 'FT123456', note: '' });
  });

  it('is a labelled dialog and closes on Escape', () => {
    const { onOpenChange } = renderDialog({ fields: [] });
    expect(screen.getByRole('dialog', { name: 'Title' })).toBeInTheDocument();
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('shows the validation message in Khmer', () => {
    renderDialog({ fields: [{ name: 'reason', label: 'មូលហេតុ', ...REASON_RULE }] }, 'km');
    fireEvent.click(screen.getByText('Send'));
    expect(screen.getByRole('alert')).toHaveTextContent(km.refunds.validation.required);
  });
});
