import os
import unittest
from uuid import uuid4
from datetime import datetime, timezone
from test_core import test_directory
from storage import Store


class StorageTests(unittest.TestCase):
    def data(self):
        return dict(id=str(uuid4()), systolic=120, diastolic=80, pulse=70,
                    measured_at=datetime.now(timezone.utc).isoformat(), note='=1+1', confirmed=True)

    def test_local_save_survives_restart_and_duplicate(self):
        with test_directory() as folder:
            path=os.path.join(folder,'db.sqlite'); store=Store(path); data=self.data()
            self.assertFalse(store.save(data)['synced'])
            store=Store(path)
            self.assertTrue(store.save(data)['duplicate'])
            self.assertEqual(len(store.history()),1)
            with self.assertRaises(ValueError): store.save(dict(data,pulse=76))
            self.assertEqual(store.statistics()['averages']['pulse'],70)
            self.assertEqual(store.statistics()['pending_sync'],1)

    def test_timeout_after_google_write_reuses_same_row(self):
        with test_directory() as folder:
            store=Store(os.path.join(folder,'db.sqlite')); store.save(self.data()); remote={}; calls=[]
            def lost_reply(row, key, values):
                remote[row]=values+[key]; calls.append(row)
                raise TimeoutError('reply lost')
            with self.assertRaises(TimeoutError): store.sync(lost_reply)
            self.assertEqual(len(store.pending()),1)
            def retry(row,key,values):
                self.assertEqual(remote[row],values+[key]); calls.append(row)
            store.sync(retry)
            self.assertEqual(calls,[2,2]); self.assertEqual(len(remote),1)
            self.assertTrue(store.history()[0]['synced'])
            self.assertEqual(store.pending(),[])

    def test_multiple_rows_and_old_measurement(self):
        with test_directory() as folder:
            store=Store(os.path.join(folder,'db.sqlite'))
            store.save(self.data()); store.save(dict(self.data(),measured_at='2020-01-01T12:00:00+03:00'))
            rows=[]; store.sync(lambda *args: rows.append(args))
            self.assertEqual([x[0] for x in rows],[2,3])
            self.assertEqual(store.statistics()['count'],1)
            self.assertEqual(len(store.history(limit=1,offset=1)),1)


if __name__ == '__main__': unittest.main()
