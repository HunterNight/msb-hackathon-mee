// DTO shapes for mi-assistant-service's public API, ported from
// mobile/src/features/mi/model/types.ts and mobile/src/core/api/dto.ts (ProposalCardDto) — same
// backend, same envelope, kept in sync by hand since this app has no shared package with mobile.

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  meta: { page: number | null; size: number | null; total: number | null } | null;
  error: { code: string; message: string; details: Record<string, unknown> } | null;
  requestId: string | null;
  timestamp: string;
}

export type MessageRole = 'USER' | 'MI';
export type MessageKind = 'TEXT' | 'TYPING' | 'CARD' | 'CHART' | 'STEPS' | 'MEMORY_CANDIDATE';
export type MessageSafety = 'OK' | 'REFUSED' | 'GUARDED';

export interface ChipDto {
  text: string;
  prompt: string;
}

export type ProposalPhase = 'PROPOSE' | 'CONFIRM' | 'STEP_UP' | 'EXEC' | 'DONE' | 'FAILED' | 'CANCEL' | 'EXPIRED';

/** Wire shape is `{k, v}`, not `{label, value}` — confirmed against
 * mobile/src/core/api/dto.ts, the field names the backend actually serialises. */
export interface KeyValueRowDto {
  k: string;
  v: string;
  emphasis?: boolean;
}

export interface ProposalCardDto {
  id: string;
  type: string;
  title: string;
  amount: number;
  rows: KeyValueRowDto[];
  phase: ProposalPhase;
  statusLabel: string;
  requiresStepUp: boolean;
  stepUpScope?: string | null;
  footnote?: string | null;
  ref?: string | null;
  expiresAt: string;
  invalidates: string[];
}

export interface MemoryCandidatePayload {
  recordId: string;
  icon: string;
  title: string;
  text: string;
  actions: ('REMEMBER' | 'FORGET')[];
  /** MEE already kept it (a statement of taste is stored without a tap) — only [Quên đi] applies. */
  saved?: boolean;
}

export interface MessageDto {
  id: string;
  role: MessageRole;
  kind: MessageKind;
  text?: string | null;
  card?: ProposalCardDto | null;
  memoryCandidate?: MemoryCandidatePayload | null;
  chips?: ChipDto[];
  citations?: { docId: string; title: string }[];
  agent?: string | null;
  safety?: MessageSafety | null;
  seq: number;
  createdAt: string;
  clientMessageId?: string;
}

export interface SuggestionDto {
  text: string;
  prompt: string;
  icon: string;
}

export interface CurrentConversationResponse {
  conversationId: string;
  messages: MessageDto[];
  suggestions: SuggestionDto[];
  paused: boolean;
  chatPayEnabled: boolean;
}

export interface NewConversationResponse {
  conversationId: string;
  suggestions: SuggestionDto[];
}

export interface TurnResponse {
  messages: MessageDto[];
  agent: string;
}

export type MessageSource = 'TYPED' | 'CHIP';

export type MemoryState =
  | 'SIGNAL'
  | 'CANDIDATE'
  | 'HYPOTHESIS'
  | 'CONFIRMED'
  | 'REJECTED'
  | 'INACTIVE'
  | 'EXPIRED'
  | 'FORGOTTEN';

export interface MemoryRecordDto {
  id: string;
  kind: string;
  text: string;
  confidence: number;
  pinned: boolean;
  createdAt: string;
  source: string;
  state: MemoryState;
  explicit: boolean;
  customerConfirmed: boolean;
  lastConfirmedAt?: string | null;
  persistence: string;
  entity?: string | null;
  evidenceSummary?: string | null;
}

export interface MemoryLinkDto {
  id: string;
  fromRecord: string;
  toRecord: string;
  relation: string;
  state: string;
  createdAt: string;
  confirmedAt?: string | null;
}

export interface MemoryGraphDto {
  nodes: MemoryRecordDto[];
  links: MemoryLinkDto[];
}

export interface MemoryWhyDto {
  recordId: string;
  kind: string;
  entity: string;
  evidenceSummary: string;
  reason: string;
  createdAt: string;
}
