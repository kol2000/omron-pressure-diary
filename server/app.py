import os
import secrets
import threading

import requests
from flask import Flask, request, jsonify
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


@app.before_request
def authenticate():
    if not secrets.compare_digest(request.headers.get('Authorization', '').encode(), ('Bearer ' + TOKEN).encode()):
        return jsonify(error='Неверный токен доступа'), 401


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
