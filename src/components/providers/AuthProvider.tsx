'use client';

import { ReactNode, useEffect } from 'react';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { apiClient } from '@/lib/apiClient';
import { UserDTO } from '@/types/api';
import { useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/lib/queryKeys';

export function AuthProvider({ children }: { children: ReactNode }) {
  const { setAuth, clearAuth, setInitialized } = useAuthStore();
  const queryClient = useQueryClient();

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
          queryClient.invalidateQueries({ queryKey: queryKeys.cart.current });
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
  }, [setAuth, clearAuth, setInitialized, queryClient]);

  return <>{children}</>;
}
