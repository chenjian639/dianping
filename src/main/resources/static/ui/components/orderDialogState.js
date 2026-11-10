import { reactive } from '../vendor/vue.esm-browser.prod.js';
import { api } from '../core/api.js';

// 订单详情弹窗状态：买家/商家两种视角。
// openOrder(id, merchant, onRefresh) 拉取详情并展示；onRefresh 在订单状态变化后刷新列表。
export const orderDialog = reactive({
  open: false,
  order: null,
  merchant: false,
  paymentInfo: '',
  busy: false,
  error: '',
  _onRefresh: null,
});

export function closeOrderDialog() {
  orderDialog.open = false;
  orderDialog.order = null;
  orderDialog.paymentInfo = '';
  orderDialog.error = '';
  orderDialog._onRefresh = null;
}

export async function openOrder(id, merchant, onRefresh) {
  closeOrderDialog();
  orderDialog.merchant = merchant;
  orderDialog._onRefresh = onRefresh || null;
  orderDialog.open = true;
  orderDialog.busy = true;
  try {
    orderDialog.order = merchant ? await api.merchantOrderDetail(id) : await api.orderDetail(id);
  } catch (e) {
    closeOrderDialog();
    throw e;
  } finally {
    orderDialog.busy = false;
  }
}

// 订单操作：payments=创建支付单、cancel=取消、refund=退款、receive=确认收货、ship=发货。
export async function runOrderAction(action) {
  const order = orderDialog.order;
  if (!order) return;
  const id = order.id;
  orderDialog.busy = true;
  orderDialog.error = '';
  try {
    if (action === 'payments') {
      const payment = await api.createPayment(id);
      orderDialog.paymentInfo =
        '支付单 #' + payment.id + '，金额 ¥' + Number(payment.amount).toFixed(2) +
        '。请由管理员在「支付演示」中确认模拟付款，无真实扣款。';
      return;
    }
    if (action === 'cancel') await api.cancelOrder(id);
    if (action === 'refund') await api.refundOrder(id);
    if (action === 'receive') await api.receiveOrder(id);
    if (action === 'ship') await api.shipOrder(id);
    orderDialog.order = orderDialog.merchant ? await api.merchantOrderDetail(id) : await api.orderDetail(id);
    if (typeof orderDialog._onRefresh === 'function') await orderDialog._onRefresh();
  } catch (e) {
    orderDialog.error = e.message;
  } finally {
    orderDialog.busy = false;
  }
}
