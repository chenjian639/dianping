import { reactive } from '../vendor/vue.esm-browser.prod.js';

// 全局通知条状态（对应 #notice 区块）。
export const notice = reactive({ text: '', error: false });

export function notify(text, error = false) {
  notice.text = text || '';
  notice.error = !!error;
}
