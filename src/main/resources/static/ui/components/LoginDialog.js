import { ref, reactive, watch } from '../vendor/vue.esm-browser.prod.js';
import { api } from '../core/api.js';
import { loginModal, closeLogin } from '../core/modal.js';
import { setToken } from '../core/store.js';
import { notify } from '../core/notify.js';

// 登录/注册弹窗：注册成功后自动登录（公开注册只创建普通用户）。
export default {
  template: `
    <dialog ref="dialogEl" @close="syncClose">
      <div class="dialog-head">
        <h2>{{ register ? '创建买家账号' : '欢迎回来' }}</h2>
        <button type="button" aria-label="关闭" @click="closeLogin">×</button>
      </div>
      <form @submit.prevent="submit">
        <label>用户名<input v-model.trim="form.username" name="username" type="text" required
               minlength="3" maxlength="20" autocomplete="username"></label>
        <label>密码<input v-model="form.password" name="password" type="password" required
               minlength="8" maxlength="30" autocomplete="current-password"></label>
        <template v-if="register">
          <label>昵称<input v-model.trim="form.nickname" name="nickname" type="text" required></label>
          <label>手机号<input v-model.trim="form.phone" name="phone" type="tel" required pattern="1[0-9]{10}"></label>
        </template>
        <p v-if="error" class="hint form-error" role="alert">{{ error }}</p>
        <button class="primary" type="submit" :disabled="busy">{{ register ? '注册并登录' : '登录' }}</button>
        <button type="button" class="switch-auth" @click="register = !register">
          {{ register ? '已有账号，去登录' : '还没有账号？注册' }}
        </button>
      </form>
    </dialog>
  `,
  setup() {
    const dialogEl = ref(null);
    const register = ref(false);
    const busy = ref(false);
    const error = ref('');
    const form = reactive({ username: '', password: '', nickname: '', phone: '' });

    watch(
      () => loginModal.open,
      open => {
        const el = dialogEl.value;
        if (!el) return;
        if (open && !el.open) {
          register.value = false;
          error.value = '';
          Object.assign(form, { username: '', password: '', nickname: '', phone: '' });
          el.showModal();
        }
        if (!open && el.open) el.close();
      }
    );

    const syncClose = () => {
      if (loginModal.open) closeLogin();
    };

    const submit = async () => {
      busy.value = true;
      error.value = '';
      try {
        if (register.value) {
          await api.register({ nickname: '', phone: '', ...pick(form, ['username', 'password', 'nickname', 'phone']) });
        }
        const result = await api.login({ username: form.username, password: form.password });
        setToken(result.token);
        closeLogin();
        notify('登录成功');
      } catch (e) {
        error.value = e.message;
      } finally {
        busy.value = false;
      }
    };

    return { dialogEl, register, busy, error, form, loginModal, closeLogin, syncClose, submit };
  },
};

function pick(source, keys) {
  return keys.reduce((acc, key) => {
    acc[key] = source[key];
    return acc;
  }, {});
}
