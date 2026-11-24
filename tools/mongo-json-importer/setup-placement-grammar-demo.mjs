import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {isDeepStrictEqual} from 'node:util';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const args=process.argv.slice(2);
const option=(name,fallback)=>args.includes(name)?args[args.indexOf(name)+1]:fallback;
const apply=args.includes('--apply');
const base=new URL(option('--api','http://localhost:8080/api/v1').replace(/\/$/,'')+'/');
if(!['http:','https:'].includes(base.protocol))throw Error('Expected an HTTP(S) API URL');
if(!['localhost','127.0.0.1','[::1]'].includes(base.hostname)&&base.protocol!=='https:')throw Error('Remote API requires HTTPS');
if(base.username||base.password)throw Error('Do not put credentials in the API URL');
if(!['localhost','127.0.0.1','[::1]'].includes(base.hostname)&&!args.includes('--allow-remote'))throw Error('Non-local API requires --allow-remote and explicit review of its database');
if(apply&&option('--confirm','')!=='DEMO')throw Error('Apply requires --confirm DEMO after reviewing dry-run');
const grammar=JSON.parse(fs.readFileSync(path.join(root,'tools/mongo-json-importer/samples/grammar/grammar-cefr-a1-b2.v2.importer.json'),'utf8'));
const placement=JSON.parse(fs.readFileSync(path.join(root,'tools/mongo-json-importer/samples/placement/placement-demo-catalog.importer.json'),'utf8'));
const profile=grammar.metadata.demo_state;
if(!profile)throw Error('Missing shared demo-state metadata');
const topics=grammar.collections.grammar_topics,lessons=grammar.collections.grammar_lessons;
const drafts=new Set(profile.draft_topic_ids);
if(drafts.size!==profile.draft_topics||lessons.filter(l=>drafts.has(l.topic_id)).length!==profile.draft_lessons
 ||topics.filter(t=>drafts.has(t._id)).length!==drafts.size||topics.length!==profile.published_topics+profile.draft_topics||lessons.length!==profile.published_lessons+profile.draft_lessons)throw Error('Demo manifest does not match seed inventory');
