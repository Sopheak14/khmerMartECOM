'use client';

import React, { useState, useRef } from 'react';
import { Star } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface StarRatingProps {
  id?: string;
  value: number;
  onChange?: (rating: number) => void;
  maxStars?: number;
  readOnly?: boolean;
  disabled?: boolean;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
  label?: string;
  error?: string;
}

export function StarRating({
  id = 'star-rating',
  value = 0,
  onChange,
  maxStars = 5,
  readOnly = false,
  disabled = false,
  size = 'md',
  className,
  label = 'Rating',
  error,
}: StarRatingProps) {
  const [hoverValue, setHoverValue] = useState<number | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const starSizes = {
    sm: 'w-4 h-4',
    md: 'w-6 h-6',
    lg: 'w-8 h-8',
  };

  const currentVal = hoverValue !== null ? hoverValue : value;

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (readOnly || disabled || !onChange) return;

    if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
      e.preventDefault();
      const next = Math.min(maxStars, (value || 0) + 1);
      onChange(next);
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
      e.preventDefault();
      const prev = Math.max(1, (value || 1) - 1);
      onChange(prev);
    } else if (e.key >= '1' && e.key <= String(maxStars)) {
      e.preventDefault();
      onChange(Number(e.key));
    }
  };

  return (
    <div className={cn('space-y-1', className)}>
      {label && (
        <label htmlFor={id} className="block text-xs font-semibold text-foreground">
          {label}
        </label>
      )}

      <div
        id={id}
        ref={containerRef}
        role="radiogroup"
        aria-label={label}
        tabIndex={readOnly || disabled ? -1 : 0}
        onKeyDown={handleKeyDown}
        className={cn(
          'inline-flex items-center gap-1 focus:outline-hidden focus-visible:ring-2 focus-visible:ring-primary/40 rounded-btn p-1 transition-all',
          disabled && 'opacity-50 cursor-not-allowed'
        )}
        data-testid="star-rating-input"
      >
        {Array.from({ length: maxStars }, (_, i) => {
          const starNumber = i + 1;
          const isFilled = starNumber <= currentVal;

          return (
            <button
              key={starNumber}
              type="button"
              role="radio"
              aria-checked={value === starNumber}
              aria-label={`${starNumber} of ${maxStars} stars`}
              disabled={disabled || readOnly}
              data-testid={`star-${starNumber}`}
              onClick={() => {
                if (!readOnly && !disabled && onChange) {
                  onChange(starNumber);
                }
              }}
              onMouseEnter={() => {
                if (!readOnly && !disabled) {
                  setHoverValue(starNumber);
                }
              }}
              onMouseLeave={() => {
                if (!readOnly && !disabled) {
                  setHoverValue(null);
                }
              }}
              className={cn(
                'p-0.5 rounded transition-transform hover:scale-110 focus:outline-hidden text-muted/30 focus-visible:ring-1 focus-visible:ring-primary',
                (readOnly || disabled) && 'hover:scale-100 cursor-default'
              )}
            >
              <Star
                className={cn(
                  starSizes[size],
                  isFilled
                    ? 'fill-amber-400 text-amber-400'
                    : 'fill-muted/20 text-muted/30 hover:fill-amber-200'
                )}
              />
            </button>
          );
        })}
      </div>

      {error && (
        <p className="text-xs text-destructive font-medium" data-testid="error-rating">
          {error}
        </p>
      )}
    </div>
  );
}
