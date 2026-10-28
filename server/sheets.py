"""Dedicated sheet tab: A:F are visible data, G is a hidden stable record ID."""
import os
from datetime import datetime
from urllib.parse import quote
from google.oauth2 import service_account
from google.auth.transport.requests import AuthorizedSession

HEADERS = ['Дата', 'Время', 'Верхнее', 'Нижнее', 'Пульс', 'Примечание', 'record_id']


class SheetWriter:
    def __init__(self):
        credentials = service_account.Credentials.from_service_account_file(
            os.getenv('GOOGLE_APPLICATION_CREDENTIALS', '/run/secrets/google.json'),
            scopes=['https://www.googleapis.com/auth/spreadsheets'])
        self.session = AuthorizedSession(credentials)
        self.base = 'https://sheets.googleapis.com/v4/spreadsheets/' + os.environ['SPREADSHEET_ID']
        self.name = os.getenv('SHEET_NAME', 'Измерения')
        self.a1 = "'" + self.name.replace("'", "''") + "'!"

    def request(self, method, path, **kwargs):
        response = self.session.request(method, self.base + path, timeout=30, **kwargs)
        response.raise_for_status()
        return response.json()

    def read(self, cells):
        return self.request('GET', '/values/' + quote(self.a1 + cells, safe=''), params={'valueRenderOption': 'UNFORMATTED_VALUE'}).get('values', [])

    def put(self, cells, values):
        self.request('PUT', '/values/' + quote(self.a1 + cells, safe=''),
                     params={'valueInputOption': 'RAW'}, json={'values': values})

    def setup(self):
        metadata = self.request('GET', '', params={'fields': 'sheets.properties'})
        found = [s['properties'] for s in metadata.get('sheets', []) if s['properties']['title'] == self.name]
        if not found:
            result = self.request('POST', ':batchUpdate', json={'requests': [{'addSheet': {'properties': {'title': self.name}}}]})
            properties = result['replies'][0]['addSheet']['properties']
        else:
            properties = found[0]
        headers = self.read('A1:G1')
        if headers and headers != [HEADERS]:
            raise RuntimeError('Use a new dedicated tab; existing headers differ')
        if not headers:
            # Do not adopt an existing populated sheet just because row 1 is empty.
            if self.read('A2:G' + str(properties['gridProperties']['rowCount'])):
                raise RuntimeError('Target tab is not empty')
            self.put('A1:G1', [HEADERS])
        self.request('POST', ':batchUpdate', json={'requests': [
            {'updateSheetProperties': {'properties': {'sheetId': properties['sheetId'], 'gridProperties': {'frozenRowCount': 1}}, 'fields': 'gridProperties.frozenRowCount'}},
            {'updateDimensionProperties': {'range': {'sheetId': properties['sheetId'], 'dimension': 'COLUMNS', 'startIndex': 6, 'endIndex': 7}, 'properties': {'hiddenByUser': True}, 'fields': 'hiddenByUser'}},
            {'repeatCell': {'range': {'sheetId': properties['sheetId'], 'startRowIndex': 1, 'startColumnIndex': 0, 'endColumnIndex': 1}, 'cell': {'userEnteredFormat': {'numberFormat': {'type': 'DATE', 'pattern': 'dd.MM.yyyy'}}}, 'fields': 'userEnteredFormat.numberFormat'}},
            {'repeatCell': {'range': {'sheetId': properties['sheetId'], 'startRowIndex': 1, 'startColumnIndex': 1, 'endColumnIndex': 2}, 'cell': {'userEnteredFormat': {'numberFormat': {'type': 'TIME', 'pattern': 'HH:mm:ss'}}}, 'fields': 'userEnteredFormat.numberFormat'}}]})
        self.sheet_id = properties['sheetId']
        self.row_count = properties['gridProperties']['rowCount']

    def __call__(self, row_number, key, values):
        # Real spreadsheet dates/times support filtering and charts. The API uses RAW.
        date=datetime.strptime(values[0],'%d.%m.%Y')
        time=datetime.strptime(values[1],'%H:%M:%S')
        values=[(date-datetime(1899,12,30)).days,(time.hour*3600+time.minute*60+time.second)/86400]+values[2:]
        if row_number > self.row_count:
            extra = max(1000, row_number - self.row_count)
            self.request('POST', ':batchUpdate', json={'requests': [{'appendDimension': {'sheetId': self.sheet_id, 'dimension': 'ROWS', 'length': extra}}]})
            self.row_count += extra
        cells = f'A{row_number}:G{row_number}'
        existing = self.read(cells)
        if existing:
            prior = existing[0] + [''] * (7 - len(existing[0]))
            same=all(abs(a-b)<1e-9 if isinstance(a,(int,float)) and isinstance(b,(int,float)) else a==b for a,b in zip(prior,values+[key]))
            if not same:
                raise RuntimeError('Sheet row changed or belongs to another record; refusing overwrite')
            return
        self.put(cells, [values + [key]])
