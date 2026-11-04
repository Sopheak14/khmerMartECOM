'use client';

import React from 'react';
import dynamic from 'next/dynamic';
import { useLocale, useTranslations } from 'next-intl';
import { isInCambodia, reverseGeocode, roundCoord, AddressSuggestion } from '@/lib/geo';
import type { LatLng } from './LocationPicker';

// Leaflet touches `window`, so the map is client-only.
const LocationPicker = dynamic(() => import('./LocationPicker'), {
  ssr: false,
  loading: () => <div className="h-56 sm:h-64 w-full rounded-btn bg-muted/15 animate-pulse" />,
});

/**
 * Keeps reverse-geocoded suggestions from clobbering what the user typed: a field is only
 * (re)filled when it is empty or still holds the previous automatic suggestion.
 */
export function useAddressAutofill(
  line1: string,
  setLine1: (v: string) => void,
  district: string,
  setDistrict: (v: string) => void
) {
  const last = React.useRef<AddressSuggestion>({});
  return React.useCallback(
    (s: AddressSuggestion) => {
      if (s.line1 && (!line1.trim() || line1 === last.current.line1)) {
        setLine1(s.line1);
        last.current.line1 = s.line1;
      }
      if (s.district && (!district.trim() || district === last.current.district)) {
        setDistrict(s.district);
        last.current.district = s.district;
      }
    },
    [line1, district, setLine1, setDistrict]
  );
}

interface Props {
  value: LatLng | null;
  onChange: (p: LatLng | null) => void;
  onSuggest?: (s: AddressSuggestion) => void;
}

export function AddressLocationField({ value, onChange, onSuggest }: Props) {
  const t = useTranslations('address');
  const locale = useLocale();
  const [error, setError] = React.useState<string | null>(null);
  const [locating, setLocating] = React.useState(false);
  const seq = React.useRef(0);

  const pick = React.useCallback(
    (p: LatLng) => {
      if (!isInCambodia(p.lat, p.lng)) {
        setError(t('locationOutside'));
        return;
      }
      setError(null);
      const point = { lat: roundCoord(p.lat), lng: roundCoord(p.lng) };
      onChange(point);
      if (onSuggest) {
        const mine = ++seq.current;
        reverseGeocode(point.lat, point.lng, locale === 'km' ? 'km,en' : 'en')
          .then((s) => {
            if (mine === seq.current) onSuggest(s);
          })
          .catch(() => {
            /* best effort: user can type the address manually */
          });
      }
    },
    [onChange, onSuggest, locale, t]
  );

  const useMyLocation = () => {
    setError(null);
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setError(t('locationUnavailable'));
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false);
        pick({ lat: pos.coords.latitude, lng: pos.coords.longitude });
      },
      (err) => {
        setLocating(false);
        setError(err.code === err.PERMISSION_DENIED ? t('locationDenied') : t('locationUnavailable'));
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  return (
    <div className="space-y-2" data-testid="address-location-field">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-xs font-semibold text-foreground">{t('mapLabel')}</span>
        <button
          type="button"
          data-testid="use-my-location-btn"
          onClick={useMyLocation}
          disabled={locating}
          className="text-xs font-semibold px-3 py-1.5 rounded-btn border border-border hover:bg-muted/10 disabled:opacity-50"
        >
          {locating ? t('locating') : `📍 ${t('useMyLocation')}`}
        </button>
      </div>
      <LocationPicker value={value} onChange={pick} />
      <p className="text-[11px] text-muted">{t('mapHint')}</p>
      {value && (
        <div className="flex items-center justify-between gap-2 text-[11px] text-muted">
          <span data-testid="location-coords">
            {value.lat.toFixed(6)}, {value.lng.toFixed(6)}
          </span>
          <button
            type="button"
            data-testid="clear-location-btn"
            onClick={() => onChange(null)}
            className="underline hover:text-foreground"
          >
            {t('clearLocation')}
          </button>
        </div>
      )}
      {error && (
        <p role="alert" data-testid="location-error" className="text-xs text-destructive">
          {error}
        </p>
      )}
      {onSuggest && (
        <p className="text-[10px] text-muted">
          {t('geocodeAttribution')}{' '}
          <a
            href="https://nominatim.org"
            target="_blank"
            rel="noopener noreferrer"
            className="underline"
          >
            Nominatim
          </a>{' '}
          · © OpenStreetMap contributors
        </p>
      )}
    </div>
  );
}
