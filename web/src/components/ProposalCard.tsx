import type { ProposalCardDto } from '../api/types';

const AMOUNT_FORMAT = new Intl.NumberFormat('vi-VN');

export function ProposalCard({
  card,
  onConfirm,
  onCancel,
  busy,
}: {
  card: ProposalCardDto;
  onConfirm: (card: ProposalCardDto) => void;
  onCancel: (card: ProposalCardDto) => void;
  busy: boolean;
}) {
  const finished = !['PROPOSE', 'CONFIRM', 'STEP_UP'].includes(card.phase);
  return (
    <div className="card-box">
      <div className="card-title">{card.title}</div>
      <div className="card-amount">{AMOUNT_FORMAT.format(card.amount)} ₫</div>
      {card.rows.map((row, index) => (
        <div className="card-row" key={`${row.k}-${index}`}>
          <span>{row.k}</span>
          <span style={row.emphasis ? { fontWeight: 700 } : undefined}>{row.v}</span>
        </div>
      ))}
      <div className="card-row">
        <span>Trạng thái</span>
        <span>{card.statusLabel}</span>
      </div>
      {card.requiresStepUp && !finished ? (
        <p className="citation-row" style={{ marginTop: 8 }}>
          Giao dịch này cần xác thực bổ sung (Face ID/PIN) mà ứng dụng web chưa hỗ trợ — xác nhận ở
          đây có thể bị máy chủ từ chối cho tới khi bước đó hoàn tất trên thiết bị di động.
        </p>
      ) : null}
      {!finished ? (
        <div className="card-actions">
          <button className="btn" disabled={busy} onClick={() => onConfirm(card)}>
            Xác nhận
          </button>
          <button className="btn secondary" disabled={busy} onClick={() => onCancel(card)}>
            Huỷ
          </button>
        </div>
      ) : null}
    </div>
  );
}
