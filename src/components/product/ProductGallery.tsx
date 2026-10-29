'use client';

import React, { useState } from 'react';
import { SafeImage } from '@/components/common/SafeImage';
import { ImageDTO } from '@/types/api';
import { getImageUrl } from '@/lib/images';

interface ProductGalleryProps {
  images: ImageDTO[];
  productName: string;
}

export function ProductGallery({ images, productName }: ProductGalleryProps) {
  const [selectedIdx, setSelectedIdx] = useState(0);

  const currentImage = images[selectedIdx] || images[0] || {
    url: '',
    id: 0,
  };

  return (
    <div className="space-y-4" data-testid="product-gallery">
      {/* Main Image */}
      <div className="relative aspect-square w-full rounded-card overflow-hidden bg-muted/20 border border-border shadow-xs">
        <SafeImage
          src={getImageUrl(currentImage.url)}
          alt={productName}
          fill
          priority
          sizes="(max-width: 768px) 100vw, 50vw"
          className="object-cover transition-all duration-300"
        />
      </div>

      {/* Thumbnails */}
      {images.length > 1 && (
        <div className="flex items-center gap-3 overflow-x-auto pb-2" data-testid="gallery-thumbnails">
          {images.map((img, idx) => (
            <button
              key={img.id || idx}
              type="button"
              data-testid={`gallery-thumb-${idx}`}
              onClick={() => setSelectedIdx(idx)}
              className={`relative w-18 h-18 sm:w-20 sm:h-20 rounded-btn overflow-hidden border-2 flex-shrink-0 transition-all ${
                selectedIdx === idx
                  ? 'border-primary ring-2 ring-primary/20 scale-102'
                  : 'border-border/70 opacity-70 hover:opacity-100 hover:border-primary/40'
              }`}
            >
              <SafeImage
                src={getImageUrl(img.url)}
                alt={`${productName} thumbnail ${idx + 1}`}
                fill
                sizes="80px"
                className="object-cover"
              />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
