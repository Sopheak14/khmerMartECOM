'use client';

import React, { useEffect, useState } from 'react';
import Image, { ImageProps } from 'next/image';
import { ImageOff } from 'lucide-react';

/**
 * next/image that swaps to a neutral placeholder when the URL fails to load (offline, 404, blocked host),
 * so the UI never shows a broken-image icon. Intended for `fill` images inside a relative container.
 */
export function SafeImage({ alt, onError, ...props }: ImageProps) {
  const [failed, setFailed] = useState(false);
  const src = typeof props.src === 'string' ? props.src : '';

  useEffect(() => {
    setFailed(false);
  }, [src]);

  if (!src || failed) {
    return (
      <div
        role="img"
        aria-label={alt}
        data-testid="image-fallback"
        className="absolute inset-0 flex items-center justify-center bg-muted/10 text-muted"
      >
        <ImageOff className="w-1/4 h-1/4 max-w-8 max-h-8 opacity-60" aria-hidden="true" />
      </div>
    );
  }

  return (
    <Image
      {...props}
      alt={alt}
      onError={(e) => {
        setFailed(true);
        onError?.(e);
      }}
    />
  );
}
