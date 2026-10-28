import logging
import os
import time
from storage import Store
from sheets import SheetWriter

logging.basicConfig(level=logging.INFO)
store = Store(os.getenv('DB_PATH', '/data/journal.sqlite'))
while True:
    try:
        if store.pending():
            writer = SheetWriter()
            try:
                writer.setup()
                store.sync(writer)
            finally:
                writer.session.close()
    except Exception:
        # Do not log patient values, tokens or credentials.
        logging.error('Google synchronization failed; retry in 60 seconds')
    time.sleep(60)
