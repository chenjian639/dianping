import { reactive, onMounted } from '../vendor/vue.esm-browser.prod.js';
import { api } from '../core/api.js';
import { notify } from '../core/notify.js';
import { openOrder } from '../components/orderDialogState.js';
import { orderState, money } from '../core/util.js';

// 我的订单 / 商家待处理订单共用列表视图。
export default {
  template: `
    <div>
      <h2>{{ merchant ? '待处理订单' : '我的订单' }}</h2>
      <div v-if="loading" class="empty">正在加载订单…</div>
      <div v-else-if="!records.length" class="empty">暂无订单。</div>
      <template v-else>
        <div v-for="order in records" :key="order.id" class="row">
          <div>
            <h3>订单 #{{ order.id }} <span class="pill">{{ orderState(order.status) }}</span></h3>
            <p>{{ money(order.totalAmount) }} · {{ order.createdAt }}</p>
          </div>
          <button type="button" @click="view(order)">查看订单</button>
        </div>
        <div class="actions">
          <button type="button" :disabled="page === 1" @click="turn(-1)">上一页</button>
          <button type="button" :disabled="!hasNext" @click="turn(1)">下一页</button>
        </div>
      </template>
    </div>
  `,
  props: { merchant: { type: Boolean, default: false } },
  setup(props) {
    const state = reactive({ page: 1, loading: true, records: [], hasNext: false });

    const load = async () => {
      state.loading = true;
      try {
        const result = props.merchant
          ? await api.merchantOrders(state.page, 20)
          : await api.orders(state.page, 20);
        state.records = result.records || [];
        state.hasNext = result.hasNext ?? state.page * 20 < Number(result.total || 0);
      } catch (e) {
        notify(e.message, true);
      } finally {
        state.loading = false;
      }
    };

    const turn = delta => {
      state.page += delta;
      load();
    };

    const view = order => {
      openOrder(order.id, props.merchant, load).catch(e => notify(e.message, true));
    };

    onMounted(load);
    return { ...toRefs(state), orderState, money, turn, view };
  },
};

import { toRefs } from '../vendor/vue.esm-browser.prod.js';
