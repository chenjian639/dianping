import { reactive, onMounted } from '../vendor/vue.esm-browser.prod.js';
import { api } from '../core/api.js';
import { notify } from '../core/notify.js';
import { navigate } from '../core/router.js';
import { money } from '../core/util.js';
import { isLoggedIn } from '../core/store.js';
import { openLogin } from '../core/modal.js';

// 商家详情：商品列表 + 加入购物车 + 评价摘要与最新评价。
export default {
  template: `
    <div>
      <button type="button" class="back" @click="back">← 返回商家列表</button>
      <template v-if="detail">
        <h2>{{ detail.name }}</h2>
        <p>{{ detail.address || '位置待维护' }}</p>
        <p class="hint">每个订单购买同一商家的商品，当前免运费。</p>
        <div v-if="!products.length" class="empty">暂无上架商品。</div>
        <div v-for="dish in products" :key="dish.id" class="row">
          <div>
            <h3>{{ dish.name }}</h3>
            <p>{{ dish.description }}</p>
            <span class="price">{{ money(dish.price) }}</span> · 库存 {{ dish.stock }}
          </div>
          <button type="button" :disabled="busyId === dish.id || !dish.stock || dish.status !== 1"
                  @click="add(dish)">加入购物车</button>
        </div>
        <h3>评价（{{ summary ? summary.reviewCount : 0 }} 条 · 平均 {{ summary ? summary.averageRating : '-' }}）</h3>
        <div v-if="!reviews.length" class="empty">暂无评价。</div>
        <div v-for="review in reviews" :key="review.id" class="row">
          <div>
            <h3>{{ review.nickname }} · {{ review.rating }} 分</h3>
            <p>{{ review.content }}</p>
            <p class="hint">{{ review.createdAt }}</p>
          </div>
        </div>
        <div class="actions">
          <button type="button" :disabled="reviewPage === 1" @click="turnReview(-1)">上一页</button>
          <button type="button" :disabled="!reviewHasNext" @click="turnReview(1)">下一页</button>
        </div>
      </template>
      <div v-else class="empty">正在加载商家…</div>
    </div>
  `,
  props: { id: { type: String, required: true } },
  setup(props) {
    const state = reactive({
      detail: null,
      products: [],
      summary: null,
      reviews: [],
      reviewPage: 1,
      reviewHasNext: false,
      busyId: null,
    });

    const loadReviews = async () => {
      const result = await api.restaurantReviews(props.id, { page: state.reviewPage, pageSize: 5 });
      state.reviews = result.records || [];
      state.reviewHasNext = Boolean(result.hasNext);
    };

    const load = async () => {
      try {
        const [detail, products, summary] = await Promise.all([
          api.restaurant(props.id),
          api.restaurantProducts(props.id),
          api.reviewSummary(props.id).catch(() => null),
        ]);
        state.detail = detail;
        state.products = products || [];
        state.summary = summary;
        await loadReviews();
      } catch (e) {
        notify(e.message, true);
      }
    };

    const add = async dish => {
      if (!isLoggedIn()) {
        notify('请先登录后再加购', true);
        openLogin();
        return;
      }
      state.busyId = dish.id;
      try {
        await api.addCartItem(dish.id, 1);
        notify('已加入购物车');
      } catch (e) {
        notify(e.message, true);
      } finally {
        state.busyId = null;
      }
    };

    const back = () => navigate('browse');
    const turnReview = delta => {
      state.reviewPage += delta;
      loadReviews().catch(e => notify(e.message, true));
    };

    onMounted(load);
    return { ...toRefs(state), money, add, back, turnReview };
  },
};

import { toRefs } from '../vendor/vue.esm-browser.prod.js';
