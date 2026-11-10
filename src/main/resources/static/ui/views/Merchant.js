import { reactive, ref, onMounted, watch, toRefs } from '../vendor/vue.esm-browser.prod.js';
import { api } from '../core/api.js';
import { notify } from '../core/notify.js';
import { openForm } from '../core/modal.js';
import { money, randomId } from '../core/util.js';
import { session } from '../core/store.js';
import { navigate } from '../core/router.js';

// 商家工作台：商品管理（新增/编辑/上下架/删除）、库存调整与流水、商家资料与营业状态。
export default {
  template: `
    <div>
      <div class="toolbar">
        <button type="button" @click="goOrders">处理订单</button>
        <button type="button" @click="create">新增商品</button>
        <button type="button" @click="editProfile">编辑商家资料</button>
        <button type="button" @click="toggleRestaurant">{{ restaurant && restaurant.status ? '暂停营业' : '恢复营业' }}</button>
      </div>
      <div v-if="loading" class="empty">正在加载商品…</div>
      <div v-else-if="!products.length" class="empty">新增商品后，买家即可在商家中查看。</div>
      <template v-else>
        <div v-for="dish in products" :key="dish.id" class="row">
          <div>
            <h3>{{ dish.name }}</h3>
            <p>{{ money(dish.price) }} · 库存 {{ dish.stock }} · {{ dish.status ? '已上架' : '已下架' }}</p>
          </div>
          <div class="actions">
            <button type="button" :disabled="busy" @click="adjust(dish)">调整库存</button>
            <button type="button" :disabled="busy" @click="showHistory(dish)">库存流水</button>
            <button type="button" :disabled="busy" @click="edit(dish)">编辑</button>
            <button type="button" :disabled="busy" @click="toggle(dish)">{{ dish.status ? '下架' : '上架' }}</button>
            <button type="button" :disabled="busy" @click="remove(dish)">删除</button>
          </div>
        </div>
      </template>
      <dialog ref="historyEl" @close="historyOpen = false">
        <div class="dialog-head">
          <h2>库存流水 · {{ historyName }}</h2>
          <button type="button" aria-label="关闭" @click="closeHistory">×</button>
        </div>
        <div v-if="movements.length">
          <div v-for="movement in movements" :key="movement.id" class="row">
            <div>
              <p>变动 {{ movement.delta }} · {{ movement.reason }}</p>
              <p class="hint">{{ movement.created_at || movement.createdAt }} · 单号 {{ movement.business_key }}</p>
            </div>
          </div>
        </div>
        <div v-else class="empty">暂无流水记录。</div>
      </dialog>
    </div>
  `,
  setup() {
    const state = reactive({ products: [], restaurant: null, loading: true, busy: false, movements: [], historyName: '', historyOpen: false });
    const historyEl = ref(null);

    const restaurantId = () => session.claims.restaurantId;

    const load = async () => {
      state.loading = true;
      try {
        const result = await api.merchantProducts(1, 100);
        state.products = result.records || [];
        if (restaurantId()) state.restaurant = await api.restaurant(restaurantId()).catch(() => null);
      } catch (e) {
        notify(e.message, true);
      } finally {
        state.loading = false;
      }
    };

    const goOrders = () => navigate('merchant-orders');

    const create = () => {
      openForm({
        title: '新增商品',
        fields: [
          { name: 'name', label: '商品名称', type: 'text', attrs: { required: true } },
          { name: 'description', label: '描述', type: 'text' },
          { name: 'price', label: '单价', type: 'number', attrs: { required: true, min: '0.01', step: '0.01' } },
          { name: 'stock', label: '库存', type: 'number', value: 0, attrs: { required: true, min: 0, step: 1 } },
        ],
        onSubmit: async values => {
          await api.createProduct({ ...values, price: Number(values.price), stock: Number(values.stock) });
          await load();
          notify('操作成功');
        },
      });
    };

    const edit = dish => {
      openForm({
        title: '编辑商品',
        fields: [
          { name: 'name', label: '商品名称', type: 'text', value: dish.name, attrs: { required: true } },
          { name: 'description', label: '描述', type: 'text', value: dish.description || '' },
          { name: 'price', label: '单价', type: 'number', value: dish.price, attrs: { required: true, min: '0.01', step: '0.01' } },
        ],
        onSubmit: async values => {
          await api.updateProduct(dish.id, { ...values, price: Number(values.price) });
          await load();
          notify('操作成功');
        },
      });
    };

    const toggle = async dish => {
      state.busy = true;
      try {
        await api.updateProductStatus(dish.id, dish.status ? 0 : 1);
        await load();
      } catch (e) {
        notify(e.message, true);
      } finally {
        state.busy = false;
      }
    };

    const remove = async dish => {
      if (!window.confirm('确认删除商品「' + dish.name + '」？删除后买家不可再见。')) return;
      state.busy = true;
      try {
        await api.deleteProduct(dish.id);
        await load();
        notify('操作成功');
      } catch (e) {
        notify(e.message, true);
      } finally {
        state.busy = false;
      }
    };

    const adjust = dish => {
      openForm({
        title: '调整库存',
        fields: [{ name: 'delta', label: '增加数量（负数为减少）', type: 'number', attrs: { required: true, step: 1 } }],
        onSubmit: async values => {
          await api.adjustInventory(dish.id, randomId(), Number(values.delta));
          await load();
          notify('操作成功');
        },
      });
    };

    const showHistory = async dish => {
      state.historyName = dish.name;
      state.movements = [];
      state.historyOpen = true;
      const el = historyEl.value;
      if (el && !el.open) el.showModal();
      try {
        state.movements = (await api.inventoryHistory(dish.id)) || [];
      } catch (e) {
        notify(e.message, true);
      }
    };

    const closeHistory = () => {
      const el = historyEl.value;
      if (el && el.open) el.close();
      state.historyOpen = false;
    };

    const editProfile = () => {
      const current = state.restaurant || {};
      openForm({
        title: '编辑商家资料',
        fields: [
          { name: 'name', label: '商家名称', type: 'text', value: current.name || '', attrs: { required: true, maxlength: 100 } },
          { name: 'description', label: '商家简介', type: 'text', value: current.description || '', attrs: { maxlength: 255 } },
          { name: 'address', label: '商家地址', type: 'text', value: current.address || '', attrs: { maxlength: 255 } },
        ],
        onSubmit: async values => {
          await api.updateRestaurant(values);
          if (restaurantId()) state.restaurant = await api.restaurant(restaurantId());
          notify('操作成功');
        },
      });
    };

    const toggleRestaurant = async () => {
      const next = state.restaurant && state.restaurant.status ? 0 : 1;
      state.busy = true;
      try {
        await api.updateRestaurantStatus(next);
        if (restaurantId()) state.restaurant = await api.restaurant(restaurantId());
        notify(next ? '已恢复营业' : '已暂停营业');
      } catch (e) {
        notify(e.message, true);
      } finally {
        state.busy = false;
      }
    };

    watch(
      () => state.historyOpen,
      open => {
        const el = historyEl.value;
        if (!el) return;
        if (open && !el.open) el.showModal();
        if (!open && el.open) el.close();
      }
    );

    onMounted(load);
    return { ...toRefs(state), historyEl, money, goOrders, create, edit, toggle, remove, adjust, showHistory, closeHistory, editProfile, toggleRestaurant };
  },
};
