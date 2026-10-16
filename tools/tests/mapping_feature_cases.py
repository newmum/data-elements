"""Synthetic golden cases shared by HTTP preview and native NiFi acceptance tests.

No business records or connection credentials belong in these fixtures.
"""
import hashlib


def literal(value):
    return {"kind": "LITERAL", "value": value}


def field(name):
    return {"kind": "FIELD", "name": name}


def step(operator, **args):
    return {"operator": operator, "args": args}


def rule(name, value, steps=(), output="ANY", **options):
    return {"ruleId": name, "enabled": True, "input": literal(value),
            "target": {"name": name, "logicalType": output, "nullable": True},
            "steps": list(steps), **options}


def functions():
    """One independently asserted example for every directly callable function."""
    cases = [
        ("upper", "a中b", [], "STRING", "A中B"),
        ("lower", "A中B", [], "STRING", "a中b"),
        ("trim", "  abc  ", [], "STRING", "abc"),
        ("ltrim", "  abc  ", [], "STRING", "abc  "),
        ("rtrim", "  abc  ", [], "STRING", "  abc"),
        ("substring", "A😀中文Z", [1, 3], "STRING", "😀中文"),
        ("replace", "a-b-a", ["a", "c"], "STRING", "c-b-c"),
        ("replaceRegex", "a12b34", ["[0-9]+", "X"], "STRING", "aXbX"),
        ("split", "a|b|", ["|"], "ARRAY", ["a", "b", ""]),
        ("pad", "12", [4, "0", "LEFT"], "STRING", "0012"),
        ("reverse", "A😀中", [], "STRING", "中😀A"),
        ("concat", ["a", None, "b"], ["-", "SKIP"], "STRING", "a-b"),
        ("enumMap", "A", [{"A": "甲"}, "未知"], "STRING", "甲"),
        ("round", "12.345", [2, "HALF_UP"], "DECIMAL", 12.35),
        ("ceil", "-1.2", [], "DECIMAL", -1),
        ("floor", "-1.2", [], "DECIMAL", -2),
        ("abs", "-12.30", [], "DECIMAL", 12.3),
        ("multiply", "3.5", [2], "DECIMAL", 7),
        ("divide", "10", [4, 2, "HALF_UP"], "DECIMAL", 2.5),
        ("add", "3.5", [2], "DECIMAL", 5.5),
        ("subtract", "3.5", [2], "DECIMAL", 1.5),
        ("parseDate", "2026/07/01 11:20:21", ["AUTO", "DATETIME"], "DATETIME", "2026-07-01 11:20:21"),
        ("formatDate", "2026-07-01 11:20:21", ["uuuu/MM/dd HH:mm:ss"], "STRING", "2026/07/01 11:20:21"),
        ("toEpochMs", "1970-01-01T00:00:01Z", [], "LONG", 1000),
        ("fromEpochMs", 1000, [], "INSTANT", "1970-01-01T00:00:01Z"),
        ("addDays", "2024-02-28 12:13:14", [1], "DATETIME", "2024-02-29 12:13:14"),
        ("addHours", "2024-02-28 23:13:14", [2], "DATETIME", "2024-02-29 01:13:14"),
        ("addMinutes", "2024-02-28 23:59:14", [2], "DATETIME", "2024-02-29 00:01:14"),
        ("addSeconds", "2024-02-28 23:59:59", [2], "DATETIME", "2024-02-29 00:00:01"),
        ("truncateDate", "2024-02-28 12:13:14.123", ["HOURS"], "DATETIME", "2024-02-28 12:00:00"),
        ("coalesce", None, [None, "fallback"], "STRING", "fallback"),
        ("nvl", None, [None, "fallback"], "STRING", "fallback"),
        ("nullIf", "empty", ["empty"], "STRING", None),
        ("toInt", "42", [], "INT", 42),
        ("toLong", "2147483648", [], "LONG", 2147483648),
        ("toDouble", "12.5", [], "DOUBLE", 12.5),
        ("toDecimal", "12.345", [8, 2, "HALF_UP"], "DECIMAL", 12.35),
        ("toBoolean", "true", [], "BOOLEAN", True),
        ("cast", "42", ["INT"], "INT", 42),
        ("base64Encode", "中文", [], "STRING", "5Lit5paH"),
        ("base64Decode", "5Lit5paH", [], "STRING", "中文"),
        ("urlEncode", "a b中", [], "STRING", "a+b%E4%B8%AD"),
        ("urlDecode", "a+b%E4%B8%AD", [], "STRING", "a b中"),
        ("jsonEncode", {"a": 1, "b": [True]}, [], "STRING", '{"a":1,"b":[true]}'),
        ("jsonDecode", '{"a":1,"b":[true],"mixed":["12",12,true,null,{"s":"12","n":12}]}', [], "ANY", {"a": 1, "b": [True], "mixed": ["12", 12, True, None, {"s": "12", "n": 12}]}),
        ("jsonValue", '{"a":{"b":12}}', ["$.a.b"], "ANY", 12),
        ("idCard15To18", "110105491231002", [{"centuryPolicy": "PREFIX_19", "already18Policy": "VALIDATE_AND_KEEP"}], "STRING", "11010519491231002X"),
        ("idCardValidate", "11010519491231002X", [], "BOOLEAN", True),
        ("now", None, [], "DATETIME", {"assert": "datetime"}),
        ("blankToNull", "   ", [], "STRING", None),
    ]
    cases.extend((name, "abc", [], "STRING", hashlib.new(algorithm, b"abc").hexdigest())
                 for name, algorithm in [("md5", "md5"), ("sha1", "sha1"), ("sha256", "sha256")])
    return cases


