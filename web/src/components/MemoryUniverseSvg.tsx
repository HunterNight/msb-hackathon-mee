import {
  layoutUniverse,
  ORB,
  type MemoryUniverseState,
  type UniverseLink,
  type UniverseNode,
} from '../lib/memoryUniverse';

const TONE: Record<MemoryUniverseState, { fill: string; ring: string; label: string; dashed: boolean; opacity: number }> = {
  CONFIRMED: { fill: '#fff', ring: '#f4600c', label: '#091e42', dashed: false, opacity: 1 },
  HYPOTHESIS: { fill: '#feefe7', ring: '#505f79', label: '#505f79', dashed: true, opacity: 1 },
  EXPIRED: { fill: '#fff', ring: '#ffa95a', label: '#505f79', dashed: true, opacity: 1 },
  INACTIVE: { fill: '#eee', ring: '#dee5ef', label: '#505f79', dashed: false, opacity: 0.6 },
};

export const STATE_LABEL: Record<MemoryUniverseState, string> = {
  CONFIRMED: 'Đã xác nhận',
  HYPOTHESIS: 'MEE đoán',
  EXPIRED: 'Cần xác nhận lại',
  INACTIVE: 'Không còn dùng',
};

export function MemoryUniverseLegend({ nodes }: { nodes: UniverseNode[] }) {
  const counts: Partial<Record<MemoryUniverseState, number>> = {};
  nodes.forEach((n) => {
    counts[n.state] = (counts[n.state] ?? 0) + 1;
  });
  const states = (Object.keys(counts) as MemoryUniverseState[]).sort(
    (a, b) => Object.keys(TONE).indexOf(a) - Object.keys(TONE).indexOf(b),
  );
  return (
    <div className="universe-legend">
      {states.map((state) => (
        <span key={state} style={{ opacity: TONE[state].opacity }}>
          <span className="legend-dot" style={{ background: TONE[state].fill, border: `1.5px solid ${TONE[state].ring}` }} />
          {STATE_LABEL[state]} · {counts[state]}
        </span>
      ))}
    </div>
  );
}

export function MemoryUniverseSvg({
  nodes,
  links,
  size,
  selectedId,
  onSelect,
  centerLabel,
}: {
  nodes: UniverseNode[];
  links: UniverseLink[];
  size: number;
  selectedId: string | null;
  onSelect: (id: string) => void;
  centerLabel: string;
}) {
  const positions = layoutUniverse(nodes, size);
  const centre = size / 2;
  const orbits = Array.from(
    new Set(
      nodes
        .map((n) => positions[n.id])
        .filter((p): p is NonNullable<typeof p> => Boolean(p))
        .map((p) => Math.round(Math.hypot(p.x - centre, p.y - centre))),
    ),
  );

  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg width={size} height={size} style={{ position: 'absolute', pointerEvents: 'none' }}>
        {orbits.map((r) => (
          <circle key={`orbit-${r}`} cx={centre} cy={centre} r={r} stroke="rgba(9,30,66,0.15)" strokeWidth={1} strokeDasharray="2 6" fill="none" />
        ))}
        {links.map((link) => {
          const from = positions[link.from];
          const to = positions[link.to];
          if (!from || !to) return null;
          return (
            <line
              key={link.id}
              x1={from.x}
              y1={from.y}
              x2={to.x}
              y2={to.y}
              stroke={link.confirmed ? '#f4600c' : '#505f79'}
              strokeOpacity={link.confirmed ? 0.45 : 0.3}
              strokeWidth={link.confirmed ? 1.5 : 1}
              strokeDasharray={link.confirmed ? undefined : '4 4'}
            />
          );
        })}
        {nodes.map((node) => {
          const point = positions[node.id];
          if (!point) return null;
          const tone = TONE[node.state];
          return (
            <line
              key={`halo-${node.id}`}
              x1={centre}
              y1={centre}
              x2={point.x}
              y2={point.y}
              stroke={tone.ring}
              strokeOpacity={0.18}
              strokeWidth={1}
              strokeDasharray={tone.dashed ? '3 5' : undefined}
            />
          );
        })}
      </svg>

      <div
        style={{
          position: 'absolute',
          left: centre,
          top: centre,
          transform: 'translate(-50%, -50%)',
          width: 64,
          height: 64,
          borderRadius: 32,
          background: 'linear-gradient(135deg, #f4600c, #ffa95a)',
          color: '#fff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 12,
          fontWeight: 700,
          textAlign: 'center',
        }}>
        {centerLabel}
      </div>

      {nodes.map((node) => {
        const point = positions[node.id];
        if (!point) return null;
        const tone = TONE[node.state];
        const selected = node.id === selectedId;
        return (
          <button
            key={node.id}
            onClick={() => onSelect(node.id)}
            aria-label={`${node.label} — ${STATE_LABEL[node.state]}`}
            style={{
              position: 'absolute',
              left: point.x,
              top: point.y,
              transform: 'translate(-50%, -50%)',
              width: ORB,
              height: ORB,
              borderRadius: ORB / 2,
              background: tone.fill,
              border: `${selected ? 2.5 : 1.5}px ${tone.dashed ? 'dashed' : 'solid'} ${tone.ring}`,
              opacity: tone.opacity,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              fontSize: 20,
              boxShadow: selected ? '0 0 0 4px rgba(244,96,12,0.15)' : 'none',
            }}>
            {node.glyph}
          </button>
        );
      })}

      {nodes.map((node) => {
        const point = positions[node.id];
        if (!point) return null;
        return (
          <div
            key={`label-${node.id}`}
            style={{
              position: 'absolute',
              left: point.x,
              top: point.y + ORB / 2 + 4,
              transform: 'translateX(-50%)',
              width: 80,
              textAlign: 'center',
              fontSize: 11,
              color: '#091e42',
              pointerEvents: 'none',
            }}>
            {node.label}
          </div>
        );
      })}
    </div>
  );
}
