const {createRequire}=require('module');
const r=createRequire('C:/Apache24/htdocs/financial-ai-advisor-/server/package.json');
const fs=require('fs');
const env=r('dotenv').parse(fs.readFileSync('C:/Apache24/htdocs/financial-ai-advisor-/server/.env'));
const {Client}=r('pg');
const c=new Client({connectionString:env.DATABASE_URL,connectionTimeoutMillis:5000});
const assert=require('assert/strict');
(async()=>{await c.connect();
console.log('PASS PostgreSQL connection', (await c.query('select current_database() as database')).rows[0]);
const dir='C:/Apache24/htdocs/financial-ai-advisor-/server/src/db/migrations';
const files=fs.readdirSync(dir).filter(f=>f.endsWith('.sql'));
const applied=(await c.query('select filename from schema_migrations')).rows.map(x=>x.filename);
assert.deepEqual(files.filter(f=>!applied.includes(f)),[]); console.log('PASS all '+files.length+' migrations applied');
const tables=[...new Set(files.flatMap(f=>[...fs.readFileSync(dir+'/'+f,'utf8').matchAll(/create table if not exists (\w+)/gi)].map(m=>m[1])))];
for(const t of tables){await c.query('select * from '+t+' limit 0');} console.log('PASS all '+tables.length+' application tables readable');
const cats=(await c.query("select type,count(*)::int as count from categories where is_default=true group by type")).rows;console.log('Default categories',cats);assert(cats.some(x=>x.type==='income'));assert(cats.some(x=>x.type==='expense'));
const anomalies=await c.query(`select count(*)::int as mismatches from wallets w where current_balance <> opening_balance + coalesce((select sum(case when type in ('income','transfer_in') then amount else -amount end) from transactions t where t.wallet_id=w.id and t.deleted_at is null),0)`);console.log('Wallet balance reconciliation',anomalies.rows[0]);
await c.query('BEGIN');
const uid=(await c.query("insert into users(full_name,email,password_hash) values('Database rollback test',$1,'not-a-real-password') returning id",['db-check-'+require('crypto').randomUUID()+'@example.invalid'])).rows[0].id;
const insert=async(sql,args)=>(await c.query(sql+' returning id',args)).rows[0].id;
const w=await insert("insert into wallets(user_id,name,type,opening_balance,current_balance) values($1,'Test cash','cash',1000,1000)",[uid]);
const w2=await insert("insert into wallets(user_id,name,type) values($1,'Test savings','savings')",[uid]);
const cat=await insert("insert into categories(user_id,name,type) values($1,'Test expense','expense')",[uid]);
await c.query("insert into sessions(user_id,token_hash,expires_at) values($1,$2,now()+interval '1 hour')",[uid,require('crypto').randomUUID()]);
await c.query("insert into transactions(user_id,wallet_id,category_id,type,amount,transaction_date) values($1,$2,$3,'expense',100,current_date)",[uid,w,cat]);
await c.query('update wallets set current_balance=current_balance-100 where id=$1',[w]);
assert.equal((await c.query('select current_balance from wallets where id=$1',[w])).rows[0].current_balance,'900.00');
await c.query('insert into transfers(user_id,from_wallet_id,to_wallet_id,amount,transfer_date) values($1,$2,$3,50,current_date)',[uid,w,w2]);
const goal=await insert("insert into savings_goals(user_id,name,target_amount) values($1,'Test goal',500)",[uid]);
await c.query('insert into goal_contributions(user_id,goal_id,wallet_id,amount,contribution_date) values($1,$2,$3,50,current_date)',[uid,goal,w]);
await c.query('insert into budgets(user_id,category_id,amount) values($1,$2,500)',[uid,cat]);
const conv=await insert('insert into advisor_conversations(user_id) values($1)',[uid]);
await c.query("insert into advisor_messages(user_id,conversation_id,role,content) values($1,$2,'user','Database test')",[uid,conv]);
await c.query('insert into user_activity_days(user_id,activity_date) values($1,current_date)',[uid]);
console.log('PASS temporary inserts across all application tables and balance update');
for(const [name,sql,args,expected] of [
['negative transaction',"insert into transactions(user_id,wallet_id,type,amount,transaction_date) values($1,$2,'expense',-1,current_date)",[uid,w],'23514'],
['missing owner',"insert into wallets(user_id,name,type) values($1,'Invalid','cash')",[require('crypto').randomUUID()],'23503'],
['duplicate budget','insert into budgets(user_id,category_id,amount) values($1,$2,100)',[uid,cat],'23505']]){
await c.query('SAVEPOINT invalid_input');let code;try{await c.query(sql,args);}catch(e){code=e.code;}await c.query('ROLLBACK TO SAVEPOINT invalid_input');assert.equal(code,expected);console.log('PASS constraint rejects '+name);
}
await c.query('delete from advisor_messages where conversation_id=$1',[conv]);
assert.equal((await c.query('select count(*)::int as n from advisor_messages where conversation_id=$1',[conv])).rows[0].n,0);
await c.query('ROLLBACK');assert.equal((await c.query('select count(*)::int as n from users where id=$1',[uid])).rows[0].n,0);console.log('PASS deletion and rollback; no test records retained');
if(anomalies.rows[0].mismatches)process.exitCode=2;
})().catch(e=>{console.error('FAIL',e.code||'',e.message.replace(/postgres(?:ql)?:\/\/\S+/g,'[redacted]'));process.exitCode=1;}).finally(async()=>{try{await c.query('ROLLBACK');}catch{}await c.end();});
