import { reactive } from '../vendor/vue.esm-browser.prod.js';
import { ROLE_LABELS } from './util.js';

const TOKEN_KEY = 'trade-token';

// 会话状态：JWT 与解析出的 claims，持久化到 sessionStorage。
export const session = reactive({ token: null, claims: {}, user: null });

function decodeToken(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      Array.from(atob(payload), c => '%' + c.charCodeAt(0).toString(16).padStart(2, '0')).join('')
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export function initSession() {
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (!token) return;
  const claims = decodeToken(token);
  if (claims) {
    session.token = token;
    session.claims = claims;
  } else {
    sessionStorage.removeItem(TOKEN_KEY);
  }
}

export function setToken(token) {
  const claims = decodeToken(token);
  if (!claims) throw new Error('登录凭证无效');
  session.token = token;
  session.claims = claims;
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearSession() {
  session.token = null;
  session.claims = {};
  session.user = null;
  sessionStorage.removeItem(TOKEN_KEY);
}

export const isLoggedIn = () => Boolean(session.token);
export const currentRole = () => session.claims.role || '';
export const roleLabel = () => ROLE_LABELS[session.claims.role] || '用户';
