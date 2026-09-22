import { useState } from 'react';

import { useAuth } from './auth/AuthProvider';
import { ChatPage } from './pages/ChatPage';
import { MemoryUniversePage } from './pages/MemoryUniversePage';

type Tab = 'chat' | 'universe';

export function App() {
  const auth = useAuth();
  const [tab, setTab] = useState<Tab>('chat');

  if (auth.status === 'loading') {
    return (
      <div className="centered">
        <p>Đang đăng nhập…</p>
      </div>
    );
  }

  if (auth.status !== 'authenticated') {
    return (
      <div className="centered">
        <div className="login-card">
          <div className="brand-mark" />
          <div className="brand-name">MSB DigiBank</div>
          <div className="brand-sub">Internet Banking — MEE</div>
          <p style={{ fontSize: 14, color: 'var(--secondary)', marginBottom: 20 }}>
            Trò chuyện với MEE và xem những gì MEE hiểu về bạn trên trình duyệt. Đăng nhập bằng tài
            khoản MSB DigiBank của bạn.
          </p>
          {auth.error ? <p className="error-text" style={{ marginBottom: 12 }}>{auth.error}</p> : null}
          <button className="btn" style={{ width: '100%' }} onClick={auth.login}>
            Đăng nhập
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="shell">
      <nav className="nav">
        <div className="nav-brand">
          <div className="brand-mark" style={{ margin: 0, width: 32, height: 32, borderRadius: 10 }} />
          <div>
            <div className="nav-brand-text">MSB DigiBank</div>
            <div className="nav-brand-sub">Internet Banking</div>
          </div>
        </div>

        <button className={`nav-item ${tab === 'chat' ? 'active' : ''}`} onClick={() => setTab('chat')}>
          💬 MEE
        </button>
        <button className={`nav-item ${tab === 'universe' ? 'active' : ''}`} onClick={() => setTab('universe')}>
          🌐 MEE Universe
        </button>

        <div className="nav-footer">
          <div className="nav-user">{auth.session?.username}</div>
          <button className="nav-item" onClick={auth.signOut}>
            Đăng xuất
          </button>
        </div>
      </nav>

      <div className="main">{tab === 'chat' ? <ChatPage /> : <MemoryUniversePage />}</div>
    </div>
  );
}
