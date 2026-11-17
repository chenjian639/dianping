"""Disposable local Compose environment only. Creates its own users/shop/product.
Run after compose.campus.yml starts. No third-party Python packages required.
"""
import concurrent.futures
import datetime as dt
import json
import random
import subprocess
import time
import urllib.error
import urllib.request
import uuid

BASES = ['http://127.0.0.1:18081', 'http://127.0.0.1:18082']

class ApiError(AssertionError):
    def __init__(self, base, method, path, result):
        self.code = result.get('code')
        self.message = result.get('msg', '')
        super().__init__((base, method, path, result))

def api(method, path, body=None, token=None, base=BASES[0], expected=0):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(base+path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            http_status = response.status
            result = json.load(response)
    except urllib.error.HTTPError as e:
        http_status = e.code
        result = json.load(e)
    if result['code'] != expected:
        raise ApiError(base, method, path, result)
    assert http_status == (200 if expected == 0 else expected), (base, path, http_status, result)
    return result['data']

def sql(statement):
    completed = subprocess.run(['docker','compose','-f','compose.campus.yml','exec','-T','mysql',
        'sh','-c','exec mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" dodo_food'],
        input=statement, text=True, capture_output=True, check=True)
    return completed.stdout.strip()

def wait_ready():
    for base in BASES:
        for attempt in range(90):
            try:
                with urllib.request.urlopen(base+'/actuator/health/readiness',timeout=3) as response:
                    if json.load(response)['status']=='UP': break
            except (urllib.error.URLError, TimeoutError, ConnectionError): pass
            time.sleep(2)
        else: raise RuntimeError('Application did not become ready: '+base)

def run():
    wait_ready()
    prefix=uuid.uuid4().hex[:8]
    sql("INSERT INTO merchant(name,status) VALUES('Trade smoke " + prefix + "',1);")
    merchant=int(sql("SELECT id FROM merchant WHERE name='Trade smoke " + prefix + "'"))
    tokens=[]
    for i,role in enumerate(('MERCHANT','ADMIN','USER')):
        username='t'+prefix+str(i)
        api('POST','/users/register',dict(username=username,password='TradeSmoke123',nickname=username,phone='1'+str(random.randrange(10**9,10**10))))
        sql(f"UPDATE user SET role='{role}',merchant_id={merchant if role=='MERCHANT' else 'NULL'} WHERE username='{username}'")
        tokens.append(api('POST','/users/login',dict(username=username,password='TradeSmoke123'))['token'])
    seller,admin,buyer=tokens
    product=api('POST','/merchant/products',dict(name='Test product',description='Smoke fixture',price=12,stock=2),seller)
    api('POST','/cart/items',dict(productId=product,quantity=1),buyer)
    body=dict(requestId=prefix,shippingAddress='Test receiver 13800000000 Test address')
    order=api('POST','/orders/create',body,buyer)
    assert api('POST','/orders/create',body,buyer,base=BASES[1])==order
    api('PUT',f'/orders/{order}/cancel',token=buyer)
    assert api('GET',f'/orders/{order}',token=buyer)['status']==5
    assert int(sql(f'SELECT stock FROM product WHERE id={product}'))==2
    print('PASS: two-instance order idempotency, cancellation and inventory; payment requires Alipay sandbox acceptance')

if __name__=='__main__':
    run()
