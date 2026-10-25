'use client';

import React, { useState } from 'react';
import Image from 'next/image';
import { useParams } from 'next/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation } from '@tanstack/react-query';
import { Link, useRouter } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { OrderDTO } from '@/types/api';
import { formatUsd, formatKhr } from '@/lib/money';
import {
  Building2,
  UploadCloud,
  FileText,
  CheckCircle2,
  Clock,
  ArrowRight,
  AlertCircle,
  Loader2,
  X,
  FileCheck,
} from 'lucide-react';
import { toast } from 'sonner';

export default function BankTransferPage() {
  const t = useTranslations('bankTransfer');
  const common = useTranslations('common');
  const locale = useLocale();
  const router = useRouter();
  const params = useParams();
  const orderNumber = params?.orderNumber as string;

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isSubmitted, setIsSubmitted] = useState<boolean>(false);

  // Fetch order to get accurate total
  const { data: order, isLoading } = useQuery({
    queryKey: ['order', orderNumber],
    queryFn: async () => {
      const res = await apiClient<OrderDTO>(`/orders/${orderNumber}`);
      return res.data;
    },
    enabled: Boolean(orderNumber),
  });

  const uploadSlipMutation = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append('file', file);

      const res = await apiClient<OrderDTO>(`/payments/bank-transfer/${orderNumber}/slip`, {
        method: 'POST',
        body: formData,
      });
      return res.data;
    },
    onSuccess: () => {
      setIsSubmitted(true);
      toast.success(t('slipUploadedSuccess'));
    },
    onError: (err: any) => {
      toast.error(err.message || 'Failed to upload payment slip');
    },
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/') && file.type !== 'application/pdf') {
      toast.error('Only image or PDF files are allowed');
      return;
    }

    setSelectedFile(file);
    if (file.type.startsWith('image/')) {
      const url = URL.createObjectURL(file);
      setPreviewUrl(url);
    } else {
      setPreviewUrl(null);
    }
  };

  const handleRemoveFile = () => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
    }
    setSelectedFile(null);
    setPreviewUrl(null);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile) {
      toast.error('Please select a payment slip to upload');
      return;
    }
    uploadSlipMutation.mutate(selectedFile);
  };

  if (isLoading) {
    return (
      <div className="container mx-auto px-4 py-16 text-center max-w-lg space-y-4">
        <Loader2 className="w-8 h-8 text-primary animate-spin mx-auto" />
        <p className="text-xs text-muted">Loading order details...</p>
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8 sm:py-12 max-w-xl space-y-6" data-testid="bank-transfer-page">
      <div className="text-center space-y-2">
        <div className="w-12 h-12 mx-auto rounded-full bg-primary/10 text-primary flex items-center justify-center">
          <Building2 className="w-6 h-6" />
        </div>
        <h1 className="text-2xl font-black text-foreground tracking-tight">{t('title')}</h1>
        <p className="text-xs text-muted max-w-md mx-auto">{t('instructions')}</p>
      </div>

      {/* Order Amount Card */}
      <div className="bg-surface border border-border rounded-2xl p-5 shadow-xs space-y-3">
        <div className="flex items-center justify-between pb-3 border-b border-border">
          <span className="text-xs font-bold text-muted uppercase tracking-wider">
            Order: <span className="text-foreground font-mono" data-testid="order-number-display">{orderNumber}</span>
          </span>
          <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-amber-500/10 text-amber-600 dark:text-amber-400">
            Pending Payment
          </span>
        </div>

        <div className="flex items-baseline justify-between pt-1">
          <span className="text-xs font-semibold text-muted">{t('amountToTransfer')}</span>
          <div className="text-right">
            <span className="text-2xl font-black text-primary" data-testid="bank-transfer-amount-usd">
              {formatUsd(order?.totalCents || 0)}
            </span>
            <span className="block text-[11px] text-muted font-medium" data-testid="bank-transfer-amount-khr">
              {formatKhr(order?.totalCents || 0)}
            </span>
          </div>
        </div>
      </div>

      {/* Bank Account Details */}
      <div className="bg-surface border border-border rounded-2xl p-5 shadow-xs space-y-4">
        <h2 className="text-xs font-bold uppercase tracking-wider text-muted">
          {t('bankDetailsTitle')}
        </h2>

        <div className="space-y-3">
          {/* ABA Account */}
          <div className="p-3.5 rounded-xl border border-border bg-background space-y-1 text-xs">
            <div className="flex items-center justify-between font-bold text-foreground">
              <span>{t('abaBank')}</span>
              <span className="text-[10px] uppercase font-mono px-2 py-0.5 rounded bg-primary/10 text-primary">
                ABA
              </span>
            </div>
            <div className="text-muted flex justify-between">
              <span>{t('accountName')}:</span>
              <strong className="text-foreground font-semibold">KhmerMart Co., Ltd.</strong>
            </div>
            <div className="text-muted flex justify-between">
              <span>USD:</span>
              <strong className="font-mono text-foreground font-bold">001 234 567</strong>
            </div>
            <div className="text-muted flex justify-between">
              <span>KHR:</span>
              <strong className="font-mono text-foreground font-bold">001 234 568</strong>
            </div>
          </div>

          {/* Canadia Account */}
          <div className="p-3.5 rounded-xl border border-border bg-background space-y-1 text-xs">
            <div className="flex items-center justify-between font-bold text-foreground">
              <span>{t('canadiaBank')}</span>
              <span className="text-[10px] uppercase font-mono px-2 py-0.5 rounded bg-blue-500/10 text-blue-600">
                Canadia
              </span>
            </div>
            <div className="text-muted flex justify-between">
              <span>{t('accountName')}:</span>
              <strong className="text-foreground font-semibold">KhmerMart Co., Ltd.</strong>
            </div>
            <div className="text-muted flex justify-between">
              <span>USD:</span>
              <strong className="font-mono text-foreground font-bold">100 888 999</strong>
            </div>
            <div className="text-muted flex justify-between">
              <span>KHR:</span>
              <strong className="font-mono text-foreground font-bold">100 888 888</strong>
            </div>
          </div>
        </div>
      </div>

      {/* Slip Upload & Status */}
      {isSubmitted ? (
        <div
          data-testid="slip-submitted-state"
          className="bg-green-500/10 border border-green-500/20 rounded-2xl p-6 text-center space-y-4 animate-in zoom-in-95"
        >
          <div className="w-12 h-12 mx-auto rounded-full bg-green-500/20 text-green-600 dark:text-green-400 flex items-center justify-center">
            <Clock className="w-6 h-6 animate-pulse" />
          </div>
          <div className="space-y-1">
            <h3 className="text-base font-bold text-foreground">{t('waitingApproval')}</h3>
            <p className="text-xs text-muted max-w-sm mx-auto leading-relaxed">
              {t('waitingApprovalDesc')}
            </p>
          </div>
          <div className="pt-2">
            <Link
              href={`/orders/${orderNumber}`}
              data-testid="view-order-btn"
              className="inline-flex items-center gap-1.5 px-6 py-2.5 rounded-btn bg-primary text-primary-foreground font-bold text-xs shadow-xs hover:bg-primary/90 transition-colors"
            >
              <span>{t('viewOrder')}</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="bg-surface border border-border rounded-2xl p-5 shadow-xs space-y-4">
          <div>
            <h2 className="text-xs font-bold uppercase tracking-wider text-muted">
              {t('uploadSlipTitle')}
            </h2>
            <p className="text-xs text-muted mt-0.5">{t('uploadSlipDesc')}</p>
          </div>

          {/* Upload Dropzone */}
          {!selectedFile ? (
            <label className="border-2 border-dashed border-border hover:border-primary/50 bg-background rounded-xl p-6 flex flex-col items-center justify-center gap-2 cursor-pointer transition-colors">
              <UploadCloud className="w-8 h-8 text-muted" />
              <span className="text-xs font-semibold text-foreground">{t('dragDrop')}</span>
              <span className="text-[11px] text-muted">JPG, PNG, PDF up to 10MB</span>
              <input
                type="file"
                data-testid="slip-file-input"
                accept="image/*,application/pdf"
                onChange={handleFileChange}
                className="hidden"
              />
            </label>
          ) : (
            <div className="space-y-3" data-testid="slip-preview">
              <div className="relative border border-border rounded-xl p-3 bg-background flex items-center justify-between">
                <div className="flex items-center gap-3 truncate">
                  {previewUrl ? (
                    <div className="relative w-12 h-12 rounded overflow-hidden bg-muted/10 shrink-0 border border-border">
                      <Image src={previewUrl} alt="Slip Preview" fill className="object-cover" />
                    </div>
                  ) : (
                    <div className="w-12 h-12 rounded bg-primary/10 text-primary flex items-center justify-center shrink-0">
                      <FileText className="w-6 h-6" />
                    </div>
                  )}
                  <div className="truncate text-xs">
                    <p className="font-bold text-foreground truncate">{selectedFile.name}</p>
                    <p className="text-muted text-[11px]">
                      {(selectedFile.size / 1024).toFixed(1)} KB
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={handleRemoveFile}
                  className="p-1 rounded-full text-muted hover:text-foreground hover:bg-muted/10 transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>
          )}

          <button
            type="submit"
            data-testid="upload-slip-btn"
            disabled={!selectedFile || uploadSlipMutation.isPending}
            className="w-full h-11 flex items-center justify-center gap-2 rounded-btn bg-primary text-primary-foreground font-bold text-xs shadow-xs hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {uploadSlipMutation.isPending ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                <span>{t('uploadingSlip')}</span>
              </>
            ) : (
              <>
                <FileCheck className="w-4 h-4" />
                <span>{t('uploadSlipBtn')}</span>
              </>
            )}
          </button>
        </form>
      )}
    </div>
  );
}
