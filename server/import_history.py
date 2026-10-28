"""Import precise dated readings from an authorized Google export, idempotently."""
import json
import os
import sys
from datetime import datetime,timedelta,timezone
from uuid import uuid5,NAMESPACE_URL
from storage import Store

store=Store(os.getenv('DB_PATH','/data/journal.sqlite'))
source=json.load(open(sys.argv[1],encoding='utf-8'))
added=0
for index,row in enumerate(source['values'],start=2):
    if not row or all(v=='' for v in row):continue
    if len(row)<5 or not isinstance(row[0],(int,float)) or not isinstance(row[1],(int,float)):
        raise ValueError('Row '+str(index)+' requires a precise date and time')
    day=datetime(1899,12,30)+timedelta(days=row[0])
    measured=day+timedelta(seconds=round(row[1]*86400))
    measured=measured.replace(tzinfo=timezone(timedelta(hours=3)))
    key=str(uuid5(NAMESPACE_URL,source['source']+'/row/'+str(index)))
    data=dict(id=key,systolic=row[2],diastolic=row[3],pulse=row[4],note=row[5] if len(row)>5 else '',measured_at=measured.isoformat())
    added+=int(store.import_record(data))
print('Imported:',added)
