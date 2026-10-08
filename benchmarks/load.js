import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const cfg = JSON.parse(open('/config.json'));
const failures = new Rate('business_failure');
const completed = new Counter('business_completed');
const conflicts = new Counter('business_conflicts');
const businessDuration = new Trend('business_duration', true);
const expected = http.expectedStatuses(200, 201, 204);
const base = 'http://gateway:8080';
let scenario;
if (cfg.model === 'closed') {
  scenario = {executor:'constant-vus', vus:cfg.load, duration:`${cfg.seconds}s`, gracefulStop:'15s'};
} else if (cfg.model === 'spike') {
  scenario = {executor:'ramping-arrival-rate', startRate:cfg.load, timeUnit:'1s',
    preAllocatedVUs:128, maxVUs:256, gracefulStop:'15s', stages:[
      {duration:'15s',target:cfg.load}, {duration:'2s',target:cfg.load*10},
      {duration:'20s',target:cfg.load*10}, {duration:'2s',target:cfg.load},
      {duration:'21s',target:cfg.load}]};
} else {
  scenario = {executor:'constant-arrival-rate', rate:cfg.load, timeUnit:'1s',
    duration:`${cfg.seconds}s`, preAllocatedVUs:128, maxVUs:256, gracefulStop:'15s'};
}
export const options = {
  scenarios: {measured:scenario},
  discardResponseBodies:true,
  systemTags:['status','method','name','scenario','expected_response','error','error_code'],
  summaryTrendStats:['avg','min','med','max','p(90)','p(95)','p(99)','count'],
  tags:{architecture:cfg.architecture,workload:cfg.workload,run_id:cfg.id},
  userAgent:'carsharing-scientific-benchmark/1',
  thresholds:{business_failure:['rate<0.01']},
};

function request(method,path,body,name,read=false) {
  const r=http.request(method,base+path,body==null?null:JSON.stringify(body),{
    headers:{Authorization:`Bearer ${cfg.token}`,'Content-Type':'application/json'},
    tags:{name}, timeout:'5s', responseType:read?'text':'none', responseCallback:expected,
  });
  if(r.status===409) conflicts.add(1);
  return r;
}
export default function() {
  const start=Date.now();
  const n=exec.scenario.iterationInTest;
  let r;
  const id=1+(n%cfg.cars);
  switch(cfg.workload) {
    case 'read_one': r=request('GET',`/api/cars/${id}`,null,'GET car'); break;
    case 'catalog_list': r=request('GET','/api/cars',null,'GET catalogue'); break;
    case 'history': r=request('GET','/api/billing/history',null,'GET billing history'); break;
    case 'login': r=request('POST','/api/auth/login',cfg.credentials,'POST login'); break;
    case 'telemetry':
      r=request('POST','/api/telemetry/location',{carId:id,latitude:50.4501,longitude:30.5234},'POST location'); break;
    case 'mixed':
      if(n%20===0) r=request('POST','/api/auth/login',cfg.credentials,'POST login');
      else if(n%20===1) r=request('GET','/api/cars',null,'GET catalogue');
      else if(n%10===2) r=request('POST','/api/telemetry/location',{carId:id,latitude:50.45,longitude:30.52},'POST location');
      else r=request('GET',`/api/cars/${id}`,null,'GET car');
      break;
    case 'lifecycle': {
      const carId=__VU;
      r=request('POST','/api/bookings/reserve',{carId},'POST reserve',true);
      if(r.status!==200) break;
      let booking;
      try { booking=r.json().id; } catch (_) { failures.add(true);return; }
      r=request('POST',`/api/bookings/${booking}/start`,null,'POST start');
      if(r.status!==200) break;
      r=request('POST',`/api/bookings/${booking}/finish`,null,'POST finish');
      break;
    }
    default: throw new Error(`Unknown workload ${cfg.workload}`);
  }
  const ok=r && r.status>=200 && r.status<300;
  failures.add(!ok);
  if(ok) completed.add(1);
  businessDuration.add(Date.now()-start);
}
export function handleSummary(data) {
  return {'/results/summary.json':JSON.stringify(data,null,2)};
}
