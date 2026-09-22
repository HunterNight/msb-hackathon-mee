import { useCallback, useEffect, useState } from 'react';

import * as memoryApi from '../api/memory';
import type { MemoryGraphDto, MemoryWhyDto } from '../api/types';
import { MemoryUniverseLegend, MemoryUniverseSvg, STATE_LABEL } from '../components/MemoryUniverseSvg';
import { toUniverse, toUniverseState } from '../lib/memoryUniverse';

const MAP_SIZE = 460;

export function MemoryUniversePage() {
  const [graph, setGraph] = useState<MemoryGraphDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [why, setWhy] = useState<MemoryWhyDto | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setGraph(await memoryApi.memoryGraph());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'MEE chưa có bộ nhớ nào cho khách hàng này, hoặc quyền ghi nhớ đang tắt.');
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!selectedId) {
      setWhy(null);
      return;
    }
    setWhy(null);
    memoryApi.memoryWhy(selectedId).then(setWhy).catch(() => setWhy(null));
  }, [selectedId]);

  const records = graph?.nodes ?? [];
  const universe = toUniverse(records, graph?.links ?? []);
  const selectedRecord = records.find((r) => r.id === selectedId) ?? null;

  async function runAction(action: (id: string) => Promise<void>) {
    if (!selectedId) return;
    setBusy(true);
    try {
      await action(selectedId);
      setSelectedId(null);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Không thực hiện được.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="universe-page">
      <div className="universe-header">
        <h1>MEE Universe</h1>
        <p>
          Những điều MEE hiểu về bạn. Ô nét đứt là MEE đang đoán; ô nét liền là điều bạn đã xác
          nhận. Mỗi thay đổi đều được ghi lại.
        </p>
      </div>

      <div className="universe-canvas">
        {error ? (
          <div className="universe-empty">
            <p>{error}</p>
          </div>
        ) : universe.nodes.length === 0 ? (
          <div className="universe-empty">
            <p>MEE chưa có ký ức nào về bạn. Trò chuyện với MEE để bắt đầu.</p>
          </div>
        ) : (
          <MemoryUniverseSvg
            nodes={universe.nodes}
            links={universe.links}
            size={MAP_SIZE}
            selectedId={selectedId}
            onSelect={setSelectedId}
            centerLabel="Bạn"
          />
        )}
      </div>

      {universe.nodes.length > 0 ? <MemoryUniverseLegend nodes={universe.nodes} /> : null}
      {universe.overflow > 0 ? (
        <p className="citation-row" style={{ textAlign: 'center', paddingBottom: 16 }}>
          +{universe.overflow} ký ức khác chưa hiển thị trên bản đồ
        </p>
      ) : null}

      {selectedRecord ? (
        <div className="side-panel">
          <button className="close-panel" onClick={() => setSelectedId(null)} aria-label="Đóng">
            ✕
          </button>
          <h2>{selectedRecord.entity || selectedRecord.text}</h2>
          <span
            className="state-badge"
            style={{
              background:
                toUniverseState(selectedRecord.state) === 'CONFIRMED' ? '#feefe7' : '#f0f1f3',
              color: toUniverseState(selectedRecord.state) === 'CONFIRMED' ? '#f4600c' : '#505f79',
            }}>
            {STATE_LABEL[toUniverseState(selectedRecord.state)]}
          </span>
          <p style={{ fontSize: 14, margin: '0 0 8px' }}>{selectedRecord.text}</p>

          {why ? (
            <div className="why">
              <strong>Tại sao MEE nghĩ vậy?</strong>
              <p style={{ margin: '6px 0 0' }}>{why.evidenceSummary || why.reason || 'Không có thêm chi tiết.'}</p>
            </div>
          ) : null}

          <div className="actions">
            {selectedRecord.state === 'HYPOTHESIS' || selectedRecord.state === 'EXPIRED' ? (
              <button className="btn" disabled={busy} onClick={() => runAction(memoryApi.confirmRecord)}>
                Đúng rồi
              </button>
            ) : null}
            {selectedRecord.state === 'HYPOTHESIS' ? (
              <button className="btn secondary" disabled={busy} onClick={() => runAction(memoryApi.rejectRecord)}>
                Không phải
              </button>
            ) : null}
            {selectedRecord.state === 'CONFIRMED' ? (
              <button className="btn secondary" disabled={busy} onClick={() => runAction(memoryApi.deactivateRecord)}>
                Không còn nữa
              </button>
            ) : null}
            <button className="btn secondary" disabled={busy} onClick={() => runAction(memoryApi.forgetRecord)}>
              Quên hẳn
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
