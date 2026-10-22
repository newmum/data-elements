#!/usr/bin/env python3
"""Resumable file push using the standard library; credentials come only from the environment."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


class Slice:
    def __init__(self, stream, length):
        self.stream, self.remaining = stream, length

    def read(self, size=65536):
        data = self.stream.read(min(size if size >= 0 else 65536, self.remaining))
        self.remaining -= len(data)
        return data


def digest(stream, length=None):
    result = hashlib.sha256()
    remaining = length
    while remaining is None or remaining > 0:
        part = stream.read(65536 if remaining is None else min(65536, remaining))
        if not part:
            break
        result.update(part)
        if remaining is not None:
            remaining -= len(part)
    if remaining:
        raise ValueError("文件在读取时变短")
    return result.hexdigest()


def main():
    parser = argparse.ArgumentParser(description="海通照片/文档分片推送；同一幂等键重跑可续传")
    parser.add_argument("file", type=Path)
    parser.add_argument("--base-url", default="http://127.0.0.1:18083")
    parser.add_argument("--datasource", required=True)
    parser.add_argument("--collection", required=True)
    parser.add_argument("--business-key", required=True)
    parser.add_argument("--idempotency-key", required=True)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--metadata", default="{}", help="集合契约规定的 JSON 元数据")
    group.add_argument("--metadata-file", type=Path, help="从 UTF-8 JSON 文件读取元数据，避免命令行引号差异")
    parser.add_argument("--wait-seconds", type=int, default=300)
    args = parser.parse_args()
    key = os.environ.get("HAITONG_API_KEY")
    if not key:
        parser.error("请在环境变量 HAITONG_API_KEY 中设置密钥")
    url = urllib.parse.urlsplit(args.base_url)
    if url.scheme not in ("http", "https") or not url.hostname or url.username or url.password or url.query or url.fragment:
        parser.error("base-url 必须为不含凭据、查询参数和片段的 HTTP(S) 地址")
    metadata = json.loads(args.metadata_file.read_text(encoding="utf-8") if args.metadata_file else args.metadata)
    if not isinstance(metadata, dict):
        parser.error("metadata 必须为 JSON 对象")
    # Do not follow redirects with the API key to another destination.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *unused):
            return None
    handlers = [NoRedirect]
    if url.hostname.lower() in ("localhost", "127.0.0.1", "::1"):
        handlers.append(urllib.request.ProxyHandler({}))
    opener = urllib.request.build_opener(*handlers)

    def request(method, path, body=None, idem=None, headers=None):
        values = {"X-Api-Key": key, **(headers or {})}
        if idem:
            values["Idempotency-Key"] = idem
        if isinstance(body, dict):
            body = json.dumps(body, ensure_ascii=False).encode("utf-8")
            values["Content-Type"] = "application/json"
        req = urllib.request.Request(args.base_url.rstrip("/") + path, data=body, headers=values, method=method)
        with opener.open(req, timeout=120) as response:
            raw = response.read(1024 * 1024 + 1)
            if len(raw) > 1024 * 1024:
                raise ValueError("服务响应过大")
            return json.loads(raw)

    with args.file.open("rb") as source:
        size = os.fstat(source.fileno()).st_size
        sha256 = digest(source)
        upload = request("POST", "/api/v1/file-uploads", {
            "datasourceId": args.datasource, "collectionId": args.collection,
            "fileName": args.file.name, "size": size, "sha256": sha256,
        }, args.idempotency_key)
        prefix = "/api/v1/file-uploads/" + upload["uploadId"]
        # Server pages are authoritative. Reading each page is not a per-file detail request.
        complete, after = set(), 0
        if upload["state"] in ("CREATED", "UPLOADING"):
            while True:
                parts = request("GET", f"{prefix}/parts?after={after}&limit=100")["parts"]
                if not parts:
                    break
                complete.update(p["partNumber"] for p in parts)
                after = max(complete)
                if len(parts) < 100:
                    break
            part_size = upload["partSize"]
            for number in range(1, upload["partCount"] + 1):
                if number in complete:
                    continue
                offset = (number - 1) * part_size
                length = min(part_size, size - offset)
                source.seek(offset)
                checksum = digest(source, length)
                source.seek(offset)
                request("PUT", f"{prefix}/parts/{number}", Slice(source, length), headers={
                    "Content-Type": "application/octet-stream", "Content-Length": str(length), "X-Part-SHA256": checksum,
                })
                print(f"已确认分片 {number}/{upload['partCount']}", file=sys.stderr)
            request("POST", prefix + "/complete")
        elif upload["state"] not in ("FINALIZING", "READY"):
            raise ValueError("该会话已取消、拒绝或过期，请检查文件后使用新的幂等键")
        accepted = request("POST", prefix + "/submit", {"businessKey": args.business_key, "metadata": metadata}, args.idempotency_key)
        deadline = time.monotonic() + max(0, args.wait_seconds)
        delay = 0.5
        while True:
            state = request("GET", prefix)
            if state["state"] in ("REJECTED", "EXPIRED", "ABORTED"):
                print(json.dumps(state, ensure_ascii=False, indent=2))
                return 2
            deliveries = state.get("deliveries", [])
            terminal = bool(deliveries) and all(d["status"] in ("SUCCEEDED", "FAILED", "UNKNOWN", "QUARANTINED") for d in deliveries)
            if terminal or time.monotonic() >= deadline:
                print(json.dumps({"acceptance": accepted, "current": state}, ensure_ascii=False, indent=2))
                return 0 if state["deliveryStatus"] == "SUCCEEDED" else 1
            time.sleep(delay)
            delay = min(5, delay * 1.5)


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    try:
        raise SystemExit(main())
    except urllib.error.HTTPError as error:
        # Response bodies are bounded; request headers/API keys are never printed.
        print(f"HTTP {error.code}: {error.read(4096).decode('utf-8', errors='replace')}", file=sys.stderr)
        raise SystemExit(2)
    except (OSError, ValueError, KeyError) as error:
        print(f"推送未完成：{type(error).__name__}；检查连接和文件后，使用原幂等键重新运行可续传。", file=sys.stderr)
        raise SystemExit(2)
