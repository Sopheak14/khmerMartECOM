'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { apiClient } from '@/lib/apiClient';
import { KhqrPaymentDTO, PaymentStatusDTO, PaymentStatus, OrderStatus } from '@/types/api';

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api';

interface UseKhqrPaymentOptions {
  orderNumber: string;
  currency?: 'USD' | 'KHR';
  onPaid?: (status: PaymentStatusDTO) => void;
}

export function useKhqrPayment({ orderNumber, currency = 'USD', onPaid }: UseKhqrPaymentOptions) {
  const [payment, setPayment] = useState<KhqrPaymentDTO | null>(null);
  const [status, setStatus] = useState<PaymentStatus>('PENDING');
  const [orderStatus, setOrderStatus] = useState<OrderStatus | null>(null);
  const [bakongHash, setBakongHash] = useState<string | null>(null);
  const [paidAt, setPaidAt] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isRegenerating, setIsRegenerating] = useState<boolean>(false);
  const [isSimulating, setIsSimulating] = useState<boolean>(false);

  const eventSourceRef = useRef<EventSource | null>(null);
  const pollingIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const isTerminalRef = useRef<boolean>(false);
  const isFetchingRef = useRef<boolean>(false);
  const onPaidRef = useRef(onPaid);
  onPaidRef.current = onPaid;

  const stopStreamAndPolling = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current);
      pollingIntervalRef.current = null;
    }
  }, []);

  const handleStatusUpdate = useCallback(
    (statusDto: PaymentStatusDTO) => {
      setStatus(statusDto.status);
      if (statusDto.orderStatus) {
        setOrderStatus(statusDto.orderStatus);
      }
      if (statusDto.bakongHash) {
        setBakongHash(statusDto.bakongHash);
      }
      if (statusDto.paidAt) {
        setPaidAt(statusDto.paidAt);
      }

      if (
        statusDto.status === 'PAID' ||
        statusDto.status === 'EXPIRED' ||
        statusDto.status === 'FAILED'
      ) {
        isTerminalRef.current = true;
        stopStreamAndPolling();

        if (statusDto.status === 'PAID') {
          onPaidRef.current?.(statusDto);
        }
      }
    },
    [stopStreamAndPolling]
  );

  const startPolling = useCallback(
    (paymentId: number) => {
      if (pollingIntervalRef.current || isTerminalRef.current) return;

      pollingIntervalRef.current = setInterval(async () => {
        try {
          const res = await apiClient<PaymentStatusDTO>(`/payments/${paymentId}/status`);
          if (res.data) {
            handleStatusUpdate(res.data);
          }
        } catch (err: any) {
          // If polling fails with 404/500, continue polling or ignore
          console.warn('Payment status polling error:', err);
        }
      }, 3000);
    },
    [handleStatusUpdate]
  );

  const connectSse = useCallback(
    (paymentId: number, streamToken: string) => {
      stopStreamAndPolling();
      if (isTerminalRef.current) return;

      const sseUrl = `${BASE_URL}/payments/${paymentId}/stream?token=${encodeURIComponent(streamToken)}`;

      try {
        const es = new EventSource(sseUrl);
        eventSourceRef.current = es;

        es.addEventListener('status', (event: MessageEvent) => {
          try {
            const data: PaymentStatusDTO = JSON.parse(event.data);
            handleStatusUpdate(data);
          } catch (err) {
            console.error('Failed to parse SSE payment status:', err);
          }
        });

        // Ignore ping events
        es.addEventListener('ping', () => {});

        es.onerror = () => {
          // On SSE error, close stream and fallback to polling every 3s
          if (eventSourceRef.current) {
            eventSourceRef.current.close();
            eventSourceRef.current = null;
          }
          if (!isTerminalRef.current) {
            startPolling(paymentId);
          }
        };
      } catch {
        startPolling(paymentId);
      }
    },
    [stopStreamAndPolling, handleStatusUpdate, startPolling]
  );

  const fetchKhqr = useCallback(async (retryCount = 0) => {
    if (!orderNumber || isFetchingRef.current) return;
    isFetchingRef.current = true;
    setIsLoading(true);
    setError(null);
    isTerminalRef.current = false;

    try {
      const res = await apiClient<KhqrPaymentDTO>(`/payments/khqr/${orderNumber}`, {
        method: 'POST',
        query: { currency },
      });

      const paymentData = res.data;
      setPayment(paymentData);
      setStatus(paymentData.status);

      if (paymentData.status === 'PENDING') {
        connectSse(paymentData.paymentId, paymentData.streamToken);
      } else {
        isTerminalRef.current = true;
        if (paymentData.status === 'PAID') {
          onPaidRef.current?.({
            paymentId: paymentData.paymentId,
            status: 'PAID',
            orderStatus: 'CONFIRMED',
            paidAt: null,
            bakongHash: null,
            expiresAt: paymentData.expiresAt,
          });
        }
      }
    } catch (err: any) {
      // Retry once on 409 conflict caused by React strict mode concurrent mount
      if (retryCount < 2 && (err?.status === 409 || err?.code === 'CONFLICT' || err?.message?.includes('conflict'))) {
        isFetchingRef.current = false;
        await new Promise((r) => setTimeout(r, 400));
        return fetchKhqr(retryCount + 1);
      }
      setError(err.message || 'Failed to generate KHQR payment');
    } finally {
      isFetchingRef.current = false;
      setIsLoading(false);
    }
  }, [orderNumber, currency, connectSse]);

  const regenerate = useCallback(async () => {
    if (!orderNumber || isRegenerating) return;
    setIsRegenerating(true);
    stopStreamAndPolling();
    isTerminalRef.current = false;
    setError(null);

    try {
      const res = await apiClient<KhqrPaymentDTO>(`/payments/khqr/${orderNumber}/regenerate`, {
        method: 'POST',
      });

      const newPayment = res.data;
      setPayment(newPayment);
      setStatus(newPayment.status);

      if (newPayment.status === 'PENDING') {
        connectSse(newPayment.paymentId, newPayment.streamToken);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to regenerate KHQR payment');
    } finally {
      setIsRegenerating(false);
    }
  }, [orderNumber, isRegenerating, stopStreamAndPolling, connectSse]);

  const simulatePaid = useCallback(async () => {
    if (!payment?.paymentId || isSimulating) return;
    setIsSimulating(true);

    try {
      const res = await apiClient<PaymentStatusDTO>(`/payments/${payment.paymentId}/simulate-paid`, {
        method: 'POST',
      });
      handleStatusUpdate(res.data);
    } catch (err: any) {
      setError(err.message || 'Failed to simulate payment');
    } finally {
      setIsSimulating(false);
    }
  }, [payment?.paymentId, isSimulating, handleStatusUpdate]);

  useEffect(() => {
    fetchKhqr();

    return () => {
      stopStreamAndPolling();
    };
  }, [fetchKhqr, stopStreamAndPolling]);

  return {
    payment,
    status,
    orderStatus,
    bakongHash,
    paidAt,
    isLoading,
    error,
    isRegenerating,
    isSimulating,
    regenerate,
    simulatePaid,
    refetch: fetchKhqr,
  };
}
