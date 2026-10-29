import { describe, it, expect } from 'vitest';
import { localizedDescription } from '@/lib/description';

const EN = 'Hand-woven hol silk sampot from Takeo, worn at weddings and ceremonies.';
const KM = 'សំពត់ហូលសូត្រត្បាញដៃពីខេត្តតាកែវ សម្រាប់ពាក់ក្នុងពិធីមង្គលការ។';
const BOTH = `${EN}\n\n${KM}`;

describe('localizedDescription', () => {
  it('returns the English paragraph for en', () => {
    expect(localizedDescription(BOTH, 'en')).toBe(EN);
  });

  it('returns the Khmer paragraph for km', () => {
    expect(localizedDescription(BOTH, 'km')).toBe(KM);
  });

  it('handles Khmer first and CRLF/blank-with-spaces separators', () => {
    expect(localizedDescription(`${KM}\r\n \r\n${EN}`, 'en')).toBe(EN);
  });

  it('returns the whole text when there is a single paragraph', () => {
    expect(localizedDescription(EN, 'km')).toBe(EN);
    expect(localizedDescription(KM, 'en')).toBe(KM);
  });

  it('returns the whole text for more than two paragraphs', () => {
    const three = `${EN}\n\nSecond English paragraph.\n\n${KM}`;
    expect(localizedDescription(three, 'en')).toBe(three);
  });

  it('returns the whole text when both paragraphs are the same script', () => {
    const two = 'First English.\n\nSecond English.';
    expect(localizedDescription(two, 'km')).toBe(two);
  });

  it('handles empty input', () => {
    expect(localizedDescription(null, 'en')).toBe('');
    expect(localizedDescription('', 'km')).toBe('');
  });
});
