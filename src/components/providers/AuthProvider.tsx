'use client';

import { ReactNode, useEffect } from 'react';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { apiClient } from '@/lib/apiClient';
import { UserDTO } from '@/types/api';

export function AuthProvider({ children }: { children: ReactNode }) {
  const { setAuth, clearAuth, setInitialized } = useAuthStore();

  useEffect(() => {
    let isMounted = true;

    async function initSession() {
      try {
        const res = await apiClient<{ accessToken: string; user: UserDTO }>('/auth/refresh', {
          method: 'POST',
          auth: false,
        });

        if (isMounted && res.data?.accessToken) {
          setAuth(res.data.accessToken, res.data.user);
        } else if (isMounted) {
          clearAuth();
        }
      } catch {
        if (isMounted) {
          clearAuth();
        }
      } finally {
        if (isMounted) {
          setInitialized(true);
        }
      }
    }

    initSession();

    return () => {
      isMounted = false;
    };
  }, [setAuth, clearAuth, setInitialized]);

  return <>{children}</>;
}
