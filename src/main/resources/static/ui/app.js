import { createApp, computed, watch } from './vendor/vue.esm-browser.prod.js';
import { session, initSession, clearSession, currentRole, roleLabel } from './core/store.js';
import { route, startRouter } from './core/router.js';
import { notice, notify } from './core/notify.js';
import { openLogin } from './core/modal.js';
import FormModal from './components/FormModal.js';
import LoginDialog from './components/LoginDialog.js';
import OrderDialog from './components/OrderDialog.js';
import Browse from './views/Browse.js';
import Stall from './views/Stall.js';
import Cart from './views/Cart.js';
import Orders from './views/Orders.js';
import Merchant from './views/Merchant.js';
import Admin from './views/Admin.js';

const VIEWS = {
  browse: Browse,
  stall: Stall,
  cart: Cart,
  orders: Orders,
  'merchant-orders': Orders,
  merchant: Merchant,
  admin: Admin,
};

const VIEW_ROLES = { merchant: 'MERCHANT', 'merchant-orders': 'MERCHANT', admin: 'ADMIN' };
const LOGIN_PROMPT = '请先登录，继续选购商品。';

const App = {
  components: { FormModal, LoginDialog, OrderDialog },
  template: `
    <aside>
      <a class="brand" href="#/browse">◉ 商品交易<span>TRADE STORE</span></a>
      <nav>
        <a v-for="link in navLinks" :key="link.name" :href="'#/' + link.name"
           :class="{ active: route.name === link.name }">{{ link.label }}</a>
      </nav>
      <p class="aside-note">选购商品，查看订单。<br>支付为演示流程，无真实扣款。</p>
    </aside>
    <main>
      <header>
        <span id="identity">{{ identity }}</span>
        <div>
          <button v-if="!session.token" type="button" @click="openLogin">登录 / 注册</button>
          <button v-else type="button" @click="logout">退出登录</button>
        </div>
      </header>
      <section id="intro">
        <p class="eyebrow">商品与订单</p>
        <h1>选购所需商品</h1>
        <p>浏览商家商品，查看订单与物流状态。</p>
      </section>
      <div id="notice" role="status" aria-live="polite" :class="{ error: notice.error }">{{ notice.text }}</div>
      <section id="content" :aria-busy="busyText">
        <div v-if="guardMessage" class="empty">{{ guardMessage }}</div>
        <component v-else :is="activeView" :key="viewKey" v-bind="viewProps"></component>
      </section>
      <footer>商品交易平台 · 支付为流程演示，无真实扣款</footer>
    </main>
    <FormModal></FormModal>
    <LoginDialog></LoginDialog>
    <OrderDialog></OrderDialog>
  `,
  setup() {
    initSession();
    startRouter();

    const navLinks = computed(() => {
      const links = [
        { name: 'browse', label: '发现商家' },
        { name: 'cart', label: '购物车' },
        { name: 'orders', label: '我的订单' },
      ];
      if (currentRole() === 'MERCHANT') {
        links.push({ name: 'merchant-orders', label: '商家订单' });
        links.push({ name: 'merchant', label: '商家工作台' });
      }
      if (currentRole() === 'ADMIN') links.push({ name: 'admin', label: '支付演示' });
      return links;
    });

    const identity = computed(() => (session.token ? '已登录 · ' + roleLabel() : '商品交易服务'));

    // 路由守卫：未登录访问受限页面、角色不符访问专属页面时给出提示。
    const guardMessage = computed(() => {
      const name = route.name;
      if (!VIEWS[name]) return null;
      if (name !== 'browse' && !session.token) return LOGIN_PROMPT;
      const role = VIEW_ROLES[name];
      if (role && session.claims.role !== role) {
        return role === 'ADMIN' ? '此页面仅管理员可用' : '此页面仅商家可用';
      }
      return null;
    });

    // 未登录访问受限页面时自动弹出登录框（与旧版行为一致）。
    watch(guardMessage, message => {
      if (message === LOGIN_PROMPT) openLogin();
    });

    const activeView = computed(() => VIEWS[route.name] || Browse);
    const viewProps = computed(() => {
      if (route.name === 'stall') return { id: String(route.params.id) };
      if (route.name === 'merchant-orders') return { merchant: true };
      return {};
    });

    // 登录态或路由变化时强制重建视图，保证数据重新加载。
    const viewKey = computed(
      () => route.name + ':' + (route.params.id || '') + ':' + (session.token ? 'auth' : 'anon')
    );

    watch(
      () => route.name,
      () => {
        notice.text = '';
        notice.error = false;
      }
    );

    const logout = () => {
      clearSession();
      notify('已退出登录');
      location.hash = '#/browse';
    };

    const busyText = 'false';

    return { session, route, notice, navLinks, identity, guardMessage, activeView, viewProps, viewKey, busyText, openLogin, logout };
  },
};

createApp(App).mount('#app');
