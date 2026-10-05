"""Generate the exact PBKDF2 hash expected by IamSecurityMagicModule.hash.

Run interactively: python scripts/hash-idaas-password.py
The password is never accepted as a command-line argument or written to disk.
"""

import base64
import getpass
import hashlib
import secrets
import sys


def main() -> int:
    password = getpass.getpass("New password: ")
    confirmation = getpass.getpass("Confirm password: ")
    if password != confirmation:
        print("Passwords do not match.", file=sys.stderr)
        return 1
    if not 8 <= len(password) <= 256:
        print("Password must contain 8–256 characters (and satisfy the configured policy).", file=sys.stderr)
        return 1
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 600_000, dklen=32)
    print("pbkdf2-sha256$600000$" + base64.b64encode(salt).decode("ascii")
          + "$" + base64.b64encode(digest).decode("ascii"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
