import unittest
from sheets import SheetWriter


class SheetTests(unittest.TestCase):
    def writer(self, existing):
        writer=SheetWriter.__new__(SheetWriter)
        writer.row_count=1000
        writer.read=lambda cells: existing
        writer.writes=[]
        writer.put=lambda cells, values: writer.writes.append((cells,values))
        return writer

    def test_empty_row_writes_raw_values_and_hidden_id(self):
        writer=self.writer([]); values=['01.01.2026','12:00:00',120,80,70,'=1+1']
        writer(2,'test-id',values)
        self.assertEqual(writer.writes,[('A2:G2',[[46023,0.5,120,80,70,'=1+1','test-id']])])

    def test_retry_existing_row_does_not_write_again(self):
        values=['01.01.2026','12:00:00',120,80,70,'']
        writer=self.writer([[46023,0.5,120,80,70,'','test-id']]); writer(2,'test-id',values)
        self.assertEqual(writer.writes,[])

    def test_changed_or_foreign_row_is_never_overwritten(self):
        writer=self.writer([['foreign data']])
        with self.assertRaises(RuntimeError): writer(2,'test-id',['01.01.2026','12:00:00',120,80,70,''])
        self.assertEqual(writer.writes,[])
