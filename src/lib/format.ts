import { format, parseISO } from "date-fns";
import { toZonedTime } from "date-fns-tz";

export const PHNOM_PENH_TZ = "Asia/Phnom_Penh";

/**
 * Format an ISO date string into Cambodia local time
 */
export function formatPhnomPenhDate(
  dateString?: string | null,
  pattern: string = "dd MMM yyyy, HH:mm"
): string {
  if (!dateString) return "-";
  try {
    const utcDate = parseISO(dateString);
    const zonedDate = toZonedTime(utcDate, PHNOM_PENH_TZ);
    return format(zonedDate, pattern);
  } catch {
    return dateString;
  }
}

/**
 * Format date only (e.g. 24 Oct 2026)
 */
export function formatDateOnly(dateString?: string | null): string {
  return formatPhnomPenhDate(dateString, "dd MMM yyyy");
}
