'use client';

import React, { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery } from '@tanstack/react-query';
import { useRouter, usePathname } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { AuditLogDTO } from '@/types/api';
import {
  History,
  Search,
  Filter,
  ChevronLeft,
  ChevronRight,
  Eye,
  RotateCcw,
  AlertTriangle,
  Loader2,
  X,
  FileCode,
  ArrowRight,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export default function AdminAuditLogsPage() {
  const t = useTranslations('admin.auditLogs');
  const commonT = useTranslations('admin.common');
  const locale = useLocale();
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const actorParam = searchParams.get('actor') || '';
  const entityParam = searchParams.get('entity') || '';
  const page = parseInt(searchParams.get('page') || '1', 10);
  const pageSize = 20;

  const [actorInput, setActorInput] = useState(actorParam);
  const [entityInput, setEntityInput] = useState(entityParam);
  const [selectedLog, setSelectedLog] = useState<AuditLogDTO | null>(null);

  const updateFilters = (updates: Record<string, string | number | null>) => {
    const current = new URLSearchParams(Array.from(searchParams.entries()));
    Object.entries(updates).forEach(([key, value]) => {
      if (value === null || value === '' || (key === 'page' && value === 1)) {
        current.delete(key);
      } else {
        current.set(key, String(value));
      }
    });
    const search = current.toString();
    const query = search ? `?${search}` : '';
    router.push(`${pathname}${query}`);
  };

  const handleFilterSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateFilters({ actor: actorInput, entity: entityInput, page: 1 });
  };

  const handleClearFilters = () => {
    setActorInput('');
    setEntityInput('');
    updateFilters({ actor: null, entity: null, page: 1 });
  };

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['admin-audit-logs', { actor: actorParam, entity: entityParam, page, pageSize }],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (actorParam) params.set('actor', actorParam);
      if (entityParam) params.set('entity', entityParam);
      params.set('page', String(page - 1)); // Spring is 0-indexed
      params.set('size', String(pageSize));
      params.set('sort', 'createdAt,desc');

      const res = await apiClient.get<AuditLogDTO[]>(`/admin/audit-logs?${params.toString()}`);
      return res;
    },
  });

  const logs = data?.data || [];
  const meta = data?.meta;
  const totalPages = meta?.totalPages || 1;
  const totalElements = meta?.totalElements || 0;

  const formatTimestamp = (iso: string) => {
    try {
      const date = new Date(iso);
      return new Intl.DateTimeFormat(locale === 'km' ? 'km-KH' : 'en-US', {
        timeZone: 'Asia/Phnom_Penh',
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      }).format(date);
    } catch {
      return iso;
    }
  };

  return (
    <div className="space-y-6" data-testid="admin-audit-logs-page">
      {/* Header */}
      <div>
        <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
          <History className="w-6 h-6 text-primary" />
          <span>{t('title')}</span>
        </h1>
        <p className="text-sm text-muted mt-1">{t('subtitle')}</p>
      </div>

      {/* Filter Bar */}
      <div className="bg-surface border border-border rounded-xl p-4">
        <form
          onSubmit={handleFilterSubmit}
          className="grid grid-cols-1 sm:grid-cols-3 md:grid-cols-4 gap-3 items-end"
        >
          <div>
            <label className="block text-xs font-medium text-muted mb-1">
              {t('actorFilter')}
            </label>
            <input
              type="text"
              value={actorInput}
              onChange={(e) => setActorInput(e.target.value)}
              placeholder="e.g. 1"
              className="w-full px-3 py-1.5 text-xs bg-background border border-border rounded-lg text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-muted mb-1">
              {t('entityFilter')}
            </label>
            <input
              type="text"
              value={entityInput}
              onChange={(e) => setEntityInput(e.target.value)}
              placeholder="e.g. USER, SETTINGS"
              className="w-full px-3 py-1.5 text-xs bg-background border border-border rounded-lg text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>

          <div className="flex items-center gap-2 sm:col-span-1 md:col-span-2">
            <button
              type="submit"
              className="px-4 py-1.5 bg-primary text-primary-foreground text-xs font-medium rounded-lg hover:bg-primary-hover transition-colors"
            >
              {commonT('search')}
            </button>
            {(actorParam || entityParam) && (
              <button
                type="button"
                onClick={handleClearFilters}
                className="px-3 py-1.5 bg-surface border border-border text-muted hover:text-foreground text-xs font-medium rounded-lg transition-colors"
              >
                Clear
              </button>
            )}
          </div>
        </form>
      </div>

      {/* Table */}
      <div className="bg-surface border border-border rounded-xl overflow-hidden shadow-sm">
        {isLoading ? (
          <div className="p-12 text-center">
            <Loader2 className="w-8 h-8 text-primary animate-spin mx-auto mb-3" />
            <p className="text-sm text-muted">{commonT('loading')}</p>
          </div>
        ) : isError ? (
          <div className="p-12 text-center">
            <AlertTriangle className="w-8 h-8 text-destructive mx-auto mb-3" />
            <p className="text-sm text-destructive mb-3">{commonT('error')}</p>
            <button
              onClick={() => refetch()}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-surface border border-border rounded-lg hover:bg-muted/10 transition-colors"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>{commonT('retry')}</span>
            </button>
          </div>
        ) : logs.length === 0 ? (
          <div className="p-12 text-center">
            <History className="w-10 h-10 text-muted mx-auto mb-3 opacity-50" />
            <p className="text-sm text-muted font-medium">{commonT('noData')}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-border bg-background/50 text-muted text-xs uppercase tracking-wider font-semibold">
                  <th className="py-3 px-4">ID</th>
                  <th className="py-3 px-4">{t('actor')}</th>
                  <th className="py-3 px-4">{t('action')}</th>
                  <th className="py-3 px-4">{t('entity')}</th>
                  <th className="py-3 px-4">{t('ip')}</th>
                  <th className="py-3 px-4">{t('timestamp')}</th>
                  <th className="py-3 px-4 text-right">{commonT('actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {logs.map((log: AuditLogDTO) => (
                  <tr key={log.id} className="hover:bg-muted/5 transition-colors" data-testid="audit-log-row">
                    <td className="py-3 px-4 font-mono text-xs text-muted">#{log.id}</td>
                    <td className="py-3 px-4 text-xs font-mono font-medium text-foreground">
                      {log.actorId ? `User #${log.actorId}` : 'System'}
                    </td>
                    <td className="py-3 px-4">
                      <span className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-mono font-medium bg-primary/10 text-primary border border-primary/20">
                        {log.action}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-xs font-medium text-foreground">
                      <span>{log.entity}</span>
                      {log.entityId && (
                        <span className="text-muted ml-1 text-[11px] font-mono">
                          (ID: {log.entityId})
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-4 text-xs font-mono text-muted">{log.ip || '—'}</td>
                    <td className="py-3 px-4 text-xs text-muted">
                      {formatTimestamp(log.createdAt)}
                    </td>
                    <td className="py-3 px-4 text-right">
                      <button
                        data-testid={`view-diff-btn-${log.id}`}
                        onClick={() => setSelectedLog(log)}
                        className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-lg border border-border hover:bg-muted/10 transition-colors text-foreground"
                      >
                        <Eye className="w-3.5 h-3.5 text-primary" />
                        <span>{t('viewDiff')}</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Bar */}
        {!isLoading && totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-border bg-background/50">
            <span className="text-xs text-muted">
              {commonT('total')}: {totalElements}
            </span>
            <div className="flex items-center gap-2">
              <button
                disabled={page <= 1}
                onClick={() => updateFilters({ page: page - 1 })}
                className="p-1 rounded border border-border bg-surface text-foreground hover:bg-muted/10 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                aria-label={commonT('previous')}
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span className="text-xs font-medium text-foreground px-2">
                {page} / {totalPages}
              </span>
              <button
                disabled={page >= totalPages}
                onClick={() => updateFilters({ page: page + 1 })}
                className="p-1 rounded border border-border bg-surface text-foreground hover:bg-muted/10 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                aria-label={commonT('next')}
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* JSON Diff Viewer Modal */}
      {selectedLog && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div data-testid="diff-modal" className="bg-surface border border-border rounded-xl max-w-4xl w-full max-h-[85vh] flex flex-col shadow-2xl animate-in fade-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="flex items-center justify-between p-4 border-b border-border">
              <div className="flex items-center gap-2">
                <FileCode className="w-5 h-5 text-primary" />
                <h2 className="text-base font-bold text-foreground">
                  {t('diffTitle')} #{selectedLog.id}
                </h2>
                <span className="text-xs px-2 py-0.5 rounded font-mono bg-primary/10 text-primary">
                  {selectedLog.action}
                </span>
              </div>
              <button
                onClick={() => setSelectedLog(null)}
                className="p-1 text-muted hover:text-foreground rounded-lg hover:bg-muted/10"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-4 overflow-y-auto flex-1 space-y-4">
              <div className="grid grid-cols-2 gap-4 text-xs text-muted bg-background p-3 rounded-lg border border-border">
                <div>
                  <span className="font-semibold text-foreground">{t('entity')}:</span>{' '}
                  {selectedLog.entity} (ID: {selectedLog.entityId || 'N/A'})
                </div>
                <div>
                  <span className="font-semibold text-foreground">{t('actor')}:</span>{' '}
                  {selectedLog.actorId ? `User #${selectedLog.actorId}` : 'System'}
                </div>
                <div>
                  <span className="font-semibold text-foreground">{t('ip')}:</span>{' '}
                  {selectedLog.ip || 'N/A'}
                </div>
                <div>
                  <span className="font-semibold text-foreground">{t('timestamp')}:</span>{' '}
                  {formatTimestamp(selectedLog.createdAt)}
                </div>
              </div>

              {/* Side-by-side JSON comparison */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Before State */}
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-muted uppercase tracking-wider">
                      {t('beforeState')}
                    </span>
                    {selectedLog.beforeJson ? (
                      <span className="text-[10px] text-destructive px-1.5 py-0.5 bg-destructive/10 rounded font-medium">
                        Prior
                      </span>
                    ) : null}
                  </div>
                  <pre className="p-3 bg-background border border-border rounded-lg text-xs font-mono overflow-x-auto max-h-80 text-foreground leading-relaxed">
                    {selectedLog.beforeJson
                      ? JSON.stringify(selectedLog.beforeJson, null, 2)
                      : t('noChanges')}
                  </pre>
                </div>

                {/* After State */}
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-muted uppercase tracking-wider">
                      {t('afterState')}
                    </span>
                    {selectedLog.afterJson ? (
                      <span className="text-[10px] text-success px-1.5 py-0.5 bg-success/10 rounded font-medium">
                        Updated
                      </span>
                    ) : null}
                  </div>
                  <pre className="p-3 bg-background border border-border rounded-lg text-xs font-mono overflow-x-auto max-h-80 text-foreground leading-relaxed">
                    {selectedLog.afterJson
                      ? JSON.stringify(selectedLog.afterJson, null, 2)
                      : t('noChanges')}
                  </pre>
                </div>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="flex justify-end p-4 border-t border-border bg-background/50">
              <button
                onClick={() => setSelectedLog(null)}
                className="px-4 py-1.5 text-xs font-medium border border-border rounded-lg hover:bg-muted/10 transition-colors"
              >
                {commonT('close')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
