import * as SFCCompiler from "vue/compiler-sfc";
import { babelParse } from "vue/compiler-sfc";
import * as sass from "sass";

// ===================== 第一步：环境兼容性处理（核心修复）=====================

// 2. Polyfill Node.js 特有 API pathToFileURL，解决浏览器环境报错
if (typeof window !== "undefined" && !window.pathToFileURL) {
  window.pathToFileURL = (path) => {
    if (typeof path !== "string") path = String(path);
    // 标准化路径分隔符，避免 Windows 路径问题
    const normalizedPath = path.replace(/\\/g, "/");
    // 简化生成 file URL，仅满足 @vue/compiler-sfc 兼容性要求，不追求完整 Node.js 功能
    try {
      return new URL(`file:///${normalizedPath.startsWith("/") ? "" : "/"}${normalizedPath}`);
    } catch (e) {
      console.error(e);
      // 路径解析失败时返回兜底 URL，避免中断编译
      return new URL("file:///dummy-path.scss");
    }
  };

  // 补充全局 url 模块，适配 compiler-sfc 内部调用逻辑
  if (!window.url) {
    window.url = {
      pathToFileURL: window.pathToFileURL,
    };
  }
}

// ===================== 第二步：Babel 按需加载 =====================
const COMP_IDENTIFIER = `__sfc__`;
const BABEL_LOAD_TIMEOUT = 30_000;
let babelLoadPromise;

function getBabel() {
  const babel = globalThis.window?.Babel;
  return typeof babel?.transform === "function" ? babel : null;
}

function loadBabel() {
  // 命令行编译器会预先注入同一份 Babel，直接复用。
  const babel = getBabel();
  if (babel) return Promise.resolve(babel);
  if (typeof document === "undefined") {
    throw new Error("TypeScript 编译器未初始化，请先加载 Babel Standalone");
  }
  if (babelLoadPromise) return babelLoadPromise;

  babelLoadPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = new URL(`${import.meta.env?.BASE_URL ?? "./"}babel.min.js`, document.baseURI).href;
    script.async = true;

    const cleanup = () => {
      clearTimeout(timeout);
      script.onload = null;
      script.onerror = null;
    };
    const fail = (reason) => {
      cleanup();
      script.remove();
      reject(new Error(`TypeScript 编译器${reason}，请重新尝试编译`));
    };
    const timeout = setTimeout(() => fail("加载超时"), BABEL_LOAD_TIMEOUT);
    script.onerror = () => fail("加载失败");
    script.onload = () => {
      const loaded = getBabel();
      if (!loaded) {
        fail("加载后未能初始化");
        return;
      }
      cleanup();
      resolve(loaded);
    };
    try {
      document.head.appendChild(script);
    } catch {
      fail("加载失败");
    }
  }).catch((error) => {
    babelLoadPromise = undefined;
    throw error;
  });
  return babelLoadPromise;
}

const usesTypeScript = (descriptor) =>
  descriptor.script?.lang === "ts" || descriptor.scriptSetup?.lang === "ts";

// ===================== 第三步：核心编译函数（compileCode）=====================
/**
 * 编译 SFC 源代码
 * TypeScript 组件在实际编译前按需加载 Babel。
 * @param {string} sourceCode - SFC 源码
 * @returns {Promise<{compileJs: string, compileCss: string}>}
 */
export async function compileCode(sourceCode) {
  // 初始化编译结果容器，避免属性未定义报错
  const compiled = {
    js: "",
    css: "",
    errors: [],
  };

  try {
    const file = parseFile(sourceCode, compiled);
    if (file) {
      const babel = usesTypeScript(file.descriptor) ? await loadBabel() : null;
      compileParsedFile(sourceCode, compiled, file, babel);
    }
  } catch (e) {
    compiled.errors.push(e instanceof Error ? e.message : "未知编译错误");
  }

  // 处理编译错误
  if (compiled.errors.length) {
    throw new Error(`编译失败：${compiled.errors.join("; ")}`);
  } else {
    let jsCode = compiled.js;
    // 容错处理：避免 jsCode 为空导致 AST 解析失败
    if (!jsCode) {
      jsCode = `const ${COMP_IDENTIFIER} = {}; export default ${COMP_IDENTIFIER};`;
    }

    // 解析 AST 并替换 import/export
    try {
      const ast = babelParse(jsCode, {
        sourceType: "module",
      });

      const replaceCode = (node, subCode) =>
        jsCode.substring(0, node.start) + subCode + jsCode.substring(node.end);

      // 倒序遍历，避免替换后节点位置偏移
      for (let i = ast.program.body.length - 1; i >= 0; i--) {
        const node = ast.program.body[i];
        if (node.type === "ImportDeclaration") {
          // 替换 import 为自定义魔法导入
          jsCode = replaceCode(
            node,
            node.specifiers
              .map(
                (it) => {
                  const importedName =
                    it.type === "ImportNamespaceSpecifier"
                      ? "*"
                      : it.type === "ImportDefaultSpecifier"
                        ? "default"
                        : it.imported?.name;
                  return `const ${it.local?.name || importedName} = ___magic__import__('${node.source.value}', '${importedName}');`;
                }
              )
              .join("\r\n")
          );
        } else if (node.type === "ExportDefaultDeclaration") {
          // 替换 export default 为 return
          jsCode = replaceCode(node, `return ${node.declaration?.name || COMP_IDENTIFIER}`);
        }
      }
    } catch (e) {
      throw new Error(`AST 解析/替换失败：${e.message}`);
    }

    return {
      compileCss: compiled.css || "/* 无有效样式 */",
      compileJs: jsCode.trim(),
    };
  }
}

