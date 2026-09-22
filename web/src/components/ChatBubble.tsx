export function ChatBubble({ role, text }: { role: 'user' | 'mi' | 'typing'; text: string }) {
  return (
    <div className={`bubble-row ${role === 'user' ? 'user' : 'mi'}`}>
      <div className={`bubble ${role}`}>{role === 'typing' ? 'MEE đang trả lời…' : text}</div>
    </div>
  );
}
