import json
import sqlite3
from contextlib import contextmanager
from datetime import datetime
from uuid import UUID


@contextmanager
def connect(path, timeout=30):
    db = sqlite3.connect(path, timeout=timeout)
    try:
        with db:
            yield db
    finally:
        db.close()


def validate(data):
    if not isinstance(data, dict):
        raise ValueError('Ожидался объект с тремя числами')
    out = {}
    for key, low, high in [('systolic', 40, 300), ('diastolic', 20, 200), ('pulse', 20, 250)]:
        n = data.get(key)
        if type(n) is not int or not low <= n <= high:
            raise ValueError('Проверьте поле ' + key)
        out[key] = n
    if out['systolic'] <= out['diastolic']:
        raise ValueError('Верхнее давление должно быть больше нижнего')
    return out


def prepare(data):
    values = validate(data)
    request_id = str(UUID(data['id']))
    measured = datetime.fromisoformat(data['measured_at'])
    if measured.tzinfo is None:
        raise ValueError('Нужен часовой пояс')
    note = data.get('note', '')
    if not isinstance(note, str) or len(note) > 1000:
        raise ValueError('Примечание: максимум 1000 символов')
    row = [measured.strftime('%d.%m.%Y'), measured.strftime('%H:%M:%S'),
           values['systolic'], values['diastolic'], values['pulse'], note]
    return request_id, row


class Journal:
    def __init__(self, path):
        self.path = path
        with connect(path) as db:
            db.execute('CREATE TABLE IF NOT EXISTS saves (id TEXT PRIMARY KEY, payload TEXT, state TEXT)')

    def save(self, request_id, row, writer):
        payload = json.dumps(row, ensure_ascii=False)
        with connect(self.path) as db:
            db.execute('BEGIN IMMEDIATE')
            found = db.execute('SELECT payload,state FROM saves WHERE id=?', (request_id,)).fetchone()
            if found:
                if found[0] != payload:
                    raise ValueError('Эта запись уже отправлялась с другими значениями')
                if found[1] == 'saved':
                    return {'saved': True, 'duplicate': True}
                raise RuntimeError('Результат прошлой отправки неизвестен. Проверьте таблицу; повторная запись заблокирована.')
            db.execute('INSERT INTO saves VALUES (?,?,?)', (request_id, payload, 'pending'))
        # Commit before the external call. A timeout/crash must never trigger a blind append retry.
        writer(row)
        with connect(self.path) as db:
            db.execute('UPDATE saves SET state=? WHERE id=?', ('saved', request_id))
        return {'saved': True, 'duplicate': False}
