// Cambodia bounding box accepted by the backend (API_CONTRACT.md): lat 9.5..15.0, lng 102.0..108.0
export const CAMBODIA_BOUNDS = { latMin: 9.5, latMax: 15.0, lngMin: 102.0, lngMax: 108.0 };
export const PHNOM_PENH: [number, number] = [11.5564, 104.9282];

export function isInCambodia(lat: number, lng: number): boolean {
  return (
    Number.isFinite(lat) &&
    Number.isFinite(lng) &&
    lat >= CAMBODIA_BOUNDS.latMin &&
    lat <= CAMBODIA_BOUNDS.latMax &&
    lng >= CAMBODIA_BOUNDS.lngMin &&
    lng <= CAMBODIA_BOUNDS.lngMax
  );
}

/** Round to the backend's NUMERIC(9,6) precision. */
export function roundCoord(n: number): number {
  return Math.round(n * 1e6) / 1e6;
}

export function osmUrl(lat: number, lng: number): string {
  return `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lng}#map=17/${lat}/${lng}`;
}

export interface AddressSuggestion {
  line1?: string;
  district?: string;
}

/** Map a Nominatim reverse-geocode JSON body to our address fields. */
export function suggestionFromNominatim(body: any): AddressSuggestion {
  const a = body?.address;
  if (!a) return {};
  const street = [a.house_number, a.road].filter(Boolean).join(' ');
  const area = a.neighbourhood || a.suburb || a.quarter || a.village;
  const line1 = [street, area].filter(Boolean).join(', ') || undefined;
  const district = a.city_district || a.district || a.county || a.suburb || undefined;
  return { line1, district };
}

let lastRequestAt = 0;
let chain: Promise<unknown> = Promise.resolve();
export const NOMINATIM_MIN_INTERVAL_MS = 1100;

/**
 * Reverse geocode via Nominatim. Calls are serialized and spaced >= 1.1s apart
 * (usage policy: max 1 request/second).
 */
export function reverseGeocode(lat: number, lng: number, lang: string): Promise<AddressSuggestion> {
  const run = async (): Promise<AddressSuggestion> => {
    const wait = lastRequestAt + NOMINATIM_MIN_INTERVAL_MS - Date.now();
    if (wait > 0) await new Promise((r) => setTimeout(r, wait));
    lastRequestAt = Date.now();
    const url =
      `https://nominatim.openstreetmap.org/reverse?format=jsonv2&addressdetails=1` +
      `&lat=${lat}&lon=${lng}&accept-language=${lang}`;
    const res = await fetch(url, { headers: { Accept: 'application/json' } });
    if (!res.ok) throw new Error(`Nominatim ${res.status}`);
    return suggestionFromNominatim(await res.json());
  };
  const p = chain.then(run, run);
  chain = p.catch(() => undefined);
  return p;
}
