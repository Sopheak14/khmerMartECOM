'use client';

import React, { useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { useTranslations, useLocale } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter, usePathname } from '@/navigation';
import { apiClient } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { UserDTO, Role } from '@/types/api';
import { useAuthStore } from '@/lib/store/useAuthStore';
import {
  Users,
  Search,
  Filter,
  Shield,
  ShieldAlert,
  ChevronLeft,
  ChevronRight,
  RotateCcw,
  AlertTriangle,
  Loader2,
  CheckCircle2,
  XCircle,
  UserCheck,
  UserX,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export default function AdminUsersPage() {
  const t = useTranslations('admin.users');
  const commonT = useTranslations('admin.common');
  const locale = useLocale();
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const currentUser = useAuthStore((s) => s.user);

  const q = searchParams.get('q') || '';
  const roleFilter = searchParams.get('role') || '';
  const page = parseInt(searchParams.get('page') || '1', 10);
  const pageSize = 15;

  const [searchInput, setSearchInput] = useState(q);
  const [deactivateTarget, setDeactivateTarget] = useState<UserDTO | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

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

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    updateFilters({ q: searchInput, page: 1 });
  };

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['admin-users', { q, roleFilter, page, pageSize }],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (q) params.set('q', q);
      if (roleFilter) params.set('role', roleFilter);
      params.set('page', String(page - 1)); // Spring is 0-indexed
      params.set('size', String(pageSize));
      params.set('sort', 'createdAt,desc');

      const res = await apiClient.get<UserDTO[]>(`/admin/users?${params.toString()}`);
      return res;
    },
  });

  const updateRoleMutation = useMutation({
    mutationFn: async ({ userId, newRole }: { userId: number; newRole: Role }) => {
      return apiClient.patch<UserDTO>(`/admin/users/${userId}`, { role: newRole });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      setActionError(null);
    },
    onError: (err: any) => {
      setActionError(err.message || 'Failed to update user role');
    },
  });

  const toggleStatusMutation = useMutation({
    mutationFn: async ({ userId, isActive }: { userId: number; isActive: boolean }) => {
      return apiClient.patch<UserDTO>(`/admin/users/${userId}`, { isActive });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      setDeactivateTarget(null);
      setActionError(null);
    },
    onError: (err: any) => {
      setActionError(err.message || 'Failed to update user status');
    },
  });

  const users = data?.data || [];
  const meta = data?.meta;
  const totalPages = meta?.totalPages || 1;
  const totalElements = meta?.totalElements || 0;

  return (
    <div className="space-y-6" data-testid="admin-users-page">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <Users className="w-6 h-6 text-primary" />
            <span>{t('title')}</span>
          </h1>
          <p className="text-sm text-muted mt-1">{t('subtitle')}</p>
        </div>
      </div>

      {actionError && (
        <div className="bg-destructive/10 border border-destructive/20 text-destructive text-sm p-4 rounded-xl flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-5 h-5 flex-shrink-0" />
            <span>{actionError}</span>
          </div>
          <button
            onClick={() => setActionError(null)}
            className="text-xs underline hover:opacity-80"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Filters Toolbar */}
      <div className="bg-surface border border-border rounded-xl p-4 space-y-3">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          {/* Search */}
          <form onSubmit={handleSearchSubmit} className="sm:col-span-2 relative">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
            <input
              type="text"
              data-testid="user-search-input"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder={t('searchPlaceholder')}
              className="w-full pl-9 pr-20 py-2 text-sm bg-background border border-border rounded-lg text-foreground placeholder:text-muted focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
            />
            <button
              type="submit"
              className="absolute right-1.5 top-1/2 -translate-y-1/2 px-3 py-1 bg-primary text-primary-foreground text-xs font-medium rounded-md hover:bg-primary-hover transition-colors"
            >
              {commonT('search')}
            </button>
          </form>

          {/* Role Filter */}
          <div className="relative">
            <select
              data-testid="user-role-filter"
              value={roleFilter}
              onChange={(e) => updateFilters({ role: e.target.value, page: 1 })}
              className="w-full px-3 py-2 text-sm bg-background border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary"
            >
              <option value="">{t('allRoles')}</option>
              <option value="ADMIN">{t('roleAdmin')}</option>
              <option value="STAFF">{t('roleStaff')}</option>
              <option value="CUSTOMER">{t('roleCustomer')}</option>
            </select>
          </div>
        </div>
      </div>

      {/* Users Table */}
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
        ) : users.length === 0 ? (
          <div className="p-12 text-center">
            <Users className="w-10 h-10 text-muted mx-auto mb-3 opacity-50" />
            <p className="text-sm text-muted font-medium">{commonT('noData')}</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-border bg-background/50 text-muted text-xs uppercase tracking-wider font-semibold">
                  <th className="py-3.5 px-4">{t('name')}</th>
                  <th className="py-3.5 px-4">{t('email')}</th>
                  <th className="py-3.5 px-4">{t('phone')}</th>
                  <th className="py-3.5 px-4">{t('role')}</th>
                  <th className="py-3.5 px-4">{t('status')}</th>
                  <th className="py-3.5 px-4 text-right">{commonT('actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {users.map((u: UserDTO) => {
                  const isSelf = currentUser?.id === u.id;
                  return (
                    <tr key={u.id} className="hover:bg-muted/5 transition-colors" data-testid="user-row" data-testid-user={`user-row-${u.id}`}>
                      <td className="py-3.5 px-4 font-medium text-foreground">
                        <div className="flex items-center gap-2">
                          <span>{u.fullName}</span>
                          {isSelf && (
                            <span className="text-[10px] px-1.5 py-0.5 bg-primary/10 text-primary rounded font-normal">
                              You
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-3.5 px-4 text-muted">{u.email}</td>
                      <td className="py-3.5 px-4 text-muted">{u.phone || '—'}</td>
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-2">
                          <select
                            data-testid={`role-select-${u.id}`}
                            disabled={isSelf || updateRoleMutation.isPending}
                            value={u.role}
                            onChange={(e) =>
                              updateRoleMutation.mutate({
                                userId: u.id,
                                newRole: e.target.value as Role,
                              })
                            }
                            className={cn(
                              'text-xs px-2.5 py-1 rounded-md border font-medium focus:outline-none focus:ring-1 focus:ring-primary',
                              u.role === 'ADMIN'
                                ? 'bg-primary/10 text-primary border-primary/20'
                                : u.role === 'STAFF'
                                ? 'bg-purple-500/10 text-purple-600 border-purple-500/20'
                                : 'bg-muted/20 text-foreground border-border',
                              isSelf && 'opacity-60 cursor-not-allowed'
                            )}
                            title={isSelf ? t('selfActionWarning') : undefined}
                          >
                            <option value="ADMIN">{t('roleAdmin')}</option>
                            <option value="STAFF">{t('roleStaff')}</option>
                            <option value="CUSTOMER">{t('roleCustomer')}</option>
                          </select>
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <span
                          className={cn(
                            'inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium',
                            u.isActive
                              ? 'bg-success/10 text-success'
                              : 'bg-destructive/10 text-destructive'
                          )}
                        >
                          {u.isActive ? (
                            <CheckCircle2 className="w-3 h-3" />
                          ) : (
                            <XCircle className="w-3 h-3" />
                          )}
                          <span>{u.isActive ? t('statusActive') : t('statusInactive')}</span>
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        {u.isActive ? (
                          <button
                            data-testid={`toggle-status-btn-${u.id}`}
                            disabled={isSelf || toggleStatusMutation.isPending}
                            onClick={() => setDeactivateTarget(u)}
                            title={isSelf ? t('selfActionWarning') : undefined}
                            className={cn(
                              'inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-lg transition-colors border',
                              isSelf
                                ? 'border-border text-muted opacity-50 cursor-not-allowed'
                                : 'border-destructive/30 text-destructive hover:bg-destructive/10'
                            )}
                          >
                            <UserX className="w-3.5 h-3.5" />
                            <span>{t('deactivate')}</span>
                          </button>
                        ) : (
                          <button
                            data-testid={`toggle-status-btn-${u.id}`}
                            disabled={toggleStatusMutation.isPending}
                            onClick={() =>
                              toggleStatusMutation.mutate({ userId: u.id, isActive: true })
                            }
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium rounded-lg transition-colors border border-success/30 text-success hover:bg-success/10"
                          >
                            <UserCheck className="w-3.5 h-3.5" />
                            <span>{t('activate')}</span>
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
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

      {/* Deactivate Confirm Modal */}
      {deactivateTarget && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div data-testid="deactivate-confirm-modal" className="bg-surface border border-border rounded-xl max-w-md w-full p-6 space-y-4 shadow-xl animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center gap-3 text-destructive">
              <div className="p-2.5 rounded-full bg-destructive/10">
                <AlertTriangle className="w-6 h-6" />
              </div>
              <h2 className="text-lg font-bold text-foreground">{t('deactivate')}</h2>
            </div>
            <p className="text-sm text-muted">
              {t('deactivateConfirm', { name: deactivateTarget.fullName })}
            </p>
            <div className="flex justify-end gap-3 pt-2">
              <button
                onClick={() => setDeactivateTarget(null)}
                className="px-4 py-2 text-xs font-medium border border-border rounded-lg hover:bg-muted/10 transition-colors"
              >
                {commonT('cancel')}
              </button>
              <button
                data-testid="confirm-deactivate-btn"
                disabled={toggleStatusMutation.isPending}
                onClick={() =>
                  toggleStatusMutation.mutate({ userId: deactivateTarget.id, isActive: false })
                }
                className="px-4 py-2 text-xs font-medium bg-destructive text-destructive-foreground rounded-lg hover:bg-destructive/90 transition-colors inline-flex items-center gap-1.5"
              >
                {toggleStatusMutation.isPending && (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                )}
                <span>{t('deactivate')}</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
