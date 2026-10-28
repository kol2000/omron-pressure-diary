import importlib
import os
import unittest
from unittest.mock import patch
from datetime import datetime, timezone
from uuid import uuid4
from test_core import test_directory
from storage import Store


class DashboardTests(unittest.TestCase):
    def setUp(self):
        self.temp=test_directory(); folder=self.temp.__enter__()
        self.env=patch.dict(os.environ,API_TOKEN='t'*32,DB_PATH=os.path.join(folder,'web.sqlite'),COOKIE_SECURE='false')
        self.env.start()
        self.module=importlib.import_module('app')
        self.journal=patch.object(self.module,'journal',Store(os.path.join(folder,'web.sqlite')));self.journal.start()
        self.client=self.module.app.test_client()

    def tearDown(self):
        self.journal.stop();self.env.stop();self.temp.__exit__(None,None,None)

    def login(self):
        response=self.client.post('/auth/login',json={'token':'t'*32})
        self.assertEqual(response.status_code,200)
        return response.json['csrf']

    def test_public_shell_never_exposes_private_data(self):
        with self.client.get('/') as response:
            self.assertEqual(response.status_code,200)
            self.assertIn("script-src 'self'",response.headers['Content-Security-Policy'])
        self.assertEqual(self.client.get('/dashboard').status_code,401)
        self.assertFalse(self.client.get('/auth/session').json['authenticated'])

    def test_cookie_csrf_and_logout(self):
        csrf=self.login()
        self.assertTrue(self.client.get('/auth/session').json['authenticated'])
        self.assertEqual(self.client.post('/auth/logout',json={}).status_code,403)
        self.assertEqual(self.client.post('/auth/logout',json={},headers={'X-CSRF-Token':csrf}).status_code,200)
        self.assertEqual(self.client.get('/dashboard').status_code,401)

    def test_period_is_inclusive_in_moscow_and_no_data(self):
        self.login()
        store=self.module.journal
        for at in ['2026-10-28T00:00:00+03:00','2026-10-28T23:59:59+03:00','2026-10-29T00:00:00+03:00']:
            store.save(dict(id=str(uuid4()),systolic=120,diastolic=80,pulse=70,measured_at=at))
        result=self.client.get('/dashboard?from=2026-10-28&to=2026-10-28').json
        self.assertEqual(result['count'],2);self.assertEqual(result['summary']['systolic']['mean'],120)
        result=self.client.get('/dashboard?from=2020-01-01&to=2020-01-02').json
        self.assertEqual(result['count'],0);self.assertIsNone(result['summary']['pulse']['mean'])
        self.assertEqual(self.client.get('/dashboard?from=2026-10-29&to=2026-10-28').status_code,400)

    def test_import_does_not_reserve_app_google_rows(self):
        data=dict(id=str(uuid4()),systolic=120,diastolic=80,pulse=70,measured_at='2026-10-01T12:00:00+03:00')
        store=self.module.journal
        self.assertTrue(store.import_record(data));self.assertFalse(store.import_record(data))
        store.save(dict(data,id=str(uuid4())))
        self.assertEqual(store.pending()[0][0],2)
