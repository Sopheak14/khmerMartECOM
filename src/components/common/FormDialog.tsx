'use client';

import React, { useEffect, useState } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { useTranslations } from 'next-intl';
import { Loader2, X } from 'lucide-react';
import { validateField, type FieldRule } from '@/lib/refund';

export interface FormDialogField extends FieldRule {
  name: string;
  label: string;
  multiline?: boolean;
  placeholder?: string;
  testId?: string;
}

export interface FormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  fields: FormDialogField[];
  submitLabel: string;
  cancelLabel: string;
  /** Red submit button (destructive actions). */
  destructive?: boolean;
  pending?: boolean;
  /** Server error shown inside the dialog. */
  error?: string | null;
  onSubmit: (values: Record<string, string>) => void;
  dialogTestId?: string;
  submitTestId?: string;
}

/**
 * Accessible in-page form dialog (Radix: focus trap, Esc closes, focus returns to the trigger, labelled by its title).
 * Required fields are validated before onSubmit is called; values are passed trimmed.
 */
export function FormDialog({
  open,
  onOpenChange,
  title,
  description,
  fields,
  submitLabel,
  cancelLabel,
  destructive,
  pending,
  error,
  onSubmit,
  dialogTestId,
  submitTestId,
}: FormDialogProps) {
  const t = useTranslations('refunds.validation');
  const [values, setValues] = useState<Record<string, string>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (open) {
      setValues({});
      setErrors({});
    }
  }, [open]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const next: Record<string, string> = {};
    for (const f of fields) {
      const code = validateField(values[f.name] ?? '', f);
      if (code) next[f.name] = t(code, { min: f.minLength ?? 0, max: f.maxLength ?? 0 });
    }
    setErrors(next);
    if (Object.keys(next).length > 0) return;
    const trimmed: Record<string, string> = {};
    for (const f of fields) trimmed[f.name] = (values[f.name] ?? '').trim();
    onSubmit(trimmed);
  };

  const inputClass =
    'w-full text-xs p-2.5 rounded-btn border bg-background text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/30';

  return (
    <Dialog.Root open={open} onOpenChange={(o) => !pending && onOpenChange(o)}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs" />
        <Dialog.Content
          data-testid={dialogTestId}
          className="fixed z-50 left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-[calc(100%-2rem)] max-w-md bg-surface border border-border rounded-2xl p-6 space-y-4 shadow-2xl text-left"
        >
          <Dialog.Close asChild>
            <button
              type="button"
              aria-label={t('close')}
              className="absolute top-4 right-4 text-muted hover:text-foreground"
            >
              <X className="w-5 h-5" />
            </button>
          </Dialog.Close>

          <Dialog.Title className="text-base font-bold text-foreground pr-8">{title}</Dialog.Title>
          {description ? (
            <Dialog.Description className="text-xs text-muted leading-relaxed">{description}</Dialog.Description>
          ) : (
            <Dialog.Description className="sr-only">{title}</Dialog.Description>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-3">
            {fields.map((f) => {
              const id = `fd-${f.name}`;
              const err = errors[f.name];
              const common = {
                id,
                'data-testid': f.testId,
                value: values[f.name] ?? '',
                placeholder: f.placeholder,
                'aria-required': f.required ? true : undefined,
                'aria-invalid': err ? true : undefined,
                'aria-describedby': err ? `${id}-error` : undefined,
                className: `${inputClass} ${err ? 'border-red-600' : 'border-border'}`,
              };
              return (
                <div key={f.name} className="space-y-1.5">
                  <label htmlFor={id} className="block text-xs font-semibold text-foreground">
                    {f.label}
                    {f.required ? <span aria-hidden="true"> *</span> : null}
                  </label>
                  {f.multiline ? (
                    <textarea
                      {...common}
                      rows={3}
                      onChange={(e) => setValues((v) => ({ ...v, [f.name]: e.target.value }))}
                      className={`${common.className} resize-none`}
                    />
                  ) : (
                    <input
                      {...common}
                      type="text"
                      onChange={(e) => setValues((v) => ({ ...v, [f.name]: e.target.value }))}
                    />
                  )}
                  {err ? (
                    <p id={`${id}-error`} role="alert" className="text-[11px] text-red-700 dark:text-red-400">
                      {err}
                    </p>
                  ) : null}
                </div>
              );
            })}

            {error ? (
              <p role="alert" data-testid="form-dialog-error" className="text-xs text-red-700 dark:text-red-400">
                {error}
              </p>
            ) : null}

            <div className="grid grid-cols-2 gap-2 pt-2">
              <button
                type="button"
                onClick={() => onOpenChange(false)}
                disabled={pending}
                className="h-9 rounded-btn border border-border bg-surface hover:bg-muted/10 text-xs font-bold text-foreground"
              >
                {cancelLabel}
              </button>
              <button
                type="submit"
                data-testid={submitTestId}
                disabled={pending}
                className={`h-9 rounded-btn text-white text-xs font-bold disabled:opacity-50 flex items-center justify-center gap-1.5 ${
                  destructive ? 'bg-red-700 hover:bg-red-800' : 'bg-primary hover:bg-primary/90 text-primary-foreground'
                }`}
              >
                {pending ? <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" /> : null}
                <span>{submitLabel}</span>
              </button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
