import importlib
import os
import unittest
from unittest.mock import patch
from uuid import uuid4
from test_core import test_directory


class ApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.folder = test_directory()
        directory = cls.folder.__enter__()
        cls.env = patch.dict(os.environ, API_TOKEN='t'*32, DB_PATH=os.path.join(directory,'api.sqlite'))
        cls.env.start()
        cls.module = importlib.import_module('app')
        cls.client = cls.module.app.test_client()
        cls.headers = {'Authorization': 'Bearer '+'t'*32}

    @classmethod
    def tearDownClass(cls):
        cls.env.stop()
        cls.folder.__exit__(None,None,None)

    def test_authentication(self):
        self.assertEqual(self.client.get('/measurements').status_code,401)
        self.assertEqual(self.client.get('/health',headers=self.headers).status_code,200)
        self.assertEqual(self.client.get('/health',headers={'Authorization':'Bearer неверно'}).status_code,401)

    def test_save_retry_history_and_statistics_without_google(self):
        data=dict(id=str(uuid4()),systolic=120,diastolic=80,pulse=70,
                  measured_at='2026-10-28T12:00:00+03:00',note='Test',confirmed=True)
        response=self.client.post('/measurements',json=data,headers=self.headers)
        self.assertEqual(response.status_code,200)
        self.assertTrue(response.json['saved']); self.assertFalse(response.json['synced'])
        response=self.client.post('/measurements',json=data,headers=self.headers)
        self.assertTrue(response.json['duplicate'])
        response=self.client.get('/measurements?limit=1',headers=self.headers)
        self.assertEqual(response.json['measurements'][0]['id'],data['id'])
        self.assertEqual(self.client.get('/statistics?days=bad',headers=self.headers).status_code,400)
        self.assertEqual(self.client.post('/measurements',json=dict(data,confirmed=False),headers=self.headers).status_code,400)
        self.assertEqual(self.client.post('/measurements',json=dict(data,pulse=71),headers=self.headers).status_code,400)

    def test_photo_errors_and_confirmation(self):
        self.assertEqual(self.client.post('/recognize',data=b'not an image',headers=self.headers).status_code,422)
        with patch.object(self.module,'read_photo',return_value=dict(systolic=120,diastolic=80,pulse=70)):
            result=self.client.post('/recognize',data=b'test',headers=self.headers)
            self.assertTrue(result.json['requires_confirmation'])
        self.module.vision_lock.acquire()
        try:
            self.assertEqual(self.client.post('/recognize',data=b'x',headers=self.headers).status_code,429)
        finally:
            self.module.vision_lock.release()
