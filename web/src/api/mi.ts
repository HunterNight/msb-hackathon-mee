import { api } from './client';
import type {
  CurrentConversationResponse,
  MessageSource,
  NewConversationResponse,
  ProposalCardDto,
  TurnResponse,
} from './types';

export function currentConversation() {
  return api.get<CurrentConversationResponse>('/mi/conversations/current?limit=50');
}

export function startConversation() {
  return api.post<NewConversationResponse>('/mi/conversations');
}

/** The JSON variant of `POST /mi/conversations/{id}/messages` — `client.ts` asks for
 * `Accept: application/json`, so this never opens the SSE stream. Used as the fallback when the
 * vrm-agent path (vrm.ts) fails or hands the turn back as out of its scope. */
export function sendMessage(conversationId: string, text: string, source: MessageSource = 'TYPED') {
  return api.post<TurnResponse>(`/mi/conversations/${conversationId}/messages`, { text, source });
}

export function confirmProposal(proposalId: string) {
  return api.post<ProposalCardDto>(`/mi/proposals/${proposalId}/confirm`);
}

export function cancelProposal(proposalId: string) {
  return api.post<ProposalCardDto>(`/mi/proposals/${proposalId}/cancel`);
}

export function confirmMemory(recordId: string) {
  return api.post<void>(`/mi/memory/${recordId}/confirm`);
}

export function rejectMemory(recordId: string) {
  return api.post<void>(`/mi/memory/${recordId}/reject`);
}

export function forgetMemory(recordId: string) {
  return api.post<void>(`/mi/memory/${recordId}/forget`);
}
