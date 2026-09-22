import { api } from './client';
import type { MemoryGraphDto, MemoryWhyDto } from './types';

export function memoryGraph() {
  return api.get<MemoryGraphDto>('/mi/memory/graph');
}

export function memoryWhy(recordId: string) {
  return api.get<MemoryWhyDto>(`/mi/memory/${recordId}/why`);
}

export function confirmRecord(recordId: string) {
  return api.post<void>(`/mi/memory/${recordId}/confirm`);
}

export function rejectRecord(recordId: string) {
  return api.post<void>(`/mi/memory/${recordId}/reject`);
}

export function deactivateRecord(recordId: string) {
  return api.post<void>(`/mi/memory/${recordId}/deactivate`);
}

export function forgetRecord(recordId: string) {
  return api.post<void>(`/mi/memory/${recordId}/forget`);
}
