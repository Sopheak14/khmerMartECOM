export type FeeError = 'required' | 'invalid' | 'negative' | 'decimals';

export type FeeResult = { ok: true; cents: number | null } | { ok: false; error: FeeError };

/**
 * Validate a dollar amount typed by an admin: must be >= 0 with at most two decimals.
 * With `optional`, an empty string is valid and yields `cents: null` (e.g. "no free threshold").
 */
export function parseFeeInput(raw: string, opts: { optional?: boolean } = {}): FeeResult {
  const value = raw.trim();
  if (value === '') {
    return opts.optional ? { ok: true, cents: null } : { ok: false, error: 'required' };
  }
  if (value.startsWith('-')) return { ok: false, error: 'negative' };
  if (!/^\d*\.?\d*$/.test(value) || value === '.') return { ok: false, error: 'invalid' };
  if (/^\d*\.\d{3,}$/.test(value)) return { ok: false, error: 'decimals' };
  const dollars = Number(value);
  if (!Number.isFinite(dollars)) return { ok: false, error: 'invalid' };
  return { ok: true, cents: Math.round(dollars * 100) };
}
