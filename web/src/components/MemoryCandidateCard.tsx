import type { MemoryCandidatePayload } from '../api/types';

export function MemoryCandidateCard({
  candidate,
  onRemember,
  onForget,
  busy,
}: {
  candidate: MemoryCandidatePayload;
  onRemember: (recordId: string) => void;
  onForget: (recordId: string, saved: boolean) => void;
  busy: boolean;
}) {
  const saved = candidate.saved === true;
  return (
    <div className="card-box">
      <div className="card-title">
        {saved ? 'MEE đã ghi nhớ điều này' : 'Điều MEE mới biết về bạn'}
      </div>
      <p style={{ margin: '4px 0 0', fontSize: 14 }}>{candidate.text}</p>
      <div className="card-actions">
        {!saved && candidate.actions.includes('REMEMBER') ? (
          <button className="btn" disabled={busy} onClick={() => onRemember(candidate.recordId)}>
            Ghi nhớ
          </button>
        ) : null}
        {candidate.actions.includes('FORGET') ? (
          <button
            className="btn secondary"
            disabled={busy}
            onClick={() => onForget(candidate.recordId, saved)}>
            Quên đi
          </button>
        ) : null}
      </div>
    </div>
  );
}
