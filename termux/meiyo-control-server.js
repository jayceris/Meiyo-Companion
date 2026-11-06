const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn, execSync } = require('child_process');
const ROOT = process.cwd();
const LOG = path.join(ROOT, 'data', 'meiyo-control.log');
const PID = path.join(ROOT, 'data', 'meiyo-control.pid');
fs.mkdirSync(path.dirname(LOG), {recursive:true});
let child = null;
function running() {
  try { const p = Number(fs.readFileSync(PID,'utf8')); process.kill(p,0); return p; }
  catch { return null; }
}
function start() {
  if (running()) return 'Meiyo already running (PID ' + running() + ')';
  const out = fs.openSync(LOG,'a');
  child = spawn('node',['src/index.js'],{cwd:ROOT,detached:true,stdio:['ignore',out,out],env:process.env});
  child.unref();
  fs.writeFileSync(PID,String(child.pid));
  return 'Meiyo started (PID ' + child.pid + ')';
}
function stop() {
  const p=running(); if(!p) return 'Meiyo is not running';
  try { process.kill(-p,'SIGTERM'); } catch { try { process.kill(p,'SIGTERM'); } catch {} }
  try { fs.unlinkSync(PID); } catch {}
  return 'Stop signal sent to Meiyo (PID ' + p + ')';
}
const server=http.createServer((req,res)=>{
  res.setHeader('Access-Control-Allow-Origin','*');
  res.setHeader('Content-Type','text/plain; charset=utf-8');
  let result='';
  if(req.url==='/status') result=running() ? 'ONLINE · PID '+running() : 'OFFLINE';
  else if(req.url==='/logs') { try { result=fs.readFileSync(LOG,'utf8').slice(-7000) || 'No logs yet'; } catch { result='No logs yet'; } }
  else if(req.url==='/start') result=start();
  else if(req.url==='/stop') result=stop();
  else if(req.url==='/restart') { stop(); setTimeout(()=>start(),1200); result='Restart requested'; }
  else if(req.url==='/register') {
    try { const output=execSync('npm run commands',{cwd:ROOT,encoding:'utf8',timeout:120000,maxBuffer:2*1024*1024}); fs.appendFileSync(LOG,'\n'+output); result='Slash commands registered'; }
    catch(e) { result='Registration failed: '+e.message; }
  } else { res.statusCode=404; result='Not found'; }
  res.end(result);
});
server.listen(8765,'127.0.0.1',()=>console.log('Meiyo Control bridge on 127.0.0.1:8765'));
