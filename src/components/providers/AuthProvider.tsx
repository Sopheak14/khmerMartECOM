'use client';

import { ReactNode, useEffect } from 'react';
import { useAuthStore } from '@/lib/store/useAuthStore';
import { refreshAccessToken } from '@/lib/apiClient';
import { useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '@/lib/queryKeys';

export function AuthProvider({ children }: { children: ReactNode }) {
  const { setInitialized } = useAuthStore();
  const queryClient = useQueryClient();

  useEffect(() => {
    let isMounted = true;

    async function initSession() {
      try {
        // Shared single-flight refresh: sets or clears the auth state itself
        const token = await refreshAccessToken();
        if (isMounted && token) {
          queryClient.invalidateQueries({ queryKey: queryKeys.cart.current });
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
  }, [setInitialized, queryClient]);

  return <>{children}</>;
}
