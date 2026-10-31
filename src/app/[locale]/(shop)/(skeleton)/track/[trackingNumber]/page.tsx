'use client';

import React from 'react';
import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { useTranslations, useLocale } from 'next-intl';
import { Link } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { TrackingDTO, ShipmentStatus } from '@/types/api';
import {
  Truck,
  Package,
  CheckCircle2,
  Clock,
  AlertTriangle,
  ArrowLeft,
  Calendar,
  MapPin,
  RotateCcw,
  Loader2,
  Search,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export default function TrackingDetailPage() {
  const t = useTranslations('track');
  const locale = useLocale();
  const params = useParams();
  const trackingNumber = params?.trackingNumber as string;

  const { data: tracking, isLoading, error } = useQuery({
    queryKey: queryKeys.tracking.detail(trackingNumber),
    queryFn: async () => {
      const res = await apiClient<TrackingDTO>(`/track/${trackingNumber}`, { auth: false });
      return res.data;
    },
    enabled: Boolean(trackingNumber),
    retry: 1,
  });

  const getStatusBadge = (status: ShipmentStatus) => {
    switch (status) {
      case 'PREPARING':
        return {
          label: t('status.PREPARING'),
          className: 'bg-amber-500/10 text-amber-800 dark:text-amber-400 border-amber-500/20',
          icon: Clock,
        };
      case 'DISPATCHED':
        return {
          label: t('status.DISPATCHED'),
          className: 'bg-blue-500/10 text-blue-700 dark:text-blue-400 border-blue-500/20',
          icon: Package,
        };
      case 'IN_TRANSIT':
        return {
          label: t('status.IN_TRANSIT'),
          className: 'bg-indigo-500/10 text-indigo-700 dark:text-indigo-400 border-indigo-500/20',
          icon: Truck,
        };
      case 'OUT_FOR_DELIVERY':
        return {
          label: t('status.OUT_FOR_DELIVERY'),
          className: 'bg-purple-500/10 text-purple-700 dark:text-purple-400 border-purple-500/20',
          icon: Truck,
        };
      case 'DELIVERED':
        return {
          label: t('status.DELIVERED'),
          className: 'bg-green-500/10 text-green-800 dark:text-green-400 border-green-500/20',
          icon: CheckCircle2,
        };
      case 'FAILED':
        return {
          label: t('status.FAILED'),
          className: 'bg-red-500/10 text-red-700 dark:text-red-400 border-red-500/20',
          icon: AlertTriangle,
        };
      default:
        return {
          label: status,
          className: 'bg-muted/15 text-muted border-border',
          icon: Package,
        };
    }
  };

  if (isLoading) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-20 text-center space-y-4">
        <Loader2 className="w-8 h-8 animate-spin mx-auto text-primary" />
        <p className="text-xs text-muted">Tracking shipment {trackingNumber}...</p>
      </div>
    );
  }

  // Not Found / Error UI
  if (error || !tracking) {
    return (
      <div
        data-testid="tracking-not-found"
        className="max-w-lg mx-auto px-4 py-16 text-center space-y-6"
      >
        <div className="w-16 h-16 rounded-full bg-red-500/10 text-red-700 flex items-center justify-center mx-auto">
          <Search className="w-8 h-8" />
        </div>

        <div className="space-y-2">
          <h2 className="text-xl font-bold text-foreground">{t('notFoundTitle')}</h2>
          <p className="text-xs sm:text-sm text-muted leading-relaxed">
            {t('notFoundDesc')}
          </p>
          <p className="text-xs font-mono font-semibold text-muted bg-muted/10 inline-block px-3 py-1 rounded-md mt-2">
            {trackingNumber}
          </p>
        </div>

        <div className="pt-2">
          <Link
            href="/track"
            data-testid="back-to-track-btn"
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-btn bg-primary text-primary-foreground text-xs font-bold shadow-xs hover:bg-primary/90 transition-all"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>{t('backToTrack')}</span>
          </Link>
        </div>
      </div>
    );
  }

  const currentStatusInfo = getStatusBadge(tracking.status);
  const StatusIcon = currentStatusInfo.icon;

  // Sort events newest first
  const sortedEvents = [...(tracking.events || [])].sort(
    (a, b) => new Date(b.at).getTime() - new Date(a.at).getTime()
  );

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8 sm:py-12 space-y-6" data-testid="tracking-detail-page">
      {/* Back button */}
      <div>
        <Link
          href="/track"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-muted hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>{t('backToTrack')}</span>
        </Link>
      </div>

      {/* Main Tracking Card */}
      <div
        className="bg-surface border border-border rounded-2xl p-6 sm:p-8 space-y-6 shadow-xs overflow-hidden"
        data-testid="tracking-detail-card"
      >
        {/* Top Header Information */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-border">
          <div className="space-y-1">
            <span className="text-[11px] font-bold uppercase tracking-wider text-muted">
              {t('trackingNumber')}
            </span>
            <h1
              data-testid="tracking-number-value"
              className="text-xl sm:text-2xl font-black text-foreground font-mono tracking-tight break-all"
            >
              {tracking.trackingNumber}
            </h1>
            <div className="flex items-center gap-2 text-xs text-muted pt-1">
              <span className="font-semibold text-foreground" data-testid="tracking-carrier-value">
                {tracking.carrier}
              </span>
              {tracking.orderNumber && (
                <>
                  <span>•</span>
                  <Link
                    href={`/orders/${tracking.orderNumber}`}
                    className="text-primary hover:underline font-mono"
                  >
                    Order {tracking.orderNumber}
                  </Link>
                </>
              )}
            </div>
          </div>

          <div>
            <span
              data-testid="tracking-status-badge"
              className={cn(
                'inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-full text-xs font-bold border shadow-2xs',
                currentStatusInfo.className
              )}
            >
              <StatusIcon className="w-3.5 h-3.5" />
              <span>{currentStatusInfo.label}</span>
            </span>
          </div>
        </div>

        {/* Estimated Delivery (if present) */}
        {tracking.estimatedDelivery && (
          <div className="p-3.5 rounded-xl bg-primary/5 border border-primary/20 flex items-center gap-3 text-xs">
            <Calendar className="w-4 h-4 text-primary shrink-0" />
            <div>
              <span className="text-muted">{t('estimatedDelivery')}: </span>
              <span className="font-bold text-foreground">
                {new Date(tracking.estimatedDelivery).toLocaleDateString(
                  locale === 'km' ? 'km-KH' : 'en-US',
                  { dateStyle: 'full' }
                )}
              </span>
            </div>
          </div>
        )}

        {/* Vertical Timeline of Events: Newest First */}
        <div className="space-y-4 pt-2">
          <h3 className="font-bold text-xs uppercase tracking-wider text-muted">
            {t('timeline')}
          </h3>

          {sortedEvents.length === 0 ? (
            <p className="text-xs text-muted">No tracking events recorded yet.</p>
          ) : (
            <div
              className="relative pl-6 space-y-6 border-l-2 border-primary/20 ml-3"
              data-testid="tracking-events-timeline"
            >
              {sortedEvents.map((event, idx) => {
                const eventStatusInfo = getStatusBadge(event.status);
                const eventDate = new Date(event.at).toLocaleString(
                  locale === 'km' ? 'km-KH' : 'en-US',
                  { dateStyle: 'medium', timeStyle: 'short' }
                );
                const isLatest = idx === 0;

                return (
                  <div
                    key={idx}
                    data-testid="tracking-event-item"
                    className="relative group space-y-1"
                  >
                    {/* Circle on vertical line */}
                    <div
                      className={cn(
                        'absolute -left-[31px] top-1 w-4 h-4 rounded-full border-4 border-surface shadow-2xs',
                        isLatest ? 'bg-primary ring-4 ring-primary/20' : 'bg-muted/40'
                      )}
                    />

                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-bold text-xs sm:text-sm text-foreground">
                        {eventStatusInfo.label}
                      </span>
                      <span
                        data-testid="tracking-event-time"
                        className="text-[11px] text-muted font-medium"
                      >
                        {eventDate}
                      </span>
                    </div>

                    {event.location && (
                      <p
                        data-testid="tracking-event-location"
                        className="text-xs text-muted flex items-center gap-1"
                      >
                        <MapPin className="w-3 h-3 text-muted/60 shrink-0" />
                        <span>{event.location}</span>
                      </p>
                    )}

                    {event.note && (
                      <p
                        data-testid="tracking-event-note"
                        className="text-xs text-foreground/80 leading-relaxed pt-0.5"
                      >
                        {event.note}
                      </p>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
