import request from "@/utils/request";
import { regexps } from "@/utils/regexps";
import * as _ from "lodash-es";
import dayjs from "dayjs";

// 假设 $dialog 和 $message 是全局引入的 UI 库 API，需确保已声明类型
declare const $dialog: typeof ElMessageBox;
declare const $message: {
  success: (msg: string) => void;
  error: (msg: string) => void;
};

interface RequestOptions {
  url: string;
  param?: Record<string, any>;
  data?: Record<string, any>;
  params?: Record<string, any>;
  method?: string;
  action?: string;
  done?: () => void;
  fail?: () => void;
  /*提示确认信息*/
  info?: string;
}

interface HttpParams {
  [key: string]: any;
}

const common = {
  /**
   * 处理删除等操作（带确认弹窗）
   * @param options - 包含请求地址、ID、方法及回调的配置对象
   */
  handle: async (options: RequestOptions) => {
    const { url, params, data, method = "post", done, fail, action = "删除", info } = options;
    try {
      // 等待用户确认
      await $dialog.confirm(info ?? `是否${action}该数据？`, "提示", {
        type: "warning",
        confirmButtonText: "确定",
        cancelButtonText: "取消",
      });

      // 发送删除请求
      const res = await request({
        url,
        method,
        params,
        data,
      });

      if (action) {
        $message.success(`${action}成功`);
      }
      done?.(res); // 安全调用回调
    } catch (error: any) {
      // 取消操作时不做处理，错误由 request 内部处理或在此捕获
      fail?.();
      console.error(error);
    }
  },

  /** 请求方法集合 */
  requestMethod: ["get", "post", "postJson", "delete"] as const,

  /**
   * 获取对应的请求方法
   * @param method - 请求方法名
   * @returns 对应的请求函数
   */
  request(method: string) {
    const validMethod = this.requestMethod.includes(method as any)
      ? (method as (typeof this.requestMethod)[number])
      : "get";
    return this[validMethod];
  },

  /**
   * GET 请求 （下载文件）
   * @param url - 请求地址
   * @param data - 请求参数
   * @returns 请求Promise
   */
  download: async (url: string, data: HttpParams = {}) => {
    try {
      const response = await request({ url, params: data, responseType: "blob" });
      // ========== 判断是否为下载文件接口的核心逻辑 ==========
      const headers = response.headers;
      let isDownloadFile = false;
      // 方式1：通过自定义响应头判断
      if (headers["content-disposition"]?.includes("attachment")) {
        isDownloadFile = true;
      }

      // ========== 处理下载逻辑 ==========
      if (isDownloadFile) {
        // 1. 从响应头提取文件名（解码中文）
        const disposition = headers["content-disposition"] || "";
        const filenameMatch = disposition.match(/filename=(.*)/);
        let filename = "导出文件.xlsx";
        if (filenameMatch && filenameMatch[1]) {
          // 解码后端返回的文件名（处理中文编码问题）
          filename = decodeURIComponent(filenameMatch[1].replace(/"/g, ""));
        }

        // 2. 创建 blob 对象并触发下载
        const blob = new Blob([response.data], {
          type: headers["content-type"],
        });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = filename; // 设置下载文件名
        document.body.appendChild(a);
        a.click(); // 触发下载
        window.URL.revokeObjectURL(url); // 释放 URL 对象
        document.body.removeChild(a);
        $message.success("下载成功");
      } else {
        // 非下载接口，按普通接口处理（如解析 JSON）
        const { success, msg } = response;
        if (success === false) {
          $message.warning(msg || "下载失败");
        }
        return await response.data.text();
      }
    } catch (error) {
      if (!error?.handled) {
        $message.warning("下载失败");
      }
      console.error("下载失败：", error);
    }
  },

  /**
   * Post 请求 （下载文件）
   * @param url - 请求地址
   * @param data - 请求参数
   * @returns 请求Promise
   */
  downloadPost: async (url: string, data: HttpParams = {}) => {
    try {
      const response = await request({ url, data, responseType: "blob", method: "POST" });
      // ========== 判断是否为下载文件接口的核心逻辑 ==========
      const headers = response.headers;
      let isDownloadFile = false;
      // 方式1：通过自定义响应头判断
      if (headers["content-disposition"]?.includes("attachment")) {
        isDownloadFile = true;
      }

      // ========== 处理下载逻辑 ==========
      if (isDownloadFile) {
        // 1. 从响应头提取文件名（解码中文）
        const disposition = headers["content-disposition"] || "";
        const filenameMatch = disposition.match(/filename=(.*)/);
        let filename = "导出文件.xlsx";
        if (filenameMatch && filenameMatch[1]) {
          // 解码后端返回的文件名（处理中文编码问题）
          filename = decodeURIComponent(filenameMatch[1].replace(/"/g, ""));
        }

        // 2. 创建 blob 对象并触发下载
        const blob = new Blob([response.data], {
          type: headers["content-type"],
        });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = filename; // 设置下载文件名
        document.body.appendChild(a);
        a.click(); // 触发下载
        window.URL.revokeObjectURL(url); // 释放 URL 对象
        document.body.removeChild(a);
        $message.success("下载成功");
      } else {
        // 非下载接口，按普通接口处理（如解析 JSON）
        const { success, msg } = response;
        if (success === false) {
          $message.warning(msg || "下载失败");
        }
        return await response.data.text();
      }
    } catch (error) {
      if (!error?.handled) {
        $message.warning("下载失败");
      }
      console.error("下载失败：", error);
    }
  },

  /**
   * GET 请求
   * @param url - 请求地址
   * @param data - 请求参数
   * @returns 请求Promise
   */
  get: (url: string, data: HttpParams = {}) => {
    return request({ url, params: data });
  },

  /** GET 请求的错误由页面内联呈现，不再触发全局消息提示。 */
  getSilently: (url: string, data: HttpParams = {}) => {
    return request({ url, params: data, errorPolicy: "silent" });
  },

  /**
   * DELETE 请求
   * @param url - 请求地址
   * @param data - 请求参数
   * @returns 请求Promise
   */
  delete: (url: string, data: HttpParams = {}) => {
    return request({ url, method: "delete", params: data });
  },

  /**
   * POST 请求（application/json 格式）
   * @param url - 请求地址
   * @param data - 请求参数
   * @param headers - 请求头
   * @returns 请求Promise
   */
  post: (url: string, data: HttpParams = {}, headers?: {}, timeout?: number) => {
    return request.post(url, data, {
      headers: {
        "Content-Type": "application/json",
        ...headers,
      },
      timeout,
    });
  },

  /**
   * POST 请求（调用方在页面内自行呈现失败状态）。
   *
   * 适用于页面中已有明确错误承载区的可重试操作，例如推送凭证签发。
   * 这不会吞掉错误：请求仍会 reject，只是不再由全局拦截器额外弹出提示。
   */
  postSilently: (url: string, data: HttpParams = {}, headers?: {}, timeout?: number) => {
    return request.post(url, data, {
      headers: {
        "Content-Type": "application/json",
        ...headers,
      },
      timeout,
      errorPolicy: "silent",
    });
  },

  /**
   * 生成UUID
   * @returns 8段16进制字符组成的UUID
   */
  uuid: (): string => {
    const S4 = () => {
      return (((1 + Math.random()) * 0x10000) | 0).toString(16).slice(1);
    };
    return [S4(), S4(), S4(), S4(), S4(), S4(), S4(), S4()].join("");
  },

  /**
   * 复制文本到剪贴板
   * @param text - 需要复制的文本
   */
  copyText: (text: string) => {
    const textarea = document.createElement("textarea");
    textarea.value = text;
    document.body.appendChild(textarea);
    textarea.select();

    try {
      const success = document.execCommand("copy");
      if (success) {
        $message.success("复制成功");
      } else {
        $message.warning("复制失败");
      }
    } catch (err) {
      $message.warning("复制失败");
    } finally {
      document.body.removeChild(textarea);
    }
  },
  regexps,

  /**
   * 递归查找树形结构中对应value
   * @param value
   * @param options
   */
  findOption: (value: string | number, options: OptionType[]): OptionType | undefined => {
    for (const option of options) {
      // 匹配当前节点
      if (String(option.value) === String(value)) {
        return option;
      }
      // 递归查找子节点
      if (option.children && option.children.length) {
        const childMatch = common.findOption(value, option.children);
        if (childMatch) {
          return childMatch;
        }
      }
    }
    return undefined;
  },

  /**
   * 递归查找树形结构中对应value
   * @param value
   * @param options
   */
  findOptionByLabel: (value: string | number, options: OptionType[]): OptionType | undefined => {
    for (const option of options) {
      if (option.label === String(value)) {
        return option;
      }
      if (option.children && option.children.length) {
        const childMatch = common.findOptionByLabel(value, option.children);
        if (childMatch) {
          return childMatch;
        }
      }
    }
    return undefined;
  },

  // 递归遍历树节点（深度优先）
  deepTree(nodes: any, callback: (arg0: any) => void) {
    if (!nodes) {
      return;
    }
    // 用lodash的forEach遍历当前层级节点
    _.forEach(!Array.isArray(nodes) ? [nodes] : nodes, (node) => {
      // 执行自定义回调（处理当前节点）
      callback(node);
      // 如果有子节点，递归遍历
      if (node.children && node.children.length > 0) {
        common.deepTree(node.children, callback);
      }
    });
  },
  parseEsDate(esDate: string): string {
    // 1. 基础时间：now 直接返回当前时间
    if (esDate === "now") {
      return dayjs().format("YYYY-MM-DD HH:mm:ss");
    }

    // 2. 匹配 now-10d / now-6M / now-1y / now-2h 格式
    const match = esDate.match(/^now-(\d+)([d|M|h|m|y])$/);
    if (!match) {
      throw new Error(`不支持的时间表达式：${esDate}`);
    }

    const num = parseInt(match[1], 10); // 数字 10、6、1
    const unit = match[2] as any; // 单位 d/M/h/m/y

    // 3. 映射单位（和 ES 一致）
    const unitMap: Record<string, dayjs.ManipulateType> = {
      d: "day",
      M: "month",
      y: "year",
      h: "hour",
      m: "minute",
    };

    if (!unitMap[unit]) {
      throw new Error(`不支持的时间单位：${unit}`);
    }

    // 4. 当前时间减去对应时长
    return dayjs().subtract(num, unitMap[unit]).format("YYYY-MM-DD HH:mm:ss");
  },
};

export default common;
