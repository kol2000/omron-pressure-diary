"""Write a consistent SQLite backup to stdout; run inside the API container."""
import os
import sqlite3
import sys
import tempfile

with tempfile.TemporaryDirectory() as folder:
    path = os.path.join(folder, 'journal.sqlite')
    with sqlite3.connect(os.getenv('DB_PATH', '/data/journal.sqlite')) as source:
        with sqlite3.connect(path) as target:
            source.backup(target)
    with open(path, 'rb') as backup:
        while block := backup.read(1024 * 1024):
            sys.stdout.buffer.write(block)
