const fs=require('fs');const {createRequire}=require('module');const r=createRequire('C:/Apache24/htdocs/financial-ai-advisor-/server/package.json');const text=fs.readFileSync('C:/Apache24/htdocs/financial-ai-advisor-/server/.env','utf8');const remote=(text.match(/postgres(?:ql)?:\/\/[^\s"'<>]+/g)||[]).find(x=>new URL(x).hostname!=='localhost');let s=fs.readFileSync(process.argv[2] || 'api-database-check.cjs','utf8');s=s.replace('const c=new',"env.DATABASE_URL="+JSON.stringify(remote)+";const c=new"); // Evaluate in memory; never write the connection string into a test file.
require('module')._compile;
const Module=require('module');const m=new Module(__filename,module);m.filename=__filename;m.paths=module.paths;m._compile(s,__filename);

