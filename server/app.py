import os
import secrets
import threading
import hashlib
import time
from datetime import datetime, date, timedelta
from zoneinfo import ZoneInfo

import requests
from flask import Flask, request, jsonify, session, redirect
from PIL import Image, UnidentifiedImageError
from vision import read_photo
from storage import Store

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = 10 * 1024 * 1024
TOKEN = os.environ['API_TOKEN']
if len(TOKEN) < 32:
    raise RuntimeError('API_TOKEN must contain at least 32 characters')
journal = Store(os.getenv('DB_PATH', '/data/journal.sqlite'))
vision_lock = threading.Lock()
app.secret_key=hashlib.sha256(('web-session:'+TOKEN).encode()).digest()
app.config.update(SESSION_COOKIE_SECURE=os.getenv('COOKIE_SECURE','true').lower()=='true',
                  SESSION_COOKIE_HTTPONLY=True,SESSION_COOKIE_SAMESITE='Strict',PERMANENT_SESSION_LIFETIME=timedelta(hours=8))
login_attempts={}
login_lock=threading.Lock()


@app.before_request
def authenticate():
    if request.path in ['/', '/auth/login','/auth/mobile','/auth/session'] or request.path.startswith('/static/'):
        return
    if secrets.compare_digest(request.headers.get('Authorization', '').encode(), ('Bearer ' + TOKEN).encode()):
        return
    if session.get('authenticated'):
        if request.method not in ['GET','HEAD','OPTIONS'] and not secrets.compare_digest(
                request.headers.get('X-CSRF-Token','').encode(),session.get('csrf','').encode()):
            return jsonify(error='Обновите страницу и повторите действие'),403
        return
    return jsonify(error='Войдите с токеном доступа'),401


@app.after_request
def private_response(response):
    response.headers['Cache-Control']='no-store'
    response.headers['X-Content-Type-Options']='nosniff'
    response.headers['Referrer-Policy']='no-referrer'
    response.headers['Content-Security-Policy']="default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob: data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
    return response


@app.get('/')
def dashboard_page():
    return app.send_static_file('index.html')


@app.post('/auth/login')
@app.post('/auth/mobile')
def login():
    key=request.remote_addr or 'unknown'
    now=time.monotonic()
    with login_lock:
        attempts=[t for t in login_attempts.get(key,[]) if now-t<300]
        if len(attempts)>=10:
            return jsonify(error='Слишком много попыток. Повторите через 5 минут.'),429
        if len(login_attempts)>2048:
            login_attempts.clear()
        login_attempts[key]=attempts+[now]
    data=request.get_json(silent=True) or request.form
    candidate=data.get('token','') if hasattr(data,'get') else ''
    if not isinstance(candidate,str) or not secrets.compare_digest(candidate.encode(),TOKEN.encode()):
        return jsonify(error='Неверный токен доступа'),401
    session.clear(); session['authenticated']=True; session['csrf']=secrets.token_urlsafe(24); session.permanent=True
    with login_lock:
        login_attempts.pop(key,None)
    return redirect('/') if request.path=='/auth/mobile' else jsonify(ok=True,csrf=session['csrf'])


@app.get('/auth/session')
def session_status():
    return jsonify(authenticated=bool(session.get('authenticated')),csrf=session.get('csrf'))


@app.post('/auth/logout')
def logout():
    session.clear()
    return jsonify(ok=True)


@app.get('/dashboard')
def dashboard_data():
    try:
        zone=ZoneInfo(os.getenv('TIMEZONE','Europe/Moscow'))
        today=datetime.now(zone).date()
        start=date.fromisoformat(request.args.get('from',(today-timedelta(days=29)).isoformat()))
        end=date.fromisoformat(request.args.get('to',today.isoformat()))
        if end<start or (end-start).days>3650:
            raise ValueError()
        lower=datetime.combine(start,datetime.min.time(),zone).timestamp()
        upper=datetime.combine(end+timedelta(days=1),datetime.min.time(),zone).timestamp()
        result=journal.report(lower,upper)
        result.update(period={'from':start.isoformat(),'to':end.isoformat()},timezone=str(zone),
                      spreadsheet_url=os.getenv('SPREADSHEET_URL',''),generated_at=datetime.now(zone).isoformat(),
                      google_configured=os.path.isfile(os.getenv('GOOGLE_APPLICATION_CREDENTIALS','/run/secrets/google.json')))
        return jsonify(result)
    except (ValueError,OverflowError):
        return jsonify(error='Проверьте даты периода'),400


@app.errorhandler(413)
def too_large(_):
    return jsonify(error='Фото больше 10 МБ'), 413


@app.get('/health')
def health():
    return jsonify(status='ok')


@app.post('/recognize')
def recognize():
    if not vision_lock.acquire(blocking=False):
        return jsonify(error='Сервер занят. Попробуйте позже.'), 429
    try:
        values = read_photo(request.get_data())
        return jsonify(**values, requires_confirmation=True)
    except (ValueError, KeyError, TypeError, OSError, UnidentifiedImageError, Image.DecompressionBombError):
        return jsonify(error='Не удалось уверенно прочитать числа. Переснимите экран без бликов или введите вручную.'), 422
    except requests.RequestException:
        return jsonify(error='Модель распознавания недоступна или не успела ответить'), 503
    finally:
        vision_lock.release()


@app.post('/measurements')
def save():
    try:
        data = request.get_json()
        if not isinstance(data, dict) or data.get('confirmed') is not True:
            raise ValueError('Подтвердите значения')
        return jsonify(journal.save(data))
    except (ValueError, KeyError, TypeError):
        return jsonify(error='Проверьте числа, дату и подтверждение'), 400
    except RuntimeError as exc:
        return jsonify(error=str(exc)), 409
    except Exception:
        app.logger.error('Local measurement save failed')
        return jsonify(error='Не удалось сохранить запись на сервере. Повторите с тем же ID.'), 503


@app.get('/measurements')
def history():
    try:
        limit = min(200, max(1, int(request.args.get('limit', 50))))
        offset = max(0, int(request.args.get('offset', 0)))
        return jsonify(measurements=journal.history(limit, offset))
    except ValueError:
        return jsonify(error='Некорректные параметры'), 400


@app.get('/statistics')
def statistics():
    try:
        days = min(3650, max(1, int(request.args.get('days', 30))))
        return jsonify(journal.statistics(days))
    except ValueError:
        return jsonify(error='Некорректный период'), 400
