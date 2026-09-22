// Ported from mobile/src/ui/components/MemoryUniverse.tsx (layout) and
// mobile/src/features/mi/model/memoryUniverse.ts (state/icon/label mapping) — same geometry and
// rules, rendered with plain SVG here instead of react-native-svg, and an emoji glyph in place of
// the mobile app's icon font (no shared icon asset between the two apps).

import type { MemoryLinkDto, MemoryRecordDto, MemoryState } from '../api/types';

export type MemoryUniverseState = 'CONFIRMED' | 'HYPOTHESIS' | 'EXPIRED' | 'INACTIVE';

export interface UniverseNode {
  id: string;
  label: string;
  glyph: string;
  state: MemoryUniverseState;
}

export interface UniverseLink {
  id: string;
  from: string;
  to: string;
  confirmed: boolean;
}

export interface UniversePoint {
  x: number;
  y: number;
  ring: 0 | 1;
}

/** Past this many orbs the radial layout stops being readable (guideline 11 §8: "max ~20 nodes"). */
export const MAP_NODE_LIMIT = 16;

const VISIBLE_STATES: MemoryState[] = ['CONFIRMED', 'HYPOTHESIS', 'INACTIVE', 'EXPIRED'];

const STATE_ORDER: Record<MemoryUniverseState, number> = {
  CONFIRMED: 0,
  EXPIRED: 1,
  HYPOTHESIS: 2,
  INACTIVE: 3,
};

const KIND_GLYPH: Record<string, string> = {
  NICKNAME: '🙂',
  HABIT: '🔁',
  PREFERENCE: '⭐',
  GOAL: '🎯',
  FACT: 'ℹ️',
  CONCERN: '🛡️',
  LIFESTYLE: '✨',
  RELATIONSHIP: '❤️',
  PLAN: '📅',
  PRODUCT_PREFERENCE: '💳',
};

const ENTITY_GLYPH: { match: RegExp; glyph: string }[] = [
  { match: /(travel|trip|japan|nhat|du.?lich|flight|plane)/, glyph: '✈️' },
  { match: /(house|home|nha|apartment|bat.?dong.?san)/, glyph: '🏠' },
  { match: /(car|auto|xe|oto)/, glyph: '🚗' },
  { match: /(study|school|education|hoc|university)/, glyph: '🎓' },
  { match: /(wedding|cuoi|marriage)/, glyph: '💍' },
  { match: /(coffee|cafe|tra|food|an.?uong)/, glyph: '☕' },
  { match: /(shopping|mua.?sam|retail)/, glyph: '🛍️' },
  { match: /(invest|chung.?khoan|stock|fund|quy)/, glyph: '📈' },
  { match: /(save|saving|tiet.?kiem|deposit)/, glyph: '🏦' },
  { match: /(gym|sport|golf|the.?thao|yoga|run)/, glyph: '🏆' },
  { match: /(family|con|vo|chong|gia.?dinh|parent)/, glyph: '👪' },
  { match: /(bill|dien|nuoc|internet|hoa.?don)/, glyph: '🧾' },
  { match: /(loan|vay|debt|no)/, glyph: '💰' },
  { match: /(card|the|credit)/, glyph: '💳' },
];

function normalise(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();
}

export function memoryGlyph(kind: string, entity?: string | null): string {
  if (entity) {
    const key = normalise(entity);
    const hit = ENTITY_GLYPH.find((c) => c.match.test(key));
    if (hit) return hit.glyph;
  }
  return KIND_GLYPH[kind] ?? '✨';
}

export function toUniverseState(state: MemoryState): MemoryUniverseState {
  switch (state) {
    case 'CONFIRMED':
    case 'INACTIVE':
    case 'EXPIRED':
      return state;
    default:
      return 'HYPOTHESIS';
  }
}

export function memoryLabel(record: Pick<MemoryRecordDto, 'text' | 'entity'>): string {
  const entity = record.entity?.trim();
  if (entity) return entity.length > 18 ? `${entity.slice(0, 17)}…` : entity;
  const words = record.text.trim().split(/\s+/).slice(0, 3).join(' ');
  return words.length > 22 ? `${words.slice(0, 21)}…` : words;
}

export function visibleMemories(nodes: MemoryRecordDto[]): MemoryRecordDto[] {
  return nodes
    .filter((n) => VISIBLE_STATES.includes(n.state))
    .slice()
    .sort((a, b) => {
      const byState = STATE_ORDER[toUniverseState(a.state)] - STATE_ORDER[toUniverseState(b.state)];
      return byState !== 0 ? byState : b.confidence - a.confidence;
    });
}

export interface UniverseViewModel {
  nodes: UniverseNode[];
  links: UniverseLink[];
  overflow: number;
}

export function toUniverse(
  records: MemoryRecordDto[],
  links: MemoryLinkDto[],
  limit: number = MAP_NODE_LIMIT,
): UniverseViewModel {
  const visible = visibleMemories(records);
  const drawn = visible.slice(0, limit);
  const ids = new Set(drawn.map((r) => r.id));
  return {
    nodes: drawn.map((record) => ({
      id: record.id,
      label: memoryLabel(record),
      glyph: memoryGlyph(record.kind, record.entity),
      state: toUniverseState(record.state),
    })),
    links: links
      .filter((l) => ids.has(l.fromRecord) && ids.has(l.toRecord) && l.state !== 'REJECTED')
      .map((l) => ({ id: l.id, from: l.fromRecord, to: l.toRecord, confirmed: l.state === 'CONFIRMED' })),
    overflow: Math.max(0, visible.length - drawn.length),
  };
}

const ORB = 56;
const NODE_BLOCK = ORB + 34;
const CENTER_ORB = 64;
const STAGGER_ABOVE = 6;

/** Radial layout, deterministic in node order — same rule as mobile's `layoutMemoryUniverse`:
 * a memory that jumped to the other side of the canvas on every refetch would read as a
 * different memory. */
export function layoutUniverse(nodes: UniverseNode[], size: number): Record<string, UniversePoint> {
  const centre = size / 2;
  const outer = Math.max(CENTER_ORB, centre - NODE_BLOCK / 2);
  const inner = outer * 0.58;

  const tiers: Record<0 | 1, UniverseNode[]> = { 0: [], 1: [] };
  nodes.forEach((n) => tiers[n.state === 'CONFIRMED' ? 0 : 1].push(n));
  const both = tiers[0].length > 0 && tiers[1].length > 0;

  const result: Record<string, UniversePoint> = {};
  ([0, 1] as const).forEach((ring) => {
    const tier = tiers[ring];
    if (tier.length === 0) return;
    const base = both ? (ring === 0 ? inner : outer) : (inner + outer) / 2;
    const step = (2 * Math.PI) / tier.length;
    const offset = -Math.PI / 2 + (ring === 1 && both ? step / 2 : 0);
    tier.forEach((node, index) => {
      const stagger = tier.length > STAGGER_ABOVE && index % 2 === 1 ? 0.82 : 1;
      const r = base * stagger;
      const angle = offset + index * step;
      result[node.id] = { x: centre + r * Math.cos(angle), y: centre + r * Math.sin(angle), ring };
    });
  });
  return result;
}

export { ORB };
