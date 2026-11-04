import { describe, it, expect } from 'vitest';
import { isInCambodia, roundCoord, osmUrl, suggestionFromNominatim, PHNOM_PENH } from '@/lib/geo';

describe('geo helpers', () => {
  it('accepts coordinates inside the backend bounds and rejects outside', () => {
    expect(isInCambodia(...PHNOM_PENH)).toBe(true);
    expect(isInCambodia(13.3633, 103.8564)).toBe(true); // Siem Reap
    expect(isInCambodia(13.75, 100.5)).toBe(false); // Bangkok
    expect(isInCambodia(51.5, -0.12)).toBe(false); // London
    expect(isInCambodia(NaN, 104)).toBe(false);
  });

  it('rounds to 6 decimals', () => {
    expect(roundCoord(11.55641234567)).toBe(11.556412);
  });

  it('builds an OpenStreetMap link', () => {
    expect(osmUrl(11.5564, 104.9282)).toBe(
      'https://www.openstreetmap.org/?mlat=11.5564&mlon=104.9282#map=17/11.5564/104.9282'
    );
  });

  it('maps a Nominatim response to address fields', () => {
    const s = suggestionFromNominatim({
      address: { house_number: '12', road: 'Street 240', suburb: 'Chaktomuk', city_district: 'Daun Penh' },
    });
    expect(s).toEqual({ line1: '12 Street 240, Chaktomuk', district: 'Daun Penh' });
    expect(suggestionFromNominatim({})).toEqual({});
  });
});
