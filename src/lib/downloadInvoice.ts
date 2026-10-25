import { useAuthStore } from '@/lib/store/useAuthStore';

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api';

export async function downloadInvoice(orderNumber: string): Promise<string> {
  const token = useAuthStore.getState().token;
  const res = await fetch(`${BASE_URL}/orders/${orderNumber}/invoice.pdf`, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    credentials: 'include',
  });

  if (!res.ok) {
    throw new Error(`Failed to download invoice (${res.status})`);
  }

  // Parse filename from Content-Disposition
  const disposition = res.headers.get('content-disposition');
  let filename = `invoice-${orderNumber}.pdf`;
  if (disposition && disposition.includes('filename=')) {
    const match = disposition.match(/filename=["']?([^"';]+)["']?/i);
    if (match && match[1]) {
      filename = match[1];
    }
  }

  const blob = await res.blob();
  const blobUrl = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = blobUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => window.URL.revokeObjectURL(blobUrl), 1000);

  return filename;
}
