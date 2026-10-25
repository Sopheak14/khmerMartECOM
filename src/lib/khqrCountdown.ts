export interface KhqrCountdownResult {
  minutes: number;
  seconds: number;
  formatted: string;
  isExpired: boolean;
  isUnderOneMinute: boolean;
  totalSeconds: number;
}

export function calculateKhqrCountdown(
  expiresAt: string | Date | number,
  now: number = Date.now()
): KhqrCountdownResult {
  const expiryMs = typeof expiresAt === 'number' ? expiresAt : new Date(expiresAt).getTime();
  const diffMs = expiryMs - now;

  if (isNaN(expiryMs) || diffMs <= 0) {
    return {
      minutes: 0,
      seconds: 0,
      formatted: '00:00',
      isExpired: true,
      isUnderOneMinute: false,
      totalSeconds: 0,
    };
  }

  const totalSeconds = Math.floor(diffMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  const mm = String(minutes).padStart(2, '0');
  const ss = String(seconds).padStart(2, '0');

  return {
    minutes,
    seconds,
    formatted: `${mm}:${ss}`,
    isExpired: false,
    isUnderOneMinute: totalSeconds < 60,
    totalSeconds,
  };
}
