import fs from "fs/promises";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const IMAGE_ROOT = path.resolve(__dirname, "src/assets/images");
const OUTPUT_SCSS = path.resolve(__dirname, "src/styles/auto-image-styles.scss");

// 工具函数：清洗文件名（替换特殊字符为合法字符）
function cleanFileName(name) {
  // 替换 @、空格、# 等特殊字符为 -
  return name.replace(/[@#\s]/g, "-");
}

// 扫描文件夹
async function scanImages(folderPath, isRoot = false) {
  const result = {};
  const files = await fs.readdir(folderPath);

  for (const file of files) {
    const fullPath = path.join(folderPath, file);
    const stat = await fs.stat(fullPath);

    if (stat.isDirectory()) {
      result[file] = await scanImages(fullPath);
    } else if (/\.(png|jpg|jpeg|svg|gif)$/i.test(file)) {
      const originalFileName = path.basename(file, path.extname(file));
      // 清洗文件名（处理@等特殊字符）
      const cleanedFileName = cleanFileName(originalFileName);
      const relativeFolder = path.relative(IMAGE_ROOT, folderPath).split(path.sep).join("/");
      const imgUrl = isRoot
        ? `@/assets/images/${file}`
        : `@/assets/images/${relativeFolder}/${file}`;

      result[cleanedFileName] = {
        url: imgUrl,
        originalName: originalFileName,
      };
    }
  }
  return result;
}

// 生成SCSS代码（修复@extend使用占位符）
function generateSCSS(imageData) {
  let scssContent = `// 自动生成：请勿手动修改 更新图片请执行 pnpm run gen:image-styles\n`;

  // 1. 生成CSS变量
  scssContent += `:root {\n`;
  Object.entries(imageData).forEach(([key, value]) => {
    if (typeof value === "object" && !value.url) {
      // 子文件夹
      Object.entries(value).forEach(([imgName, imgInfo]) => {
        scssContent += `  --images-${key}-${imgName}: url("${imgInfo.url}");\n`;
      });
    } else if (value.url) {
      // 根目录文件
      scssContent += `  --images-${key}: url("${value.url}");\n`;
    }
  });
  scssContent += `}\n\n`;

  // 2. 生成公共背景样式（改用SCSS占位符）
  scssContent += `// 公共背景样式（SCSS占位符，用于@extend）\n`;
  scssContent += `%images-base {\n`;
  scssContent += `  background-repeat: no-repeat;\n`;
  scssContent += `}\n\n`;

  // 3. 生成背景图类
  scssContent += `// 自动生成的背景类\n`;
  Object.entries(imageData).forEach(([key, value]) => {
    if (typeof value === "object" && !value.url) {
      // 子文件夹的类
      Object.entries(value).forEach(([imgName]) => {
        scssContent += `.images-${key}-${imgName} {\n`;
        scssContent += `  @extend %images-base;\n`; // 引用占位符
        scssContent += `  background-image: var(--images-${key}-${imgName});\n`;
        scssContent += `}\n`;
      });
    } else if (value.url) {
      // 根目录文件的类
      scssContent += `.images-${key} {\n`;
      scssContent += `  @extend %images-base;\n`; // 引用占位符
      scssContent += `  background-image: var(--images-${key});\n`;
      scssContent += `}\n`;
    }
  });

  return scssContent;
}

async function run() {
  try {
    const imageData = await scanImages(IMAGE_ROOT, true);
    const scssCode = generateSCSS(imageData);
    await fs.writeFile(OUTPUT_SCSS, scssCode);
    console.log("✅ 图片样式文件已生成：", OUTPUT_SCSS);
  } catch (error) {
    console.error("❌ 生成图片样式失败：", error.message);
    process.exit(1);
  }
}

run();
