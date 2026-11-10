import { reactive, ref } from '../vendor/vue.esm-browser.prod.js';
import { api } from '../core/api.js';

// 管理员支付演示：按支付单编号与金额确认模拟付款，无真实扣款。
export default {
  template: `
    <div>
      <h2>模拟支付回调</h2>
      <p>按买家提供的支付单编号与金额确认付款，无真实扣款。重复提交同一渠道流水不会重复支付。</p>
      <form class="admin-form" @submit.prevent="submit">
        <label>支付单编号<input v-model.trim="form.id" name="id" type="number" required min="1"></label>
        <label>渠道流水号<input v-model.trim="form.channelReference" name="channelReference" type="text" required maxlength="64"></label>
        <label>支付金额<input v-model.trim="form.amount" name="amount" type="number" required min="0.01" step="0.01"></label>
        <button class="primary" type="submit" :disabled="busy">确认模拟付款</button>
        <p v-if="result" role="status">{{ result }}</p>
        <p v-if="error" role="alert" class="hint form-error">{{ error }}</p>
      </form>
    </div>
  `,
  setup() {
    const form = reactive({ id: '', channelReference: '', amount: '' });
    const busy = ref(false);
    const result = ref('');
    const error = ref('');

    const submit = async () => {
      busy.value = true;
      error.value = '';
      result.value = '';
      try {
        await api.mockPaymentCallback(Number(form.id), {
          channelReference: form.channelReference,
          amount: Number(form.amount),
        });
        result.value = '付款已确认';
        form.id = '';
        form.channelReference = '';
        form.amount = '';
      } catch (e) {
        error.value = e.message;
      } finally {
        busy.value = false;
      }
    };

    return { form, busy, result, error, submit };
  },
};
