const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const project = 'C:/Apache24/htdocs/financial-ai-advisor-/server';
const contents = fs.readFileSync(path.join(project, '.env'), 'utf8');
const remote = (contents.match(/postgres(?:ql)?:\/\/[^\s"'<>]+/g) || []).find(value => !['localhost', '127.0.0.1'].includes(new URL(value).hostname));
if (!remote) throw new Error('No online database connection found in backend configuration');
const out = fs.openSync(path.join(__dirname, 'backend-online.stdout.log'), 'a');
const err = fs.openSync(path.join(__dirname, 'backend-online.stderr.log'), 'a');
const child = spawn(process.execPath, ['--import', 'tsx', 'src/server.ts'], {
 cwd: project, env: { ...process.env, DATABASE_URL: remote }, detached: true,
 windowsHide: true, stdio: ['ignore', out, err]
});
child.unref();
console.log('Started backend using online database. PID:', child.pid);
