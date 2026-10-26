"""Disposable local Compose environment only. Creates its own users/canteen/dish.
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
        with urllib.request.urlopen(req, timeout=15) as response: result = json.load(response)
    except urllib.error.HTTPError as e:
        result = json.load(e)
    if result['code'] != expected:
        raise ApiError(base, method, path, result)
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
    with urllib.request.urlopen(BASES[0]+'/v3/api-docs',timeout=15) as response:
        spec=json.load(response)
        assert spec['info']['title']=='校园预约取餐 API'
        assert '/merchant/orders/{id}/pickup' in spec['paths']
        assert '/merchant/orders/{id}/complete' not in spec['paths']
    prefix = uuid.uuid4().hex[:8]
    # This script only changes the isolated campus-pickup database, never the legacy Compose project.
    sql("INSERT INTO restaurant(name,status) VALUES('Smoke campus " + prefix + "',1);")
    restaurant = int(sql("SELECT id FROM restaurant WHERE name='Smoke campus " + prefix + "'"))
    sql(f"INSERT INTO dish(restaurant_id,name,price,stock,status) VALUES({restaurant},'Smoke lunch',12,100,1);")
    dish = int(sql(f"SELECT id FROM dish WHERE restaurant_id={restaurant}"))
    tokens=[]
    for i in range(32):
        username = 's'+prefix+str(i)
        api('POST','/users/register',dict(username=username,password='CampusSmoke123',nickname=username,
            phone='1'+str(random.randrange(10**9,10**10))))
        if i==0: sql(f"UPDATE user SET role='MERCHANT',restaurant_id={restaurant} WHERE username='{username}'")
        tokens.append(api('POST','/users/login',dict(username=username,password='CampusSmoke123'))['token'])
    start = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).replace(tzinfo=None)+dt.timedelta(hours=2)
    slot = api('POST','/merchant/pickup-slots',dict(startsAt=start.isoformat(timespec='seconds'),
        endsAt=(start+dt.timedelta(minutes=15)).isoformat(timespec='seconds'),capacity=20),tokens[0])
    for token in tokens[1:]: api('POST','/cart/items',dict(dishId=dish,quantity=1),token)
    def reserve(i):
        # A full slot is an expected business rejection; unexpected errors fail the smoke test.
        # Keep app2's first authenticated traffic concurrent to catch cold-start JWT races.
        try: return i,api('POST','/orders/create',dict(requestId=prefix+str(i),pickupSlotId=slot),tokens[i],BASES[i%2])
        except ApiError as e:
            if e.code != 400 or e.message != '时段不属于该档口、已满或已截止预约':
                raise
            return i,None
    with concurrent.futures.ThreadPoolExecutor(max_workers=24) as pool:
        successes=[(i,order) for i,order in pool.map(reserve,range(1,32)) if order is not None]
    assert len(successes)==20, successes
    assert int(sql(f'SELECT reserved FROM pickup_slot WHERE id={slot}'))==20
    assert int(sql(f'SELECT stock FROM dish WHERE id={dish}'))==80
    i,order=successes[0]
    api('GET',f'/orders/{order}',token=tokens[0],expected=400)
    # Replay on the other instance must return the same order after the cart was cleared.
    assert api('POST','/orders/create',dict(requestId=prefix+str(i),pickupSlotId=slot),tokens[i],BASES[(i+1)%2])==order
    detail=api('GET',f'/orders/{order}',token=tokens[i])
    assert api('GET',f'/merchant/orders/{order}',token=tokens[0]).get('pickupCode') is None
    api('PUT',f'/orders/{order}/pay',token=tokens[i])
    api('PUT',f'/merchant/orders/{order}/prepare',token=tokens[0])
    api('PUT',f'/merchant/orders/{order}/ready',token=tokens[0])
    api('PUT',f'/merchant/orders/{order}/pickup',dict(pickupCode=detail['pickupCode']),tokens[0],BASES[1])
    api('PUT',f'/merchant/orders/{order}/pickup',dict(pickupCode=detail['pickupCode']),tokens[0],expected=400)
    i,canceled=successes[1]
    api('PUT',f'/orders/{canceled}/cancel',token=tokens[i])
    assert int(sql(f'SELECT reserved FROM pickup_slot WHERE id={slot}'))==19
    assert int(sql(f'SELECT stock FROM dish WHERE id={dish}'))==81
    api('POST','/admin/cache-recovery/start',{},tokens[i],expected=403)
    # Prime L1 on both instances, modify restaurant via merchant API, and wait for broadcast.
    for base in BASES: api('GET',f'/restaurants/{restaurant}',token=tokens[0],base=base)
    changed = 'Updated campus '+prefix
    api('PUT','/merchant/restaurant',dict(name=changed,description='smoke',address='Campus'),tokens[0])
    for attempt in range(40):
        names=[api('GET',f'/restaurants/{restaurant}',token=tokens[0],base=base)['name'] for base in BASES]
        if names==[changed,changed]: break
        time.sleep(.25)
    else: raise AssertionError('Cache broadcast did not reach both instances within 10s: '+str(names))
    with urllib.request.urlopen('http://127.0.0.1:18080/actuator/health/liveness',timeout=5) as response:
        assert response.headers.get('X-Campus-Upstream')
        assert json.load(response)['status']=='UP'
    print('PASS: two-instance capacity, stock, idempotency, pickup, cancellation, admin authorization, cache broadcast and gateway')

if __name__=='__main__': run()
