'use client';

import React, { useState } from 'react';
import { useTranslations } from 'next-intl';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, ApiError } from '@/lib/apiClient';
import { queryKeys } from '@/lib/queryKeys';
import { CategoryDTO, CategoryRequest } from '@/types/api';
import {
  FolderTree,
  Plus,
  Edit2,
  Trash2,
  ChevronRight,
  Folder,
  Loader2,
  X,
  AlertCircle,
  RotateCcw,
} from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

export default function AdminCategoriesPage() {
  const t = useTranslations('admin.categories');
  const commonT = useTranslations('admin.common');
  const queryClient = useQueryClient();

  const [formOpen, setFormOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<CategoryDTO | null>(null);
  const [deleteConfirmCategory, setDeleteConfirmCategory] = useState<CategoryDTO | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // Form Fields
  const [name, setName] = useState('');
  const [nameKm, setNameKm] = useState('');
  const [slug, setSlug] = useState('');
  const [parentId, setParentId] = useState<string>('');
  const [icon, setIcon] = useState('');
  const [sortOrder, setSortOrder] = useState('0');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const { data: categories, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.admin.categories,
    queryFn: async () => {
      const res = await apiClient<CategoryDTO[]>('/admin/categories');
      return res.data;
    },
  });

  const createMutation = useMutation({
    mutationFn: async (payload: CategoryRequest) => {
      const res = await apiClient<CategoryDTO>('/admin/categories', {
        method: 'POST',
        body: payload,
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.categories });
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all });
      toast.success('Category created successfully');
      setFormOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => {
      if (err.details && typeof err.details === 'object') {
        setFieldErrors(err.details);
      } else {
        toast.error(err.message || 'Failed to create category');
      }
    },
  });

  const updateMutation = useMutation({
    mutationFn: async ({ id, payload }: { id: number; payload: CategoryRequest }) => {
      const res = await apiClient<CategoryDTO>(`/admin/categories/${id}`, {
        method: 'PUT',
        body: payload,
      });
      return res.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.categories });
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all });
      toast.success('Category updated successfully');
      setFormOpen(false);
      resetForm();
    },
    onError: (err: ApiError) => {
      if (err.details && typeof err.details === 'object') {
        setFieldErrors(err.details);
      } else {
        toast.error(err.message || 'Failed to update category');
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: number) => {
      setDeleteError(null);
      await apiClient(`/admin/categories/${id}`, { method: 'DELETE' });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.admin.categories });
      queryClient.invalidateQueries({ queryKey: queryKeys.categories.all });
      toast.success('Category deleted successfully');
      setDeleteConfirmCategory(null);
    },
    onError: (err: any) => {
      setDeleteError(err.message || t('hasProductsError'));
    },
  });

  const resetForm = () => {
    setEditingCategory(null);
    setName('');
    setNameKm('');
    setSlug('');
    setParentId('');
    setIcon('');
    setSortOrder('0');
    setFieldErrors({});
  };

  const openCreateModal = (parent?: CategoryDTO) => {
    resetForm();
    if (parent) {
      setParentId(String(parent.id));
    }
    setFormOpen(true);
  };

  const openEditModal = (cat: CategoryDTO) => {
    resetForm();
    setEditingCategory(cat);
    setName(cat.name || '');
    setNameKm(cat.nameKm || '');
    setSlug(cat.slug || '');
    setParentId(cat.parentId ? String(cat.parentId) : '');
    setIcon(cat.imageUrl || '');
    setSortOrder(String(cat.sortOrder ?? 0));
    setFormOpen(true);
  };

  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFieldErrors({});

    const payload: CategoryRequest = {
      name: name.trim(),
      nameKm: nameKm.trim() || undefined,
      slug: slug.trim() || undefined,
      parentId: parentId ? parseInt(parentId, 10) : null,
      imageUrl: icon.trim() || undefined,
      sortOrder: parseInt(sortOrder, 10) || 0,
    };

    if (editingCategory) {
      updateMutation.mutate({ id: editingCategory.id, payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  // Build tree
  const items = categories || [];
  const rootCategories = items.filter((c) => !c.parentId);
  const getChildren = (pid: number) => items.filter((c) => c.parentId === pid);

  return (
    <div className="space-y-6" data-testid="admin-categories-page">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-border/50">
        <div>
          <h1 className="text-xl sm:text-2xl font-black text-foreground tracking-tight flex items-center gap-2">
            <FolderTree className="w-6 h-6 text-primary" />
            <span>{t('title')}</span>
          </h1>
          <p className="text-xs text-muted mt-0.5">{t('subtitle')}</p>
        </div>

        <button
          type="button"
          data-testid="add-category-btn"
          onClick={() => openCreateModal()}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-primary text-primary-foreground text-xs font-bold hover:bg-primary/90 transition-colors shadow-xs self-start sm:self-auto"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>{t('addCategory')}</span>
        </button>
      </div>

      {/* Category Tree Container */}
      <div className="bg-surface border border-border rounded-2xl shadow-2xs overflow-hidden p-4 sm:p-6">
        {isLoading ? (
          <div className="p-8 flex items-center justify-center space-x-2">
            <Loader2 className="w-6 h-6 text-primary animate-spin" />
            <span className="text-xs text-muted">{commonT('loading')}</span>
          </div>
        ) : isError ? (
          <div className="p-8 text-center space-y-3">
            <AlertCircle className="w-8 h-8 text-red-500 mx-auto" />
            <p className="text-xs text-muted">{commonT('errorTitle')}</p>
            <button
              onClick={() => refetch()}
              className="px-3 py-1.5 bg-primary text-white text-xs rounded-lg"
            >
              {commonT('retry')}
            </button>
          </div>
        ) : rootCategories.length === 0 ? (
          <div className="p-8 text-center text-xs text-muted">{commonT('empty')}</div>
        ) : (
          <div className="space-y-2">
            {rootCategories.map((root) => {
              const children = getChildren(root.id);

              return (
                <div
                  key={root.id}
                  data-testid="category-tree-node"
                  data-category-id={root.id}
                  className="border border-border/70 rounded-xl overflow-hidden bg-background"
                >
                  {/* Root Node Row */}
                  <div className="p-3.5 flex items-center justify-between hover:bg-muted/5 transition-colors">
                    <div className="flex items-center gap-2.5">
                      <Folder className="w-4 h-4 text-primary shrink-0" />
                      <div>
                        <span className="font-bold text-xs sm:text-sm text-foreground">
                          {root.name}
                        </span>
                        {root.nameKm && (
                          <span className="text-xs text-muted ml-2">({root.nameKm})</span>
                        )}
                        <span className="ml-2 font-mono text-[11px] text-muted">/{root.slug}</span>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5">
                      <button
                        type="button"
                        onClick={() => openCreateModal(root)}
                        data-testid={`add-subcat-btn-${root.id}`}
                        className="px-2 py-1 rounded border border-border text-[11px] font-semibold hover:bg-muted/10 text-muted hover:text-foreground"
                      >
                        + Subcategory
                      </button>
                      <button
                        type="button"
                        onClick={() => openEditModal(root)}
                        data-testid={`edit-cat-btn-${root.id}`}
                        className="p-1.5 text-muted hover:text-foreground rounded hover:bg-muted/10"
                        title={t('editCategory')}
                      >
                        <Edit2 className="w-3.5 h-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setDeleteError(null);
                          setDeleteConfirmCategory(root);
                        }}
                        data-testid={`delete-cat-btn-${root.id}`}
                        className="p-1.5 text-muted hover:text-red-500 rounded hover:bg-red-500/10"
                        title={commonT('cancel')}
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>

                  {/* Children (Indented) */}
                  {children.length > 0 && (
                    <div className="border-t border-border/50 divide-y divide-border/40 pl-6 bg-muted/5">
                      {children.map((child) => (
                        <div
                          key={child.id}
                          data-testid="category-child-node"
                          data-category-id={child.id}
                          className="p-3 flex items-center justify-between hover:bg-muted/10 transition-colors"
                        >
                          <div className="flex items-center gap-2">
                            <ChevronRight className="w-3.5 h-3.5 text-muted" />
                            <span className="font-medium text-xs text-foreground">{child.name}</span>
                            {child.nameKm && (
                              <span className="text-[11px] text-muted">({child.nameKm})</span>
                            )}
                            <span className="font-mono text-[10px] text-muted">/{child.slug}</span>
                          </div>

                          <div className="flex items-center gap-1">
                            <button
                              type="button"
                              onClick={() => openEditModal(child)}
                              data-testid={`edit-cat-btn-${child.id}`}
                              className="p-1.5 text-muted hover:text-foreground rounded hover:bg-muted/10"
                            >
                              <Edit2 className="w-3 h-3" />
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setDeleteError(null);
                                setDeleteConfirmCategory(child);
                              }}
                              data-testid={`delete-cat-btn-${child.id}`}
                              className="p-1.5 text-muted hover:text-red-500 rounded hover:bg-red-500/10"
                            >
                              <Trash2 className="w-3 h-3" />
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* CREATE / EDIT MODAL */}
      {formOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in">
          <div
            data-testid="category-form-modal"
            className="bg-surface border border-border rounded-2xl max-w-md w-full p-6 space-y-4 shadow-xl text-left"
          >
            <div className="flex items-center justify-between border-b border-border pb-3">
              <h3 className="text-base font-bold text-foreground">
                {editingCategory ? t('editCategory') : t('addCategory')}
              </h3>
              <button
                type="button"
                onClick={() => setFormOpen(false)}
                className="text-muted hover:text-foreground"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleFormSubmit} className="space-y-4 text-xs sm:text-sm">
              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('nameEn')} <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  data-testid="category-name-input"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Laptops"
                  className="w-full px-3 py-2 bg-background border border-border rounded-lg text-xs sm:text-sm"
                  required
                />
                {fieldErrors.name && (
                  <p className="text-xs text-red-600 mt-1">{fieldErrors.name}</p>
                )}
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('nameKm')}
                </label>
                <input
                  type="text"
                  data-testid="category-name-km-input"
                  value={nameKm}
                  onChange={(e) => setNameKm(e.target.value)}
                  placeholder="e.g. កុំព្យូទ័រយួរដៃ"
                  className="w-full px-3 py-2 bg-background border border-border rounded-lg text-xs sm:text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('slug')}
                </label>
                <input
                  type="text"
                  data-testid="category-slug-input"
                  value={slug}
                  onChange={(e) => setSlug(e.target.value)}
                  placeholder="e.g. laptops"
                  className="w-full px-3 py-2 bg-background border border-border rounded-lg text-xs sm:text-sm font-mono"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('parentCategory')}
                </label>
                <select
                  data-testid="category-parent-select"
                  value={parentId}
                  onChange={(e) => setParentId(e.target.value)}
                  className="w-full px-3 py-2 bg-background border border-border rounded-lg text-xs sm:text-sm"
                >
                  <option value="">{t('noParent')}</option>
                  {items
                    .filter((c) => !editingCategory || c.id !== editingCategory.id)
                    .map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('icon')}
                </label>
                <input
                  type="text"
                  data-testid="category-icon-input"
                  value={icon}
                  onChange={(e) => setIcon(e.target.value)}
                  placeholder="e.g. Laptop or https://.../icon.png"
                  className="w-full px-3 py-2 bg-background border border-border rounded-lg text-xs sm:text-sm"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-foreground mb-1">
                  {t('sortOrder')}
                </label>
                <input
                  type="number"
                  data-testid="category-sort-input"
                  value={sortOrder}
                  onChange={(e) => setSortOrder(e.target.value)}
                  className="w-full px-3 py-2 bg-background border border-border rounded-lg text-xs sm:text-sm font-mono"
                />
              </div>

              <div className="flex items-center justify-end gap-2 pt-3 border-t border-border">
                <button
                  type="button"
                  onClick={() => setFormOpen(false)}
                  className="px-4 py-2 text-xs font-semibold text-muted hover:text-foreground"
                >
                  {commonT('cancel')}
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  data-testid="category-submit-btn"
                  className="px-5 py-2 text-xs font-bold rounded-xl bg-primary text-primary-foreground hover:bg-primary/90"
                >
                  {t('saveCategory')}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* DELETE CONFIRM MODAL */}
      {deleteConfirmCategory && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
          <div
            data-testid="delete-category-modal"
            className="bg-surface border border-border rounded-2xl max-w-sm w-full p-6 space-y-4 shadow-xl text-center"
          >
            <Trash2 className="w-10 h-10 text-destructive mx-auto" />
            <h3 className="text-base font-bold text-foreground">
              Delete &quot;{deleteConfirmCategory.name}&quot;
            </h3>
            <p className="text-xs text-muted">{t('deleteConfirm')}</p>

            {deleteError && (
              <div
                data-testid="category-delete-error"
                className="p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-red-600 text-xs text-left font-medium"
              >
                {deleteError}
              </div>
            )}

            <div className="flex items-center justify-center gap-3 pt-2">
              <button
                type="button"
                onClick={() => setDeleteConfirmCategory(null)}
                className="px-4 py-2 text-xs font-semibold border border-border rounded-xl"
              >
                {commonT('cancel')}
              </button>
              <button
                type="button"
                data-testid="confirm-delete-cat-btn"
                disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(deleteConfirmCategory.id)}
                className="px-4 py-2 text-xs font-bold bg-destructive text-destructive-foreground rounded-xl hover:bg-destructive/90"
              >
                {deleteMutation.isPending ? 'Deleting...' : commonT('confirm')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