// ===================== 第四步：文件编译核心逻辑（compileFile）=====================
export function compileFile(code, compiled) {
  const file = parseFile(code, compiled);
  if (file) compileParsedFile(code, compiled, file, getBabel());
}

function parseFile(code, compiled) {
  // 生成唯一 ID，用于 scoped CSS 和文件名
  const uuid = () => {
    function S4() {
      return (((1 + Math.random()) * 0x10000) | 0).toString(16).substring(1);
    }
    return S4() + S4() + S4() + S4() + S4() + S4() + S4() + S4();
  };

  const id = uuid();
  const filename = `${id}.vue`;

  // 第一步：解析 SFC 模板
  try {
    const parseResult = SFCCompiler.parse(code, {
      filename,
      sourceMap: true,
    });

    if (parseResult.errors.length) {
      compiled.errors = parseResult.errors.map((err) => err.message);
      return;
    }
    return { descriptor: parseResult.descriptor, id, filename };
  } catch (e) {
    compiled.errors.push(`SFC 解析失败：${e.message}`);
  }
}

function compileParsedFile(code, compiled, { descriptor, id, filename }, babel) {
  // 第二步：校验组合式 API 使用环境（仅允许 <script setup>）
  if (hasVueCompositionFunctions(code) && !hasScriptSetup(code)) {
    compiled.errors.push(
      "defineProps、defineExpose、defineEmits、defineSlots、defineOptions、defineModel需要在<script setup>下使用"
    );
    return;
  }

  // 第三步：编译脚本（script/script setup）
  const hasScoped = descriptor.styles.some((s) => s.scoped);
  let clientCode = "";

  const appendSharedCode = (code) => {
    clientCode += code;
  };

  const clientScriptResult = compileScript(descriptor, id, compiled, babel);
  if (!clientScriptResult) {
    return;
  }
  const [clientScript, bindings] = clientScriptResult;
  clientCode += clientScript;

  // 第四步：编译模板（仅非 <script setup> 场景需要单独编译）
  if (descriptor.template && !descriptor.scriptSetup) {
    const clientTemplateResult = doCompileTemplate(descriptor, id, bindings, compiled);
    if (!clientTemplateResult) {
      return;
    }
    clientCode += clientTemplateResult;
  }

  // 第五步：添加 scoped 标识和文件信息
  if (hasScoped) {
    appendSharedCode(`\n${COMP_IDENTIFIER}.__scopeId = ${JSON.stringify(`data-v-${id}`)}`);
  }

  if (clientCode) {
    appendSharedCode(
      `\n${COMP_IDENTIFIER}.__file = ${JSON.stringify(filename)}` +
        `\nexport default ${COMP_IDENTIFIER}`
    );
    compiled.js = clientCode.trimStart();
  }

  // 第六步：编译样式（核心修改：手动预编译 SCSS，避免 compiler-sfc 内置预编译报错）
  let css = "";
  for (const style of descriptor.styles) {
    // 不支持 style module
    if (style.module) {
      compiled.errors.push(`<style module> is not supported in the playground.`);
      return;
    }

    let styleContent = style.content;
    const styleLang = style.lang || "css";
    const isScssOrSass = styleLang === "scss" || styleLang === "sass";

    // 手动预编译 SCSS/Sass 为纯 CSS（绕过 compiler-sfc 内置预编译，避免 pathToFileURL 调用）
    if (isScssOrSass) {
      try {
        if (!sass || typeof sass.compileString !== "function") {
          throw new Error("sass 编译器未正确加载，无法编译 SCSS/Sass 样式");
        }

        const sassResult = sass.compileString(styleContent, {
          style: "expanded", // 生成易读的 CSS 格式
          sourceMap: false, // 浏览器环境关闭 sourceMap，简化编译
          indentedSyntax: styleLang === "sass", // 适配 Sass 缩进语法
          quietDeps: true, // 抑制无关依赖警告
        });

        // 替换为编译后的纯 CSS
        styleContent = sassResult.css;
      } catch (e) {
        const errorMsg = `SCSS/Sass 手动编译失败：${e.message}`;
        console.error(errorMsg);
        compiled.errors.push(errorMsg);
        // 编译失败仍保留原内容，避免中断后续流程
        styleContent = style.content;
      }
    }

    // 仅用 compiler-sfc 处理纯 CSS（scoped 标识、样式格式化），不做预编译
    const compileStyleOptions = {
      source: styleContent, // 传入手动编译后的纯 CSS
      filename,
      id,
      scoped: style.scoped,
      modules: !!style.module,
      // 移除所有预编译相关配置，避免 compiler-sfc 调用内置预编译逻辑
    };

    try {
      const styleResult = SFCCompiler.compileStyle(compileStyleOptions);

      // 过滤无效错误，仅保留有效样式错误
      if (styleResult.errors.length) {
        const validErrors = styleResult.errors.filter(
          (err) =>
            !err.message.includes("pathToFileURL") &&
            !err.message.includes("preprocess") &&
            !err.message.includes("Unsupported Sass feature")
        );

        if (validErrors.length) {
          compiled.errors.push(...validErrors.map((err) => err.message));
          return;
        }
      } else {
        css += styleResult.code + "\n";
      }
    } catch (e) {
      compiled.errors.push(`样式处理失败：${e.message}`);
      return;
    }
  }

  // 整理最终 CSS 结果
  compiled.css = css ? css.trim() : "/* No <style> tags present */";
  // 清空错误容器（若所有步骤均成功）
  if (!compiled.errors.length) {
    compiled.errors = [];
  }
}

