"""Create dedicated presentation accounts and Chinese food catalog. Never clears business tables."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import urllib.error
import urllib.request

SHOPS = [
    ('禾味家常菜', '现炒盖饭与家常小菜，适合一人食', '校园生活区商业街1号', [
        ('宫保鸡丁盖饭', '鸡腿肉搭配花生、时蔬，微辣', 16.8),
        ('番茄炒蛋盖饭', '番茄鸡蛋配米饭，清爽家常口味', 12.8),
        ('红烧肉套餐', '红烧五花肉、时蔬与米饭', 22.8),
        ('香菇滑鸡饭', '香菇与鸡肉焖制，口味鲜香', 18.8)]),
    ('青禾轻食', '谷物、蔬菜与优质蛋白搭配', '校园生活区商业街2号', [
        ('香煎鸡胸肉沙拉', '鸡胸肉、生菜、玉米和小番茄', 23.9),
        ('牛肉藜麦能量碗', '牛肉搭配藜麦饭与季节蔬菜', 29.9),
        ('南瓜燕麦粥', '南瓜与燕麦熬煮，口感绵密', 8.9),
        ('无糖豆浆', '原味豆浆，不额外添加糖', 5.0)]),
    ('麦香烘焙', '面包、早餐与下午茶', '校园生活区商业街3号', [
        ('黄油可颂', '层次酥脆，奶香浓郁', 9.9),
        ('全麦三明治', '全麦面包搭配鸡蛋、蔬菜与火腿', 14.9),
        ('葡式蛋挞双只装', '酥皮蛋挞，两只一份', 12.0),
        ('红豆吐司', '松软吐司搭配绵密红豆馅', 15.9)])]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', required=True)
    parser.add_argument('--environment', choices=['server', 'local'], default='local')
    parser.add_argument('--output', default='.sandbox-tmp/demo/accounts.json')
    parser.add_argument('--hide-smoke', action='store_true', help='Hide only known smoke-named shops; preserve rows/orders')
    args = parser.parse_args()
    path = Path(args.output)
    if '.sandbox-tmp' not in path.parts:
        raise SystemExit('Credentials must stay inside .sandbox-tmp')
    path.parent.mkdir(parents=True, exist_ok=True)
    state = {'base': args.base, 'accounts': [], 'shops': [], 'hidden_shop_ids': []}
    with path.open('x', encoding='utf-8') as stream:
        os.chmod(path, 0o600)
        json.dump(state, stream)

    def save():
        path.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')

    def sql(statement):
        command = (['sudo', '-n'] if args.environment == 'server' else []) + ['docker', 'compose']
        command += (['-p', 'campus-server', '--env-file', '.env.server', '-f', 'compose.server.yml']
                    if args.environment == 'server' else ['-f', 'compose.campus.yml'])
        command += ['exec', '-T', 'mysql', 'sh', '-c',
                    'exec mysql --default-character-set=utf8mb4 -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"']
        result = subprocess.run(command, input=statement, text=True, encoding='utf-8',
                                capture_output=True, check=True, timeout=60)
        return result.stdout.strip()

    def api(method, endpoint, body=None, token=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = urllib.request.Request(args.base.rstrip('/') + endpoint, method=method, headers=headers,
            data=json.dumps(body).encode() if body is not None else None)
        with urllib.request.urlopen(request, timeout=20) as response:
            result = json.load(response)
        if result['code'] != 0:
            raise RuntimeError(endpoint + ': ' + result['msg'])
        return result['data']

    def text(value):
        return 'CONVERT(0x' + value.encode('utf-8').hex() + ' USING utf8mb4)'

    prefix = 'show' + secrets.token_hex(3)

    def account(suffix, nickname, role, restaurant=None):
        entry = dict(username=prefix + suffix, password=secrets.token_urlsafe(12),
                     nickname=nickname, role=role, restaurantId=restaurant)
        state['accounts'].append(entry)
        save()
        user = api('POST', '/users/register', dict(username=entry['username'], password=entry['password'],
            nickname=nickname, phone='1' + ''.join(secrets.choice('0123456789') for _ in range(10))))
        entry['userId'] = user['id']
        sql(f"UPDATE user SET role='{role}',restaurant_id={restaurant if restaurant else 'NULL'} "
            f"WHERE id={int(user['id'])} AND username='{entry['username']}' AND role='USER';")
        save()
        return api('POST', '/users/login', dict(username=entry['username'], password=entry['password']))['token']

    account('a', '运营管理员', 'ADMIN')
    account('u', '林同学', 'USER')
    for i, (name, description, address, products) in enumerate(SHOPS):
        shop = int(sql(f"INSERT INTO restaurant(name,description,address,status) VALUES("
                       f"{text(name)},{text(description)},{text(address)},1); SELECT LAST_INSERT_ID();"))
        shop_state = dict(id=shop, name=name, products=[])
        state['shops'].append(shop_state)
        save()
        token = account('m' + str(i), name + '店长', 'MERCHANT', shop)
        for name, description, price in products:
            product = api('POST', '/merchant/products', dict(name=name, description=description, price=price, stock=80), token)
            shop_state['products'].append(dict(id=product, name=name, price=price))
            save()
    if args.hide_smoke:
        rows = sql("SELECT id FROM restaurant WHERE status=1 AND name REGEXP '^(Trade smoke |Smoke campus |Updated campus )';")
        ids = [int(value) for value in rows.split()] if rows else []
        state['hidden_shop_ids'] = ids
        save()
        if ids:
            values = ','.join(map(str, ids))
            sql(f"START TRANSACTION; UPDATE restaurant SET status=0 WHERE id IN ({values}); "
                "INSERT INTO outbox_event(event_id,event_type,aggregate_id,payload,status,retry_count) "
                f"SELECT UUID(),'RESTAURANT_CACHE_EVICT',id,CAST(id AS CHAR),'PENDING',0 FROM restaurant WHERE id IN ({values}); COMMIT;")
    state['complete'] = True
    save()
    guide = ['# 本地演示账号', '', '入口：' + args.base, '', '以下为本次创建的专用演示账号，请勿用于正式环境。', '', '| 角色 | 名称 | 用户名 | 密码 |', '|---|---|---|---|']
    guide += [f"| {a['role']} | {a['nickname']} | {a['username']} | {a['password']} |" for a in state['accounts']]
    guide += ['', '演示顺序：买家下单并创建支付单 → 管理员选择待付款并确认 → 对应商家发货 → 买家确认收货。', '退款演示另下一单，确认付款后、发货前申请整单退款。', '建议三个不同浏览器窗口/配置分别登录买家、管理员、商家。']
    path.with_suffix('.md').write_text('\n'.join(guide), encoding='utf-8')
    print(f'Created 3 shops, 12 products and 5 dedicated accounts. Credentials: {path}')
    print('Existing orders preserved. Search caches may take up to 2 minutes to expire.')


if __name__ == '__main__':
    main()
