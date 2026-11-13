import path from "node:path";
import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import { repository } from "./check-mock-contracts.mjs";

// 使用应用已安装的编译器及真实 tsconfig，不创建替代声明或修改生产配置。
const app = path.join(repository, "data-elements-chengtian");
const load = createRequire(path.join(app, "package.json"));
const ts = load("typescript");
const configPath = path.join(app, "tsconfig.json");
const config = ts.readConfigFile(configPath, ts.sys.readFile);
if (config.error) throw new Error(ts.flattenDiagnosticMessageText(config.error.messageText, " "));
const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, app, undefined, configPath);
const options = { ...parsed.options, noEmit: true };
const format = (diagnostics) => ts.formatDiagnosticsWithColorAndContext(diagnostics, {
  getCurrentDirectory: () => repository,
  getCanonicalFileName: (file) => file,
  getNewLine: () => "\n",
});
const fixture = path.join(app, "tests/mock-transport.types.ts");
const fixtures = ts.getPreEmitDiagnostics(ts.createProgram([fixture], options));
console.log(`Mock传输正反向类型样例：${fixtures.length} 条诊断`);

const entry = path.join(app, "src/mock/browser.ts");
const roots = [entry, fixture, ...parsed.fileNames.filter((file) => file.endsWith(".d.ts"))];
const current = ts.getPreEmitDiagnostics(ts.createProgram(roots, options));
// 仅只读替换宿主入口为 HEAD；其余依赖使用同一当前工作区，隔离本次入口接入影响。
const before = execFileSync("git", ["show", "HEAD:data-elements-chengtian/src/mock/browser.ts"], {
  cwd: repository, encoding: "utf8",
});
const host = ts.createCompilerHost(options), read = host.readFile;
host.readFile = (file) => path.resolve(file).toLowerCase() === entry.toLowerCase() ? before : read(file);
const baseline = ts.getPreEmitDiagnostics(ts.createProgram(roots, options, host));
const key = (diagnostic) => `${diagnostic.file?.fileName || ""}|${diagnostic.code}|${ts.flattenDiagnosticMessageText(diagnostic.messageText, " ")}`;
const old = new Set(baseline.map(key));
const added = current.filter((diagnostic) => !old.has(key(diagnostic)));
const local = current.filter((diagnostic) => diagnostic.file &&
  /[\\/]src[\\/]mock[\\/]|[\\/]mock[\\/]core[\\/]|mock-transport\.types\.ts$/.test(diagnostic.file.fileName));
console.log(`Mock入口依赖图：当前 ${current.length}，HEAD入口基线 ${baseline.length}；新增 ${added.length}，Mock范围 ${local.length}`);
console.log("未通过的依赖文件（不宣称全仓类型检查通过）：", [...new Set(current.filter((diagnostic) => !local.includes(diagnostic))
  .map((diagnostic) => diagnostic.file ? path.relative(repository, diagnostic.file.fileName).replaceAll("\\", "/") : "编译选项"))]);
const failures = [...parsed.errors, ...fixtures, ...added, ...local];
if (failures.length) { console.error(format(failures)); process.exitCode = 1; }