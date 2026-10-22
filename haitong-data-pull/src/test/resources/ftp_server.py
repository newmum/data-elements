"""Optional blocking FTP fixture for hosts whose Java selector pipe is unavailable."""
import logging
from pathlib import Path
import sys

sys.path.insert(0, str(Path(sys.argv[1]).resolve()))
from pyftpdlib.authorizers import DummyAuthorizer
from pyftpdlib.handlers import FTPHandler
from pyftpdlib.servers import FTPServer

logging.basicConfig(level=logging.WARNING)
authorizer = DummyAuthorizer()
authorizer.add_user("relay", "test-password", str(Path(sys.argv[2]).resolve()), perm="elradfmwMT")
FTPHandler.authorizer = authorizer
FTPServer(("127.0.0.1", int(sys.argv[3])), FTPHandler).serve_forever(timeout=0.2)
