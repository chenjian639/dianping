import { reactive, onMounted } from '../vendor/vue.esm-browser.prod.js';
import { api } from '../core/api.js';
import { notify } from '../core/notify.js';
import { navigate } from '../core/router.js';

// 发现商家：关键词 + 排序搜索，分页浏览。
export default {
  template: `
    <div>
      <form class="toolbar" @submit.prevent="search">
        <input v-model.trim="keyword" name="keyword" aria-label="搜索商家" placeholder="搜索商家名称">
        <select v-model="sort" name="sort" aria-label="排序">
          <option value="latest">最新商家</option>
          <option value="rating">评分优先</option>
          <option value="reviews">评价最多</option>
        </select>
        <button class="primary" type="submit">查找商家</button>
      </form>
      <div v-if="loading" class="empty">正在加载商家…</div>
      <div v-else-if="!records.length" class="empty">没有找到商家，试试其他关键词。</div>
      <template v-else>
        <div class="grid">
          <article v-for="item in records" :key="item.id" class="stall">
            <div class="mark">商</div>
            <h3>{{ item.name }}</h3>
            <p>{{ item.description || '查看商品，便捷下单' }}</p>
            <p>{{ item.address || '位置待维护' }}</p>
            <p>评分 {{ item.averageRating }} · {{ item.reviewCount }} 条评价</p>
            <button type="button" @click="open(item.id)">查看商品 →</button>
          </article>
        </div>
        <div class="actions">
          <button type="button" :disabled="page === 1" @click="turn(-1)">上一页</button>
          <button type="button" :disabled="!hasNext" @click="turn(1)">下一页</button>
        </div>
      </template>
    </div>
  `,
  setup() {
    const state = reactive({ keyword: '', sort: 'latest', page: 1, loading: true, records: [], hasNext: false });

    const load = async () => {
      state.loading = true;
      try {
        const result = await api.searchRestaurants({
          keyword: state.keyword,
          sort: state.sort,
          page: state.page,
          pageSize: 12,
        });
        state.records = result.records || [];
        state.hasNext = Boolean(result.hasNext);
      } catch (e) {
        notify(e.message, true);
      } finally {
        state.loading = false;
      }
    };

    const search = () => {
      state.page = 1;
      load();
    };

    const turn = delta => {
      state.page += delta;
      load();
    };

    const open = id => navigate('stall', { id });

    onMounted(load);
    return { ...toRefs(state), search, turn, open };
  },
};

// 将 reactive 状态展开为模板可直接绑定的引用。
import { toRefs } from '../vendor/vue.esm-browser.prod.js';
