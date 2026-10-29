const KHMER_RE = /[ក-៿]/;
const LATIN_RE = /[A-Za-z]/;

/**
 * The backend stores one description holding the English paragraph followed by the Khmer paragraph.
 * When the text is exactly one Latin paragraph and one Khmer paragraph, returns the one matching `locale`;
 * any other shape is returned untouched.
 */
export function localizedDescription(text: string | null | undefined, locale: string): string {
  if (!text) return '';
  const paragraphs = text.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean);
  if (paragraphs.length !== 2) return text;

  const kinds = paragraphs.map((p) => (KHMER_RE.test(p) ? 'km' : LATIN_RE.test(p) ? 'en' : null));
  if (!kinds.includes('km') || !kinds.includes('en')) return text;

  return paragraphs[kinds.indexOf(locale === 'km' ? 'km' : 'en')];
}
