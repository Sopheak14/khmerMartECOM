/**
 * Resolves product image URLs.
 * Backend stores relative paths like `/uploads/products/...`
 * which need to be resolved against the backend host.
 */
export function getImageUrl(url: string | null | undefined): string {
  if (!url) return '';
  // The backend fills imageless products with a via.placeholder.com URL. That host is not allowed by next/image
  // (400 in the console), so treat it as "no image" and let SafeImage render its neutral fallback.
  if (/^https?:\/\/via\.placeholder\.com\//i.test(url)) return '';
  if (url.startsWith('/uploads/')) {
    const base = process.env.NEXT_PUBLIC_UPLOADS_URL || 'http://localhost:4000';
    return `${base}${url}`;
  }
  return url;
}
