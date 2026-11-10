import { reactive } from '../vendor/vue.esm-browser.prod.js';

// 全局弹窗状态：通用表单弹窗 + 登录弹窗。
// 字段定义：{ name, label, type, value, attrs }，attrs 原样绑定到 input。
export const formModal = reactive({
  open: false,
  title: '',
  fields: [],
  submitLabel: '保存',
  hint: '',
  busy: false,
  error: '',
  _onSubmit: null,
});

export function openForm({ title, fields, submitLabel = '保存', hint = '', onSubmit }) {
  formModal.title = title;
  formModal.fields = fields.map(field => ({ ...field }));
  formModal.submitLabel = submitLabel;
  formModal.hint = hint;
  formModal.error = '';
  formModal.busy = false;
  formModal._onSubmit = onSubmit;
  formModal.open = true;
}

export function closeForm() {
  formModal.open = false;
  formModal._onSubmit = null;
}

export async function submitForm(values) {
  if (typeof formModal._onSubmit !== 'function') return;
  formModal.busy = true;
  formModal.error = '';
  try {
    await formModal._onSubmit(values);
    closeForm();
  } catch (error) {
    formModal.error = (error && error.message) || String(error);
  } finally {
    formModal.busy = false;
  }
}

export const loginModal = reactive({ open: false });

export function openLogin() {
  loginModal.open = true;
}

export function closeLogin() {
  loginModal.open = false;
}
