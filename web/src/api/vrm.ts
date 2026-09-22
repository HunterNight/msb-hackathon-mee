// The agent path: app → gateway → vrm-agent, the same route mobile uses
// (mobile/src/features/mi/api/vrmTransport.ts). The agent answers the turn directly; mi.ts's
// `sendMessage` is the fallback ChatPage calls when this fails or the agent hands the turn back
// (`status: 'out_of_scope'` — money-moving, personal-data or off-topic turns; see
// services/vrm-agent/scope.py).
//
// The gateway strips any `X-GreenNode-AgentBase-User-Id` off the request and re-sets it from the
// verified JWT (see gateway's CustomerHeaderFilter), so nothing here asserts an identity.

import type { ChipDto, MemoryCandidatePayload, MessageDto } from './types';

const VRM_SESSION_HEADER = 'X-GreenNode-AgentBase-Session-Id';
const VRM_DISABLED = import.meta.env.VITE_MSB_VRM_DISABLED === '1';

export interface VrmTurnResult {
  text: string;
  domain: string;
  citations: { title?: string; docId?: string }[];
  suggestions: ChipDto[];
  memoryCandidate: MemoryCandidatePayload | null;
}

const LATEX_GLYPHS: Record<string, string> = {
  approx: '≈',
  times: '×',
  div: '÷',
  pm: '±',
  leq: '≤',
  geq: '≥',
  neq: '≠',
};

/** Plain-text-safe rendering of the agent's markdown — same transform as mobile's
 * `flattenAgentMarkdown`, since the chat bubble here is plain text too. */
export function flattenAgentMarkdown(raw: string): string {
  if (!raw) return '';
  return raw
    .replace(/^\s*\|?[\s:|-]{3,}\|?\s*$/gm, '')
    .replace(/\\(approx|times|div|pm|leq|geq|neq)/g, (_m, cmd: string) => LATEX_GLYPHS[cmd] ?? '')
    .replace(/\$\s*([≈×÷±≤≥≠])\s*\$/g, '$1')
    .replace(/^\s*\|(.+)\|\s*$/gm, (_m, row: string) =>
      row.split('|').map((cell) => cell.trim()).filter(Boolean).join(' · '),
    )
    .replace(/^\s{0,3}#{1,6}\s*/gm, '')
    .replace(/^\s{0,3}>\s?/gm, '')
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '$1')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

export async function sendVrmTurn(input: {
  text: string;
  conversationId: string;
  accessToken: string | null;
}): Promise<VrmTurnResult> {
  const response = await fetch(`/api/v1/vrm/invocations`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [VRM_SESSION_HEADER]: input.conversationId,
      ...(input.accessToken ? { Authorization: `Bearer ${input.accessToken}` } : {}),
    },
    body: JSON.stringify({ message: input.text }),
    signal: AbortSignal.timeout(60_000),
  });
  if (!response.ok) throw new Error(`vrm ${response.status}`);

  const body = (await response.json()) as {
    status?: string;
    response?: string;
    domain?: string;
    citations?: { title?: string; docId?: string }[];
    suggestions?: { text?: string; prompt?: string }[];
    memoryCandidate?: {
      recordId?: string;
      icon?: string;
      title?: string;
      text?: string;
      actions?: string[];
      saved?: boolean;
    } | null;
  };
  if (body.status !== 'success' || !body.response) {
    throw new Error(body.status === 'out_of_scope' ? 'out_of_scope' : 'vrm returned no answer');
  }

  return {
    text: flattenAgentMarkdown(body.response),
    domain: body.domain ?? 'general',
    citations: body.citations ?? [],
    suggestions: (body.suggestions ?? [])
      .filter((s) => Boolean(s.text?.trim()))
      .map((s) => ({ text: s.text as string, prompt: s.prompt?.trim() || (s.text as string) })),
    memoryCandidate: body.memoryCandidate?.recordId
      ? {
          recordId: body.memoryCandidate.recordId,
          icon: body.memoryCandidate.icon ?? '',
          title: body.memoryCandidate.title ?? '',
          text: body.memoryCandidate.text ?? '',
          actions: (body.memoryCandidate.actions ?? ['REMEMBER', 'FORGET']).filter(
            (a): a is 'REMEMBER' | 'FORGET' => a === 'REMEMBER' || a === 'FORGET',
          ),
          saved: body.memoryCandidate.saved === true,
        }
      : null,
  };
}

export function vrmEnabled(): boolean {
  return !VRM_DISABLED;
}

export function vrmMessage(result: VrmTurnResult, seq: number): MessageDto {
  return {
    id: `vrm-${seq}-${Date.now()}`,
    role: 'MI',
    kind: result.memoryCandidate ? 'MEMORY_CANDIDATE' : 'TEXT',
    text: result.text,
    seq,
    createdAt: new Date().toISOString(),
    agent: result.domain ? result.domain.toUpperCase() : null,
    chips: result.suggestions,
    memoryCandidate: result.memoryCandidate,
    citations: result.citations
      .filter((c) => Boolean(c.title) && Boolean(c.docId))
      .map((c) => ({ title: c.title as string, docId: c.docId as string })),
  };
}
