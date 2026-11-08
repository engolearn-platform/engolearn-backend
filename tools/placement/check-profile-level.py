import json,os,uuid,urllib.request,urllib.error
from pathlib import Path
from http.cookiejar import CookieJar
from urllib.parse import urlparse
from concurrent.futures import ThreadPoolExecutor
base=os.environ.get('PLACEMENT_CHECK_BASE','http://127.0.0.1:3000')
assert urlparse(base).hostname in ('127.0.0.1','localhost'),'Local development target required'
assert os.environ.get('PLACEMENT_LIVE_ALLOW_WRITES')=='1','Opt in to disposable local test accounts/attempts'
checks=[];accounts=[];attempts=[]
root=Path(__file__).resolve().parents[2]
seed=json.loads((root/'tools/mongo-json-importer/samples/placement-v3-test-sets-v2.importer.json').read_text(encoding='utf-8'))
answers={q['id']:q['correct_option_id'] for s in seed['collections']['placement_test_sets'] for q in s['items']}
api='/api/v1/placement/attempts'
password='LevelCheck-Only-2026!'
def client():return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))
def check(label,ok):
 checks.append({'check':label,'pass':bool(ok)})
 assert ok,label
def req(c,m,p,b=None):
 q=urllib.request.Request(base+p,data=json.dumps(b).encode() if b is not None else None,headers={'Origin':base,'Content-Type':'application/json'},method=m)
 try:r=c.open(q,timeout=30)
 except urllib.error.HTTPError as e:r=e
 raw=r.read();return r.status,json.loads(raw) if raw else None
def data(c,m,p,b=None,status=200):
 code,out=req(c,m,p,b);check(m+' '+p+' => '+str(status),code==status);return out['data']
def level(c):return data(c,'GET','/api/v1/topic-learning/viewer')['cefrLevel']
def register(c):
 email='level-live-'+uuid.uuid4().hex[:10]+'@example.test';accounts.append(email)
 data(c,'POST','/api/v1/auth/register',{'email':email,'password':password},201)
 check('registration stays A1',level(c)=='A1');return email
def answer(c,count):
 start=data(c,'POST',api,status=201);id=start['attemptId'];attempts.append(id)
 qs=data(c,'GET',api+'/'+id)['questions']
 for q in qs[:count]:data(c,'PATCH',api+'/'+id+'/answers',{'questionId':q['id'],'selectedOptionId':answers[q['id']]})
 data(c,'POST',api+'/'+id+'/submit');return id
# Explicit historical reconciliation, never a result-read backfill.
historicalEmail=os.environ.get('PLACEMENT_HISTORICAL_EMAIL')
historicalPassword=os.environ.get('PLACEMENT_HISTORICAL_PASSWORD')
historicalId=os.environ.get('PLACEMENT_HISTORICAL_ATTEMPT')
assert not any((historicalEmail,historicalPassword,historicalId)) or all((historicalEmail,historicalPassword,historicalId)), 'Provide all historical test account settings together'
if historicalEmail:
 old=client();data(old,'POST','/api/v1/auth/login',{'email':historicalEmail,'password':historicalPassword})
 historical=historicalId
 oldResult=data(old,'GET',api+'/'+historical+'/result')
 activated=data(old,'POST',api+'/'+historical+'/learning')
 check('historical result promotes through explicit command',activated['profileLevel']=='A2' and level(old)=='A2' and oldResult['placementLevel']=='A2')
u=client();email=register(u);owned=answer(u,8)
check('User submit promotes profile before learning click',level(u)=='A2')
check('same level command returns actual profile level',data(u,'POST',api+'/'+owned+'/learning')['profileLevel']=='A2')
check('caller cannot choose an arbitrary higher level',data(u,'POST',api+'/'+owned+'/learning',{'profileLevel':'C2'})['profileLevel']=='A2')
with ThreadPoolExecutor(max_workers=4) as pool:
 codes=list(pool.map(lambda _:req(u,'POST',api+'/'+owned+'/learning')[0],range(4)))
check('concurrent repeated learning commands succeed',codes==[200]*4 and level(u)=='A2')
stranger=client();register(stranger)
check('foreign owner cannot activate someone else result',req(stranger,'POST',api+'/'+owned+'/learning')[0]==404 and level(stranger)=='A1')
check('unauthenticated learning command denied',req(client(),'POST',api+'/'+owned+'/learning')[0]==401)
# A discarded Guest B2 must not override an existing User A2 result.
data(u,'POST','/api/v1/auth/logout',{})
discarded=answer(u,20)
data(u,'POST','/api/v1/auth/login',{'email':email,'password':password})
gate=data(u,'GET',api+'/'+discarded+'/claim-gate')
check('USER_HAS_RESULT uses own result and preserves its profile level',gate['claimStatus']=='USER_HAS_RESULT' and gate['resultAttemptId']==owned and level(u)=='A2')
g=client();guest=answer(g,8)
check('unclaimed Guest still cannot see a result',req(g,'GET',api+'/'+guest+'/result')[0]==401)
register(g);check('Guest score does not promote before authenticated claim',level(g)=='A1')
with ThreadPoolExecutor(max_workers=4) as pool:
 claimResponses=list(pool.map(lambda _:req(g,'GET',api+'/'+guest+'/claim-gate'),range(4)))
check('concurrent Guest claims all succeed',all(code==200 for code,_ in claimResponses))
claimed=claimResponses[0][1]['data']
check('Guest claim and profile promotion commit together',claimed['resultAttemptId']==guest and level(g)=='A2')
again=data(g,'GET',api+'/'+guest+'/claim-gate')
check('repeat claim stays idempotent',again['resultAttemptId']==guest and level(g)=='A2')
print(json.dumps({'base':base,'accountsCreated':accounts,'attemptsCreated':attempts,'passed':len(checks),'checks':checks},ensure_ascii=False,indent=2))
