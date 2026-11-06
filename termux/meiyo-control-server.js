const http=require('http'),fs=require('fs'),path=require('path'),os=require('os');
const {spawn,execFileSync}=require('child_process');
const ROOT=path.resolve(process.env.MEIYO_ROOT||process.cwd());
const DATA=path.join(ROOT,'data'),LOG=path.join(DATA,'meiyo-control.log'),PID=path.join(DATA,'meiyo-control.pid');
const BACKUPS=path.join(DATA,'backups'),DASHBOARD=path.join(__dirname,'dashboard.html');
fs.mkdirSync(DATA,{recursive:true});fs.mkdirSync(BACKUPS,{recursive:true});
let shell=null,terminalBuffer='',terminalSeq=0;
function getBotPid(){
  let saved=null;try{saved=Number(fs.readFileSync(PID,'utf8'))||null}catch{}
  const alive=p=>{try{process.kill(p,0);return true}catch{return false}};
  if(saved&&alive(saved))return saved;
  try{for(const name of fs.readdirSync('/proc')){if(!/^\\d+$/.test(name))continue;const p=Number(name);if(p===process.pid)continue;let cmd='';try{cmd=fs.readFileSync('/proc/'+name+'/cmdline','utf8').replace(/\\0/g,' ')}catch{}if(/node .*src\\/index\\.js(?: |$)/.test(cmd)){try{fs.writeFileSync(PID,String(p))}catch{}return p}}}catch{}
  return null
}
function startBot(){if(getBotPid())return 'Meiyo already running (PID '+getBotPid()+')';const fd=fs.openSync(LOG,'a');const p=spawn('node',['src/index.js'],{cwd:ROOT,detached:true,stdio:['ignore',fd,fd],env:process.env});p.unref();fs.writeFileSync(PID,String(p.pid));return 'Meiyo started (PID '+p.pid+')'}
function stopBot(){const p=getBotPid();if(!p)return 'Meiyo is not running';try{process.kill(-p,'SIGTERM')}catch{try{process.kill(p,'SIGTERM')}catch{}}try{fs.unlinkSync(PID)}catch{}return 'Stop signal sent to Meiyo (PID '+p+')'}
function appendTerminal(s){terminalBuffer+=String(s);terminalSeq+=String(s).length;if(terminalBuffer.length>160000)terminalBuffer=terminalBuffer.slice(-120000)}
function ensureShell(){if(shell&&!shell.killed)return;shell=spawn('bash',['--noprofile','--norc'],{cwd:ROOT,env:process.env,stdio:['pipe','pipe','pipe']});shell.stdout.on('data',appendTerminal);shell.stderr.on('data',appendTerminal);shell.on('close',()=>{shell=null;appendTerminal('\n[Shell exited]\n')})}
function sendJson(res,status,obj){res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(obj))}
function readBody(req,max=300*1024*1024){return new Promise((resolve,reject)=>{const chunks=[];let size=0;req.on('data',c=>{size+=c.length;if(size>max){reject(Error('Upload exceeds 300 MB'));req.destroy()}else chunks.push(c)});req.on('end',()=>resolve(Buffer.concat(chunks)));req.on('error',reject)})}
function makeBackup(){const stamp=new Date().toISOString().replace(/[:.]/g,'-'),name='meiyo-backup-'+stamp+'.zip',dest=path.join(BACKUPS,name);execFileSync('zip',['-qr',dest,'meiyo','-x','meiyo/node_modules/*','meiyo/.git/*','meiyo/data/backups/*'],{cwd:path.dirname(ROOT),stdio:'pipe'});return{name,path:dest}}
function inspectZip(zip){const entries=execFileSync('unzip',['-Z1',zip],{encoding:'utf8',maxBuffer:20*1024*1024}).split(/\r?\n/).filter(Boolean);if(!entries.length)throw Error('ZIP is empty');for(const entry of entries){const normalized=entry.replace(/\\/g,'/');if(normalized.startsWith('/')||/^[A-Za-z]:/.test(normalized)||normalized.split('/').includes('..')||!normalized.startsWith('meiyo/'))throw Error('Unsafe entry or ZIP must contain only a top-level meiyo/ folder: '+entry)}return entries}
function mergeDir(src,dst,rel=''){fs.mkdirSync(dst,{recursive:true});for(const ent of fs.readdirSync(src,{withFileTypes:true})){const r=path.posix.join(rel,ent.name);if(!rel&&ent.name==='.env')continue;if(r==='data/backups'||r.startsWith('data/backups/'))continue;const from=path.join(src,ent.name),to=path.join(dst,ent.name);if(ent.isSymbolicLink())throw Error('Symbolic links are not accepted: '+r);if(ent.isDirectory())mergeDir(from,to,r);else if(ent.isFile()){fs.mkdirSync(path.dirname(to),{recursive:true});fs.copyFileSync(from,to)}}}
const server=http.createServer(async(req,res)=>{try{const u=new URL(req.url,'http://127.0.0.1:8765'),p=u.pathname;
if(req.method==='GET'&&p==='/'){if(!fs.existsSync(DASHBOARD)){res.writeHead(500);return res.end('Dashboard file missing: '+DASHBOARD)}res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'});return res.end(fs.readFileSync(DASHBOARD))}
if(req.method==='GET'&&p==='/api/status'){const pid=getBotPid();return sendJson(res,200,{online:!!pid,pid,root:ROOT})}
if(req.method==='GET'&&p==='/api/logs'){let logs='No bot log file yet. Start Meiyo from this panel to capture its output.';try{logs=fs.readFileSync(LOG,'utf8').slice(-20000)}catch{}res.writeHead(200,{'Content-Type':'text/plain; charset=utf-8'});return res.end(logs)}
if(req.method==='GET'&&p==='/api/console'){let content='';try{content=fs.readFileSync(LOG,'utf8')}catch{}const size=Buffer.byteLength(content),since=Math.max(0,Number(u.searchParams.get('since')||0));const start=since>size?0:since;return sendJson(res,200,{text:content.slice(start),end:size,reset:since>size})}
if(req.method==='POST'&&p==='/api/start')return sendJson(res,200,{message:startBot()});
if(req.method==='POST'&&p==='/api/stop')return sendJson(res,200,{message:stopBot()});
if(req.method==='POST'&&p==='/api/restart'){stopBot();setTimeout(()=>{try{startBot()}catch(e){fs.appendFileSync(LOG,'\n'+e.message)}},1200);return sendJson(res,200,{message:'Restart requested'})}
if(req.method==='POST'&&p==='/api/register'){const child=spawn('npm',['run','commands'],{cwd:ROOT,env:process.env,stdio:['ignore','pipe','pipe']});let output='';child.stdout.on('data',d=>output+=d);child.stderr.on('data',d=>output+=d);const code=await new Promise(resolve=>child.on('close',resolve));fs.appendFileSync(LOG,'\n'+output);return sendJson(res,code?500:200,{message:code?'Registration failed: '+output:'Slash commands registered'})}
if(req.method==='POST'&&p==='/api/terminal/run'){const data=JSON.parse((await readBody(req,1024*1024)).toString());if(typeof data.command!=='string'||!data.command.trim())return sendJson(res,400,{error:'Empty command'});ensureShell();shell.stdin.write(data.command+'\n');return sendJson(res,200,{accepted:true})}
if(req.method==='POST'&&p==='/api/terminal/input'){const data=JSON.parse((await readBody(req,4096)).toString());if(shell&&typeof data.input==='string')shell.stdin.write(data.input);return sendJson(res,200,{ok:true})}
if(req.method==='GET'&&p==='/api/terminal/output'){const since=Math.max(0,Number(u.searchParams.get('since')||0));const offset=Math.max(0,terminalBuffer.length-Math.max(0,terminalSeq-since));return sendJson(res,200,{text:terminalBuffer.slice(offset),end:terminalSeq})}
if(req.method==='POST'&&p==='/api/terminal/clear'){terminalBuffer='';terminalSeq=0;return sendJson(res,200,{ok:true})}
if(req.method==='POST'&&p==='/api/backup'){const b=makeBackup();return sendJson(res,200,{name:b.name,url:'/api/backup/download?name='+encodeURIComponent(b.name)})}
if(req.method==='GET'&&p==='/api/backup/download'){const name=path.basename(u.searchParams.get('name')||''),file=path.join(BACKUPS,name);if(!name.endsWith('.zip')||!fs.existsSync(file))return sendJson(res,404,{error:'Backup not found'});res.writeHead(200,{'Content-Type':'application/zip','Content-Disposition':'attachment; filename="'+name+'"'});return fs.createReadStream(file).pipe(res)}
if(req.method==='POST'&&p==='/api/import'){const name=u.searchParams.get('name')||'upload.zip';if(!name.toLowerCase().endsWith('.zip'))return sendJson(res,400,{error:'Select a ZIP file'});const bytes=await readBody(req),tmp=fs.mkdtempSync(path.join(os.tmpdir(),'meiyo-import-')),zip=path.join(tmp,'upload.zip');fs.writeFileSync(zip,bytes);try{inspectZip(zip);const unpacked=path.join(tmp,'unpacked');fs.mkdirSync(unpacked);execFileSync('unzip',['-q',zip,'-d',unpacked],{stdio:'pipe'});const incoming=path.join(unpacked,'meiyo');if(!fs.existsSync(incoming))throw Error('ZIP must contain a meiyo/ folder');const backup=makeBackup();mergeDir(incoming,ROOT);return sendJson(res,200,{message:'Installed '+name+'. Safety backup created: '+backup.name+'. Existing .env and data/backups preserved.'})}finally{fs.rmSync(tmp,{recursive:true,force:true})}}
res.writeHead(404,{'Content-Type':'text/plain'});res.end('Not found');
}catch(e){sendJson(res,500,{error:e.message||String(e)})}});
server.listen(8765,'127.0.0.1',()=>console.log('Meiyo dashboard live at http://127.0.0.1:8765/ | ROOT='+ROOT));
