// Authorization Code + PKCE against Keycloak, realm `msb` — the same customer realm the mobile
// app's users log into. The client is public, so there is no secret: the code verifier is what
// proves this browser started the exchange. Ported from admin/src/auth/oidc.ts (same pattern,
// against the customer realm instead of msb-staff).

// Local Keycloak serves under its own /auth context path (KC_HTTP_RELATIVE_PATH in
// infra/docker-compose.yml) — the same reason mi-assistant's issuer URI and quest.js's default
// token URL both carry it.
const KC_URL = import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180/auth';
const REALM = import.meta.env.VITE_KEYCLOAK_REALM ?? 'msb';
const CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'msb-web';
const REDIRECT_URI = `${window.location.origin}/callback`;

const AUTH_ENDPOINT = `${KC_URL}/realms/${REALM}/protocol/openid-connect/auth`;
const TOKEN_ENDPOINT = `${KC_URL}/realms/${REALM}/protocol/openid-connect/token`;
const LOGOUT_ENDPOINT = `${KC_URL}/realms/${REALM}/protocol/openid-connect/logout`;

const VERIFIER_KEY = 'ib.pkce.verifier';
const STATE_KEY = 'ib.pkce.state';
const SESSION_KEY = 'ib.session';

export interface Session {
  accessToken: string;
  refreshToken: string | null;
  expiresAt: number;
  username: string;
  /** From the `customerId` claim (same mapper as msb-mobile-bff) — the gateway derives its own
   * copy from the JWT itself; this is only for display. */
  customerId: string | null;
}

function base64Url(bytes: ArrayBuffer): string {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

function randomString(length = 64): string {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return base64Url(bytes.buffer).slice(0, length);
}

async function challenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64Url(digest);
}

export function decodeJwt(token: string): Record<string, unknown> {
  const payload = token.split('.')[1];
  const padded = payload.replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(decodeURIComponent(escape(atob(padded))));
}

function toSession(token: { access_token: string; refresh_token?: string; expires_in: number }): Session {
  const claims = decodeJwt(token.access_token) as {
    preferred_username?: string;
    customerId?: string;
  };
  return {
    accessToken: token.access_token,
    refreshToken: token.refresh_token ?? null,
    expiresAt: Date.now() + token.expires_in * 1000,
    username: claims.preferred_username ?? 'unknown',
    customerId: claims.customerId ?? null,
  };
}

export async function beginLogin(): Promise<void> {
  const verifier = randomString();
  const state = randomString(32);
  sessionStorage.setItem(VERIFIER_KEY, verifier);
  sessionStorage.setItem(STATE_KEY, state);
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    response_type: 'code',
    scope: 'openid',
    state,
    code_challenge: await challenge(verifier),
    code_challenge_method: 'S256',
  });
  window.location.assign(`${AUTH_ENDPOINT}?${params.toString()}`);
}

export async function completeLogin(search: string): Promise<Session> {
  const params = new URLSearchParams(search);
  const error = params.get('error');
  if (error) throw new Error(params.get('error_description') ?? error);

  const code = params.get('code');
  const state = params.get('state');
  const verifier = sessionStorage.getItem(VERIFIER_KEY);
  if (!code || !verifier) throw new Error('Missing authorization code or PKCE verifier.');
  if (state !== sessionStorage.getItem(STATE_KEY)) throw new Error('State mismatch, login rejected.');

  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    code,
    code_verifier: verifier,
  });
  const response = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  if (!response.ok) throw new Error(`Token exchange failed (${response.status}).`);

  sessionStorage.removeItem(VERIFIER_KEY);
  sessionStorage.removeItem(STATE_KEY);
  const session = toSession(await response.json());
  saveSession(session);
  return session;
}

export async function refresh(session: Session): Promise<Session | null> {
  if (!session.refreshToken) return null;
  const body = new URLSearchParams({
    grant_type: 'refresh_token',
    client_id: CLIENT_ID,
    refresh_token: session.refreshToken,
  });
  const response = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  if (!response.ok) return null;
  const next = toSession(await response.json());
  saveSession(next);
  return next;
}

export function saveSession(session: Session): void {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function loadSession(): Session | null {
  const raw = sessionStorage.getItem(SESSION_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Session;
  } catch {
    return null;
  }
}

export function logout(session: Session | null): void {
  sessionStorage.removeItem(SESSION_KEY);
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    post_logout_redirect_uri: window.location.origin,
  });
  if (session?.refreshToken) params.set('refresh_token', session.refreshToken);
  window.location.assign(`${LOGOUT_ENDPOINT}?${params.toString()}`);
}
