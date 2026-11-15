#!/usr/bin/env python3
"""Disposable full-backend acceptance. Requires built jars, Docker and openssl.
Creates only uniquely named containers and temp files, never uses application volumes.
No real payment/AI provider is contacted. Tokens/secrets are never printed.
"""
import concurrent.futures
import contextlib
import datetime as dt
import hashlib
import hmac
import http.client
import http.server
import json
import os
from pathlib import Path
import socket
import ssl
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
MODULES = ["identity", "social", "post", "notification", "chat", "ai", "moderation", "subscription", "api-gateway"]


def command(*args, **kw):
    return subprocess.run(args, check=True, capture_output=True, text=True, **kw).stdout.strip()


def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def main():
    containers, processes, logs = [], [], []
    tls_server = None
    checks = []
    with tempfile.TemporaryDirectory(prefix="loccoc-closeout-") as temporary:
        work = Path(temporary)
        def infrastructure(kind, image, target, *args):
            name = "loccoc-closeout-" + uuid.uuid4().hex[:12] + "-" + kind
            command("docker", "run", "--rm", "-d", "--name", name, "-p", "127.0.0.1::"+str(target), *args, image,
                    *(["server", "/data"] if kind == "minio" else []))
            containers.append(name)
            return name, int(command("docker", "port", name, str(target)).rsplit(":", 1)[1])
        try:
            pg, pgport = infrastructure("postgres", "postgres:16-alpine", 5432,
                "-e", "POSTGRES_DB=social_media_ai_db", "-e", "POSTGRES_USER=postgres", "-e", "POSTGRES_PASSWORD=test-only-password")
            _, redisport = infrastructure("redis", "redis:7-alpine", 6379)
            _, minioport = infrastructure("minio", "minio/minio:latest", 9000,
                "-e", "MINIO_ROOT_USER=closeout-test", "-e", "MINIO_ROOT_PASSWORD=closeout-test-password")
            deadline = time.monotonic()+60
            while True:
                try:
                    command("docker", "exec", pg, "pg_isready", "-U", "postgres", "-d", "social_media_ai_db")
                    break
                except subprocess.CalledProcessError:
                    if time.monotonic()>deadline: raise
                    time.sleep(.5)
            migrations=sorted((BACKEND/"database/migration").glob("V*__*.sql"),key=lambda p:int(p.name.split("__")[0][1:]))
            for migration in migrations:
                command("docker", "exec", "-i", pg, "psql", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "social_media_ai_db", input=migration.read_text())
            checks.append("empty PostgreSQL migration: " + ",".join(m.name.split("__")[0] for m in migrations))
            ports={name:port() for name in MODULES}
            tls_port=port()
            common=dict(os.environ)
            common.update({"DB_URL":f"jdbc:postgresql://localhost:{pgport}/social_media_ai_db",
                "JWT_SECRET":"acceptance-only-jwt-secret-key-at-least-32-bytes",
                "INTERNAL_API_KEY":"acceptance-only-internal-key", "REDIS_HOST":"localhost", "REDIS_PORT":str(redisport),
                "MINIO_URL":f"http://localhost:{minioport}","MINIO_PUBLIC_URL":f"http://localhost:{minioport}",
                "MINIO_ACCESS_KEY":"closeout-test","MINIO_SECRET_KEY":"closeout-test-password",
                "OTP_EXPOSE_CODE":"true","ADMIN_USERNAME":"acceptance-admin","ADMIN_PASSWORD":"acceptance-admin-password",
                "PAYMENT_WEBHOOK_SECRET":"acceptance-only-webhook-secret-at-least-32-bytes",
                "SANDBOX_CHECKOUT_BASE_URL":f"https://localhost:{tls_port}/api/v1/payments/sandbox/checkouts"})
            for name, number in ports.items():
                if name != "api-gateway": common[name.upper()+"_SERVICE_URL"]=f"http://localhost:{number}"
            users={"identity":"identity","social":"social","post":"post","notification":"notification","chat":"chat","ai":"ai","moderation":"moderation","subscription":"subscription"}
            for name in MODULES:
                module=name if name=="api-gateway" else name+"-service"
                jars=list((BACKEND/module/"target").glob("*-SNAPSHOT.jar"))
                if len(jars)!=1: raise RuntimeError("Build jars first: mvn clean verify")
                env=dict(common);env["SERVER_PORT"]=str(ports[name])
                if name in users:
                    env["DB_USERNAME"]=users[name]+"_service_user";env["DB_PASSWORD"]=users[name]+"_password"
                log=open(work/(name+".log"),"w+");logs.append((name,log))
                processes.append(subprocess.Popen(["java","-Xms32m","-Xmx192m","-jar",str(jars[0])],env=env,stdout=log,stderr=subprocess.STDOUT))
            gateway=f"http://localhost:{ports['api-gateway']}"
            def request(path, data=None, token=None, method=None, base=gateway, headers=None, expected=200, form=False, tls=None):
                h={"X-Request-Id":"closeout-acceptance"};h.update(headers or {})
                if token:h["Authorization"]="Bearer "+token
                payload=None
                if data is not None:
                    payload=(urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
                    h["Content-Type"]="application/x-www-form-urlencoded" if form else "application/json"
                req=urllib.request.Request(base+path,payload,h,method=method or ("POST" if data is not None else "GET"))
                try:
                    with urllib.request.urlopen(req,timeout=10,context=tls) as response:
                        status=response.status;body=response.read();response_headers=response.headers
                except urllib.error.HTTPError as e:status=e.code;body=e.read();response_headers=e.headers
                if status!=expected:raise AssertionError(f"{req.method} {path}: expected {expected}, got {status}: "+body.decode()[:500])
                try:result=json.loads(body)
                except ValueError:result=body.decode()
                return result,response_headers
            deadline=time.monotonic()+180
            for name,number in ports.items():
                while True:
                    if any(p.poll() is not None for p in processes):
                        raise RuntimeError("A backend process exited during startup; inspect startup errors above.")
                    try:
                        request("/actuator/health",base=f"http://localhost:{number}")
                        break
                    except (OSError,AssertionError):
                        if time.monotonic()>deadline:raise RuntimeError("Service health timed out: "+name)
                        time.sleep(.5)
            checks.append("all nine HTTP services healthy with Hibernate validate")
            print("All services healthy; exercising gateway journeys", flush=True)
            # A local TLS ingress exposes the actual hosted sandbox over HTTPS.
            command("openssl","req","-x509","-newkey","rsa:2048","-nodes","-keyout",str(work/"key.pem"),"-out",str(work/"cert.pem"),"-days","1","-subj","/CN=localhost","-addext","subjectAltName=DNS:localhost")
            class Proxy(http.server.BaseHTTPRequestHandler):
                def log_message(self,*args):pass
                def do_GET(self):self.forward()
                def do_POST(self):self.forward()
                def forward(self):
                    length=int(self.headers.get("Content-Length",0));body=self.rfile.read(length) if length else None
                    conn=http.client.HTTPConnection("localhost",ports["api-gateway"],timeout=15)
                    headers={k:v for k,v in self.headers.items() if k.lower() not in {"host","connection"}}
                    conn.request(self.command,self.path,body,headers);result=conn.getresponse();content=result.read()
                    self.send_response(result.status)
                    for key,value in result.getheaders():
                        if key.lower() not in {"transfer-encoding","connection","content-length"}:self.send_header(key,value)
                    self.send_header("Content-Length",str(len(content)));self.end_headers();self.wfile.write(content);conn.close()
            tls_server=http.server.ThreadingHTTPServer(("localhost",tls_port),Proxy)
            server_context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);server_context.load_cert_chain(work/"cert.pem",work/"key.pem")
            tls_server.socket=server_context.wrap_socket(tls_server.socket,server_side=True)
            threading.Thread(target=tls_server.serve_forever,daemon=True).start()
            client_context=ssl.create_default_context(cafile=str(work/"cert.pem"))
            def login(phone):
                otp,_=request("/api/v1/auth/phone/send-otp",{"phoneNumber":phone})
                auth,_=request("/api/v1/auth/phone/verify-otp",{"phoneNumber":phone,"otp":otp["devOtp"]})
                terms,_=request("/api/v1/terms/latest")
                request("/api/v1/terms/accept",{"termsVersionId":terms["id"]},auth["accessToken"])
                refreshed,_=request("/api/v1/auth/refresh",{"refreshToken":auth["refreshToken"]})
                return refreshed["accessToken"],auth["user"]["id"]
            alice,aid=login("+84901111001");bob,bid=login("+84901111002")
            free,headers=request("/api/v1/subscriptions/me",token=alice)
            assert free["tier"]=="FREE" and not free["entitlements"]["aiGeneration"] and headers["Cache-Control"]=="no-store"
            request("/api/v1/ai/generations",{"stylePrompt":"hello","generationType":"TEXT_TO_IMAGE"},alice,expected=403)
            key=str(uuid.uuid4())
            checkout,_=request("/api/v1/subscriptions/checkout",{"tier":"PLUS","clientRequestId":key},alice)
            replay,_=request("/api/v1/subscriptions/checkout",{"tier":"PLUS","clientRequestId":str(uuid.uuid4())},alice)
            assert checkout["sessionId"]==replay["sessionId"]
            url=urllib.parse.urlsplit(checkout["url"])
            page,_=request(url.path,base=f"https://localhost:{tls_port}",tls=client_context)
            assert "Sandbox" in page
            still_free,_=request("/api/v1/subscriptions/me",token=alice);assert still_free["tier"]=="FREE"
            request(url.path+"/complete",{"outcome":"SUCCEEDED"},base=f"https://localhost:{tls_port}",form=True,tls=client_context)
            request(url.path+"/complete",{"outcome":"SUCCEEDED"},base=f"https://localhost:{tls_port}",form=True,tls=client_context)
            paid,_=request("/api/v1/subscriptions/me",token=alice);assert paid["tier"]=="PLUS" and paid["entitlements"]["aiGeneration"]
            checks.append("FREE deny → HTTPS hosted sandbox → signed webhook → PLUS; checkout/form replay")
            ai,_=request("/api/v1/ai/generations",{"stylePrompt":"acceptance","generationType":"TEXT_TO_IMAGE"},alice,expected=202)
            deadline=time.monotonic()+15
            while True:
                result,_=request("/api/v1/ai/generations/"+str(ai["id"]),token=alice)
                if result["status"]=="SUCCESS":break
                if time.monotonic()>deadline:raise AssertionError("AI did not complete")
                time.sleep(.3)
            request("/api/v1/ai/generations/"+str(ai["id"]),token=bob,expected=404)
            request("/api/v1/subscriptions/me/cancel",{"cancelAtPeriodEnd":True},alice)
            canceled,_=request("/api/v1/subscriptions/me",token=alice);assert canceled["cancelAtPeriodEnd"] and canceled["entitlements"]["aiGeneration"]
            post,_=request("/api/v1/posts",{"mediaType":"IMAGE","mediaUrl":"https://example.test/acceptance.png","caption":"acceptance ordinary post"},alice,expected=201)
            report,_=request("/api/v1/reports",{"reportedPostId":post["id"],"reason":"acceptance report"},bob,expected=201)
            admin,_=request("/api/v1/admin/auth/login",{"username":"acceptance-admin","password":"acceptance-admin-password"})
            at=admin["accessToken"]
            request("/api/v1/admin/reports/queue",token=alice,expected=403)
            request(f"/api/v1/admin/reports/{report['id']}/resolve",{"action":"HIDE_POST","resolutionNote":"acceptance hide"},at)
            request(f"/api/v1/posts/{post['id']}",token=alice,expected=404)
            audit,_=request(f"/api/v1/admin/reports/{report['id']}/audit",token=at);assert len(audit)>=2
            ban,_=request("/api/v1/reports",{"reportedUserId":aid,"reason":"acceptance ban"},bob,expected=201)
            request(f"/api/v1/admin/reports/{ban['id']}/resolve",{"action":"BAN_USER","resolutionNote":"acceptance ban"},at)
            request("/api/v1/subscriptions/me",token=alice,expected=403)
            request("/api/v1/subscriptions/me",token=alice,base=f"http://localhost:{ports['subscription']}",expected=403)
            request(f"/internal/v1/subscriptions/{bid}/entitlements",expected=404)
            request(f"/internal/v1/subscriptions/{bid}/entitlements",token=bob,base=f"http://localhost:{ports['subscription']}",expected=403)
            checks.append("AI worker/owner/cancel; ordinary post → report → hide/audit; ban blocks issued JWT at gateway+downstream; internal APIs isolated")
            # Retention applies only to a synthetic account in this newly-created container.
            carol,cid=login("+84901111003")
            request("/api/v1/account/delete-request",{},carol,expected=202)
            command("docker","exec",pg,"psql","-v","ON_ERROR_STOP=1","-U","postgres","-d","social_media_ai_db","-c",
                    f"UPDATE account_deletion_requests SET scheduled_purge_at=now()-interval '1 day' WHERE user_id={int(cid)}")
            def account_rows():
                return command("docker","exec",pg,"psql","-At","-U","postgres","-d","social_media_ai_db","-c",
                    f"SELECT row_to_json(u)::text FROM users u WHERE id={int(cid)}; SELECT row_to_json(d)::text FROM account_deletion_requests d WHERE user_id={int(cid)}")
            before=account_rows()
            internal={"X-Internal-Api-Key":common["INTERNAL_API_KEY"]}
            preview,_=request("/internal/v1/retention/accounts/run?dryRun=true",{},base=f"http://localhost:{ports['identity']}",headers=internal)
            assert preview.get("DRY_RUN")==1 and account_rows()==before
            applied,_=request("/internal/v1/retention/accounts/run?dryRun=false",{},base=f"http://localhost:{ports['identity']}",headers=internal)
            assert applied.get("COMPLETED")==1
            tombstone=json.loads(account_rows().splitlines()[0])
            assert tombstone["username"].startswith("deleted-") and tombstone["phone_number"] is None
            checks.append("account retention dry-run leaves exact DB state; explicit isolated apply anonymizes after owner cleanup")
            for name,log in logs:
                log.flush();log.seek(0);text=log.read()
                assert "Using generated security password" not in text, "Unused default security password must not be generated"
                if name in {"api-gateway","identity","subscription","ai","post","moderation"}:
                    assert "closeout-acceptance" in text, "Missing cross-service trace: "+name
            checks.append("sanitized request ID present across gateway/domain/internal logs; no generated fallback passwords")
            print(json.dumps({"status":"PASS","checks":checks,"provider":"local hosted sandbox; synthetic AI; no third-party provider"},ensure_ascii=False,indent=2))
        except Exception:
            # Only startup errors and their source are shown; never print request bodies, tokens or env.
            for name,log in logs:
                log.flush();log.seek(0);lines=log.read().splitlines()
                errors=[line[:300] for line in lines if "ERROR" in line or "APPLICATION FAILED" in line]
                if errors:print(name+": "+"\n".join(errors[-4:]))
            raise
        finally:
            if tls_server:tls_server.shutdown();tls_server.server_close()
            for process in processes:process.terminate()
            for process in processes:
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:process.kill();process.wait()
            for _,log in logs:log.close()
            for name in reversed(containers):
                subprocess.run(["docker","rm","-f",name],capture_output=True)


if __name__=="__main__":main()
