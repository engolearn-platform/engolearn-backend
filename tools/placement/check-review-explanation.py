import json,os,urllib.request,urllib.error
from http.cookiejar import CookieJar
from pathlib import Path
from urllib.parse import urlparse
base=os.environ.get('PLACEMENT_CHECK_BASE','http://127.0.0.1:3000')
assert urlparse(base).hostname in ('127.0.0.1','localhost'),'Local development target required'
email=os.environ['PLACEMENT_TEST_EMAIL'];password=os.environ['PLACEMENT_TEST_PASSWORD'];attempt=os.environ['PLACEMENT_TEST_ATTEMPT']
api='/api/v1/placement/attempts/'+attempt
checks=[]
def client():return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))
def req(c,m,p,b=None):
 q=urllib.request.Request(base+p,data=json.dumps(b).encode() if b else None,headers={'Origin':base,'Content-Type':'application/json'},method=m)
 try:r=c.open(q,timeout=20)
 except urllib.error.HTTPError as e:r=e
 raw=r.read();return r.status,json.loads(raw) if raw else None
def check(s,ok):
 checks.append({'check':s,'pass':bool(ok)})
 assert ok,s
c=client();check('anonymous review denied',req(c,'GET',api+'/review')[0]==401)
code,_=req(c,'POST','/api/v1/auth/login',{'email':email,'password':password});check('test User login',code==200)
code,r=req(c,'GET',api+'/review');check('owned submitted review authorized',code==200)
qs=r['data']['questions'];check('all20 real V2 explanations returned',len(qs)==20 and all(q.get('explanation','').strip() for q in qs))
check('Listening explanations returned',all(q.get('explanation','').strip() for q in qs if q['section']=='LISTENING'))
code,ordinary=req(c,'GET',api);check('attempt endpoint never exposes explanations',code==200 and all('explanation' not in q for q in ordinary['data']['questions']))
check('review does not expose transcript or identity fields',not any(k in json.dumps(r) for k in ['"transcript"','"guestSessionId"','"userId"']))
other=client();check('separate unauthenticated browser remains denied',req(other,'GET',api+'/review')[0]==401)
print(json.dumps({'base':base,'attemptId':attempt,'passed':len(checks),'checks':checks},indent=2))
