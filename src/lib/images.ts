/**
 * Resolves product image URLs.
 * Backend stores relative paths like `/uploads/products/...`
 * which need to be resolved against the backend host.
 */
export function getImageUrl(url: string | null | undefined): string {
  if (!url) return '';
  if (url.startsWith('/uploads/')) {
    const base = process.env.NEXT_PUBLIC_UPLOADS_URL || 'http://localhost:4000';
    return `${base}${url}`;
  }
  return url;
}
