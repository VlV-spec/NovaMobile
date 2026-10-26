import assert from 'node:assert/strict';
import handler from '../worker/src/index.js';

const env = {DEVICE_TOKEN:'localtest_token_at_least_16_characters',OPENAI_API_KEY:'dummy'};
function req(path, message, token=env.DEVICE_TOKEN){
  return new Request('https://example.workers.dev'+path,{
    method:'POST',headers:{'authorization':'Bearer '+token,'content-type':'application/json'},
    body:JSON.stringify({message})
  });
}
const health=await handler.fetch(new Request('https://example.workers.dev/health'),env);
assert.equal(health.status,200);
assert.equal((await health.json()).ok,true);
const denied=await handler.fetch(req('/chat','Привет','wrong'),env);
assert.equal(denied.status,401);
const invalid=await handler.fetch(req('/chat',''),env);
assert.equal(invalid.status,400);
const cmd=await handler.fetch(req('/chat','Открой ютуб'),env);
assert.equal(cmd.status,200);
assert.equal((await cmd.json()).action,'open_youtube');
const previousFetch=globalThis.fetch;
try{
  globalThis.fetch=async (url, opts)=>{
    assert.equal(url,'https://api.openai.com/v1/responses');
    assert.equal(opts.method,'POST');
    assert.equal(opts.headers.authorization,'Bearer dummy');
    return new Response(JSON.stringify({output:[{content:[{type:'output_text',text:'Ответ тестового ИИ'}]}]}),{status:200});
  };
  const reply=await handler.fetch(req('/chat','Расскажи про планеты'),env);
  assert.equal(reply.status,200);
  assert.equal((await reply.json()).reply,'Ответ тестового ИИ');
}finally{globalThis.fetch=previousFetch;}
console.log('PASS: health, token validation, body validation, TV command, mocked OpenAI response');
