'use strict';
const $ = s => document.querySelector(s);
const content = $('#content'), modal = $('#modal');
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let token = sessionStorage.getItem('trade-token'), claims = {}, page = 1;
const states = ['待支付','已支付','历史制作中','已发货','已完成','已取消','已退款'];
function notify(text, error = false) { $('#notice').textContent = text; $('#notice').className = error ? 'error' : ''; }
async function api(path, method = 'GET', body) {
  const response = await fetch(path, {method, headers:{...(token ? {Authorization:`Bearer ${token}`} : {}), ...(body === undefined ? {} : {'Content-Type':'application/json'})}, body:body === undefined ? undefined : JSON.stringify(body)});
  const result = await response.json();
  if (response.status === 401) { token = null; sessionStorage.removeItem('trade-token'); updateIdentity(); }
  if (!response.ok || result.code !== 0) throw new Error(result.msg || `请求失败 (${response.status})`);
  return result.data;
}
function updateIdentity() {
  try { claims = token ? JSON.parse(decodeURIComponent(Array.from(atob(token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')), c => '%' + c.charCodeAt(0).toString(16).padStart(2,'0')).join(''))) : {}; } catch { token = null; claims = {}; sessionStorage.removeItem('trade-token'); }
  $('#identity').textContent = token ? `已登录 · ${{USER:'买家',MERCHANT:'商家',ADMIN:'管理员'}[claims.role] || '用户'}` : '商品交易服务';
  $('#login').hidden = !!token; $('#logout').hidden = !token;
  const links = [['browse','发现商家'],['cart','购物车'],['orders','我的订单']];
  if (claims.role === 'MERCHANT') links.push(['merchant','商家工作台']);
  if (claims.role === 'ADMIN') links.push(['admin','支付演示']);
  $('#nav').innerHTML = links.map(([id,label]) => `<a href="#${id}" class="${location.hash.slice(1) === id ? 'active' : ''}">${label}</a>`).join('');
}
function dialog(title, html) { $('#modal-title').textContent = title; $('#modal-body').innerHTML = html; if (!modal.open) modal.showModal(); }
function field(name, label, type = 'text', value = '', extra = '') { return `<label>${label}<input name="${name}" type="${type}" value="${escape(value)}" ${extra}></label>`; }
function form(title, fields, submit, label = '保存') {
  dialog(title, `<form>${fields}<p class="hint" id="form-error" role="alert"></p><button class="primary">${label}</button></form>`);
  $('#modal-body form').onsubmit = async event => {
    event.preventDefault(); const button = event.currentTarget.querySelector('button'); button.disabled = true;
    try { await submit(Object.fromEntries(new FormData(event.currentTarget))); modal.close(); notify('操作成功'); await route(); }
    catch (error) { $('#form-error').textContent = error.message; }
    finally { button.disabled = false; }
  };
}
function login(register = false) {
  form(register ? '创建买家账号' : '欢迎回来', field('username','用户名','text','','required minlength="3" maxlength="20" autocomplete="username"') + field('password','密码','password','','required minlength="8" maxlength="30" autocomplete="current-password"') + (register ? field('nickname','昵称','text','','required') + field('phone','手机号','tel','','required pattern="1[0-9]{10}"') : '') + `<button type="button" id="switch-auth">${register ? '已有账号，去登录' : '还没有账号？注册'}</button>`, async data => {
    if (register) { await api('/users/register','POST',data); }
    const result = await api('/users/login','POST',{username:data.username,password:data.password});
    token = result.token; sessionStorage.setItem('trade-token',token); updateIdentity();
  }, register ? '注册并登录' : '登录');
  $('#switch-auth').onclick = () => login(!register);
}
$('#login').onclick = () => login(); $('#close').onclick = () => modal.close();
$('#logout').onclick = () => { sessionStorage.removeItem('trade-token'); token = null; updateIdentity(); location.hash = 'browse'; route(); };
const empty = text => `<div class="empty">${text}</div>`;
function actions() { content.querySelectorAll('[data-act]').forEach(button => { button.onclick = async () => { button.disabled = true; try { await act(button.dataset); } catch (error) { notify(error.message,true); } finally { button.disabled = false; } }; }); }
async function browse() {
  content.innerHTML = `<form class="toolbar" id="search"><input name="keyword" aria-label="搜索商家" placeholder="搜索商家名称"><select name="sort" aria-label="排序"><option value="latest">最新商家</option><option value="rating">评分优先</option><option value="reviews">评价最多</option></select><button class="primary">查找商家</button></form><div id="results">正在加载商家…</div>`;
  const search = async () => {
    const params = new URLSearchParams(new FormData($('#search'))); params.set('page',page); params.set('pageSize',12);
    const result = await api('/restaurants/search?' + params);
    $('#results').innerHTML = result.records.length ? `<div class="grid">${result.records.map(r => `<article class="stall"><div class="mark">商</div><h3>${escape(r.name)}</h3><p>${escape(r.description || '查看商品，便捷下单')}</p><p>${escape(r.address || '位置待维护')}</p><p>评分 ${escape(r.averageRating)} · ${escape(r.reviewCount)} 条评价</p><button data-act="stall" data-id="${r.id}">查看商品 →</button></article>`).join('')}</div><div class="actions"><button id="prev" ${page === 1 ? 'disabled' : ''}>上一页</button><button id="next" ${!result.hasNext ? 'disabled' : ''}>下一页</button></div>` : empty('没有找到商家，试试其他关键词。');
    if ($('#prev')) { $('#prev').onclick = () => { page--; search().catch(e => notify(e.message,true)); }; $('#next').onclick = () => { page++; search().catch(e => notify(e.message,true)); }; } actions();
  };
  $('#search').onsubmit = event => { event.preventDefault(); page = 1; search().catch(e => notify(e.message,true)); }; await search();
}
async function stall(id) {
  const [detail,dishes] = await Promise.all([api(`/restaurants/${id}`),api(`/restaurants/${id}/products`)]);
  content.innerHTML = `<button data-act="back">← 返回商家列表</button><h2>${escape(detail.name)}</h2><p>${escape(detail.address)}</p><p class="hint">每个订单购买同一商家的商品，当前免运费。</p>${dishes.length ? dishes.map(d => `<div class="row"><div><h3>${escape(d.name)}</h3><p>${escape(d.description)}</p><span class="price">¥${Number(d.price).toFixed(2)}</span> · 库存 ${d.stock}</div><button data-act="add" data-id="${d.id}" ${!d.stock || d.status !== 1 ? 'disabled' : ''}>加入购物车</button></div>`).join('') : empty('暂无上架商品。')}`; actions();
}
async function cart() {
  const items = await api('/cart');
  if (!items.length) { content.innerHTML = empty('购物车为空，先到「发现商家」选择商品。'); return; }
  content.innerHTML = items.map(i => `<div class="row"><div><h3>${escape(i.dishName)}</h3><p>¥${Number(i.price).toFixed(2)} × ${i.quantity}</p></div><div class="actions"><button data-act="quantity" data-id="${i.id}" data-value="${i.quantity}">修改数量</button><button data-act="remove" data-id="${i.id}">移除</button></div></div>`).join('') + `<div class="row"><strong>合计 ¥${items.reduce((sum,i) => sum + Number(i.price)*i.quantity,0).toFixed(2)}</strong><button class="primary" data-act="checkout" data-dish="${items[0].dishId}">填写地址并下单</button></div>`; actions();
}
async function orders(merchant = false) {
  const prefix = merchant ? '/merchant/orders' : '/orders';
  const result = await api(prefix + '?page=' + page + '&pageSize=20');
  content.innerHTML = `<h2>${merchant ? '待处理订单' : '我的订单'}</h2>` + (result.records.length ? result.records.map(o => `<div class="row"><div><h3>订单 #${o.id} <span class="pill">${states[o.status]}</span></h3><p>¥${Number(o.totalAmount).toFixed(2)} · ${escape(o.createdAt)}</p></div><button data-act="order" data-id="${o.id}" data-merchant="${merchant}">查看订单</button></div>`).join('') : empty('暂无订单。')) + `<div class="actions"><button id="order-prev" ${page === 1 ? 'disabled' : ''}>上一页</button><button id="order-next" ${!(result.hasNext ?? page*20 < result.total) ? 'disabled' : ''}>下一页</button></div>`;
  $('#order-prev').onclick = () => { page--; orders(merchant).catch(e => notify(e.message,true)); }; $('#order-next').onclick = () => { page++; orders(merchant).catch(e => notify(e.message,true)); }; actions();
}
async function order(id, merchant = false) {
  const o = await api(`${merchant ? '/merchant' : ''}/orders/${id}`);
  dialog('订单 #' + id, `<p class="pill">${states[o.status]}</p><p>支付截止：${escape(o.expiresAt)}</p><p>收货信息：${escape(o.shippingAddress)}</p>${o.items.map(i => `<p>${escape(i.dishName)} × ${i.quantity}</p>`).join('')}<p>商品 ¥${Number(o.goodsAmount).toFixed(2)} · 运费 ¥${Number(o.freightAmount).toFixed(2)}</p><p>应付 ¥${Number(o.totalAmount).toFixed(2)}</p><div class="actions" id="order-actions"></div><p id="payment-info" role="status"></p>`);
  const buttons = merchant ? (o.status === 1 ? [['ship','发货']] : []) : o.status === 0 ? [['payments','创建支付单'],['cancel','取消订单']] : o.status === 1 ? [['refund','整单退款']] : o.status === 3 ? [['receive','确认收货']] : [];
  buttons.forEach(([action,label]) => {
    const button = document.createElement('button'); button.textContent = label; $('#order-actions').append(button);
    button.onclick = async () => {
      button.disabled = true;
      try {
        const result = await api(`${merchant ? '/merchant' : ''}/orders/${id}/${action}`, ['payments','refund'].includes(action) ? 'POST' : 'PUT');
        if (action === 'payments') { $('#payment-info').textContent = `支付单 #${result.id}，金额 ¥${Number(result.amount).toFixed(2)}。请由管理员在「支付演示」中确认模拟付款，无真实扣款。`; return; }
        await order(id,merchant); await orders(merchant);
      } catch(e) { $('#payment-info').textContent = e.message; }
      finally { button.disabled = false; }
    };
  });
}
async function merchant() {
  if (claims.role !== 'MERCHANT') throw new Error('此页面仅商家可用');
  content.innerHTML = `<div class="toolbar"><button data-act="merchant-orders">处理订单</button><button data-act="dish-create">新增商品</button></div><div id="merchant-list"></div>`;
  const result = await api('/merchant/products?page=1&pageSize=100');
  $('#merchant-list').innerHTML = result.records.map(d => `<div class="row"><div><h3>${escape(d.name)}</h3><p>¥${Number(d.price).toFixed(2)} · 库存 ${d.stock} · ${d.status ? '已上架' : '已下架'}</p></div><button data-act="stock" data-id="${d.id}">调整库存</button><button data-act="dish-status" data-id="${d.id}" data-status="${d.status ? 0 : 1}">${d.status ? '下架' : '上架'}</button></div>`).join('') || empty('新增商品后，买家即可在商家中查看。'); actions();
}
async function admin() {
  if (claims.role !== 'ADMIN') throw new Error('此页面仅管理员可用');
  content.innerHTML = `<h2>模拟支付回调</h2><p>按买家提供的支付单编号与金额确认付款，无真实扣款。重复提交同一渠道流水不会重复支付。</p><form id="callback">${field('id','支付单编号','number','','required min="1"')}${field('channelReference','渠道流水号','text','','required maxlength="64"')}${field('amount','支付金额','number','','required min="0.01" step="0.01"')}<button class="primary">确认模拟付款</button><p id="callback-result" role="status"></p></form>`;
  $('#callback').onsubmit = async e => {
    e.preventDefault(); const button=e.currentTarget.querySelector('button');button.disabled=true;
    try { const v=Object.fromEntries(new FormData(e.currentTarget)); await api(`/admin/mock-payments/${v.id}/callback`,'POST',{channelReference:v.channelReference,amount:Number(v.amount)});$('#callback-result').textContent='付款已确认'; }
    catch(error) { $('#callback-result').textContent=error.message; }
    finally {button.disabled=false;}
  };
}
async function act(d) {
  if (d.act === 'back') return browse();
  if (d.act === 'stall') return stall(d.id);
  if (d.act === 'add') { await api('/cart/items','POST',{dishId:Number(d.id),quantity:1}); return notify('已加入购物车'); }
  if (d.act === 'remove') { await api('/cart/items/' + d.id,'DELETE'); return cart(); }
  if (d.act === 'quantity') return form('修改数量',field('quantity','份数','number',d.value,'min="1" required'), value => api('/cart/items/' + d.id,'PUT',{quantity:Number(value.quantity)}));
  if (d.act === 'checkout') {
    const requestId = sessionStorage.getItem('trade-purchase') || Array.from(crypto.getRandomValues(new Uint8Array(16)), n => n.toString(16).padStart(2,'0')).join(''); sessionStorage.setItem('trade-purchase',requestId);
    return form('确认订单',field('shippingAddress','收件人、电话与收货地址','text','','required maxlength="500"')+'<p class="hint">最终金额以服务端下单结果为准，当前免运费。</p>', async value => { await api('/orders/create','POST',{requestId,shippingAddress:value.shippingAddress}); sessionStorage.removeItem('trade-purchase'); location.hash = 'orders'; },'提交订单');
  }
  if (d.act === 'order') return order(d.id,d.merchant === 'true');
  if (d.act === 'merchant-orders') return orders(true);
  if (d.act === 'dish-create') return form('新增商品',field('name','商品名称','text','','required')+field('description','描述')+field('price','单价','number','','required min="0.01" step="0.01"')+field('stock','库存','number',0,'required min="0" step="1"'),value => api('/merchant/products','POST',{...value,price:Number(value.price),stock:Number(value.stock)}));
  if (d.act === 'dish-status') { await api(`/merchant/products/${d.id}/status`,'PUT',{status:Number(d.status)}); return merchant(); }
  if (d.act === 'stock') return form('调整库存',field('delta','增加数量（负数为减少）','number','','required step="1"'),value=>api(`/merchant/products/${d.id}/inventory`,'POST',{requestId:crypto.randomUUID(),delta:Number(value.delta)}));

}
async function route() {
  updateIdentity(); notify(''); const view = location.hash.slice(1) || 'browse'; content.setAttribute('aria-busy','true');
  try { if (!token && view !== 'browse') { content.innerHTML = empty('请先登录，继续选购商品。'); return login(); } await ({browse,cart,orders,merchant,admin}[view] || browse)(); }
  catch(error) { content.innerHTML = empty('暂时无法加载，请刷新页面重试。'); notify(error.message,true); }
  finally { content.setAttribute('aria-busy','false'); }
}
window.addEventListener('hashchange',() => { page = 1; route(); }); route();