def golden_plan():
    rules, expected = [], {}
    for index, (name, value, params, output, result) in enumerate(functions()):
        key = "f%02d" % index
        rules.append(rule(key, value, [step("FUNCTION", function=name, parameters=params)], output))
        expected[key] = result
    examples = [
        (rule("direct", "原值", [step("DIRECT")], "STRING"), "原值"),
        (rule("constant", False, output="BOOLEAN"), False),
        (rule("zero", 0, output="INT"), 0),
        (rule("cast", "42", [step("CAST", logicalType="INT")], "INT"), 42),
        (rule("date", "20250219", [step("DATE_PARSE", pattern="AUTO", logicalType="DATE")], "DATE"), "2025-02-19"),
        (rule("dateTime", "20260701112021", [step("DATE_PARSE", pattern="AUTO", logicalType="DATETIME")], "DATETIME"), "2026-07-01 11:20:21"),
        (rule("dateFormat", "2026-07-01 11:20:21", [step("DATE_FORMAT", pattern="uuuuMMddHHmmss")], "STRING"), "20260701112021"),
        (rule("converted", "110105491231002", [step("ID_CARD_15_TO_18", centuryPolicy="PREFIX_19", already18Policy="VALIDATE_AND_KEEP")], "STRING"), "11010519491231002X"),
        (rule("valid", "11010519491231002X", [step("ID_CARD_VALIDATE")], "BOOLEAN"), True),
        (rule("enum", "A", [step("STATIC_MAP", values={"A": "甲"}, fallback="未知")], "STRING"), "甲"),
        (rule("single", "A", [step("LOOKUP", values={"A": "甲"}, missPolicy="NULL")], "STRING"), "甲"),
        (rule("multi", "A,X,A,B", [step("LOOKUP_MULTI", values={"A": "甲", "B": "乙"}, partialMissingPolicy="KEEP_TOKEN", outputSeparator="|", outputMode="STRING")], "STRING"), "甲|X|甲|乙"),
        (rule("multiArray", "A,B", [step("LOOKUP_MULTI", values={"A": "甲", "B": "乙"}, outputMode="ARRAY")], "ARRAY"), ["甲", "乙"]),
        (rule("concat", None, [step("CONCAT", inputs=[field("x"), literal("乙")], separator="-", nullElementPolicy="EMPTY")], "STRING"), "甲-乙"),
        (rule("expression", 2, [step("EXPRESSION", expression="${value} * 3 + 1")], "INT"), 7),
        (rule("validation", 12, [step("VALIDATE", required=True, minimum=10, maximum=20, allowedValues=[12])], "INT"), 12),
        (rule("uuid32", None, [step("SYSTEM_VALUE", generator="UUID32")], "STRING"), {"assert": "uuid32"}),
        (rule("uuid", None, [step("SYSTEM_VALUE", generator="UUID")], "STRING"), {"assert": "uuid"}),
        (rule("clock", None, [step("SYSTEM_VALUE", generator="NOW")], "DATETIME"), {"assert": "datetime"}),
        (rule("blankDefault", "  ", [step("FUNCTION", function="blankToNull", parameters=[]), step("FUNCTION", function="coalesce", parameters=["默认值"])], "STRING"), "默认值"),
        (rule("stepOriginal", "bad", [{**step("CAST", logicalType="INT"), "onError": "KEEP_SOURCE"}], "STRING"), "bad"),
        (rule("stepDefault", "bad", [{**step("CAST", logicalType="INT"), "onError": "DEFAULT_VALUE", "defaultValue": "9"}], "STRING"), "9"),
        (rule("ruleDefault", "bad", [step("CAST", logicalType="INT")], "STRING", onError="DEFAULT_VALUE", defaultValue="9"), "9"),
        (rule("ruleNull", "bad", [step("CAST", logicalType="INT")], "STRING", onError="NULL"), None),
        (rule("ruleOriginal", "bad", [step("CAST", logicalType="INT")], "STRING", onError="KEEP_SOURCE"), "bad"),
    ]
    conditional = rule("condition", None, output="STRING")
    conditional.update(when={"op": "eq", "left": field("x"), "right": literal("甲")},
                       then={"input": literal("yes"), "steps": []},
                       **{"else": {"input": literal("no"), "steps": []}})
    examples.append((conditional, "yes"))
    for item, result in examples:
        rules.append(item)
        expected[item["target"]["name"]] = result
    return {"schemaVersion": "2.0", "policies": {"zoneId": "Asia/Shanghai", "recordErrorMode": "ROUTE_INVALID"}, "rules": rules}, expected
