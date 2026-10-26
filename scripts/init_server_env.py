"""Generate deployment secrets locally on the server; never print their values."""
from pathlib import Path
import os
import secrets

target = Path('.env.server')
try:
    fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
except FileExistsError:
    raise SystemExit('.env.server already exists; preserving existing database credentials.')
with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as stream:
    for key in ['MYSQL_ROOT_PASSWORD', 'MYSQL_PASSWORD', 'RABBITMQ_PASSWORD', 'JWT_SECRET']:
        stream.write(key+'='+secrets.token_hex(32)+'\n')
    stream.write('HTTP_BIND=127.0.0.1\nHTTP_PORT=8088\n')
print('Created .env.server. Keep it private and preserve it for upgrades.')
