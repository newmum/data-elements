import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { request } from './client';
import type { AlertEvent, AlertRule, AuditLog, CallLog, MonitorOverview } from './types';

export function useMonitorOverview() {
  return useQuery({
    queryKey: ['monitor', 'overview'],
    queryFn: () => request<MonitorOverview>('GET', '/monitor/overview'),
  });
}

export function useAuditLogs() {
  return useQuery({
    queryKey: ['audit'],
    queryFn: () => request<AuditLog[]>('GET', '/audit'),
  });
}

export function useCallLogs() {
  return useQuery({
    queryKey: ['monitor', 'calls'],
    queryFn: () => request<CallLog[]>('GET', '/monitor/calls?limit=50'),
  });
}

export function useAlertRules() {
  return useQuery({
    queryKey: ['monitor', 'alert-rules'],
    queryFn: () => request<AlertRule[]>('GET', '/monitor/alert-rules'),
  });
}

export function useAlertEvents() {
  return useQuery({
    queryKey: ['monitor', 'alert-events'],
    queryFn: () => request<AlertEvent[]>('GET', '/monitor/alert-events?limit=50'),
  });
}

export function useSetAlertRuleEnabled() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      request<AlertRule>('PUT', `/monitor/alert-rules/${id}/enabled`, { enabled }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['monitor', 'alert-rules'] });
      void queryClient.invalidateQueries({ queryKey: ['monitor', 'alert-events'] });
    },
  });
}

export function useEvaluateAlertRules() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => request<AlertEvent[]>('POST', '/monitor/alert-rules/evaluate'),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['monitor', 'alert-events'] });
      void queryClient.invalidateQueries({ queryKey: ['monitor', 'alert-rules'] });
    },
  });
}

export interface NodeTypeDto {
  type: string;
  category: string;
  label: string;
  description: string;
  icon: string;
  color: string;
}

export function useNodeTypes() {
  return useQuery({
    queryKey: ['node-types'],
    queryFn: () => request<NodeTypeDto[]>('GET', '/node-types'),
    staleTime: Infinity,
  });
}
