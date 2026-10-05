export * from "./es";
export * from "./metadata-table-classification.js";

/**
 * Check if an element has a class
 * @param {HTMLElement} ele
 * @param {string} cls
 * @returns {boolean}
 */
export function hasClass(ele: HTMLElement, cls: string) {
  return !!ele.className.match(new RegExp("(\\s|^)" + cls + "(\\s|$)"));
}

/**
 * Add class to element
 * @param {HTMLElement} ele
 * @param {string} cls
 */
export function addClass(ele: HTMLElement, cls: string) {
  if (!hasClass(ele, cls)) ele.className += " " + cls;
}

/**
 * Remove class from element
 * @param {HTMLElement} ele
 * @param {string} cls
 */
export function removeClass(ele: HTMLElement, cls: string) {
  if (hasClass(ele, cls)) {
    const reg = new RegExp("(\\s|^)" + cls + "(\\s|$)");
    ele.className = ele.className.replace(reg, " ");
  }
}

/**
 * 判断是否是外部链接
 *
 * @param {string} path
 * @returns {Boolean}
 */
export function isExternal(path: string) {
  return /^(https?:|http?:|mailto:|tel:)/.test(path);
}

/**
 * 判断是否是内部链接
 *
 * @param {string} path
 * @returns {Boolean}
 */
export function isInternal(path: string) {
  return /^(#\/)/.test(path);
}

/**
 * 格式化增长率，保留两位小数 ，并且去掉末尾的0  取绝对值
 *
 * @param growthRate
 * @returns
 */
export function formatGrowthRate(growthRate: number) {
  if (growthRate === 0) {
    return "-";
  }

  const formattedRate = Math.abs(growthRate * 100)
    .toFixed(2)
    .replace(/\.?0+$/, "");
  return formattedRate + "%";
}

/**
 * 计算文本宽度
 *
 * @param text
 * @param fontSize
 * @param fontFamily
 * @returns 文本宽度
 */
export function getTextWidth(text: string, fontSize = "14px", fontFamily = "PingFang") {
  const canvas = document.createElement("canvas");
  const context = canvas.getContext("2d");

  if (!context) {
    return 0;
  }

  context.font = `${fontSize} ${fontFamily}`;
  return context.measureText(text).width;
}

/**
 * 格式化数据单位
 *
 * 万以下：展示如 1256。
 * 万以上亿以下：如 1.25万、1256.25万。
 * 亿以上万亿以下：如 1255亿。
 * 万亿以上：如 1.25万亿。
 */
export const formatDataUnit = (
  data = 0,
  options?: {
    unit?: string;
    decimals?: number;
    autoDecimals?: boolean; // 无小数为时，不返回，否则按小数位返回，默认2，可自定义返回长度
  }
) => {
  const { unit = "", decimals = 2, autoDecimals = true } = options || {};
  if (!Number(data)) return { data: 0, unit, complete: 0 + unit };
  const stateData = Number(data);

  const RESULT_MAPS = [
    {
      condition: stateData < Math.pow(10, 4),
      getData: () => stateData,
      unit: "",
    },
    {
      condition: stateData >= Math.pow(10, 4) && stateData < Math.pow(10, 8),
      getData: () => Number((stateData / Math.pow(10, 4)).toFixed(decimals)),
      unit: "万",
    },
    {
      condition: stateData >= Math.pow(10, 8) && stateData < Math.pow(10, 12),
      getData: () => Number((stateData / Math.pow(10, 8)).toFixed(decimals)),
      unit: "亿",
    },
    {
      condition: stateData >= Math.pow(10, 12),
      getData: () => Number((stateData / Math.pow(10, 12)).toFixed(decimals)),
      unit: "万亿",
    },
  ];

  const result = RESULT_MAPS.find((item) => item.condition);
  if (!result) return { data: 0, unit };

  const { getData, unit: resultUnit } = result;
  let resultData = getData();
  let resultDecimals = decimals;
  // 自动小数位时，查看是否是整数，即小数位返回0，如果是直接返回整数
  const isInteger = resultData % 1 === 0;
  if (autoDecimals && isInteger) {
    resultData = parseInt(resultData.toString());
    resultDecimals = 0;
  }

  return {
    data: resultData,
    unit: resultUnit + unit,
    decimals: resultDecimals,
    complete: resultData + resultUnit + unit,
  };
};

export function getUrlKey(name: string, url: string = location.href) {
  return (
    decodeURIComponent(
      (new RegExp("[?|&]" + name + "=" + "([^&;]+?)(&|#|;|$)").exec(url) || [
        undefined,
        "",
      ])[1].replace(/\+/g, "%20")
    ) || null
  );
}

/**
 * 遍历树节点，根据tid查找对应的节点
 * @param treeData 树数据
 * @param key 节点ID键名
 * @param value 节点ID值
 * @returns 找到的节点对象
 */
export function findTreeNodeByTid(treeData: any[], value: string, key: string = "tid"): any {
  for (const node of treeData) {
    if (node[key] === value) {
      return node;
    }
    if (node.children && node.children.length > 0) {
      const found = findTreeNodeByTid(node.children, value, key);
      if (found) {
        return found;
      }
    }
  }
  return null;
}
