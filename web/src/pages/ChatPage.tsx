import { useEffect, useRef, useState } from 'react';

import { useAuth } from '../auth/AuthProvider';
import { ApiError } from '../api/client';
import * as mi from '../api/mi';
import type { MessageDto, ProposalCardDto } from '../api/types';
import { sendVrmTurn, vrmEnabled, vrmMessage } from '../api/vrm';
import { ChatBubble } from '../components/ChatBubble';
import { MemoryCandidateCard } from '../components/MemoryCandidateCard';
import { ProposalCard } from '../components/ProposalCard';

const TYPING_ID = '__typing__';

let seqCounter = 0;
function nextSeq() {
  seqCounter += 1;
  return seqCounter;
}

export function ChatPage() {
  const { session, tokenProvider } = useAuth();
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<MessageDto[]>([]);
  const [suggestions, setSuggestions] = useState<{ text: string; prompt: string }[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const current = await mi.currentConversation();
        if (cancelled) return;
        setConversationId(current.conversationId);
        setMessages(current.messages);
        setSuggestions(current.suggestions);
      } catch (e) {
        if (e instanceof ApiError && e.code === 'CM-004') {
          const created = await mi.startConversation();
          if (cancelled) return;
          setConversationId(created.conversationId);
          setSuggestions(created.suggestions);
        } else {
          setLoadError(e instanceof Error ? e.message : 'Không tải được cuộc trò chuyện.');
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  function upsert(message: MessageDto) {
    setMessages((current) => {
      const withoutTyping = current.filter((m) => m.id !== TYPING_ID);
      const i = withoutTyping.findIndex((m) => m.id === message.id);
      if (i === -1) return [...withoutTyping, message];
      const copy = withoutTyping.slice();
      copy[i] = message;
      return copy;
    });
  }

  function removeTyping() {
    setMessages((current) => current.filter((m) => m.id !== TYPING_ID));
  }

  async function send(text: string) {
    if (!conversationId || !text.trim() || sending) return;
    setInput('');
    setSending(true);
    setMessages((current) => [
      ...current,
      { id: `local-${Date.now()}`, role: 'USER', kind: 'TEXT', text, seq: nextSeq(), createdAt: new Date().toISOString() },
      { id: TYPING_ID, role: 'MI', kind: 'TYPING', seq: nextSeq(), createdAt: new Date().toISOString() },
    ]);
    setSuggestions([]);

    try {
      if (vrmEnabled()) {
        try {
          const token = await tokenProvider();
          const result = await sendVrmTurn({ text, conversationId, accessToken: token });
          upsert(vrmMessage(result, nextSeq()));
          setSuggestions(result.suggestions.length ? result.suggestions.map((s) => ({ text: s.text, prompt: s.prompt })) : []);
          return;
        } catch {
          // Money-moving, personal-data or out-of-scope turns are handed back by the agent
          // (services/vrm-agent/scope.py); mi-assistant answers those. Fall through.
        }
      }
      const turn = await mi.sendMessage(conversationId, text);
      turn.messages.forEach(upsert);
    } catch (e) {
      removeTyping();
      setLoadError(e instanceof Error ? e.message : 'MEE hiện chưa trả lời được, thử lại nhé.');
    } finally {
      setSending(false);
    }
  }

  async function onConfirmProposal(card: ProposalCardDto) {
    setBusyAction(card.id);
    try {
      const updated = await mi.confirmProposal(card.id);
      patchCard(card.id, updated);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Không xác nhận được.');
    } finally {
      setBusyAction(null);
    }
  }

  async function onCancelProposal(card: ProposalCardDto) {
    setBusyAction(card.id);
    try {
      const updated = await mi.cancelProposal(card.id);
      patchCard(card.id, updated);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Không huỷ được.');
    } finally {
      setBusyAction(null);
    }
  }

  function patchCard(proposalId: string, updated: ProposalCardDto) {
    setMessages((current) => current.map((m) => (m.card?.id === proposalId ? { ...m, card: updated } : m)));
  }

  async function onRemember(recordId: string) {
    setBusyAction(recordId);
    try {
      await mi.confirmMemory(recordId);
      patchCandidate(recordId, { actions: ['FORGET'], saved: true });
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Không ghi nhớ được.');
    } finally {
      setBusyAction(null);
    }
  }

  async function onForget(recordId: string, saved: boolean) {
    setBusyAction(recordId);
    try {
      if (saved) await mi.forgetMemory(recordId);
      else await mi.rejectMemory(recordId);
      patchCandidate(recordId, { actions: [] });
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : 'Không thực hiện được.');
    } finally {
      setBusyAction(null);
    }
  }

  function patchCandidate(recordId: string, patch: Partial<NonNullable<MessageDto['memoryCandidate']>>) {
    setMessages((current) =>
      current.map((m) =>
        m.memoryCandidate?.recordId === recordId
          ? { ...m, memoryCandidate: { ...m.memoryCandidate, ...patch } }
          : m,
      ),
    );
  }

  return (
    <div className="chat-page">
      <div className="chat-header">
        <h1>MEE</h1>
        {session?.username ? <span className="citation-row">Xin chào, {session.username}</span> : null}
      </div>

      <div className="chat-scroll" ref={scrollRef}>
        {messages.length === 0 ? (
          <div className="chat-empty">
            <p>Hỏi MEE về ngân hàng, tài chính cá nhân, hoặc nhờ MEE chuyển tiền, mở sổ tiết kiệm.</p>
          </div>
        ) : null}

        {messages.map((message) => {
          if (message.kind === 'TYPING') return <ChatBubble key={message.id} role="typing" text="" />;
          if (message.kind === 'MEMORY_CANDIDATE' && message.memoryCandidate) {
            return (
              <div key={message.id} className="bubble-row mi">
                {message.text ? <div className="bubble mi">{message.text}</div> : null}
                {message.memoryCandidate.actions.length > 0 ? (
                  <div style={{ marginTop: 8 }}>
                    <MemoryCandidateCard
                      candidate={message.memoryCandidate}
                      onRemember={onRemember}
                      onForget={onForget}
                      busy={busyAction === message.memoryCandidate.recordId}
                    />
                  </div>
                ) : null}
              </div>
            );
          }
          if (message.card) {
            return (
              <div key={message.id} className="bubble-row mi">
                {message.text ? <div className="bubble mi">{message.text}</div> : null}
                <div style={{ marginTop: 8 }}>
                  <ProposalCard
                    card={message.card}
                    onConfirm={onConfirmProposal}
                    onCancel={onCancelProposal}
                    busy={busyAction === message.card.id}
                  />
                </div>
              </div>
            );
          }
          return (
            <div key={message.id} className={`bubble-row ${message.role === 'USER' ? 'user' : 'mi'}`}>
              <ChatBubble role={message.role === 'USER' ? 'user' : 'mi'} text={message.text ?? ''} />
              {message.citations?.length ? (
                <div className="citation-row">Nguồn: {message.citations.map((c) => c.title).join(', ')}</div>
              ) : null}
              {message.chips?.length ? (
                <div className="chip-row">
                  {message.chips.map((chip) => (
                    <button key={chip.text} className="chip" onClick={() => send(chip.prompt)}>
                      {chip.text}
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
          );
        })}

        {messages.length === 0 && suggestions.length > 0 ? (
          <div className="chip-row" style={{ justifyContent: 'center' }}>
            {suggestions.map((s) => (
              <button key={s.text} className="chip" onClick={() => send(s.prompt)}>
                {s.text}
              </button>
            ))}
          </div>
        ) : null}

        {loadError ? <p className="error-text">{loadError}</p> : null}
      </div>

      <form
        className="chat-composer"
        onSubmit={(e) => {
          e.preventDefault();
          send(input);
        }}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Hỏi MEE bất cứ điều gì…"
          disabled={!conversationId || sending}
        />
        <button className="btn" type="submit" disabled={!conversationId || sending || !input.trim()}>
          Gửi
        </button>
      </form>
    </div>
  );
}
