"""Real HTTP/MySQL/Kafka acceptance suite. Run against this project's local Compose stack.
Creates uniquely named test records, never deletes existing records. --recovery briefly
stops Kafka and billing in THIS Compose project and restarts them in finally blocks.
"""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--url', default='http://127.0.0.1:' + os.environ.get('HTTP_PORT', '8080'))
parser.add_argument('--recovery', action='store_true')
args = parser.parse_args()
token = None
checks = 0

def check(condition, message):
    global checks
    if not condition:
        raise AssertionError(message)
    checks += 1

def request(method, path, data=None, auth=True):
    headers = {'Content-Type': 'application/json'}
    if auth and token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(args.url + path, data=None if data is None else json.dumps(data).encode(), headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read().decode()
        try:
            body = json.loads(body) if body else None
        except json.JSONDecodeError:
            pass
        return response.status, body

def expect(method, path, status, data=None, auth=True):
    actual, body = request(method, path, data, auth)
    check(actual == status, f'{method} {path}: expected {status}, got {actual}: {body}')
    return body

def eventually(fn, timeout=90):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        result = fn()
        if result:
            return result
        time.sleep(1)
    raise AssertionError('Timed out waiting for ' + fn.__name__)

def compose(*cmd):
    return subprocess.run(['docker', 'compose', *cmd], cwd=ROOT, check=True, capture_output=True, text=True, timeout=90).stdout.strip()

def sql(query):
    return compose('exec', '-T', 'mysql', 'mysql', '-uroot', '-plocal-root-password', '--batch', '--skip-column-names', '-e', query)

def invoice(booking_id):
    status, rows = request('GET', '/api/billing/history')
    return next((row for row in rows if row['bookingId'] == booking_id), None) if status == 200 else None

def new_car():
    return expect('POST', '/api/cars', 201, {'brand': 'Acceptance', 'model': 'Test', 'registrationNumber': uuid.uuid4().hex[:20], 'hourlyRate': 320.00})

def active_booking():
    car = new_car()
    booking = expect('POST', '/api/bookings/reserve', 200, {'carId': car['id']})
    expect('POST', f"/api/bookings/{booking['id']}/start", 200)
    return car, booking

eventually(lambda: request('GET', '/api/cars', auth=False)[0] == 200)
print('Gateway available', flush=True)
expect('GET', '/actuator/health', 200, auth=False)
expect('GET', '/swagger-ui.html', 200, auth=False)
config = expect('GET', '/v3/api-docs/swagger-config', 200, auth=False)
check(len(config['urls']) == 4, 'Swagger contains all four services')
for doc in config['urls']:
    check('paths' in expect('GET', doc['url'], 200, auth=False), 'OpenAPI paths are present')
for method, path, data in [('POST', '/api/cars', {}), ('POST', '/api/bookings/reserve', {'carId': 1}), ('GET', '/api/billing/history', None), ('POST', '/api/telemetry/location', {})]:
    expect(method, path, 401, data, auth=False)
# The source security chain protects /error; anonymous validation failures return 401.
expect('POST', '/api/auth/register', 401, {'email': 'invalid', 'password': 'x'}, auth=False)
credentials = {'email': uuid.uuid4().hex + '@example.com', 'password': 'password123'}
auth = expect('POST', '/api/auth/register', 201, credentials, auth=False)
check(set(auth) == {'accessToken', 'tokenType', 'expiresInSeconds'}, 'Token response contract')
check(auth['tokenType'] == 'Bearer' and auth['expiresInSeconds'] == 3600, 'JWT defaults')
token = auth['accessToken']
expect('POST', '/api/auth/register', 409, credentials, auth=False)
expect('POST', '/api/auth/login', 401, {**credentials, 'password': 'wrong'}, auth=False)
token = expect('POST', '/api/auth/login', 200, credentials, auth=False)['accessToken']
expect('GET', '/api/cars/9223372036854775807', 404, auth=False)
expect('POST', '/api/cars', 400, {'brand': '', 'model': 'X', 'registrationNumber': 'INVALID', 'hourlyRate': -1})
expect('POST', '/api/bookings/reserve', 400, {'carId': 0})
expect('POST', '/api/bookings/reserve', 404, {'carId': 9223372036854775807})
expect('POST', '/api/bookings/9223372036854775807/start', 404)
expect('POST', '/api/bookings/9223372036854775807/finish', 404)
expect('POST', '/api/billing/pay', 404, {'billingId': 9223372036854775807})
expect('POST', '/api/billing/pay', 400, {'billingId': 0})
car = new_car()
check(set(car) == {'id', 'brand', 'model', 'registrationNumber', 'hourlyRate', 'status'}, 'Car response contract')
check(car['status'] == 'AVAILABLE', 'New car is available')
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    outcomes = list(pool.map(lambda _: request('POST', '/api/bookings/reserve', {'carId': car['id']}), range(8)))
check(sorted(code for code, _ in outcomes) == [200] + [409]*7, f'Concurrent reservation has one winner: {outcomes}')
booking = next(body for code, body in outcomes if code == 200)
bid = booking['id']
check(set(booking) == {'id', 'carId', 'hourlyRate', 'status', 'startedAt', 'finishedAt'}, 'Booking response contract')
check(booking['status'] == 'RESERVED' and booking['startedAt'] is None, 'Reservation state')
check(expect('GET', f"/api/cars/{car['id']}", 200)['status'] == 'RESERVED', 'Car reservation committed')
check(sql(f"SELECT COUNT(*) FROM fleet.bookings WHERE car_id={car['id']}") == '1', 'Losing transactions rolled back')
expect('POST', f'/api/bookings/{bid}/finish', 409)
check(sql(f'SELECT COUNT(*) FROM fleet.outbox_events WHERE booking_id={bid}') == '0', 'Rejected finish emits no event')
started = expect('POST', f'/api/bookings/{bid}/start', 200)
check(started['status'] == 'ACTIVE' and started['startedAt'], 'Start response')
check(expect('GET', f"/api/cars/{car['id']}", 200)['status'] == 'IN_USE', 'Car in use')
expect('POST', f'/api/bookings/{bid}/start', 409)
expect('POST', '/api/telemetry/location', 404, {'carId': 9223372036854775807, 'latitude': 1, 'longitude': 1})
expect('POST', '/api/telemetry/location', 400, {'carId': car['id'], 'latitude': 91, 'longitude': 1})
for lat in [50.4501, 51.123456]:
    expect('POST', '/api/telemetry/location', 204, {'carId': car['id'], 'latitude': lat, 'longitude': 30.5234})
check(sql(f"SELECT CONCAT(COUNT(*), ':', MAX(latitude)) FROM telemetry.car_locations WHERE car_id={car['id']}") == '1:51.123456', 'Telemetry keeps latest location only')
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    outcomes = list(pool.map(lambda _: request('POST', f'/api/bookings/{bid}/finish'), range(8)))
check(sorted(code for code, _ in outcomes) == [200] + [409]*7, f'Concurrent finish has one winner: {outcomes}')
finished = next(body for code, body in outcomes if code == 200)
check(finished['status'] == 'FINISHED' and finished['finishedAt'], 'Finish response')
check(expect('GET', f"/api/cars/{car['id']}", 200)['status'] == 'AVAILABLE', 'Car released')
expect('POST', f'/api/bookings/{bid}/finish', 409)
expect('POST', f'/api/bookings/{bid}/start', 409)
bill = eventually(lambda: invoice(bid))
check(set(bill) == {'id','bookingId','carId','chargedHours','hourlyRate','amount','status','createdAt','paidAt'}, 'Billing response contract')
check(bill['chargedHours'] == 1 and bill['amount'] == 320 and bill['status'] == 'PENDING', 'Minimum one hour billed')
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    results = list(pool.map(lambda _: request('POST', '/api/billing/pay', {'billingId': bill['id']}), range(2)))
check(sorted(code for code, _ in results) == [200, 409], 'Concurrent payment happens once')
check(invoice(bid)['paidAt'] is not None, 'Paid timestamp recorded')
# Simulate relay crash after send but before marking publication: replay durable outbox row.
sql(f'UPDATE fleet.outbox_events SET published_at=NULL WHERE booking_id={bid}')
eventually(lambda: sql(f'SELECT COUNT(*) FROM fleet.outbox_events WHERE booking_id={bid} AND published_at IS NOT NULL') == '1')
time.sleep(3)
check(sql(f'SELECT COUNT(*) FROM billing.billing_records WHERE booking_id={bid}') == '1', 'Duplicate Kafka event does not duplicate bill')
check(invoice(bid)['status'] == 'PAID', 'Replay does not reset paid invoice')
print(f'API, concurrency, persistence and Kafka replay passed ({checks} assertions)', flush=True)

if args.recovery:
    car2, booking2 = active_booking()
    compose('stop', 'kafka')
    try:
        before = time.monotonic()
        expect('POST', f"/api/bookings/{booking2['id']}/finish", 200)
        check(time.monotonic() - before < 5, 'Finish remains available during broker outage')
        check(sql(f"SELECT COUNT(*) FROM fleet.outbox_events WHERE booking_id={booking2['id']} AND published_at IS NULL") == '1', 'Pending event survives Kafka outage')
        check(expect('GET', f"/api/cars/{car2['id']}", 200)['status'] == 'AVAILABLE', 'Car released without Kafka')
        check(invoice(booking2['id']) is None, 'No invoice until delivery')
    finally:
        compose('start', 'kafka')
    check(eventually(lambda: invoice(booking2['id']))['amount'] == 320, 'Outbox delivers after broker recovery')
    print('Kafka outage and recovery passed', flush=True)
    car3, booking3 = active_booking()
    compose('stop', 'billing-service')
    try:
        expect('POST', f"/api/bookings/{booking3['id']}/finish", 200)
        eventually(lambda: sql(f"SELECT COUNT(*) FROM fleet.outbox_events WHERE booking_id={booking3['id']} AND published_at IS NOT NULL") == '1')
    finally:
        compose('start', 'billing-service')
    check(eventually(lambda: invoice(booking3['id']))['amount'] == 320, 'Kafka retains event while billing is offline')
    print('Billing outage and recovery passed', flush=True)
print(f'PASS: {checks} assertions', flush=True)
