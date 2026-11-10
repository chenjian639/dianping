// 共享常量与小工具：订单状态文案、角色文案、金额格式、随机请求号。
export const ORDER_STATES = ['待支付', '已支付', '历史制作中', '已发货', '已完成', '已取消', '已退款'];

export const ROLE_LABELS = { USER: '买家', MERCHANT: '商家', ADMIN: '管理员' };

export function money(value) {
  return '¥' + Number(value ?? 0).toFixed(2);
}

export function orderState(status) {
  return ORDER_STATES[status] ?? '未知状态';
}

export function randomId() {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID();
  return Array.from(window.crypto.getRandomValues(new Uint8Array(16)), n => n.toString(16).padStart(2, '0')).join('');
}
