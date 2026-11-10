import { ref, watch, computed } from '../vendor/vue.esm-browser.prod.js';
import { orderDialog, closeOrderDialog, runOrderAction } from './orderDialogState.js';
import { ORDER_STATES, money } from '../core/util.js';

// 订单详情弹窗：展示金额/收货信息，并按角色与状态提供操作按钮。
export default {
  template: `
    <dialog ref="dialogEl" @close="syncClose">
      <div class="dialog-head">
        <h2>订单 #{{ state.order ? state.order.id : '' }}</h2>
        <button type="button" aria-label="关闭" @click="close">×</button>
      </div>
      <div v-if="state.order">
        <p class="pill">{{ stateText(state.order.status) }}</p>
        <p>支付截止：{{ state.order.expiresAt }}</p>
        <p>收货信息：{{ state.order.shippingAddress }}</p>
        <p v-for="item in state.order.items" :key="item.id">{{ item.dishName }} × {{ item.quantity }}</p>
        <p>商品 {{ money(state.order.goodsAmount) }} · 运费 {{ money(state.order.freightAmount) }}</p>
        <p>应付 {{ money(state.order.totalAmount) }}</p>
        <div class="actions">
          <button v-for="action in availableActions" :key="action.key" type="button"
                  :disabled="state.busy" @click="run(action.key)">{{ action.label }}</button>
        </div>
        <p v-if="state.paymentInfo" role="status">{{ state.paymentInfo }}</p>
        <p v-if="state.error" role="alert" class="hint form-error">{{ state.error }}</p>
      </div>
      <div v-else-if="state.open" class="empty">正在加载订单…</div>
    </dialog>
  `,
  setup() {
    const dialogEl = ref(null);
    watch(
      () => orderDialog.open,
      open => {
        const el = dialogEl.value;
        if (!el) return;
        if (open && !el.open) el.showModal();
        if (!open && el.open) el.close();
      }
    );

    const syncClose = () => {
      if (orderDialog.open) closeOrderDialog();
    };

    const stateText = status => ORDER_STATES[status] || '未知状态';

    // 买家：待支付可创建支付单/取消，已支付可退款，已发货可确认收货；商家：已支付可发货。
    const BUYER_ACTIONS = {
      0: [
        { key: 'payments', label: '创建支付单' },
        { key: 'cancel', label: '取消订单' },
      ],
      1: [{ key: 'refund', label: '整单退款' }],
      3: [{ key: 'receive', label: '确认收货' }],
    };

    const availableActions = computed(() => {
      if (!orderDialog.order) return [];
      if (orderDialog.merchant) {
        return orderDialog.order.status === 1 ? [{ key: 'ship', label: '发货' }] : [];
      }
      return BUYER_ACTIONS[orderDialog.order.status] || [];
    });

    return { state: orderDialog, close: closeOrderDialog, dialogEl, syncClose, stateText, availableActions, run: runOrderAction, money };
  },
};
