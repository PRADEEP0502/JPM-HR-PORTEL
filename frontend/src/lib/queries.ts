import { useQuery } from '@tanstack/react-query';
import { api } from './api';
import type { Dashboard, Department, NotificationItem, SyncStatus } from './types';

/** Data refreshes in the background so charts follow new submissions without a reload. */
export const REFRESH_MS = 60_000;

export const useDashboard = () =>
  useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<Dashboard>('/dashboard'), refetchInterval: REFRESH_MS });

export const useDepartments = () =>
  useQuery({ queryKey: ['departments'], queryFn: () => api.get<Department[]>('/departments'), staleTime: 5 * 60_000 });

export const useSyncStatus = () =>
  useQuery({ queryKey: ['sync-status'], queryFn: () => api.get<SyncStatus>('/sync/status'), refetchInterval: REFRESH_MS });

export const usePublicInfo = () =>
  useQuery({
    queryKey: ['public-info'],
    queryFn: () => api.get<{ appName: string; demoMode: boolean; timezone: string }>('/public/info'),
    staleTime: Infinity,
    // Free hosting puts the backend to sleep; waking it takes up to a minute or two. Keep trying for ~3 minutes.
    retry: 36,
    retryDelay: 5000,
  });

export const useNotifications = () =>
  useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get<{ unreadCount: number; items: NotificationItem[] }>('/notifications'),
    refetchInterval: REFRESH_MS,
  });

/** Query keys whose data depends on candidates/interviews - invalidated after every mutation. */
export const CANDIDATE_KEYS = [['dashboard'], ['candidates'], ['candidate'], ['interviews'], ['reports'], ['notifications'], ['pipeline']];
