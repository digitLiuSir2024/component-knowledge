import json, os, subprocess, urllib.request, pathlib, re
root=pathlib.Path(__file__).resolve().parents[2]
identity=json.loads((root/'docs/knowledge/validation/model-context/component-request.json').read_text())
def http(base,path,payload):
 req=urllib.request.Request(base+path,data=json.dumps(payload).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(req) as r: value=json.load(r)
 assert value['code']==200,(base,path,value)
 return value
results={}
for base in ['http://127.0.0.1:8081','http://127.0.0.1:5173/zhituo']:
 for path in ['/api/v1/knowledge/component-context','/api/v1/model-context/component','/api/v1/knowledge/maintenance/context']:
  value=http(base,path,identity);results[base+path]={'code':value['code'],'dataKeys':list(value['data'])}
results['statistics']=http('http://127.0.0.1:5173/zhituo','/api/v1/reports/filters',{})['code']
p=subprocess.Popen(['/usr/bin/python3',str(root/'skills/component-knowledge-maintenance/server.py')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,text=True,env={**os.environ,'KNOWLEDGE_API_BASE':'http://127.0.0.1:8081'})
def rpc(method,params={},notify=False):
 msg={'jsonrpc':'2.0','method':method,'params':params}
 if not notify: msg['id']=method
 p.stdin.write(json.dumps(msg)+'\n');p.stdin.flush()
 if notify:return
 value=json.loads(p.stdout.readline());assert 'error' not in value,value
 return value['result']
def call(name,args):
 value=rpc('tools/call',{'name':name,'arguments':args});assert not value.get('isError'),value
 return json.loads(value['content'][0]['text'])
results['initialize']=rpc('initialize',{'protocolVersion':'2024-11-05'})['serverInfo']
rpc('notifications/initialized',notify=True)
results['tools']=[t['name'] for t in rpc('tools/list')['tools']]
results['mcpRead']=list(call('read_component_knowledge',identity))
snapshot={'pageIdentity':{k:identity[k] for k in ['systemCode','projectCode','pagePath']},'components':{'kpiStrip':{'id':'kpiStrip','name':'指标','source':'src/pages/PlanReport','assemblyParents':['page']}}}
args={**snapshot['pageIdentity'],'root':'kpiStrip','registrySnapshot':snapshot}
scope=call('knowledge_coverage_scope',args)
results['coverageScope']=scope
# Only verify inventory validation mechanics; no candidate or business claim is submitted.
components=scope.get('components',scope) if isinstance(scope,dict) else scope
plan={'root':'kpiStrip','registrySnapshot':snapshot,'components':{c['componentId']:{'items':{i:{'status':'blocked','evidence':'仅验证迁移连通性，不执行知识取证或提交','changeIds':[]} for i in c['requiredItems']},'request':None} for c in components}}
results['coverageValidation']=call('validate_knowledge_coverage',{**snapshot['pageIdentity'],'coveragePlan':plan})
p.terminate();p.wait()
skill=root/'skills/component-knowledge-maintenance'
assert pathlib.Path('/Users/natasha/.codex/skills/component-knowledge-maintenance').resolve()==skill
for f in skill.rglob('*.md'):
 for link in re.findall(r'\]\(([^)]+)\)',f.read_text()):
  if '://' not in link and not link.startswith('#'):assert (f.parent/link.split('#')[0]).exists(),(f,link)
results['skillLinks']='passed'
print(json.dumps(results,ensure_ascii=False,indent=2))
