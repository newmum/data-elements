import html2canvas from "html2canvas";
import jsPDF from "jspdf";

/**
 * Vue3 打印组合式函数（DOM转图片预览版）
 * 解决打印样式不一致问题，实现所见即所得
 */
export function usePrint() {
  // 引入 html2canvas（需先安装：npm install html2canvas）

  const printDOM = async (target, options = {}) => {
    const defaultOptions = {
      title: "打印内容",
      // 预览弹窗配置
      preview: false,
      previewTitle: "打印预览",
      // 打印配置
      fitToA4: true,
      scale: 1.0,
      beforePrint: () => {},
      afterPrint: () => {},
    };
    const config = { ...defaultOptions, ...options };

    try {
      // 1. 获取目标 DOM 元素
      let printElement;
      if (target && target.__v_isRef) {
        printElement = target.value;
      } else if (typeof target === "string") {
        printElement = document.querySelector(target);
      } else if (target instanceof HTMLElement) {
        printElement = target;
      }

      if (!printElement) {
        console.error("未找到要打印的DOM元素");
        return;
      }

      await config.beforePrint();

      // 2. DOM 转图片
      const canvas = await html2canvas(printElement, {
        scale: 2, // 2倍缩放保证高清
        useCORS: true, // 解决跨域图片问题
        logging: false,
      });
      const imgDataUrl = canvas.toDataURL("image/png");

      // 3. 生成预览弹窗（可选）
      if (config.preview) {
        const previewModal = document.createElement("div");
        previewModal.style.cssText = `
          position: fixed; top: 0; left: 0; width: 100%; height: 100%;
          background: rgba(0,0,0,0.7); z-index: 9999; display: flex;
          justify-content: center; align-items: center;
        `;
        const previewContent = document.createElement("div");
        previewContent.style.cssText = `
          background: white; padding: 20px; border-radius: 8px;
          max-width: 90%; max-height: 90%; overflow: auto;
        `;
        previewContent.innerHTML = `
          <h3 style="margin:0 0 16px 0;">${config.previewTitle}</h3>
          <img src="${imgDataUrl}" style="max-width: 100%;" />
          <div style="margin-top: 16px; display: flex; gap: 10px; justify-content: flex-end;">
            <button id="print-confirm-btn" style="padding: 8px 16px; background: #1989fa; color: white; border: none; border-radius: 4px; cursor: pointer;">确认打印</button>
            <button id="print-cancel-btn" style="padding: 8px 16px; background: #fff; color: #666; border: 1px solid #ccc; border-radius: 4px; cursor: pointer;">取消</button>
          </div>
        `;
        previewModal.appendChild(previewContent);
        document.body.appendChild(previewModal);

        // 4. 弹窗按钮事件
        await new Promise((resolve) => {
          document.getElementById("print-confirm-btn").addEventListener("click", () => {
            document.body.removeChild(previewModal);
            resolve(true);
          });
          document.getElementById("print-cancel-btn").addEventListener("click", () => {
            document.body.removeChild(previewModal);
            resolve(false);
          });
        });
      }

      // 5. 创建隐藏 iframe 用于打印图片
      const iframe = document.createElement("iframe");
      iframe.style.cssText = "position:absolute;width:0;height:0;border:none;visibility:hidden;";
      document.body.appendChild(iframe);
      const iframeDoc = iframe.contentDocument || iframe.contentWindow.document;

      // 6. 构建打印页面（仅包含图片）
      iframeDoc.open();
      iframeDoc.write(`
        <!DOCTYPE html>
        <html>
          <head>
            <meta charset="UTF-8">
            <title>${config.title}</title>
            <style>
              @media print {
                @page {
                  size: A4 portrait;
                  margin: 0;
                }
                body {
                  margin: 0 12px; padding: 20px 0 0 0;
                  display: flex; justify-content: center; align-items: flex-start;
                }
                .print-img {
                  max-width: 100%;
                  max-height: 267mm; /* A4 有效打印高度 */
                  ${config.fitToA4 ? `transform: scale(${config.scale});` : ""}
                  transform-origin: top center;
                }
                header, footer { display: none !important; }
              }
            </style>
          </head>
          <body>
            <img src="${imgDataUrl}" class="print-img" />
          </body>
        </html>
      `);
      iframeDoc.close();

      // 7. 等待加载并打印
      await new Promise((resolve) => {
        iframe.onload = resolve;
        setTimeout(resolve, 1000);
      });
      iframe.contentWindow.print();

      // 8. 清理
      setTimeout(() => {
        document.body.removeChild(iframe);
        config.afterPrint();
      }, 1000);
    } catch (error) {
      console.error("打印失败:", error);
      if (window.ElMessage) {
        ElMessage.warning(`打印出错：${error.message}`);
      } else {
        alert(`打印出错：${error.message}`);
      }
    }
  };

  // ========== 新增：DOM转PDF下载方法 ==========
  /**
   * 将指定DOM元素转成图片后下载为PDF（A4尺寸）
   * @param {HTMLElement|string|Ref<HTMLElement>} target - 目标DOM元素/选择器/Ref对象
   * @param {Object} options - 配置项
   * @param {string} options.filename - 下载的PDF文件名（默认：打印内容.pdf）
   * @param {boolean} options.fitToA4 - 是否适配A4尺寸（默认：true）
   * @param {string} options.orientation - 纸张方向：portrait(纵向)/landscape(横向)（默认：portrait）
   * @param {Function} options.beforeDownload - 下载前回调
   * @param {Function} options.afterDownload - 下载后回调
   */
  const downloadPdf = async (target, options = {}) => {
    const defaultOptions = {
      filename: "打印内容.pdf",
      fitToA4: true,
      orientation: "portrait", // portrait:纵向，landscape:横向
      beforeDownload: () => {},
      afterDownload: () => {},
    };
    const config = { ...defaultOptions, ...options };

    try {
      // 1. 获取目标DOM元素
      let pdfElement;
      if (target && target.__v_isRef) {
        pdfElement = target.value;
      } else if (typeof target === "string") {
        pdfElement = document.querySelector(target);
      } else if (target instanceof HTMLElement) {
        pdfElement = target;
      }

      if (!pdfElement) {
        console.warn("未找到要转换的DOM元素");
        return;
      }

      // 2. 执行下载前回调
      await config.beforeDownload();

      // 3. DOM转高清图片（scale:2保证清晰度）
      const canvas = await html2canvas(pdfElement, {
        scale: 2, // 2倍缩放，解决PDF模糊问题
        useCORS: true, // 支持跨域图片
        logging: false,
        backgroundColor: "#ffffff", // 背景色默认白色，避免透明
      });

      // 4. 初始化jsPDF（A4尺寸）
      const imgWidth = 210; // A4宽度（mm）
      const imgHeight = (canvas.height * imgWidth) / canvas.width; // 等比计算高度
      // 创建PDF实例：orientation(方向), unit(单位:mm), format(格式:A4)
      const pdf = new jsPDF({
        orientation: config.orientation,
        unit: "mm",
        format: "a4",
      });

      // 5. 适配A4尺寸（如果内容超出，自动缩放）
      let finalWidth = imgWidth;
      let finalHeight = imgHeight;
      const a4MaxHeight = config.orientation === "portrait" ? 297 : 210; // A4最大高度（纵向297mm，横向210mm）

      if (config.fitToA4 && finalHeight > a4MaxHeight) {
        const scaleRatio = a4MaxHeight / finalHeight; // 计算缩放比例
        finalWidth = imgWidth * scaleRatio;
        finalHeight = a4MaxHeight;
      }

      // 6. 将图片添加到PDF（居中显示）
      const x = (210 - finalWidth) / 2; // 水平居中
      const y = 2; // 垂直上边距10mm
      pdf.addImage(
        canvas.toDataURL("image/png"), // 图片base64
        "PNG", // 图片格式
        x, // 横坐标
        y, // 纵坐标
        finalWidth, // 图片宽度
        finalHeight // 图片高度
      );

      // 7. 下载PDF文件
      pdf.save(config.filename);

      // 8. 执行下载后回调
      await config.afterDownload();

      // 提示成功
      if (window.ElMessage) {
        $message.success(`PDF下载成功：${config.filename}`);
      } else {
        console.log(`PDF下载成功：${config.filename}`);
      }
    } catch (error) {
      console.error("PDF下载失败:", error);
      if (window.ElMessage) {
        ElMessage.warning(`PDF下载出错：${error.message}`);
      } else {
        alert(`PDF下载出错：${error.message}`);
      }
    }
  };

  return { printDOM, downloadPdf };
}
