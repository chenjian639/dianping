import { session, clearSession } from './store.js';

// 统一请求封装：注入 Bearer token，解包 Result{code,msg,data}。
async function request(path, { method = 'GET', body } = {}) {
  const headers = {};
  if (session.token) headers.Authorization = 'Bearer ' + session.token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new Error('网络异常，请稍后重试');
  }
  let result = null;
  try {
    result = await response.json();
  } catch {
    // 非 JSON 响应，按状态码报错
  }
  if (response.status === 401) clearSession();
  if (!response.ok || !result || result.code !== 0) {
    throw new Error((result && result.msg) || '请求失败 (' + response.status + ')');
  }
  return result.data;
}

function query(params) {
  const search = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, value);
  });
  const text = search.toString();
  return text ? '?' + text : '';
}

// 后端接口清单：用户、商家/商品公共接口、购物车、订单、支付、商家工作台。
export const api = {
  // 用户
  register: data => request('/users/register', { method: 'POST', body: data }),
  login: data => request('/users/login', { method: 'POST', body: data }),
  currentUser: () => request('/users/me'),

  // 商家与商品（公共）
  searchRestaurants: params => request('/restaurants/search' + query(params)),
  restaurant: id => request('/restaurants/' + id),
  restaurantProducts: id => request('/restaurants/' + id + '/products'),
  restaurantReviews: (id, params) => request('/restaurants/' + id + '/reviews' + query(params)),
  reviewSummary: id => request('/restaurants/' + id + '/review-summary'),

  // 购物车
  cart: () => request('/cart'),
  addCartItem: (dishId, quantity) => request('/cart/items', { method: 'POST', body: { dishId, quantity } }),
  updateCartItem: (id, quantity) => request('/cart/items/' + id, { method: 'PUT', body: { quantity } }),
  deleteCartItem: id => request('/cart/items/' + id, { method: 'DELETE' }),

  // 订单（买家）
  createOrder: (requestId, shippingAddress) =>
    request('/orders/create', { method: 'POST', body: { requestId, shippingAddress } }),
  orders: (page, pageSize) => request('/orders' + query({ page, pageSize })),
  orderDetail: id => request('/orders/' + id),
  receiveOrder: id => request('/orders/' + id + '/receive', { method: 'PUT' }),
  cancelOrder: id => request('/orders/' + id + '/cancel', { method: 'PUT' }),

  // 支付（模拟支付无真实扣款）
  createPayment: orderId => request('/orders/' + orderId + '/payments', { method: 'POST' }),
  refundOrder: orderId => request('/orders/' + orderId + '/refund', { method: 'POST' }),
  mockPaymentCallback: (id, body) =>
    request('/admin/mock-payments/' + id + '/callback', { method: 'POST', body }),

  // 商家工作台：订单
  merchantOrders: (page, pageSize, status) => request('/merchant/orders' + query({ page, pageSize, status })),
  merchantOrderDetail: id => request('/merchant/orders/' + id),
  shipOrder: id => request('/merchant/orders/' + id + '/ship', { method: 'PUT' }),

  // 商家工作台：商品与库存
  merchantProducts: (page, pageSize, status) => request('/merchant/products' + query({ page, pageSize, status })),
  createProduct: data => request('/merchant/products', { method: 'POST', body: data }),
  updateProduct: (id, data) => request('/merchant/products/' + id, { method: 'PUT', body: data }),
  updateProductStatus: (id, status) => request('/merchant/products/' + id + '/status', { method: 'PUT', body: { status } }),
  deleteProduct: id => request('/merchant/products/' + id, { method: 'DELETE' }),
  adjustInventory: (id, requestId, delta) =>
    request('/merchant/products/' + id + '/inventory', { method: 'POST', body: { requestId, delta } }),
  inventoryHistory: id => request('/merchant/products/' + id + '/inventory'),

  // 商家工作台：商家资料
  updateRestaurant: data => request('/merchant/restaurant', { method: 'PUT', body: data }),
  updateRestaurantStatus: status => request('/merchant/restaurant/status', { method: 'PUT', body: { status } }),
};
