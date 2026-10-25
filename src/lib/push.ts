import { apiClient } from './apiClient';

export function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding)
    .replace(/-/g, '+')
    .replace(/_/g, '/');

  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);

  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

export async function isPushNotificationSupported(): Promise<boolean> {
  if (typeof window === 'undefined') return false;
  return (
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  );
}

export async function getExistingPushSubscription(): Promise<PushSubscription | null> {
  if (!(await isPushNotificationSupported())) return null;
  try {
    const reg = await navigator.serviceWorker.getRegistration('/sw.js');
    if (!reg) return null;
    return await reg.pushManager.getSubscription();
  } catch {
    return null;
  }
}

export interface SubscribePushResult {
  success: boolean;
  error?: 'UNSUPPORTED' | 'PERMISSION_DENIED' | 'FAILED';
  message?: string;
}

export async function subscribeToWebPush(): Promise<SubscribePushResult> {
  const supported = await isPushNotificationSupported();
  if (!supported) {
    return {
      success: false,
      error: 'UNSUPPORTED',
      message: 'Web push notifications are not supported in your browser.',
    };
  }

  // Check initial permission
  if (Notification.permission === 'denied') {
    return {
      success: false,
      error: 'PERMISSION_DENIED',
      message: 'Notification permission was denied. Please allow notifications in your browser settings.',
    };
  }

  // Request permission
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    return {
      success: false,
      error: 'PERMISSION_DENIED',
      message: 'Notification permission was denied. Please allow notifications in your browser settings.',
    };
  }

  try {
    // 1. Register sw.js
    const registration = await navigator.serviceWorker.register('/sw.js');
    await navigator.serviceWorker.ready;

    // 2. GET /push/vapid-public-key
    const vapidRes = await apiClient<{ key?: string; publicKey?: string }>('/push/vapid-public-key');
    const vapidKey = vapidRes.data?.key || vapidRes.data?.publicKey;

    if (!vapidKey) {
      throw new Error('VAPID public key not received from server');
    }

    // 3. PushManager.subscribe
    const applicationServerKey = urlBase64ToUint8Array(vapidKey);
    const subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: applicationServerKey as unknown as BufferSource,
    });

    const subJson = subscription.toJSON();

    // 4. POST /push/subscribe
    await apiClient('/push/subscribe', {
      method: 'POST',
      body: {
        endpoint: subscription.endpoint,
        keys: {
          p256dh: subJson.keys?.p256dh,
          auth: subJson.keys?.auth,
        },
      },
    });

    return { success: true };
  } catch (err: any) {
    return {
      success: false,
      error: 'FAILED',
      message: err.message || 'Failed to subscribe to web push notifications.',
    };
  }
}

export async function unsubscribeFromWebPush(): Promise<boolean> {
  try {
    const sub = await getExistingPushSubscription();
    if (sub) {
      await sub.unsubscribe();
      return true;
    }
    return false;
  } catch {
    return false;
  }
}
