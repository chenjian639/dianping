import { reactive, onMounted } from '../vendor/vue.esm-browser.prod.js';
import { api } from '../core/api.js';
import { notify } from '../core/notify.js';
import { navigate } from '../core/router.js';
import { money, randomId } from '../core/util.js';
import { openForm } from '../core/modal.js';

// 购物车：改数量、移除、填写地址下单（requestId 幂等，失败重试不重复下单）。
const PURCHASE_KEY = 'trade-purchase';

export default {
  template: `
    <div>
      <div v-if="!items.length" class="empty">购物车为空，先到「发现商家」选择商品。</div>
      <template v-else>
        <div v-for="item in items" :key="item.id" class="row">
          <div>
            <h3>{{ item.dishName }}</h3>
            <p>{{ money(item.price) }} × {{ item.quantity }}</p>
          </div>
          <div class="actions">
            <button type="button" @click="editQuantity(item)">修改数量</button>
            <button type="button" @click="remove(item)">移除</button>
          </div>
        </div>
        <div class="row">
          <strong>合计 {{ money(total) }}</strong>
          <button class="primary" type="button" @click="checkout">填写地址并下单</button>
        </div>
      </template>
    </div>
  `,
  setup() {
    const state = reactive({ items: [] });

    const load = async () => {
      try {
        state.items = (await api.cart()) || [];
      } catch (e) {
        notify(e.message, true);
      }
    };

    const editQuantity = item => {
      openForm({
        title: '修改数量',
        fields: [{ name: 'quantity', label: '份数', type: 'number', value: item.quantity, attrs: { min: 1, required: true } }],
        submitLabel: '保存',
        onSubmit: async values => {
          await api.updateCartItem(item.id, Number(values.quantity));
          await load();
          notify('操作成功');
        },
      });
    };

    const remove = async item => {
      try {
        await api.deleteCartItem(item.id);
        await load();
      } catch (e) {
        notify(e.message, true);
      }
    };

    const checkout = () => {
      let requestId = sessionStorage.getItem(PURCHASE_KEY);
      if (!requestId) {
        requestId = randomId();
        sessionStorage.setItem(PURCHASE_KEY, requestId);
      }
      openForm({
        title: '确认订单',
        fields: [
          { name: 'shippingAddress', label: '收件人、电话与收货地址', type: 'text', value: '', attrs: { required: true, maxlength: 500 } },
        ],
        hint: '最终金额以服务端下单结果为准，当前免运费。',
        submitLabel: '提交订单',
        onSubmit: async values => {
          await api.createOrder(requestId, values.shippingAddress);
          sessionStorage.removeItem(PURCHASE_KEY);
          notify('下单成功');
          navigate('orders');
        },
      });
    };

    const total = () => state.items.reduce((sum, item) => sum + Number(item.price) * item.quantity, 0);

    onMounted(load);
    return { ...toRefs(state), money, editQuantity, remove, checkout, total };
  },
};

import { toRefs } from '../vendor/vue.esm-browser.prod.js';
