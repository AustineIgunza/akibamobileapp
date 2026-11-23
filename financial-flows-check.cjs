const {createRequire}=require('module');const r=createRequire('C:/Apache24/htdocs/financial-ai-advisor-/server/package.json');const fs=require('fs');const assert=require('assert/strict');const env=r('dotenv').parse(fs.readFileSync('C:/Apache24/htdocs/financial-ai-advisor-/server/.env'));const c=new (r('pg').Client)({connectionString:env.DATABASE_URL,connectionTimeoutMillis:5000});const email='api-check-'+require('crypto').randomUUID()+'@example.invalid';const password=require('crypto').randomUUID()+'aA1!';let cookie='';
async function req(path,method='GET',body,expected=200){const res=await fetch('http://127.0.0.1:4000/api/'+path,{method,headers:{'Content-Type':'application/json',Cookie:cookie},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(10000)});const text=await res.text();const data=text?JSON.parse(text):{};assert.equal(res.status,expected,path+' '+JSON.stringify(data.error||{}));const sc=res.headers.get('set-cookie');if(sc)cookie=sc.split(';')[0];console.log('PASS',method,path,res.status);return data;}
(async()=>{await c.connect();await req('health');await req('auth/register','POST',{fullName:'Temporary API check',email,password},201);
const a=(await req('wallets','POST',{name:'Test cash',type:'cash',currency:'KES',openingBalance:1000},201)).wallet;
const b=(await req('wallets','POST',{name:'Test bank',type:'bank',currency:'KES',openingBalance:0},201)).wallet;
const balance=async(id)=>(await req('wallets')).wallets.find(w=>w.id===id).currentBalance;
const t=(await req('transactions','POST',{walletId:a.id,type:'income',amount:200},201)).transaction;assert.equal(await balance(a.id),1200);
await req('transactions/'+t.id,'PATCH',{amount:300});assert.equal(await balance(a.id),1300);
await req('transactions/'+t.id,'DELETE',undefined,200);assert.equal(await balance(a.id),1000);
const cat=(await req('categories')).categories.find(x=>x.type==='expense');
await req('transactions','POST',{walletId:a.id,type:'expense',categoryId:cat.id,amount:100},201);assert.equal(await balance(a.id),900);
await req('transactions','POST',{walletId:a.id,type:'expense',amount:99999},400);assert.equal(await balance(a.id),900);
await req('transfers','POST',{fromWalletId:a.id,toWalletId:b.id,amount:200},201);assert.equal(await balance(a.id),700);assert.equal(await balance(b.id),200);
const g=(await req('goals','POST',{name:'Test goal',targetAmount:500},201)).goal;
await req('goals/'+g.id+'/contributions','POST',{amount:50,walletId:a.id},201);assert.equal(await balance(a.id),650);assert.equal((await req('goals')).goals.find(x=>x.id===g.id).currentAmount,50);
const budget=(await req('budgets','POST',{categoryId:cat.id,amount:500},201)).budget;
await req('budgets/'+budget.id,'PATCH',{amount:600});await req('budgets/'+budget.id,'DELETE',undefined,200);
const summary=(await req('dashboard/summary')).summary;assert.equal(summary.totalBalance,850);assert.equal(summary.expense.month,150);assert.equal(summary.income.month,0);
await req('wallets/'+b.id,'PATCH',{name:'Renamed bank'});await req('wallets/'+b.id,'DELETE',undefined,200);
await req('advisor/conversations','POST',{title:'Test conversation'},201);
await req('auth/logout','POST',undefined,204);await req('auth/login','POST',{email,password});assert.equal(await balance(a.id),650);
console.log('PASS all financial balance and persistence assertions');
})().catch(e=>{console.error('FAIL',e.message);process.exitCode=1;}).finally(async()=>{try{await c.query('BEGIN');for(const table of ['advisor_messages','advisor_conversations','goal_contributions','budgets','transactions','transfers','savings_goals','wallets','user_activity_days','sessions'])await c.query('delete from '+table+' where user_id=(select id from users where email=$1)',[email]);const res=await c.query('delete from users where email=$1 returning id',[email]);await c.query('COMMIT');console.log('CLEANUP temporary accounts removed:',res.rowCount);assert.equal((await c.query('select count(*)::int as n from users where email=$1',[email])).rows[0].n,0);}catch(e){console.error('CLEANUP FAILED',e.code);process.exitCode=1;}await c.end();});


