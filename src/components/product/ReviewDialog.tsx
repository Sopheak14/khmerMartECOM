'use client';

import React, { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { apiClient, ApiError } from '@/lib/apiClient';
import { StarRating } from '@/components/ui/star-rating';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { ReviewDTO } from '@/types/api';
import { X, Loader2, Star, MessageSquare } from 'lucide-react';
import { toast } from 'sonner';

export interface ReviewDialogProps {
  isOpen: boolean;
  onClose: () => void;
  productId: number;
  productName: string;
  existingReview?: {
    id: number;
    rating: number;
    title: string;
    body: string;
  } | null;
  onSuccess: (review: ReviewDTO) => void;
}

export function ReviewDialog({
  isOpen,
  onClose,
  productId,
  productName,
  existingReview,
  onSuccess,
}: ReviewDialogProps) {
  const t = useTranslations('reviews');

  const [rating, setRating] = useState<number>(existingReview?.rating || 5);
  const [title, setTitle] = useState<string>(existingReview?.title || '');
  const [body, setBody] = useState<string>(existingReview?.body || '');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [generalError, setGeneralError] = useState<string | null>(null);

  useEffect(() => {
    if (existingReview) {
      setRating(existingReview.rating || 5);
      setTitle(existingReview.title || '');
      setBody(existingReview.body || '');
    } else {
      setRating(5);
      setTitle('');
      setBody('');
    }
    setFieldErrors({});
    setGeneralError(null);
  }, [existingReview, isOpen]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});
    setGeneralError(null);

    // Client-side quick check
    const errors: Record<string, string> = {};
    if (!rating || rating < 1 || rating > 5) {
      errors.rating = 'Rating is required';
    }
    if (!body || body.trim() === '') {
      errors.body = 'Review body is required';
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setIsSubmitting(true);

    try {
      let res: { data: ReviewDTO };
      if (existingReview?.id) {
        // Edit existing review
        res = await apiClient<ReviewDTO>(`/reviews/${existingReview.id}`, {
          method: 'PATCH',
          body: {
            rating,
            title: title.trim() || undefined,
            body: body.trim(),
          },
        });
      } else {
        // Create new review
        res = await apiClient<ReviewDTO>(`/products/${productId}/reviews`, {
          method: 'POST',
          body: {
            rating,
            title: title.trim() || undefined,
            body: body.trim(),
          },
        });
      }

      toast.success(t('successMessage'));
      onSuccess(res.data);
      onClose();
    } catch (err: any) {
      if (err instanceof ApiError) {
        if (err.details && Object.keys(err.details).length > 0) {
          setFieldErrors(err.details);
        } else {
          setGeneralError(err.message || t('duplicateError'));
        }
      } else {
        setGeneralError(err.message || 'Failed to submit review');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="review-dialog-title"
      data-testid="review-dialog"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in duration-200"
    >
      <div className="bg-surface border border-border rounded-2xl max-w-md w-full p-6 space-y-5 shadow-2xl text-left relative animate-in zoom-in-95 duration-150">
        {/* Close Button */}
        <button
          type="button"
          onClick={onClose}
          aria-label="Close dialog"
          className="absolute top-4 right-4 p-1 rounded-full text-muted hover:text-foreground hover:bg-muted/10 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Dialog Header */}
        <div className="space-y-1">
          <h2
            id="review-dialog-title"
            className="text-lg font-bold text-foreground tracking-tight"
          >
            {existingReview ? t('editReview') : t('writeReview')}
          </h2>
          <p className="text-xs text-muted truncate max-w-[320px] font-medium">
            {productName}
          </p>
        </div>

        {/* General Error Banner */}
        {generalError && (
          <div
            data-testid="review-form-error"
            className="p-3 text-xs rounded-btn bg-destructive/10 border border-destructive/30 text-destructive font-medium"
          >
            {generalError}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Star rating component */}
          <div>
            <StarRating
              id="review-rating-input"
              value={rating}
              onChange={setRating}
              label={t('starRating')}
              error={fieldErrors.rating}
              size="lg"
            />
          </div>

          {/* Title */}
          <div>
            <label
              htmlFor="review-title-input"
              className="block text-xs font-semibold text-foreground mb-1"
            >
              {t('reviewTitle')}
            </label>
            <input
              type="text"
              id="review-title-input"
              data-testid="review-title-input"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={t('reviewTitlePlaceholder')}
              className="w-full text-xs p-2.5 rounded-btn border border-border bg-background text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/20"
            />
            {fieldErrors.title && (
              <p className="text-xs text-destructive font-medium mt-1" data-testid="error-title">
                {fieldErrors.title}
              </p>
            )}
          </div>

          {/* Comment / Body */}
          <div>
            <label
              htmlFor="review-body-input"
              className="block text-xs font-semibold text-foreground mb-1"
            >
              {t('reviewBody')} *
            </label>
            <textarea
              id="review-body-input"
              data-testid="review-body-input"
              rows={4}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder={t('reviewBodyPlaceholder')}
              className="w-full text-xs p-2.5 rounded-btn border border-border bg-background text-foreground focus:outline-hidden focus:ring-2 focus:ring-primary/20 resize-none"
            />
            {fieldErrors.body && (
              <p className="text-xs text-destructive font-medium mt-1" data-testid="error-body">
                {fieldErrors.body}
              </p>
            )}
          </div>

          {/* Action Buttons */}
          <div className="grid grid-cols-2 gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="h-9 rounded-btn border border-border bg-surface hover:bg-muted/10 text-xs font-bold text-foreground transition-colors cursor-pointer"
            >
              {t('cancel')}
            </button>
            <button
              type="submit"
              data-testid="submit-review-btn"
              disabled={isSubmitting}
              className="h-9 rounded-btn bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-bold shadow-xs transition-colors disabled:opacity-50 flex items-center justify-center gap-1.5 cursor-pointer"
            >
              {isSubmitting ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <span>{t('submitReview')}</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
