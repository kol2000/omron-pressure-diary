"""Test the same recognition path as the API, without saving a measurement."""
import argparse
import json
import time
from pathlib import Path
from vision import read_photo

parser = argparse.ArgumentParser()
parser.add_argument('--url', required=True)
parser.add_argument('--photo', type=Path, required=True)
parser.add_argument('--model', default='gemma3:4b')
parser.add_argument('--expect', type=int, nargs=3, metavar=('SYS','DIA','PULSE'))
args = parser.parse_args()
raw = args.photo.read_bytes()
if len(raw) > 10 * 1024 * 1024:
    parser.error('Photo exceeds 10 MB')
started = time.monotonic()
values = read_photo(raw, args.url, args.model)
print(json.dumps(dict(values, seconds=round(time.monotonic()-started,2))))
if args.expect:
    actual = [values[k] for k in ['systolic','diastolic','pulse']]
    if actual != args.expect:
        raise SystemExit('FAIL: digits differ from expected values')
    print('PASS: all three values match')
