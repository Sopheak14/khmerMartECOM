export const KHMER_DIGITS: Record<string, string> = {
  '0': '០',
  '1': '១',
  '2': '២',
  '3': '៣',
  '4': '៤',
  '5': '៥',
  '6': '៦',
  '7': '៧',
  '8': '៨',
  '9': '៩',
};

export function toKhmerDigits(value: number | string): string {
  return String(value)
    .split('')
    .map((char) => KHMER_DIGITS[char] ?? char)
    .join('');
}

export function formatRelativeTime(
  dateInput: string | number | Date,
  locale: string = 'en',
  nowInput?: Date
): string {
  const date = new Date(dateInput);
  if (isNaN(date.getTime())) return '';

  const now = nowInput ?? new Date();
  const diffInSeconds = Math.max(0, Math.floor((now.getTime() - date.getTime()) / 1000));
  const isKm = locale === 'km';

  // < 60s
  if (diffInSeconds < 60) {
    return isKm ? 'អម្បាញ់មិញ' : 'just now';
  }

  const minutes = Math.floor(diffInSeconds / 60);
  if (minutes < 60) {
    if (isKm) {
      return `${toKhmerDigits(minutes)} នាទីមុន`;
    }
    return minutes === 1 ? '1 minute ago' : `${minutes} minutes ago`;
  }

  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    if (isKm) {
      return `${toKhmerDigits(hours)} ម៉ោងមុន`;
    }
    return hours === 1 ? '1 hour ago' : `${hours} hours ago`;
  }

  const days = Math.floor(hours / 24);
  if (days < 30) {
    if (isKm) {
      return `${toKhmerDigits(days)} ថ្ងៃមុន`;
    }
    return days === 1 ? '1 day ago' : `${days} days ago`;
  }

  const months = Math.floor(days / 30);
  if (months < 12) {
    if (isKm) {
      return `${toKhmerDigits(months)} ខែមុន`;
    }
    return months === 1 ? '1 month ago' : `${months} months ago`;
  }

  const years = Math.floor(days / 365);
  if (isKm) {
    return `${toKhmerDigits(years)} ឆ្នាំមុន`;
  }
  return years === 1 ? '1 year ago' : `${years} years ago`;
}