if(placement.collections.placement_test_sets.filter(s=>s.status==='PUBLISHED').length!==7||placement.collections.placement_test_sets.filter(s=>s.status==='DRAFT').length!==9)throw Error('Placement seed no longer matches the reviewed 7/9 demo inventory');
let credentials;
if(process.env.ENGOLEARN_DEMO_EMAIL&&process.env.ENGOLEARN_DEMO_PASSWORD)credentials={email:process.env.ENGOLEARN_DEMO_EMAIL,password:process.env.ENGOLEARN_DEMO_PASSWORD};
else if(!process.stdin.isTTY)credentials=JSON.parse(fs.readFileSync(0,'utf8'));
if(!credentials?.email||!credentials?.password)throw Error('Use the PowerShell wrapper to enter an existing Admin account securely');
const cookies=new Map();
async function api(endpoint,body){
 const response=await fetch(new URL(endpoint,base),{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',Cookie:[...cookies].map(([k,v])=>`${k}=${v}`).join('; ')},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(25000)});
 for(const cookie of response.headers.getSetCookie()){if(/;\s*Secure(?:;|$)/i.test(cookie)&&base.protocol!=='https:')throw Error('Secure auth cookies require HTTPS; no cookie downgrade');const pair=cookie.split(';')[0],i=pair.indexOf('=');cookies.set(pair.slice(0,i),pair.slice(i+1));}
 if(response.status===204&&response.ok)return null;
 const payload=await response.json();
 if(!response.ok)throw Error(`${endpoint}: HTTP ${response.status} ${payload.code||''}; no automatic overwrite or retry`);
 return payload.data;
}
const text=value=>(value??'').trim();
const pick=(item,camel,snake)=>item[camel]??item[snake];
const normalizedAnswer=value=>text(value).normalize('NFKC').replace(/[’‘ʼ′]/g,"'").replace(/[“”]/g,'"').replace(/[–—]/g,'-').replace(/\s+/gu,' ').toLowerCase().replace(/[.,;:!?]+$/,'').trimEnd();
function content(row){
 const c=row.draft??row;
 const blocks=(c.blocks??[]).map(b=>{
  const value={id:pick(b,'id','block_id'),type:b.type};
  switch(b.type){
   case 'EXPLANATION':return {...value,title:text(b.title),text:text(b.text)};
   case 'FORMULA':return {...value,formula:text(b.formula),text:text(b.text)};
   case 'EXAMPLE':return {...value,examples:(b.examples??[]).map(e=>({english:text(e.english),vietnamese:text(e.vietnamese),analysis:text(e.analysis)}))};
   case 'RULE_QUICK_NOTE':return {...value,title:text(b.title),text:text(b.text),ruleItems:(pick(b,'ruleItems','rule_items')??[]).map(text)};
   case 'COMMON_MISTAKE':return {...value,incorrect:text(b.incorrect),correct:text(b.correct),explanation:text(b.explanation)};
   default:throw Error('Unsupported block type in shared demo');
  }
 });
 const questions=(c.questions??[]).map(q=>{
  const value={id:pick(q,'id','question_id'),type:q.type,prompt:text(q.prompt),explanation:text(q.explanation)};
  if(q.type==='MULTIPLE_CHOICE')return {...value,options:(q.options??[]).map(text),correctIndex:pick(q,'correctIndex','correct_index')};
  const correctAnswer=text(pick(q,'correctAnswer','correct_answer')),seen=new Set(['',normalizedAnswer(correctAnswer)]);
  const acceptedAnswers=(pick(q,'acceptedAnswers','accepted_answers')??[]).map(text).filter(answer=>{const key=normalizedAnswer(answer);if(seen.has(key))return false;seen.add(key);return true;});
  return {...value,correctAnswer,acceptedAnswers};
 });
 return {titleVi:text(pick(c,'titleVi','title_vi')),titleEn:text(pick(c,'titleEn','title_en')),objective:text(c.objective),tags:(c.tags??[]).map(text),blocks,questions};
}
const bank=new Map(placement.collections.placement_questions.map(q=>[q._id,q]));
const media=new Map(placement.collections.media_assets.map(a=>[a._id,a.storage]));
function placementContent(q){
 return {id:q.id??q._id,code:q.code,skill:q.skill,level:q.level,prompt:text(q.prompt),passage:text(q.passage),
  options:(q.options??[]).map(o=>({id:o.id,label:o.label,text:text(o.text)})),
  correctOptionId:text(pick(q,'correctOptionId','correct_option_id')),explanation:text(q.explanation),transcript:text(q.transcript)};
}
function assertPlacementSnapshot(current,expected){
 const source=expected.status==='PUBLISHED'?expected.items:expected.question_ids.map(id=>bank.get(id));
 check(source?.length===20&&current.snapshot?.length===20,`Placement snapshot unavailable: ${expected.code}`);
 check(isDeepStrictEqual(current.snapshot.map(placementContent),source.map(placementContent)),`Placement question/snapshot differs from shared seed: ${expected.code}`);
 for(let index=0;index<source.length;index++){
  const question=source[index];if(question.skill!=='LISTENING')continue;
  const storage=media.get(question.audio?.media_id??question.audio_media_id);
  check(storage,`Shared audio metadata missing: ${expected.code}`);
  const url=new URL(current.snapshot[index].audioUrl);
  check(url.protocol==='https:'&&url.hostname==='res.cloudinary.com'
   &&url.pathname.includes('/'+storage.resource_type.toLowerCase()+'/'+storage.delivery_type.toLowerCase()+'/')
   &&decodeURIComponent(url.pathname).endsWith('/'+storage.public_id+'.'+storage.format),`Placement audio differs from shared seed: ${expected.code}`);
 }
}
const topicInfo=t=>({code:t.code,titleVi:pick(t,'titleVi','title_vi'),titleEn:pick(t,'titleEn','title_en'),description:t.description,level:t.level,order:t.order});
function check(value,message){if(!value)throw Error(message);}
function assertChecklist(result,id,allowNotReady=false){
 const failures=result.checks.filter(c=>!c.passed&&!(allowNotReady&&c.label==='Mọi bài đều Sẵn sàng'));
 check(!failures.length,`Checklist failed: ${id}; review in Admin before continuing`);
}
const grammarPath=id=>`grammar/admin/topics/${encodeURIComponent(id)}`;
const lessonPath=(topic,id)=>`${grammarPath(topic)}/lessons/${encodeURIComponent(id)}`;
const setPath=id=>`placement/admin/test-sets/${encodeURIComponent(id)}`;
let applied=0,authenticated=false;
async function preflight(){
 const actions=[],states={grammar:{publishedTopics:0,draftTopics:0,publishedLessons:0,draftLessons:0},placement:{published:0,draft:0}};
 for(const expected of topics){
  const current=await api(grammarPath(expected._id));
  check(isDeepStrictEqual(topicInfo(current),topicInfo(expected)),`Topic differs from shared seed: ${expected.code}; no overwrite`);
  const target=drafts.has(expected._id)?'DRAFT':'PUBLISHED';
  check(['DRAFT','IN_PROGRESS','PUBLISHED'].includes(current.status),`Archived/unexpected topic: ${expected.code}`);
  check(target!=='DRAFT'||current.status!=='PUBLISHED',`Demo-reserved topic already published: ${expected.code}; review manually, tool will not unpublish`);
  const expectedLessons=lessons.filter(l=>l.topic_id===expected._id);
  check(current.lessons.length===expectedLessons.length,`Lesson inventory changed: ${expected.code}`);
  for(const expectedLesson of expectedLessons){
   const lesson=current.lessons.find(l=>l.id===expectedLesson._id);
   check(lesson&&lesson.code===expectedLesson.code&&lesson.order===expectedLesson.order&&isDeepStrictEqual(content(lesson),content(expectedLesson)),`Lesson differs from shared seed: ${expectedLesson.code}; no overwrite`);
   check(!lesson.hasDraft,`Pending published edits: ${expectedLesson.code}; review manually`);
   check(['DRAFT','PUBLISHED'].includes(lesson.status),`Unexpected lesson status: ${expectedLesson.code}`);
   check(current.status!=='PUBLISHED'||lesson.status==='PUBLISHED',`Mixed published/draft topic: ${expected.code}; review manually`);
   check(current.status==='PUBLISHED'||lesson.status==='DRAFT',`Unexpected published child: ${expectedLesson.code}`);
   assertChecklist(await api(lessonPath(expected._id,lesson.id)+'/checklist'),lesson.code);
   if(lesson.status==='DRAFT'&&!lesson.ready)actions.push({type:'ready',topicId:expected._id,lessonId:lesson.id,code:lesson.code});
   states.grammar[lesson.status==='PUBLISHED'?'publishedLessons':'draftLessons']++;
  }
  assertChecklist(await api(grammarPath(expected._id)+'/checklist'),expected.code,true);
  if(target==='PUBLISHED'&&current.status!=='PUBLISHED')actions.push({type:'publishGrammar',topicId:expected._id,code:expected.code});
  states.grammar[current.status==='PUBLISHED'?'publishedTopics':'draftTopics']++;
 }
 for(const expected of placement.collections.placement_test_sets){
  const current=await api(setPath(expected._id));
  check(current.code===expected.code&&current.name===expected.name&&current.version===expected.version&&isDeepStrictEqual(current.questionIds,expected.question_ids),`Placement form differs from shared seed: ${expected.code}; no overwrite`);
  check(current.status===expected.status,`Placement status differs: ${expected.code}; import/review the approved catalog first, no automatic publish/archive`);
  assertPlacementSnapshot(current,expected);
  states.placement[current.status==='PUBLISHED'?'published':'draft']++;
 }
 return {states,actions};
}
async function main(){
 await api('auth/login',credentials);authenticated=true;
 const plan=await preflight();
 console.log(JSON.stringify({mode:apply?'APPLY':'DRY_RUN',api:base.href,current:plan.states,plannedActions:plan.actions.length,target:{grammar:{publishedTopics:profile.published_topics,draftTopics:profile.draft_topics,publishedLessons:profile.published_lessons,draftLessons:profile.draft_lessons},placement:{published:7,draft:9}}},null,2));
 if(!apply)return;
 // Each action uses the latest resource token. Already-completed actions are skipped on a later run.
 for(const action of plan.actions){
  if(action.type==='ready'){
   const endpoint=lessonPath(action.topicId,action.lessonId),lesson=await api(endpoint);
   check(lesson.status==='DRAFT',`Lesson changed during apply: ${action.code}`);
   const expected=lessons.find(l=>l._id===action.lessonId);
   check(lesson.code===expected.code&&lesson.order===expected.order&&isDeepStrictEqual(content(lesson),content(expected)),`Lesson edited during apply: ${action.code}`);
   if(!lesson.ready)await api(endpoint+'/ready',{version:lesson.version});
  }else if(action.type==='publishGrammar'){
   const endpoint=grammarPath(action.topicId),topic=await api(endpoint);
   check(isDeepStrictEqual(topicInfo(topic),topicInfo(topics.find(t=>t._id===action.topicId))),`Topic edited during apply: ${action.code}`);
   check(topic.lessons.length===lessons.filter(l=>l.topic_id===action.topicId).length,`Lesson inventory changed during apply: ${action.code}`);
   for(const lesson of topic.lessons){const expected=lessons.find(l=>l._id===lesson.id);check(expected&&lesson.code===expected.code&&lesson.order===expected.order&&!lesson.hasDraft&&isDeepStrictEqual(content(lesson),content(expected)),`Content changed during apply: ${action.code}`);}
   assertChecklist(await api(endpoint+'/checklist'),action.code);
   if(topic.status!=='PUBLISHED')await api(endpoint+'/publish',{version:topic.version});
  }
  applied++;
 }
 const final=await preflight();check(final.actions.length===0,'State changed during apply; rerun dry-run for remaining actions');
 console.log(JSON.stringify({result:'COMPLETE',actionsCompleted:applied,final:final.states},null,2));
}
main().catch(error=>{console.error(`DEMO SETUP STOPPED after ${applied} completed actions: ${error.message}. No rollback/reset of content or learner history was attempted; review then rerun dry-run.`);process.exitCode=1;}).finally(async()=>{if(authenticated){try{await api('auth/logout',{});}catch{console.error('Could not close this tool login session; content result above is unchanged.');}}});
