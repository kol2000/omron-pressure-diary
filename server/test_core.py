import os
import tempfile
import unittest
from contextlib import contextmanager
from uuid import uuid4
from core import Journal, prepare, validate


@contextmanager
def test_directory():
    # Python 3.14's private temporary-directory ACLs conflict with some managed Windows runners.
    folder = os.path.join(os.getenv('TEST_TEMP_DIR', tempfile.gettempdir()), 'omron-test-' + str(uuid4()))
    os.mkdir(folder, 0o777)
    try:
        yield folder
    finally:
        for filename in os.listdir(folder):
            os.remove(os.path.join(folder, filename))
        os.rmdir(folder)


class Tests(unittest.TestCase):
    def test_sample_and_order(self):
        _, row = prepare(dict(id=str(uuid4()), systolic=120, diastolic=80, pulse=70,
                              measured_at='2026-10-28T12:18:05+03:00', note='=1+1'))
        self.assertEqual(row, ['28.10.2026','12:18:05',120,80,70,'=1+1'])

    def test_invalid_digits(self):
        for change in [dict(pulse=None), dict(systolic=80), dict(pulse=True), dict(diastolic=500), dict(pulse='70')]:
            with self.assertRaises(ValueError):
                validate(dict(dict(systolic=120, diastolic=80, pulse=70), **change))

    def test_timezone_required(self):
        with self.assertRaises(ValueError):
            prepare(dict(id=str(uuid4()), systolic=120, diastolic=80, pulse=70, measured_at='2026-10-28T12:18:05'))

    def test_duplicate_and_changed_payload(self):
        with test_directory() as folder:
            journal=Journal(os.path.join(folder,'db.sqlite')); calls=[]
            key=str(uuid4()); row=['date','time',120,80,70,'']
            journal.save(key,row,calls.append)
            self.assertTrue(journal.save(key,row,calls.append)['duplicate'])
            self.assertEqual(len(calls),1)
            with self.assertRaises(ValueError): journal.save(key,row+['changed'],calls.append)

    def test_uncertain_delivery_not_repeated_after_restart(self):
        with test_directory() as folder:
            path=os.path.join(folder,'db.sqlite'); journal=Journal(path); calls=[]
            def failed(row):
                calls.append(row)
                raise TimeoutError('response lost')
            with self.assertRaises(TimeoutError): journal.save('test',[120,80,70],failed)
            with self.assertRaises(RuntimeError): Journal(path).save('test',[120,80,70],failed)
            self.assertEqual(len(calls),1)


if __name__ == '__main__': unittest.main()
