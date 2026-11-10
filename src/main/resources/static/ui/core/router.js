import { reactive } from '../vendor/vue.esm-browser.prod.js';

// hash 路由：#/browse、#/stall/{id}、#/cart、#/orders、#/merchant-orders、#/merchant、#/admin
export const route = reactive({ name: 'browse', params: {} });

const PUBLIC_VIEWS = new Set(['browse']);

export function parseHash() {
  const hash = location.hash.replace(/^#\/?/, '');
  const [rawName, ...rest] = hash.split('/').filter(Boolean);
  const name = rawName || 'browse';
  const params = {};
  if (name === 'stall' && rest[0]) params.id = rest[0];
  route.name = name;
  route.params = params;
}

export function navigate(name, params = {}) {
  const suffix = params.id != null ? '/' + params.id : '';
  const target = '#/' + name + suffix;
  if (location.hash === target) parseHash();
  else location.hash = target;
}

export function requiresAuth(name) {
  return !PUBLIC_VIEWS.has(name);
}

export function startRouter() {
  window.addEventListener('hashchange', parseHash);
  parseHash();
}
