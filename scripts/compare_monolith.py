"""Compare an isolated unchanged monolith with the microservices over real HTTP."""
import argparse
import json
import time
import urllib.error
import urllib.request
import uuid

parser=argparse.ArgumentParser()
parser.add_argument('--source', default='http://127.0.0.1:18080')
parser.add_argument('--target', default='http://127.0.0.1:8080')
args=parser.parse_args()

def call(base, method, path, payload=None, token=None):
    headers={'Content-Type':'application/json'}
    if token:
        headers['Authorization']='Bearer '+token
    req=urllib.request.Request(base+path,data=None if payload is None else json.dumps(payload).encode(),headers=headers,method=method)
    try:
        response=urllib.request.urlopen(req,timeout=15)
    except urllib.error.HTTPError as error:
        response=error
    with response:
        body=response.read().decode()
        return response.status, json.loads(body) if body else None

def normalized(value):
    if isinstance(value,dict):
        return {key: ('<generated>' if key in {'id','carId','bookingId','accessToken','registrationNumber','timestamp','startedAt','finishedAt','createdAt','paidAt'} and val is not None else normalized(val)) for key,val in value.items()}
    if isinstance(value,list):
        return [normalized(item) for item in value]
    return value

def scenario(base):
    results={}
    def run(name,method,path,payload=None,token=None):
        status,body=call(base,method,path,payload,token)
        results[name]=(status,normalized(body))
        return body
    run('anonymous history','GET','/api/billing/history')
    run('anonymous reserve','POST','/api/bookings/reserve',{'carId':1})
    run('invalid registration','POST','/api/auth/register',{'email':'invalid','password':'x'})
    credentials={'email':uuid.uuid4().hex+'@example.com','password':'password123'}
    auth=run('register','POST','/api/auth/register',credentials)
    run('duplicate email','POST','/api/auth/register',credentials)
    run('bad password','POST','/api/auth/login',{**credentials,'password':'wrong'})
    token=run('login','POST','/api/auth/login',credentials)['accessToken']
    run('missing car','GET','/api/cars/9223372036854775807')
    run('invalid car','POST','/api/cars',{'brand':'','model':'X','registrationNumber':'INVALID','hourlyRate':-1},token)
    run('invalid reserve','POST','/api/bookings/reserve',{'carId':0},token)
    run('missing reserve','POST','/api/bookings/reserve',{'carId':9223372036854775807},token)
    run('missing start','POST','/api/bookings/9223372036854775807/start',token=token)
    run('missing finish','POST','/api/bookings/9223372036854775807/finish',token=token)
    run('missing pay','POST','/api/billing/pay',{'billingId':9223372036854775807},token)
    car=run('create car','POST','/api/cars',{'brand':'Parity','model':'Test','registrationNumber':uuid.uuid4().hex[:20],'hourlyRate':320},token)
    cid=car['id']
    booking=run('reserve','POST','/api/bookings/reserve',{'carId':cid},token)
    bid=booking['id']
    run('reserved car','GET',f'/api/cars/{cid}')
    run('duplicate reserve','POST','/api/bookings/reserve',{'carId':cid},token)
    run('finish before start','POST',f'/api/bookings/{bid}/finish',token=token)
    run('start','POST',f'/api/bookings/{bid}/start',token=token)
    run('in use car','GET',f'/api/cars/{cid}')
    run('repeat start','POST',f'/api/bookings/{bid}/start',token=token)
    run('missing telemetry car','POST','/api/telemetry/location',{'carId':9223372036854775807,'latitude':1,'longitude':1},token)
    run('invalid latitude','POST','/api/telemetry/location',{'carId':cid,'latitude':91,'longitude':1},token)
    run('location','POST','/api/telemetry/location',{'carId':cid,'latitude':50.45,'longitude':30.5},token)
    run('finish','POST',f'/api/bookings/{bid}/finish',token=token)
    run('repeat finish','POST',f'/api/bookings/{bid}/finish',token=token)
    run('available car','GET',f'/api/cars/{cid}')
    bill=None
    for _ in range(60):
        status,history=call(base,'GET','/api/billing/history',token=token)
        bill=next((b for b in history if b['bookingId']==bid),None)
        if bill:
            break
        time.sleep(1)
    assert bill, 'No invoice from '+base
    results['invoice']=(200,normalized(bill))
    run('pay','POST','/api/billing/pay',{'billingId':bill['id']},token)
    run('repeat pay','POST','/api/billing/pay',{'billingId':bill['id']},token)
    return results,token

source,legacy_token=scenario(args.source)
target,_=scenario(args.target)
for name in source:
    assert source[name]==target[name], f'{name}: source={source[name]}, target={target[name]}'
# Legacy JWTs remain usable when JWT_SECRET matches; no identity database lookup is required.
assert call(args.target,'GET','/api/billing/history',token=legacy_token)[0]==200
source_doc=call(args.source,'GET','/v3/api-docs')[1]
merged_paths={}
merged_schemas={}
for service in ['fleet','identity','billing','telemetry']:
    doc=call(args.target,'GET','/v3/api-docs/'+service)[1]
    merged_paths.update(doc['paths'])
    merged_schemas.update(doc.get('components',{}).get('schemas',{}))
assert source_doc['paths']==merged_paths, 'OpenAPI operations differ'
assert source_doc['components']['schemas']==merged_schemas, 'OpenAPI DTO schemas differ'
aggregate=call(args.target,'GET','/v3/api-docs')[1]
assert aggregate['paths']==merged_paths, 'Gateway OpenAPI snapshot needs updating'
assert aggregate['components']['schemas']==merged_schemas, 'Gateway DTO schemas need updating'
print(f'PASS: {len(source)} source/target HTTP scenarios, legacy JWT, all OpenAPI paths and DTO schemas match')
