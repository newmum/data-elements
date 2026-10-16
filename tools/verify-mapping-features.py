"""Run synthetic mapping acceptance cases against an authenticated development backend.

Usage: python tools/verify-mapping-features.py --token-file <private session path>
Writes only synthetic cases and results below the ignored logs directory.
"""
import argparse
import datetime
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "tests"))
from mapping_feature_cases import functions, golden_plan


def matches(actual, expected):
    if isinstance(expected, dict) and "assert" in expected:
        pattern = {"uuid32": r"[0-9a-fA-F]{32}", "uuid": r"[0-9a-fA-F-]{36}", "datetime": r"\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}.*"}[expected["assert"]]
        return isinstance(actual, str) and re.fullmatch(pattern, actual) is not None
    if isinstance(expected, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}", expected):
        return isinstance(actual, str) and datetime.datetime.fromisoformat(actual.replace("Z", "+00:00")) == datetime.datetime.fromisoformat(expected)
    return actual == expected


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://127.0.0.1:8088")
    parser.add_argument("--token-file", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("logs/mapping-all-features"))
    options = parser.parse_args()
    token = options.token_file.read_text(encoding="utf-8").strip()
    def api(path, body=None):
        request = urllib.request.Request(options.base.rstrip("/") + path,
            data=None if body is None else json.dumps(body, ensure_ascii=False).encode(),
            headers={"token": token, "Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=45) as response:
            result = json.load(response)
        if isinstance(result, dict) and "data" in result and result.get("code") in [0, 1, 200]:
            return result["data"]
        return result
    capabilities = api("/nifi/api/field-mapping/capabilities")
    direct = {item["name"] for item in capabilities["functions"] if item.get("requiredOperator", "FUNCTION") == "FUNCTION"}
    covered = {case[0] for case in functions()}
    # Some capability versions omit requiredOperator for direct functions.
    if direct != covered:
        direct -= {"uuid", "expression", "codeTableLookup"}
    assert direct == covered, {"uncovered": sorted(direct-covered), "unexpected": sorted(covered-direct)}
    spec, expected = golden_plan()
    options.output.mkdir(parents=True, exist_ok=True)
    (options.output / "golden-spec.json").write_text(json.dumps(spec, ensure_ascii=False, indent=2), encoding="utf-8")
    (options.output / "golden-expected.json").write_text(json.dumps(expected, ensure_ascii=False, indent=2), encoding="utf-8")
    compiled = api("/nifi/api/field-mapping/compile", {"spec": spec})
    (options.output / "golden-compiled.json").write_text(json.dumps(compiled, ensure_ascii=False, indent=2), encoding="utf-8")
    preview = api("/nifi/api/field-mapping/preview", {"spec": spec, "sampleRows": [{"x": "甲"}]})
    results = preview.get("resultRows", [])
    failures = []
    if not results:
        failures.append({"rowErrors": preview.get("rowResults")})
    else:
        for name, value in expected.items():
            if not matches(results[0].get(name), value):
                failures.append({"field": name, "expected": value, "actual": results[0].get(name)})
    report = {"engineVersion": capabilities["engineVersion"], "directFunctions": len(covered), "goldenRules": len(expected),
              "preview": preview, "failures": failures}
    (options.output / "golden-verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "preview"}, ensure_ascii=False))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
