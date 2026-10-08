"""Reproducible local comparative benchmark. Only csbench-* stacks are mutated.
The existing development stack is stopped for isolation and restored in finally.
All measurements are checkpointed after each phase. No source project is modified.
"""
import argparse, concurrent.futures, csv, datetime as dt, hashlib, json, os, random
import re, statistics, subprocess, sys, threading, time, urllib.error, urllib.request
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
BENCH=ROOT/'benchmarks'
PYTHON=sys.executable
TOPIC='carsharing.booking-finished.v1'
SEED=20261008
SECRET='change-this-development-secret-to-a-random-32-byte-value'

def utc(): return dt.datetime.now(dt.timezone.utc).isoformat()
def save(path, value):
    Path(path).parent.mkdir(parents=True,exist_ok=True)
    Path(path).write_text(json.dumps(value,indent=2,ensure_ascii=False),encoding='utf-8')
def command(args, timeout=180, check=True, input=None):
    p=subprocess.run(args,cwd=ROOT,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout,input=input)
    if check and p.returncode:
        raise RuntimeError(f'{args[:5]} exit {p.returncode}: {p.stderr[-2000:]} {p.stdout[-1000:]}')
    return p

def http(base, method, path, data=None, token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request(base+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
    try: response=urllib.request.urlopen(req,timeout=8)
    except urllib.error.HTTPError as e: response=e
    with response:
        body=response.read().decode()
        try: body=json.loads(body) if body else None
        except json.JSONDecodeError: pass
        return response.status,body

def wait_ready(base, seconds=120):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        try:
            if http(base,'GET','/api/cars/1')[0]==200:return
        except Exception:pass
        time.sleep(1)
    raise RuntimeError('API not ready: '+base)

class Experiment:
    def __init__(self,args):
        self.args=args
        self.out=BENCH/'results'/args.id
        self.out.mkdir(parents=True,exist_ok=True)
        self.rows=[]
        self.arch=None
        self.counter=0
        self.dev_running=[]
        self.credentials={'email':'benchmark@example.com','password':'BenchmarkPassword123'}
        self.token=None
        self.cars=1000
        self.started=time.monotonic()
        self.stop_stats=threading.Event()
        self.phase='setup'
        self.events_file=self.out/'events.jsonl'
        self.configs={}
        self.manifest={'id':args.id,'created_at':utc(),'seed':SEED,'profile':args.profile,
          'source_scope':'whole-system comparison: architecture AND PostgreSQL vs MySQL differ',
          'backend_budget':{'cpus':6,'cpuset':'0-5','memory_mib':8192},
          'generator_budget':{'cpus':2,'cpuset':'6-7','memory_mib':2048},
          'matrix_repetitions':3,'measurement_seconds':25,'warmup_seconds':5,
          'notes':['closed lifecycle load is VUs; all other regular matrix loads are iterations/s',
                   'databases reset between architecture blocks, not phases; catalogue stays fixed at 1000 cars',
                   'booking/billing rows accumulate; phase order randomized and database counts logged',
                   'lifecycle phases drain billing outside measured interval (180s cap) to avoid carrying backlog into later phases',
                   'both systems have one Nginx gateway; this is an experiment-only addition for monolith',
                   'request timeout=5s; max open-model VUs=256; dropped iterations are retained',
                   'resource samples include generator separately; measurements run sequentially on one host'],
          'artifacts':{},'state':'preparing'}
        self.event('created')
    def event(self,kind,**data):
        entry={'time':utc(),'phase':self.phase,'kind':kind,**data}
        with self.events_file.open('a',encoding='utf-8') as f:f.write(json.dumps(entry,ensure_ascii=False)+'\n')
        print(json.dumps(entry,ensure_ascii=False),flush=True)
    def compose(self,arch,*args,**kwargs):
        return command(['docker','compose','-p','csbench-'+arch,'-f',str(self.configs[arch]),*args],**kwargs)
    def sql(self,query,arch=None):
        arch=arch or self.arch
        if arch=='micro':
            p=self.compose(arch,'exec','-T','mysql','mysql','-uroot','-plocal-root-password','--batch','--skip-column-names',input=query,timeout=90)
        else:
            p=self.compose(arch,'exec','-T','postgres','psql','-U','car_sharing','-d','car_sharing','-v','ON_ERROR_STOP=1','-At','-F','\t',input=query,timeout=90)
        return p.stdout.strip()
    def prepare(self):
        info=json.loads(command(['docker','info','--format','{{json .}}']).stdout)
        self.manifest['docker']={key:info.get(key) for key in ['NCPU','MemTotal','ServerVersion','KernelVersion','OperatingSystem','OSType','Architecture','CgroupVersion','Driver']}
        if info['NCPU']<8:raise RuntimeError('This profile requires at least eight Docker CPUs')
        self.manifest['versions']={
          'docker':command(['docker','version','--format','{{json .}}']).stdout,
          'compose':command(['docker','compose','version']).stdout,
          'python':sys.version,
          'k6':command(['docker','run','--rm','grafana/k6:1.3.0','version']).stdout}
        self.manifest['source_hashes']={}
        for label,path in [('micro',ROOT),('mono',ROOT.parent/'car_sharing_module_monolith')]:
            hashes={}
            for p in path.rglob('*'):
                if p.is_file() and (p.suffix in {'.java','.xml','.properties','.sql','.yaml'} or p.name=='Dockerfile'):
                    if any(part in {'target','.git','.idea','benchmarks','tmp_doc_review'} for part in p.relative_to(path).parts):continue
                    hashes[str(p.relative_to(path))]=hashlib.sha256(p.read_bytes()).hexdigest()
            self.manifest['source_hashes'][label]=hashes
        for p in BENCH.glob('*'):
            if p.is_file():self.manifest['artifacts'][p.name]=hashlib.sha256(p.read_bytes()).hexdigest()
        original=json.loads(command(['docker','compose','config','--format','json']).stdout)
        micro=original
        micro['name']='csbench-micro'
        budget={'fleet-service':(1.5,1536),'identity-service':(.5,768),'billing-service':(.5,768),'telemetry-service':(.5,768),
          'mysql':(1.5,2048),'kafka':(1.25,2048),'gateway':(.25,256)}
        images={}
        for name,svc in micro['services'].items():
            image=svc.get('image','carsharing-microservices-'+name+':latest')
            inspect=json.loads(command(['docker','image','inspect',image]).stdout)[0]
            svc['image']=inspect['Id'];images[name]={'id':inspect['Id'],'digests':inspect.get('RepoDigests'),'tag':image}
            svc.pop('build',None)
            svc['restart']='no'
            if name in budget:
                cpu,mem=budget[name];svc.update(cpus=cpu,cpuset='0-5',mem_limit=str(mem)+'m',memswap_limit=str(mem)+'m')
            if name.endswith('-service'):
                svc['environment']['JAVA_TOOL_OPTIONS']='-XX:MaxRAMPercentage=60 -Xlog:gc:stdout:time,level,tags'
                svc['environment']['JWT_SECRET']=SECRET
            if name=='gateway':svc['ports']=[{'target':8080,'published':'18081','host_ip':'127.0.0.1','protocol':'tcp'}]
        for name in micro.get('volumes',{}):micro['volumes'][name]={'name':'csbench-micro-'+name}
        for name in micro.get('networks',{}):micro['networks'][name]={'name':'csbench-micro_'+name}
        monoimage=json.loads(command(['docker','image','inspect','carsharing-reference-test-monolith:latest']).stdout)[0]
        images['monolith']={'id':monoimage['Id'],'tag':'carsharing-reference-test-monolith:latest'}
        mono={'name':'csbench-mono','services':{
          'postgres':{'image':'postgres:17-alpine','cpus':1.5,'cpuset':'0-5','mem_limit':'2048m','memswap_limit':'2048m',
            'environment':{'POSTGRES_DB':'car_sharing','POSTGRES_USER':'car_sharing','POSTGRES_PASSWORD':'car_sharing'},
            'volumes':['pgdata:/var/lib/postgresql/data'],
            'healthcheck':{'test':['CMD-SHELL','pg_isready -U car_sharing'],'interval':'2s','retries':60}},
          'monolith':{'image':monoimage['Id'],'cpus':4.25,'cpuset':'0-5','mem_limit':'5888m','memswap_limit':'5888m',
            'environment':{'DB_URL':'jdbc:postgresql://postgres:5432/car_sharing','DB_USERNAME':'car_sharing','DB_PASSWORD':'car_sharing','JWT_SECRET':SECRET,
              'JAVA_TOOL_OPTIONS':'-XX:MaxRAMPercentage=60 -Xlog:gc:stdout:time,level,tags'},
            'depends_on':{'postgres':{'condition':'service_healthy'}}},
          'gateway':{'image':'nginx:1.28-alpine','cpus':.25,'cpuset':'0-5','mem_limit':'256m','memswap_limit':'256m',
            'ports':['127.0.0.1:18082:8080'],'volumes':[str(BENCH/'mono-nginx.conf')+':/etc/nginx/nginx.conf:ro'],
            'depends_on':['monolith']}},'volumes':{'pgdata':{'name':'csbench-mono-pgdata'}}}
        for name in ['postgres','gateway']:
            image=mono['services'][name]['image'];inspection=json.loads(command(['docker','image','inspect',image]).stdout)[0]
            mono['services'][name]['image']=inspection['Id'];images['mono-'+name]={'id':inspection['Id'],'tag':image}
        self.manifest['images']=images
        for arch,cfg in [('micro',micro),('mono',mono)]:
            path=self.out/(arch+'.compose.json');save(path,cfg);self.configs[arch]=path
            self.compose(arch,'config','--quiet')
        self.dev_running=command(['docker','compose','ps','--status','running','--services']).stdout.split()
        self.manifest['development_services_to_restore']=self.dev_running
        save(self.out/'manifest.json',self.manifest)
        if self.dev_running:command(['docker','compose','stop',*self.dev_running],timeout=120)
        self.stats_thread=threading.Thread(target=self.stats,daemon=True);self.stats_thread.start()
    def stats(self):
        target=self.out/'resources.jsonl'
        while not self.stop_stats.is_set():
            arch=self.arch;phase=self.phase
            try:
                names=command(['docker','ps','--format','{{.Names}}'],timeout=10).stdout.split()
                names=[n for n in names if n.startswith('csbench-')]
                if names:
                    p=command(['docker','stats','--no-stream','--format','{{json .}}',*names],timeout=15,check=False)
                    now=utc()
                    with target.open('a',encoding='utf-8') as f:
                        for line in p.stdout.splitlines():
                            if line.startswith('{'):
                                f.write(json.dumps({'time':now,'phase':phase,'architecture':arch,'stats':json.loads(line)})+'\n')
            except Exception as e:
                with target.open('a',encoding='utf-8') as f:f.write(json.dumps({'time':utc(),'error':str(e)})+'\n')
            self.stop_stats.wait(3)
    def start(self,arch,label):
        self.arch=arch;self.phase=label+'-startup'
        for other in self.configs:
            if other!=arch:self.compose(other,'stop',check=False)
        # These named volumes belong exclusively to the experiment, never to development.
        self.compose(arch,'down','--volumes',check=False)
        start=time.monotonic()
        p=self.compose(arch,'up','-d','--no-build',timeout=240)
        (self.out/(label+'-startup.log')).write_text(p.stdout+p.stderr,encoding='utf-8')
        self.base='http://127.0.0.1:'+('18081' if arch=='micro' else '18082')
        wait_ready(self.base)
        self.event('ready',architecture=arch,startup_seconds=time.monotonic()-start)
        self.seed(1000)
    def seed(self,n):
        self.cars=n
        table='fleet.cars' if self.arch=='micro' else 'catalog_schema.cars'
        location='telemetry.car_locations' if self.arch=='micro' else 'telemetry_schema.car_locations'
        current=int(self.sql(f'SELECT COUNT(*) FROM {table};').splitlines()[-1])
        values=[f"({i},'Benchmark','Car','BENCH{i:014d}',320.00,'AVAILABLE',0)" for i in range(current+1,n+1)]
        for begin in range(0,len(values),1000):
            self.sql(f'INSERT INTO {table} (id,brand,model,registration_number,hourly_rate,status,version) VALUES '+','.join(values[begin:begin+1000])+';')
        if self.arch=='mono':self.sql("SELECT setval(pg_get_serial_sequence('catalog_schema.cars','id'),(SELECT MAX(id) FROM catalog_schema.cars));")
        now='CURRENT_TIMESTAMP'
        if self.arch=='micro':
            self.sql(f'INSERT IGNORE INTO {location}(car_id,latitude,longitude,recorded_at) SELECT id,50.45,30.52,{now} FROM {table};')
        else:self.sql(f'INSERT INTO {location}(car_id,latitude,longitude,recorded_at) SELECT id,50.45,30.52,{now} FROM {table} ON CONFLICT (car_id) DO NOTHING;')
        status,body=http(self.base,'POST','/api/auth/register',self.credentials)
        if status==409:status,body=http(self.base,'POST','/api/auth/login',self.credentials)
        if status not in [200,201]:raise RuntimeError(f'Cannot prepare identity: {status} {body}')
        self.token=body['accessToken']
        self.event('seeded',cars=n)
    def counts(self):
        names=['fleet.cars','fleet.bookings','billing.billing_records','fleet.outbox_events'] if self.arch=='micro' else ['catalog_schema.cars','booking_schema.bookings','billing_schema.billing_records']
        return {name:int(self.sql(f'SELECT COUNT(*) FROM {name};').splitlines()[-1]) for name in names}
    def phase_run(self,workload,load,model,seconds,repeat,kind='matrix'):
        self.counter+=1
        ident=f'{self.counter:03d}-{self.arch}-r{repeat}-{kind}-{workload}-{load}'
        folder=self.out/ident;folder.mkdir()
        self.phase=ident
        cfg={'id':ident,'architecture':self.arch,'workload':workload,'load':load,'model':model,'seconds':seconds,
          'repeat':repeat,'kind':kind,'cars':self.cars,'token':self.token,'credentials':self.credentials}
        save(folder/'config.json',cfg)
        metadata={k:v for k,v in cfg.items() if k not in {'token','credentials'}}
        metadata.update(started_at=utc(),counts_before=self.counts())
        self.event('phase_start',config=metadata)
        args=['docker','run','--rm','--name','csbench-generator','--network','csbench-'+self.arch+'_default',
          '--cpuset-cpus','6-7','--cpus','2','--memory','2g','--memory-swap','2g',
          '-v',str(BENCH/'load.js')+':/load.js:ro','-v',str(folder/'config.json')+':/config.json:ro',
          '-v',str(folder)+':/results','grafana/k6:1.3.0','run','--quiet','--out','csv=/results/raw.csv.gz','/load.js']
        start=time.monotonic()
        p=command(args,timeout=seconds+120,check=False)
        (folder/'k6.log').write_text(p.stdout+p.stderr,encoding='utf-8')
        metadata.update(ended_at=utc(),wall_seconds=time.monotonic()-start,exit_code=p.returncode,counts_after=self.counts())
        if workload=='lifecycle':
            drain_start=time.monotonic()
            book='fleet.bookings' if self.arch=='micro' else 'booking_schema.bookings'
            bill='billing.billing_records' if self.arch=='micro' else 'billing_schema.billing_records'
            query=f"SELECT COUNT(*) FROM {book} b LEFT JOIN {bill} r ON b.id=r.booking_id WHERE b.status='FINISHED' AND r.id IS NULL;"
            pending=int(self.sql(query).splitlines()[-1])
            metadata['pending_billing_after_load']=pending
            while pending and time.monotonic()-drain_start<180:
                time.sleep(1)
                pending=int(self.sql(query).splitlines()[-1])
            metadata['drain_seconds']=time.monotonic()-drain_start
            metadata['pending_billing_after_drain']=pending
            metadata['drain_censored']=bool(pending)
        save(folder/'metadata.json',metadata)
        self.rows.append(metadata);save(self.out/'phases.json',self.rows)
        summary=json.loads((folder/'summary.json').read_text()) if (folder/'summary.json').exists() else {}
        metrics=summary.get('metrics',{})
        self.event('phase_end',id=ident,exit_code=p.returncode,
          requests=metrics.get('http_reqs',{}).get('values',{}).get('count'),
          p95_ms=metrics.get('http_req_duration',{}).get('values',{}).get('p(95)'),
          errors=metrics.get('business_failure',{}).get('values',{}).get('rate'),
          dropped=metrics.get('dropped_iterations',{}).get('values',{}).get('count',0))
        if p.returncode not in [0,99]:raise RuntimeError('Load generator failed: '+ident+' '+p.stderr[-1000:])
        return folder
    def capture(self,label):
        # Drain asynchronous billing, then inspect invariants without changing data.
        book='fleet.bookings' if self.arch=='micro' else 'booking_schema.bookings'
        bill='billing.billing_records' if self.arch=='micro' else 'billing_schema.billing_records'
        car='fleet.cars' if self.arch=='micro' else 'catalog_schema.cars'
        pending_query=f"SELECT COUNT(*) FROM {book} b LEFT JOIN {bill} r ON b.id=r.booking_id WHERE b.status='FINISHED' AND r.id IS NULL;"
        deadline=time.monotonic()+180
        while time.monotonic()<deadline:
            if self.sql(pending_query).splitlines()[-1]=='0':break
            time.sleep(1)
        report={'time':utc(),'architecture':self.arch,'counts':self.counts(),
          'finished_without_bill':int(self.sql(pending_query).splitlines()[-1]),
          'duplicate_bills':int(self.sql(f'SELECT COUNT(*) FROM (SELECT booking_id FROM {bill} GROUP BY booking_id HAVING COUNT(*)>1) d;').splitlines()[-1]),
          'negative_amounts':int(self.sql(f'SELECT COUNT(*) FROM {bill} WHERE amount<0;').splitlines()[-1]),
          'active_bookings':int(self.sql(f"SELECT COUNT(*) FROM {book} WHERE status<>'FINISHED';").splitlines()[-1]),
          'nonavailable_cars':int(self.sql(f"SELECT COUNT(*) FROM {car} WHERE status<>'AVAILABLE';").splitlines()[-1])}
        if self.arch=='micro':
            report['pending_outbox']=int(self.sql('SELECT COUNT(*) FROM fleet.outbox_events WHERE published_at IS NULL;').splitlines()[-1])
            delay=f'SELECT b.id,b.car_id,TIMESTAMPDIFF(MICROSECOND,b.finished_at,r.created_at)/1000.0,r.charged_hours,r.amount FROM {book} b JOIN {bill} r ON b.id=r.booking_id ORDER BY b.id;'
        else:delay=f'SELECT b.id,b.car_id,EXTRACT(EPOCH FROM (r.created_at-b.finished_at))*1000,r.charged_hours,r.amount FROM {book} b JOIN {bill} r ON b.id=r.booking_id ORDER BY b.id;'
        (self.out/(label+'-billing-delays.tsv')).write_text('booking_id\tcar_id\tcreated_minus_finished_ms\tcharged_hours\tamount\n'+self.sql(delay)+'\n',encoding='utf-8')
        save(self.out/(label+'-invariants.json'),report)
        logs=self.compose(self.arch,'logs','--no-color',timeout=60)
        (self.out/(label+'-containers.log')).write_text(logs.stdout+logs.stderr,encoding='utf-8')
        containers=self.compose(self.arch,'ps','-q').stdout.split()
        if containers:
            # Runtime state and limits only; do not store unrelated host/container secrets.
            inspected=json.loads(command(['docker','inspect',*containers]).stdout)
            save(self.out/(label+'-container-state.json'),[{'Name':x['Name'],'Image':x['Image'],'State':x['State'],
              'limits':{k:x['HostConfig'].get(k) for k in ['NanoCpus','CpusetCpus','Memory','MemorySwap']}} for x in inspected])
        self.event('invariants',result=report)
    def main(self):
        self.prepare()
        self.manifest['state']='running';save(self.out/'manifest.json',self.manifest)
        rng=random.Random(SEED)
        matrix=[('read_one',x,'open') for x in [50,250,1000]]+\
          [('catalog_list',x,'open') for x in [5,20,80]]+\
          [('login',x,'open') for x in [2,10,30]]+\
          [('telemetry',x,'open') for x in [20,100,400]]+\
          [('lifecycle',x,'closed') for x in [1,8,32]]+\
          [('mixed',x,'open') for x in [20,100,400]]
        repeats=1 if self.args.profile=='pilot' else 3
        for rep in range(1,repeats+1):
            order=['mono','micro'] if rep%2 else ['micro','mono']
            phases=matrix.copy();rng.shuffle(phases)
            if self.args.profile=='pilot':phases=[('read_one',50,'open'),('lifecycle',8,'closed'),('mixed',100,'open')]
            for arch in order:
                label=f'r{rep}-{arch}'
                self.start(arch,label)
                self.phase_run('mixed',30,'open',10,rep,'warmup')
                for work,load,model in phases:
                    self.phase_run(work,max(1,min(load,10)),model,5,rep,'warmup')
                    self.phase_run(work,load,model,8 if self.args.profile=='pilot' else 25,rep)
                if self.args.profile!='pilot' and rep==3:
                    for rate in [2000,4000,8000]:
                        self.phase_run('read_one',rate,'open',20,rep,'saturation')
                    self.phase_run('mixed',100,'spike',60,rep,'spike')
                    self.phase_run('mixed',100,'open',300,rep,'soak')
                    self.seed(10000)
                    self.phase_run('catalog_list',20,'open',30,rep,'volume-10000')
                self.capture(label)
                self.compose(arch,'stop',timeout=90)
        self.manifest['state']='measurement_complete'
        self.manifest['finished_at']=utc();self.manifest['elapsed_seconds']=time.monotonic()-self.started
        save(self.out/'manifest.json',self.manifest)
    def cleanup(self):
        self.phase='cleanup'
        self.stop_stats.set()
        if hasattr(self,'stats_thread'):self.stats_thread.join(timeout=20)
        for arch in self.configs:
            try:self.compose(arch,'stop',timeout=90,check=False)
            except Exception as e:self.event('cleanup_error',error=str(e))
        if self.dev_running:
            try:command(['docker','compose','start',*self.dev_running],timeout=120)
            except Exception as e:self.event('restore_error',error=str(e))
        self.event('stopped',elapsed_seconds=time.monotonic()-self.started)

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--profile',choices=['pilot','full'],default='pilot')
    p.add_argument('--id',default=dt.datetime.now().strftime('%Y%m%d-%H%M%S'))
    args=p.parse_args()
    experiment=Experiment(args)
    try:experiment.main()
    except BaseException as e:
        experiment.manifest['state']='failed';experiment.manifest['error']=str(e)
        save(experiment.out/'manifest.json',experiment.manifest)
        experiment.event('fatal',error=str(e));raise
    finally:experiment.cleanup()
