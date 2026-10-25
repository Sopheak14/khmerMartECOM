'use client';

import React, { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter, usePathname } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { ReviewDTO, ReviewStatus } from '@/types/api';
import {
  Star,
  CheckCircle,
  XCircle,
  MessageSquare,
  ChevronLeft,
  ChevronRight,
  Loader2,
  X,
  AlertCircle,
  ShieldCheck,
  Send,
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

export default function AdminReviewsPage() {
  const t = useTranslations('admin.reviews');
  const commonT = useTranslations('admin.common');
  const locale = useLocale();
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();

  const statusParam = (searchParams.get('status') as ReviewStatus) || 'PENDING';
  const page = parseInt(searchParams.get('page') || '1', 10);
  const pageSize = 15;

  const [replyReview, setReplyReview] = useState<ReviewDTO | null>(null);
  const [replyText, setReplyText] = useState('');

  const updateFilters = (newParams: Record<string, string | number | null | undefined>) => {
    const current = new URLSearchParams(searchParams.toString());
    Object.entries(newParams).forEach(([k, v]) => {
      if (v === null || v === undefined || v === '') {
        current.delete(k);
      } else {
        current.set(k, String(v));
      }
    });
    router.push(`${pathname}?${current.toString()}`);
  };

  const { data: reviewsData, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.admin.reviews({ status: statusParam, page: page - 1, size: pageSize }),
    queryFn: async () => {
      const res = await apiClient<ReviewDTO[]>('/admin/reviews', {
        query: { status: statusParam, page: page - 1, size: pageSize },
      });
      return res;
    },
  });

  const reviews = reviewsData?.data || [];
  const meta = reviewsData?.meta;
  const totalPages = meta?.totalPages || 1;

  const updateMutation = useMutation({
    mutationFn: async ({ id, status, reply }: { id: number; status: ReviewStatus; reply?: string }) => {
      const res = await apiClient<ReviewDTO>(`/admin/reviews/${id}`, {
        method: 'PATCH',
        body: { status, adminReply: reply || undefined },
      });
      return res.data;
    },
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'reviews'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      toast.success(`Review ${updated.status.toLowerCase()} successfully`);
      setReplyReview(null);
      setReplyText('');
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to update review status');
    },
  });

  const handleApprove = (rev: ReviewDTO) => {
    updateMutation.mutate({ id: rev.id, status: 'APPROVED', reply: rev.adminReply || undefined });
  };

  const handleReject = (rev: ReviewDTO) => {
    updateMutation.mutate({ id: rev.id, status: 'REJECTED', reply: rev.adminReply || undefined });
  };

  const openReplyModal = (rev: ReviewDTO) => {
    setReplyReview(rev);
    setReplyText(rev.adminReply || '');
  };

  const handleReplySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!replyReview) return;
    updateMutation.mutate({
      id: replyReview.id,
      status: replyReview.status,
      reply: replyText.trim(),
    });
  };

  return (
    <div className="space-y-6" data-testid="admin-reviews-page">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/50">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-foreground tracking-tight flex items-center gap-2">
            <Star className="w-6 h-6 text-amber-500 fill-amber-500" />
            <span>{t('title')}</span>
          </h1>
          <p className="text-xs text-muted mt-0.5">{t('subtitle')}</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-2 border-b border-border text-xs font-semibold">
        <button
          type="button"
          data-testid="tab-pending"
          onClick={() => updateFilters({ status: 'PENDING', page: 1 })}
          className={cn(
            'px-4 py-2.5 border-b-2 transition-colors cursor-pointer',
            statusParam === 'PENDING'
              ? 'border-primary text-primary font-bold'
              : 'border-transparent text-muted hover:text-foreground'
          )}
        >
          {t('tabPending')}
        </button>
        <button
          type="button"
          data-testid="tab-approved"
          onClick={() => updateFilters({ status: 'APPROVED', page: 1 })}
          className={cn(
            'px-4 py-2.5 border-b-2 transition-colors cursor-pointer',
            statusParam === 'APPROVED'
              ? 'border-primary text-primary font-bold'
              : 'border-transparent text-muted hover:text-foreground'
          )}
        >
          {t('tabApproved')}
        </button>
        <button
          type="button"
          data-testid="tab-rejected"
          onClick={() => updateFilters({ status: 'REJECTED', page: 1 })}
          className={cn(
            'px-4 py-2.5 border-b-2 transition-colors cursor-pointer',
            statusParam === 'REJECTED'
              ? 'border-primary text-primary font-bold'
              : 'border-transparent text-muted hover:text-foreground'
          )}
        >
          {t('tabRejected')}
        </button>
      </div>

      {/* Reviews Queue List */}
      <div className="bg-surface border border-border rounded-2xl shadow-2xs overflow-hidden">
        {isLoading ? (
          <div className="p-8 flex items-center justify-center space-x-2">
            <Loader2 className="w-6 h-6 text-primary animate-spin" />
            <span className="text-xs text-muted">{commonT('loading')}</span>
          </div>
        ) : isError ? (
          <div className="p-8 text-center space-y-2">
            <AlertCircle className="w-8 h-8 text-red-500 mx-auto" />
            <p className="text-xs text-muted">{commonT('errorTitle')}</p>
            <button
              onClick={() => refetch()}
              className="px-3 py-1.5 bg-primary text-white text-xs rounded-lg"
            >
              {commonT('retry')}
            </button>
          </div>
        ) : reviews.length === 0 ? (
          <div className="p-12 text-center text-xs text-muted space-y-1">
            <CheckCircle className="w-8 h-8 mx-auto text-emerald-500 opacity-60" />
            <p className="font-semibold">All caught up!</p>
            <p>No reviews currently matching the &quot;{statusParam}&quot; status.</p>
          </div>
        ) : (
          <div className="divide-y divide-border/60">
            {reviews.map((rev) => (
              <div
                key={rev.id}
                data-testid="review-row"
                data-review-id={rev.id}
                className="p-4 sm:p-5 hover:bg-muted/5 transition-colors space-y-3"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <div className="flex items-center text-amber-500">
                        {[...Array(5)].map((_, i) => (
                          <Star
                            key={i}
                            className={cn(
                              'w-4 h-4',
                              i < rev.rating ? 'fill-amber-500' : 'text-muted/30 fill-transparent'
                            )}
                          />
                        ))}
                      </div>
                      <span className="font-bold text-xs text-foreground font-mono">
                        {rev.rating}/5
                      </span>
                      {rev.verifiedPurchase && (
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/10 text-emerald-700 dark:text-emerald-400">
                          <ShieldCheck className="w-3 h-3" />
                          <span>{t('verifiedPurchase')}</span>
                        </span>
                      )}
                    </div>

                    <div className="text-xs text-muted">
                      <span className="font-semibold text-foreground">{rev.user?.fullName || 'Customer'}</span>
                      <span className="mx-1.5">·</span>
                      <span>Product #{rev.productId}</span>
                      <span className="mx-1.5">·</span>
                      <span>{new Date(rev.createdAt).toLocaleDateString()}</span>
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-2 shrink-0">
                    {rev.status !== 'APPROVED' && (
                      <button
                        type="button"
                        onClick={() => handleApprove(rev)}
                        data-testid={`approve-review-btn-${rev.id}`}
                        className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-emerald-600 text-white text-xs font-bold hover:bg-emerald-700 transition-colors"
                      >
                        <CheckCircle className="w-3.5 h-3.5" />
                        <span>{t('approve')}</span>
                      </button>
                    )}
                    {rev.status !== 'REJECTED' && (
                      <button
                        type="button"
                        onClick={() => handleReject(rev)}
                        data-testid={`reject-review-btn-${rev.id}`}
                        className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-red-500/30 text-red-600 hover:bg-red-500/10 text-xs font-bold transition-colors"
                      >
                        <XCircle className="w-3.5 h-3.5" />
                        <span>{t('reject')}</span>
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => openReplyModal(rev)}
                      data-testid={`reply-review-btn-${rev.id}`}
                      className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-border bg-background hover:bg-muted/10 text-xs font-semibold text-foreground transition-colors"
                    >
                      <MessageSquare className="w-3.5 h-3.5 text-muted" />
                      <span>{t('reply')}</span>
                    </button>
                  </div>
                </div>

                {/* Review Title & Comment Body */}
                <div className="space-y-1.5">
                  {rev.title && (
                    <h4 className="text-sm font-bold text-foreground">{rev.title}</h4>
                  )}
                  <p className="text-xs sm:text-sm text-foreground/90 leading-relaxed bg-muted/5 p-3 rounded-xl border border-border/40">
                    {rev.body}
                  </p>
                </div>

                {/* Admin Official Reply */}
                {rev.adminReply && (
                  <div className="pl-4 border-l-2 border-primary space-y-1 text-xs">
                    <span className="font-bold text-primary">{t('reply')}:</span>
                    <p className="text-muted italic">{rev.adminReply}</p>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}

        {/* Pagination Bar */}
        {meta && meta.totalPages > 1 && (
          <div className="p-4 border-t border-border flex items-center justify-between text-xs sm:text-sm">
            <span className="text-muted">
              {commonT('pageOf', { page: meta.page, total: meta.totalPages })}
            </span>
            <div className="flex items-center gap-2">
              <button
                disabled={page <= 1}
                onClick={() => updateFilters({ page: page - 1 })}
                className="p-1.5 rounded-lg border border-border disabled:opacity-40"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                disabled={page >= totalPages}
                onClick={() => updateFilters({ page: page + 1 })}
                className="p-1.5 rounded-lg border border-border disabled:opacity-40"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* REPLY MODAL */}
      {replyReview && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
          <div
            data-testid="reply-review-modal"
            className="bg-surface border border-border rounded-2xl max-w-md w-full p-6 space-y-4 shadow-xl text-left"
          >
            <div className="flex items-center justify-between border-b border-border pb-3">
              <h3 className="text-base font-bold text-foreground flex items-center gap-2">
                <MessageSquare className="w-5 h-5 text-primary" />
                <span>{t('reply')}</span>
              </h3>
              <button
                type="button"
                onClick={() => setReplyReview(null)}
                className="text-muted hover:text-foreground"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleReplySubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  Customer Comment
                </label>
                <p className="text-xs text-muted p-2.5 bg-muted/10 rounded-lg italic">
                  &quot;{replyReview.body}&quot;
                </p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('reply')}
                </label>
                <textarea
                  data-testid="admin-reply-textarea"
                  rows={4}
                  value={replyText}
                  onChange={(e) => setReplyText(e.target.value)}
                  placeholder={t('replyPlaceholder')}
                  className="w-full p-3 text-xs sm:text-sm bg-background border border-border rounded-xl resize-none"
                  required
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-2 border-t border-border">
                <button
                  type="button"
                  onClick={() => setReplyReview(null)}
                  className="px-4 py-2 text-xs font-semibold text-muted hover:text-foreground"
                >
                  {commonT('cancel')}
                </button>
                <button
                  type="submit"
                  disabled={updateMutation.isPending}
                  data-testid="submit-reply-btn"
                  className="px-5 py-2 text-xs font-bold rounded-xl bg-primary text-primary-foreground hover:bg-primary/90 flex items-center gap-1.5"
                >
                  {updateMutation.isPending ? (
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  ) : (
                    <Send className="w-3.5 h-3.5" />
                  )}
                  <span>{t('submitReply')}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
