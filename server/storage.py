"""Durable measurements and an outbox. Never store photos."""
import json
from datetime import datetime, timezone, timedelta
from core import connect, prepare


class Store:
    def __init__(self, path):
        self.path = path
        with connect(path) as db:
            db.execute('''CREATE TABLE IF NOT EXISTS measurements (
                seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT UNIQUE NOT NULL,
                payload TEXT NOT NULL, measured_at TEXT NOT NULL, timestamp REAL NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0, sync_error TEXT)''')
            columns = {r[1] for r in db.execute('PRAGMA table_info(measurements)')}
            if 'sheet_row' not in columns:
                db.execute('ALTER TABLE measurements ADD COLUMN sheet_row INTEGER')
                db.execute('UPDATE measurements SET sheet_row=seq+1')
            if 'source' not in columns:
                db.execute("ALTER TABLE measurements ADD COLUMN source TEXT NOT NULL DEFAULT 'app'")
            db.execute('CREATE UNIQUE INDEX IF NOT EXISTS unique_sheet_row ON measurements(sheet_row)')
            db.execute('CREATE INDEX IF NOT EXISTS measured_timestamp ON measurements(timestamp)')

    def save(self, data):
        key, row = prepare(data)
        timestamp = datetime.fromisoformat(data['measured_at']).timestamp()
        payload = json.dumps(row, ensure_ascii=False)
        with connect(self.path) as db:
            db.execute('BEGIN IMMEDIATE')
            old = db.execute('SELECT payload, synced FROM measurements WHERE id=?', (key,)).fetchone()
            if old:
                if old[0] != payload:
                    raise ValueError('ID already used with different values')
                return dict(saved=True, duplicate=True, synced=bool(old[1]))
            sheet_row = db.execute('SELECT COALESCE(MAX(sheet_row),1)+1 FROM measurements').fetchone()[0]
            db.execute('INSERT INTO measurements(id,payload,measured_at,timestamp,sheet_row) VALUES (?,?,?,?,?)',
                       (key, payload, data['measured_at'], timestamp, sheet_row))
        return dict(saved=True, duplicate=False, synced=False)

    def pending(self, limit=50):
        with connect(self.path) as db:
            rows = db.execute('SELECT sheet_row,id,payload FROM measurements WHERE synced=0 ORDER BY seq LIMIT ?', (limit,)).fetchall()
        return [(seq, key, json.loads(payload)) for seq, key, payload in rows]

    def sync(self, writer):
        # One dedicated worker only. Fixed row writes remain idempotent after timeouts.
        for sheet_row, key, row in self.pending():
            try:
                writer(sheet_row, key, row)
            except Exception:
                with connect(self.path) as db:
                    db.execute('UPDATE measurements SET sync_error=? WHERE id=?', ('Google sync failed; will retry', key))
                raise
            with connect(self.path) as db:
                db.execute('UPDATE measurements SET synced=1,sync_error=NULL WHERE id=?', (key,))

    def history(self, limit=50, offset=0):
        with connect(self.path) as db:
            rows = db.execute('SELECT id,payload,measured_at,synced FROM measurements ORDER BY timestamp DESC,seq DESC LIMIT ? OFFSET ?', (limit, offset)).fetchall()
        return [dict(id=key, measured_at=at, systolic=v[2], diastolic=v[3], pulse=v[4], note=v[5], synced=bool(synced))
                for key, payload, at, synced in rows for v in [json.loads(payload)]]

    def statistics(self, days=30):
        cutoff = (datetime.now(timezone.utc) - timedelta(days=days)).timestamp()
        with connect(self.path) as db:
            rows = db.execute('SELECT payload FROM measurements WHERE timestamp>=?', (cutoff,)).fetchall()
            pending = db.execute('SELECT count(*) FROM measurements WHERE synced=0').fetchone()[0]
        values = [json.loads(row[0]) for row in rows]
        means = {name: round(sum(v[index] for v in values) / len(values), 1) if values else None
                 for name, index in [('systolic', 2), ('diastolic', 3), ('pulse', 4)]}
        return dict(days=days, count=len(values), averages=means, pending_sync=pending)

    def import_record(self, data):
        key,row=prepare(data)
        payload=json.dumps(row,ensure_ascii=False)
        with connect(self.path) as db:
            old=db.execute('SELECT payload FROM measurements WHERE id=?',(key,)).fetchone()
            if old:
                if old[0]!=payload:
                    raise ValueError('Imported record changed; review it before importing again')
                return False
            db.execute('INSERT INTO measurements(id,payload,measured_at,timestamp,synced,source) VALUES (?,?,?,?,1,?)',
                       (key,payload,data['measured_at'],datetime.fromisoformat(data['measured_at']).timestamp(),'google-history'))
        return True

    def report(self, start, end):
        with connect(self.path) as db:
            rows=db.execute('SELECT id,payload,measured_at,synced,source FROM measurements WHERE timestamp>=? AND timestamp<? ORDER BY timestamp,seq', (start,end)).fetchall()
            pending=db.execute('SELECT count(*) FROM measurements WHERE synced=0').fetchone()[0]
        measurements=[dict(id=key,measured_at=at,systolic=v[2],diastolic=v[3],pulse=v[4],note=v[5],synced=bool(synced),source=source)
                      for key,payload,at,synced,source in rows for v in [json.loads(payload)]]
        summary={}
        for name in ['systolic','diastolic','pulse']:
            values=[m[name] for m in measurements]
            summary[name]=dict(mean=round(sum(values)/len(values),1),min=min(values),max=max(values)) if values else dict(mean=None,min=None,max=None)
        return dict(measurements=measurements,summary=summary,count=len(measurements),pending_sync=pending)