// ===================== 第五步：脚本编译（compileScript）=====================
function compileScript(descriptor, id, compiled, babel) {
  try {
    // 无脚本时返回空组件
    if (!descriptor.script && !descriptor.scriptSetup) {
      return [`\nconst ${COMP_IDENTIFIER} = {}`, undefined];
    }

    // 编译 SFC 脚本
    const compiledScript = SFCCompiler.compileScript(descriptor, {
      id,
      refSugar: true,
      inlineTemplate: true,
    });

    let code = compiledScript.content;
    // 添加绑定信息注释（调试用）
    if (compiledScript.bindings) {
      code += `\n/* Analyzed bindings: ${JSON.stringify(compiledScript.bindings, null, 2)} */`;
    }

    if (usesTypeScript(descriptor)) {
      if (!babel) {
        compiled.errors.push("TypeScript 编译器尚未加载，请通过 compileCode 编译 TypeScript 组件");
        return;
      }
      try {
        const transformResult = babel.transform(code, {
          filename: `${id}.ts`,
          presets: ["typescript"],
          sourceType: "module",
        });

        if (transformResult.errors && transformResult.errors.length) {
          compiled.errors = transformResult.errors.map((e) => `TS 转译失败：${e.message}`);
          return;
        }

        code = transformResult.code || code;
      } catch (transformErr) {
        const tsError = `TS 转译异常：${transformErr instanceof Error ? transformErr.message : String(transformErr)}`;
        compiled.errors = [tsError];
        return;
      }
    }

    // 重写默认导出，适配自定义组件标识
    code = SFCCompiler.rewriteDefault(code, COMP_IDENTIFIER);

    return [code, compiledScript.bindings];
  } catch (e) {
    compiled.errors.push(`脚本编译失败：${e.message}`);
    return;
  }
}

// ===================== 第六步：模板编译（doCompileTemplate）=====================
function doCompileTemplate(descriptor, id, bindingMetadata, compiled) {
  try {
    const templateResult = SFCCompiler.compileTemplate({
      source: descriptor.template?.content || "",
      filename: descriptor.filename,
      id,
      scoped: descriptor.styles.some((s) => s.scoped),
      slotted: descriptor.slotted,
      isProd: false,
      compilerOptions: {
        bindingMetadata,
      },
    });

    if (templateResult.errors.length) {
      compiled.errors = templateResult.errors.map((err) => `模板编译失败：${err.message}`);
      return;
    }

    const fnName = `render`;
    // 替换 export 为局部函数，并挂载到组件标识上
    return (
      `\n${templateResult.code.replace(
        /\nexport (function|const) (render|ssrRender)/,
        `$1 ${fnName}`
      )}` + `\n${COMP_IDENTIFIER}.${fnName} = ${fnName}`
    );
  } catch (e) {
    compiled.errors.push(`模板处理失败：${e.message}`);
    return;
  }
}

// ===================== 第七步：辅助工具函数 =====================
/**
 * 检测是否包含 Vue 组合式 API
 */
function hasVueCompositionFunctions(content) {
  const regexPatterns = [/defineExpose\s*/, /defineProps\s*/, /defineEmits\s*/];
  for (const pattern of regexPatterns) {
    if (pattern.test(content)) {
      return true;
    }
  }
  return false;
}

/**
 * 检测是否为 <script setup> 语法
 */
function hasScriptSetup(content) {
  const regex = /<script\s+setup[^>]*>/i; // 忽略大小写，提高兼容性
  return regex.test(content);
}
