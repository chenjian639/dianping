'use strict';
const $ = s => document.querySelector(s);
const content = $('#content'), modal = $('#modal');
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let token = sessionStorage.getItem('campus-token'), claims = {}, page = 1;
const states = ['待支付','已支付','制作中','待取餐','已完成','已取消'];
function notify(text, error = false) { $('#notice').textContent = text; $('#notice').className = error ? 'error' : ''; }
async function api(path, method = 'GET', body) {
  const response = await fetch(path, {method, headers:{...(token ? {Authorization:`Bearer ${token}`} : {}), ...(body === undefined ? {} : {'Content-Type':'application/json'})}, body:body === undefined ? undefined : JSON.stringify(body)});
  const result = await response.json();
  if (response.status === 401) { token = null; sessionStorage.removeItem('campus-token'); updateIdentity(); }
  if (!response.ok || result.code !== 0) throw new Error(result.msg || `请求失败 (${response.status})`);
  return result.data;
}
function updateIdentity() {
  try { claims = token ? JSON.parse(decodeURIComponent(Array.from(atob(token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')), c => '%' + c.charCodeAt(0).toString(16).padStart(2,'0')).join(''))) : {}; } catch { token = null; claims = {}; sessionStorage.removeItem('campus-token'); }
  $('#identity').textContent = token ? `已登录 · ${{USER:'学生',MERCHANT:'商家',ADMIN:'管理员'}[claims.role] || '用户'}` : '校园预约服务';
  $('#login').hidden = !!token; $('#logout').hidden = !token;
  const links = [['browse','发现档口'],['nearby','附近食堂'],['cart','购物车'],['orders','我的订单']];
  if (claims.role === 'MERCHANT') links.push(['merchant','商家工作台']);
  if (claims.role === 'ADMIN') links.push(['admin','食堂管理']);
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
  form(register ? '创建学生账号' : '欢迎回来', field('username','用户名','text','','required minlength="3" maxlength="20" autocomplete="username"') + field('password','密码','password','','required minlength="8" maxlength="30" autocomplete="current-password"') + (register ? field('nickname','昵称','text','','required') + field('phone','手机号','tel','','required pattern="1[0-9]{10}"') : '') + `<button type="button" id="switch-auth">${register ? '已有账号，去登录' : '还没有账号？注册'}</button>`, async data => {
    if (register) { await api('/users/register','POST',data); }
    const result = await api('/users/login','POST',{username:data.username,password:data.password});
    token = result.token; sessionStorage.setItem('campus-token',token); updateIdentity();
  }, register ? '注册并登录' : '登录');
  $('#switch-auth').onclick = () => login(!register);
}
$('#login').onclick = () => login(); $('#close').onclick = () => modal.close();
$('#logout').onclick = () => { sessionStorage.removeItem('campus-token'); token = null; updateIdentity(); location.hash = 'browse'; route(); };
const empty = text => `<div class="empty">${text}</div>`;
function actions() { content.querySelectorAll('[data-act]').forEach(button => { button.onclick = async () => { button.disabled = true; try { await act(button.dataset); } catch (error) { notify(error.message,true); } finally { button.disabled = false; } }; }); }
async function browse() {
  content.innerHTML = `<form class="toolbar" id="search"><input name="keyword" aria-label="搜索档口" placeholder="搜索档口名称"><select name="sort" aria-label="排序"><option value="latest">最新档口</option><option value="rating">评分优先</option><option value="reviews">评价最多</option></select><button class="primary">查找档口</button></form><div id="results">正在加载档口…</div>`;
  const search = async () => {
    const params = new URLSearchParams(new FormData($('#search'))); params.set('page',page); params.set('pageSize',12);
    const result = await api('/restaurants/search?' + params);
    $('#results').innerHTML = result.records.length ? `<div class="grid">${result.records.map(r => `<article class="stall"><div class="mark">食</div><h3>${escape(r.name)}</h3><p>${escape(r.description || '提前预约，按时取餐')}</p><p>${escape(r.address || '位置待维护')}</p><p>评分 ${escape(r.averageRating)} · ${escape(r.reviewCount)} 条评价</p><button data-act="stall" data-id="${r.id}">查看菜品与时段 →</button></article>`).join('')}</div><div class="actions"><button id="prev" ${page === 1 ? 'disabled' : ''}>上一页</button><button id="next" ${!result.hasNext ? 'disabled' : ''}>下一页</button></div>` : empty('没有找到档口，试试其他关键词。');
    if ($('#prev')) { $('#prev').onclick = () => { page--; search().catch(e => notify(e.message,true)); }; $('#next').onclick = () => { page++; search().catch(e => notify(e.message,true)); }; } actions();
  };
  $('#search').onsubmit = event => { event.preventDefault(); page = 1; search().catch(e => notify(e.message,true)); }; await search();
}
async function stall(id) {
  const [detail,dishes,slots,position] = await Promise.all([api(`/restaurants/${id}`),api(`/restaurants/${id}/dishes`),api(`/restaurants/${id}/pickup-slots`),api(`/restaurants/${id}/location`)]);
  content.innerHTML = `<button data-act="back">← 返回档口列表</button><h2>${escape(detail.name)}</h2><p>${escape(position.canteenName || detail.address || '食堂位置待维护')} ${escape(position.floor)} ${escape(position.windowNumber)}</p><p>${escape(position.pickupInstructions)}</p><p class="hint">当前可预约时段 ${slots.length} 个，结算时选择。一个订单仅购买同一档口的菜品。</p>${dishes.length ? dishes.map(d => `<div class="row"><div><h3>${escape(d.name)}</h3><p>${escape(d.description)}</p><span class="price">¥${Number(d.price).toFixed(2)}</span> · 剩余 ${d.stock} 份</div><button data-act="add" data-id="${d.id}" ${!d.stock || d.status !== 1 ? 'disabled' : ''}>加入购物车</button></div>`).join('') : empty('档口还未上架菜品。')}`; actions();
}
async function cart() {
  const items = await api('/cart');
  if (!items.length) { content.innerHTML = empty('购物车为空，先到「发现档口」选择一份餐。'); return; }
  content.innerHTML = items.map(i => `<div class="row"><div><h3>${escape(i.dishName)}</h3><p>¥${Number(i.price).toFixed(2)} × ${i.quantity}</p></div><div class="actions"><button data-act="quantity" data-id="${i.id}" data-value="${i.quantity}">修改数量</button><button data-act="remove" data-id="${i.id}">移除</button></div></div>`).join('') + `<div class="row"><strong>合计 ¥${items.reduce((sum,i) => sum + Number(i.price)*i.quantity,0).toFixed(2)}</strong><button class="primary" data-act="checkout" data-dish="${items[0].dishId}">选择取餐时段</button></div>`; actions();
}
async function orders(merchant = false) {
  const prefix = merchant ? '/merchant/orders' : '/orders';
  const result = await api(prefix + '?page=' + page + '&pageSize=20');
  content.innerHTML = `<h2>${merchant ? '待处理订单' : '我的预约'}</h2>` + (result.records.length ? result.records.map(o => `<div class="row"><div><h3>订单 #${o.id} <span class="pill">${states[o.status]}</span></h3><p>¥${Number(o.totalAmount).toFixed(2)} · ${escape(o.createdAt)}</p></div><button data-act="order" data-id="${o.id}" data-merchant="${merchant}">查看订单</button></div>`).join('') : empty('暂无订单。')) + `<div class="actions"><button id="order-prev" ${page === 1 ? 'disabled' : ''}>上一页</button><button id="order-next" ${!(result.hasNext ?? page*20 < result.total) ? 'disabled' : ''}>下一页</button></div>`;
  $('#order-prev').onclick = () => { page--; orders(merchant).catch(e => notify(e.message,true)); }; $('#order-next').onclick = () => { page++; orders(merchant).catch(e => notify(e.message,true)); }; actions();
}
async function order(id, merchant) {
  const o = await api(`${merchant ? '/merchant' : ''}/orders/${id}`);
  dialog('订单 #' + id, `<p class="pill">${states[o.status]}</p><p>支付截止：${escape(o.expiresAt)}</p>${o.items.map(i => `<p>${escape(i.dishName)} × ${i.quantity}</p>`).join('')}<p>合计 ¥${Number(o.totalAmount).toFixed(2)}</p>${!merchant && o.pickupCode ? `<p>到档口后出示取餐码</p><div class="code">${escape(o.pickupCode)}</div>` : ''}<div class="actions" id="order-actions"></div>`);
  const buttons = merchant ? (o.status === 1 ? [['prepare','开始制作']] : o.status === 2 ? [['ready','标记待取餐']] : o.status === 3 ? [['pickup','输入取餐码核销']] : []) : o.status === 0 ? [['pay','模拟支付'],['cancel','取消预约']] : [];
  buttons.forEach(([action,label]) => { const button = document.createElement('button'); button.textContent = label; $('#order-actions').append(button); button.onclick = async () => { button.disabled = true; try { let body; if (action === 'pickup') { const code = prompt('输入学生出示的32位取餐码'); if (!code) return; body = {pickupCode:code}; } await api(`${merchant ? '/merchant' : ''}/orders/${id}/${action}`,'PUT',body); await order(id,merchant); await orders(merchant); } catch(e) { notify(e.message,true); } finally { button.disabled = false; } }; });
}
async function nearby() {
  content.innerHTML = `<h2>附近的食堂入口</h2><p class="hint">输入已知的 WGS84 参考坐标。结果为直线距离，不代表步行距离；本次参考位置不保存。</p><form id="near-form"><div class="toolbar">${field('longitude','经度','number','','required min="-180" max="180" step="any"')}${field('latitude','纬度','number','','required min="-85.05112878" max="85.05112878" step="any"')}${field('radiusMeters','半径（米）','number',1000,'min="1" max="10000" required')}</div><button class="primary">查询附近食堂</button></form><div id="near-results"></div>`;
  $('#near-form').onsubmit = async event => { event.preventDefault(); const button = event.currentTarget.querySelector('button'); button.disabled = true; try { const data = Object.fromEntries(new FormData(event.currentTarget)); for (const key of Object.keys(data)) data[key] = Number(data[key]); const rows = await api('/canteens/nearby','POST',{...data,limit:20,coordinateSystem:'WGS84'}); $('#near-results').innerHTML = rows.length ? rows.map(c => `<div class="row"><div><h3>${escape(c.name)}</h3><p>${escape(c.address)}</p><p>${escape(c.entranceInstructions)}</p></div><span class="price">直线 ${Math.round(c.distanceMeters)} 米</span></div>`).join('') : empty('该范围内没有已启用的食堂入口。'); } catch(e) { notify(e.message,true); } finally { button.disabled = false; } };
}
async function merchant() {
  if (claims.role !== 'MERCHANT') throw new Error('此页面仅商家可用');
  content.innerHTML = `<div class="toolbar"><button data-act="merchant-orders">处理订单</button><button data-act="dish-create">新增菜品</button><button data-act="slot-create">创建时段</button><button data-act="location">取餐说明</button></div><div id="merchant-list"></div>`;
  const result = await api('/merchant/dishes?page=1&pageSize=100');
  $('#merchant-list').innerHTML = result.records.map(d => `<div class="row"><div><h3>${escape(d.name)}</h3><p>¥${Number(d.price).toFixed(2)} · 库存 ${d.stock} · ${d.status ? '已上架' : '已下架'}</p></div><button data-act="dish-status" data-id="${d.id}" data-status="${d.status ? 0 : 1}">${d.status ? '下架' : '上架'}</button></div>`).join('') || empty('新增菜品后，学生即可在档口中查看。'); actions();
}
function canteenForm(c = {}) {
  form(c.id ? '编辑食堂入口' : '新增食堂入口', field('name','食堂名称','text',c.name,'required maxlength="100"') + field('address','地址','text',c.address,'required maxlength="255"') + field('longitude','WGS84 经度','number',c.longitude,'required min="-180" max="180" step="any"') + field('latitude','WGS84 纬度','number',c.latitude,'required min="-85.05112878" max="85.05112878" step="any"') + field('entranceInstructions','入口说明','text',c.entranceInstructions,'maxlength="500"') + `<label>状态<select name="enabled"><option value="true" ${c.enabled !== false ? 'selected' : ''}>启用</option><option value="false" ${c.enabled === false ? 'selected' : ''}>停用</option></select></label>`, d => api('/admin/canteens' + (c.id ? '/' + c.id : ''), c.id ? 'PUT' : 'POST',{...d,longitude:Number(d.longitude),latitude:Number(d.latitude),enabled:d.enabled === 'true',coordinateSystem:'WGS84'}));
}
async function admin() {
  if (claims.role !== 'ADMIN') throw new Error('此页面仅管理员可用');
  const rows = await api('/admin/canteens');
  content.innerHTML = `<div class="toolbar"><button class="primary" id="create-canteen">新增食堂</button><button data-act="bind">关联档口</button><button data-act="rebuild">重建位置索引</button></div>` + (rows.map(c => `<div class="row"><div><h3>${escape(c.name)} <span class="pill">${c.enabled ? '启用' : '停用'}</span></h3><p>${escape(c.address)}</p><p>${escape(c.entranceInstructions)}</p></div><div class="actions"><button id="edit-${c.id}">编辑</button><button data-act="canteen-delete" data-id="${c.id}">删除</button></div></div>`).join('') || empty('尚未录入食堂，请使用核实过的入口坐标。'));
  $('#create-canteen').onclick = () => canteenForm(); rows.forEach(c => $('#edit-' + c.id).onclick = () => canteenForm(c)); actions();
}
async function act(d) {
  if (d.act === 'back') return browse();
  if (d.act === 'stall') return stall(d.id);
  if (d.act === 'add') { await api('/cart/items','POST',{dishId:Number(d.id),quantity:1}); return notify('已加入购物车'); }
  if (d.act === 'remove') { await api('/cart/items/' + d.id,'DELETE'); return cart(); }
  if (d.act === 'quantity') return form('修改数量',field('quantity','份数','number',d.value,'min="1" required'), value => api('/cart/items/' + d.id,'PUT',{quantity:Number(value.quantity)}));
  if (d.act === 'checkout') {
    const dish = (await api('/cart')).find(i => i.dishId === Number(d.dish)); if (!dish) throw new Error('购物车已变更，请刷新');
    // Cart API omits restaurantId; resolve it against the authenticated restaurant/dish listings.
    const all = await api('/restaurants'); let restaurantId;
    for (const r of all) { if ((await api(`/restaurants/${r.id}/dishes`)).some(item => item.id === dish.dishId)) { restaurantId = r.id; break; } }
    if (!restaurantId) throw new Error('档口已休息或菜品不可用');
    const slots = await api(`/restaurants/${restaurantId}/pickup-slots`); if (!slots.length) throw new Error('该档口暂时没有可预约时段');
    const requestId = sessionStorage.getItem('campus-purchase') || Array.from(crypto.getRandomValues(new Uint8Array(16)), n => n.toString(16).padStart(2,'0')).join(''); sessionStorage.setItem('campus-purchase',requestId);
    return form('预约取餐',`<label>取餐时段<select name="slot">${slots.map(s => `<option value="${s.id}">${escape(s.startsAt.replace('T',' '))} 至 ${escape(s.endsAt.slice(11))} · 余 ${s.capacity-s.reserved} 单</option>`).join('')}</select></label><p class="hint">下单成功后请在截止时间前完成模拟支付。</p>`, async value => { await api('/orders/create','POST',{requestId,pickupSlotId:Number(value.slot)}); sessionStorage.removeItem('campus-purchase'); location.hash = 'orders'; },'确认预约');
  }
  if (d.act === 'order') return order(d.id,d.merchant === 'true');
  if (d.act === 'merchant-orders') return orders(true);
  if (d.act === 'dish-create') return form('新增菜品',field('name','菜名','text','','required')+field('description','描述')+field('price','单价','number','','required min="0.01" step="0.01"')+field('stock','库存','number',0,'required min="0" step="1"'),value => api('/merchant/dishes','POST',{...value,price:Number(value.price),stock:Number(value.stock)}));
  if (d.act === 'dish-status') { await api(`/merchant/dishes/${d.id}/status`,'PUT',{status:Number(d.status)}); return merchant(); }
  if (d.act === 'slot-create') return form('创建取餐时段',field('startsAt','开始时间','datetime-local','','required')+field('endsAt','结束时间','datetime-local','','required')+field('capacity','可接订单数','number',20,'required min="1" max="10000"')+'<p class="hint">使用北京时间；同档口时段不能重叠。</p>', value => api('/merchant/pickup-slots','POST',{...value,capacity:Number(value.capacity)}));
  if (d.act === 'location') { const p = await api(`/restaurants/${claims.restaurantId}/location`); return form('修改取餐说明',field('floor','楼层','text',p.floor,'maxlength="30"')+field('windowNumber','窗口号','text',p.windowNumber,'maxlength="30"')+field('pickupInstructions','取餐说明','text',p.pickupInstructions,'maxlength="500"'), value => api('/merchant/restaurant/location','PUT',value)); }
  if (d.act === 'bind') return form('关联档口与食堂',field('restaurantId','档口 ID','number','','required min="1"')+field('canteenId','食堂 ID（留空解除关联）','number','','min="1"'), value => api(`/admin/restaurants/${Number(value.restaurantId)}/canteen`,'PUT',{canteenId:value.canteenId ? Number(value.canteenId) : null}));
  if (d.act === 'rebuild') { await api('/admin/canteens/geo-index/rebuild','POST'); return notify('位置索引重建完成'); }
  if (d.act === 'canteen-delete' && confirm('删除该食堂？历史订单将保留。')) { await api('/admin/canteens/' + d.id,'DELETE'); return admin(); }
}
async function route() {
  updateIdentity(); notify(''); const view = location.hash.slice(1) || 'browse'; content.setAttribute('aria-busy','true');
  try { if (!token && view !== 'browse') { content.innerHTML = empty('请先登录，继续预约取餐。'); return login(); } await ({browse,cart,orders,nearby,merchant,admin}[view] || browse)(); }
  catch(error) { content.innerHTML = empty('暂时无法加载，请刷新页面重试。'); notify(error.message,true); }
  finally { content.setAttribute('aria-busy','false'); }
}
window.addEventListener('hashchange',() => { page = 1; route(); }); route();
