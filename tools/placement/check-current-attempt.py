import json,os,uuid,time,urllib.request,urllib.error
from http.cookiejar import CookieJar
from urllib.parse import urlparse
from datetime import datetime
def same_deadline(a,b):
    return int(datetime.fromisoformat(a.replace("Z","+00:00")).timestamp()*1000)==int(datetime.fromisoformat(b.replace("Z","+00:00")).timestamp()*1000)
BASE=os.environ.get('PLACEMENT_CHECK_BASE','http://127.0.0.1:3000')
assert urlparse(BASE).hostname in ('127.0.0.1','localhost'), 'Local development target only'
checks=[]
def client():return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))
def req(c,m,p,b=None):
 q=urllib.request.Request(BASE+p,data=json.dumps(b).encode() if b is not None else None,headers={'Content-Type':'application/json','Origin':BASE},method=m)
 try:r=c.open(q,timeout=20)
 except urllib.error.HTTPError as e:r=e
 raw=r.read();return r.status,json.loads(raw) if raw else None
def check(label,ok):
 checks.append({'check':label,'pass':bool(ok)})
 assert ok,label
def data(c,m,p,b=None,status=200):
 code,out=req(c,m,p,b);check(m+' '+p+' => '+str(status),code==status);return out.get('data')
api='/api/v1/placement/attempts';c=client()
check('anonymous lookup is empty and does not start',data(c,'GET',api+'/current') is None)
assert os.environ.get('PLACEMENT_LIVE_ALLOW_WRITES')=='1','Set PLACEMENT_LIVE_ALLOW_WRITES=1 for disposable local test accounts'
email='resume-live-'+uuid.uuid4().hex[:10]+'@example.test'
data(c,'POST','/api/v1/auth/register',{'email':email,'password':'ResumeCheck-Only-2026!'},201)
check('new User has no current attempt',data(c,'GET',api+'/current') is None)
check('repeated entry lookup still has no attempt',data(c,'GET',api+'/current') is None)
start=data(c,'POST',api,status=201);p=api+'/'+start['attemptId']
cur=data(c,'GET',api+'/current');check('current is original active User attempt',cur['attemptId']==start['attemptId'] and cur['status']=='IN_PROGRESS' and not cur['claimRequired'])
check('empty attempt summary',cur['answeredCount']==0 and cur['totalQuestions']==20 and cur.get('lastSavedAt') is None and cur['resumeOrder']==1)
detail=data(c,'GET',p)
for q in detail['questions'][:3]:data(c,'PATCH',p+'/answers',{'questionId':q['id'],'selectedOptionId':q['options'][0]['id']})
time.sleep(1)
cur=data(c,'GET',api+'/current');check('real persisted progress and timestamp',cur['answeredCount']==3 and bool(cur['lastSavedAt']) and cur['resumeOrder']==4)
check('lookup never resets deadline',same_deadline(cur['deadlineAt'],start['deadlineAt']))
check('summary has no score/answer key',not any(k in json.dumps(cur).lower() for k in ['correctoption','correctcount','score','level']))
other=client();check('foreign browser cannot discover User attempt',data(other,'GET',api+'/current') is None)
check('foreign browser cannot read attempt',req(other,'GET',p)[0]==404)
again=data(c,'POST',api);check('explicit start still resumes same attempt/deadline',again['attemptId']==start['attemptId'] and same_deadline(again['deadlineAt'],start['deadlineAt']))
data(c,'POST',p+'/submit')
finished=data(c,'GET',api+'/current');check('submitted User entry leads to same result',finished['attemptId']==start['attemptId'] and finished['status']=='SUBMITTED' and not finished['claimRequired'])
result=data(c,'GET',p+'/result');check('result belongs to same attempt',result['attemptId']==start['attemptId'])
data(c,'POST','/api/v1/auth/logout',{})
check('logout hides User summary',data(c,'GET',api+'/current') is None)
print(json.dumps({'base':BASE,'email':email,'attemptId':start['attemptId'],'passed':len(checks),'checks':checks},ensure_ascii=False,indent=2))
