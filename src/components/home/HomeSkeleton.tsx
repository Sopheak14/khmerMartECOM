'use client';

import { Skeleton } from '@/components/ui/skeleton';

export function HomeSkeleton() {
  return (
    <div className="space-y-12 animate-pulse" data-testid="home-skeleton">
      {/* Hero Banner Skeleton */}
      <section className="relative overflow-hidden bg-secondary/80 py-16 sm:py-24">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-2xl space-y-6">
            <Skeleton className="h-6 w-32 bg-white/10 rounded-chip" />
            <Skeleton className="h-12 sm:h-16 w-3/4 bg-white/10 rounded-btn" />
            <Skeleton className="h-16 w-full bg-white/10 rounded-btn" />
            <div className="flex gap-4 pt-2">
              <Skeleton className="h-12 w-36 bg-white/20 rounded-btn" />
              <Skeleton className="h-12 w-44 bg-white/10 rounded-btn" />
            </div>
          </div>
        </div>
      </section>

      {/* Trust Cards Skeleton */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="p-5 rounded-card bg-surface border border-border flex items-start gap-3.5">
              <Skeleton className="w-10 h-10 rounded-btn" />
              <div className="space-y-2 flex-1">
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-3 w-36" />
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Categories Skeleton */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-6">
        <div className="space-y-2">
          <Skeleton className="h-7 w-48" />
          <Skeleton className="h-4 w-64" />
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3 sm:gap-4">
          {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
            <div key={i} className="rounded-card bg-surface border border-border p-3 text-center space-y-2">
              <Skeleton className="w-14 h-14 rounded-full mx-auto" />
              <Skeleton className="h-3 w-16 mx-auto" />
              <Skeleton className="h-2.5 w-10 mx-auto" />
            </div>
          ))}
        </div>
      </section>

      {/* Flash Sale Skeleton */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-6">
        <div className="flex items-center justify-between">
          <div className="space-y-2">
            <Skeleton className="h-7 w-40" />
            <Skeleton className="h-4 w-48" />
          </div>
          <Skeleton className="h-8 w-44 rounded-btn" />
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="rounded-card bg-surface border border-border p-4 space-y-3">
              <div className="flex justify-between">
                <Skeleton className="h-4 w-16" />
                <Skeleton className="h-4 w-12" />
              </div>
              <Skeleton className="aspect-square w-full rounded-btn" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-5 w-24" />
            </div>
          ))}
        </div>
      </section>

      {/* Best Sellers Skeleton */}
      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 space-y-6">
        <div className="flex items-center justify-between">
          <div className="space-y-2">
            <Skeleton className="h-7 w-40" />
            <Skeleton className="h-4 w-52" />
          </div>
          <Skeleton className="h-4 w-16" />
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="rounded-card bg-surface border border-border p-4 space-y-3">
              <Skeleton className="aspect-square w-full rounded-btn" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-5 w-24" />
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
