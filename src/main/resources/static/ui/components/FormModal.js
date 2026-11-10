import { ref, watch } from '../vendor/vue.esm-browser.prod.js';
import { formModal, closeForm, submitForm } from '../core/modal.js';

// 通用表单弹窗：由 openForm() 驱动，字段来自调用方定义。
export default {
  template: `
    <dialog ref="dialogEl" @close="syncClose">
      <div class="dialog-head">
        <h2>{{ formModal.title }}</h2>
        <button type="button" aria-label="关闭" @click="closeForm">×</button>
      </div>
      <form @submit.prevent="onSubmit">
        <label v-for="field in formModal.fields" :key="field.name">
          {{ field.label }}
          <input :name="field.name" :type="field.type || 'text'" :value="field.value || ''"
                 v-bind="field.attrs || {}">
        </label>
        <p v-if="formModal.hint" class="hint">{{ formModal.hint }}</p>
        <p v-if="formModal.error" class="hint form-error" role="alert">{{ formModal.error }}</p>
        <button class="primary" type="submit" :disabled="formModal.busy">{{ formModal.submitLabel }}</button>
      </form>
    </dialog>
  `,
  setup() {
    const dialogEl = ref(null);
    watch(
      () => formModal.open,
      open => {
        const el = dialogEl.value;
        if (!el) return;
        if (open && !el.open) el.showModal();
        if (!open && el.open) el.close();
      }
    );
    const syncClose = () => {
      if (formModal.open) closeForm();
    };
    const onSubmit = event => {
      const values = Object.fromEntries(new FormData(event.target));
      submitForm(values);
    };
    return { formModal, closeForm, dialogEl, syncClose, onSubmit };
  },
};
