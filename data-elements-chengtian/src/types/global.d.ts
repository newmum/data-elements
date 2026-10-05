import common from "@/plugins/common";
import { request } from "@/utils/request";
import { sendMessage } from "@/plugins/frame";
import { useDictStore, useSettingStore, type UserType } from "@/store";
import { FormUtils } from "@/utils/form";

declare global {
  /** window绑定对象 */
  interface Window {
    $request: typeof request;
    $message: typeof ElMessage;
    $dialog: any;
    $common: typeof common;
    $sendMessage: typeof sendMessage;
    $user: UserType;
    $dict: useDictStore;
    $setting: useSettingStore;
    $form: FormUtils;
  }
  const $message: Window["$message"];
  const $request: Window["$request"];
  const $dialog: Window["$dialog"];
  const $common: Window["$common"];
  let $user: Window["$user"];
  const $dict: Window["$dict"];
  const $setting: Window["$setting"];
  const $form: Window["$form"];

  /**
   * 响应数据
   */
  interface ApiResponse<T = any> {
    code: number;
    data: T;
    msg: string;
    message: string;
    success?: boolean;
    timestamp?: number;
    error?: {
      code?: string;
      message?: string;
      detail?: string;
      traceId?: string;
      type?: string;
      method?: string;
      path?: string;
    };
  }

  /**
   * 分页查询参数
   */
  interface PageQuery {
    current: number;
    size: number;
  }

  /**
   * 分页响应对象
   */
  interface PageResult<T> {
    /** 数据列表 */
    list: T;
    /** 总数 */
    total: number;
  }

  /**
   * 页签对象
   */
  interface TagView {
    /** 页签名称 */
    name: string;
    /** 页签标题 */
    title: string;
    /** 页签路由路径 */
    path: string;
    /** 页签路由完整路径 */
    fullPath: string;
    /** 页签图标 */
    icon?: string;
    /** 是否固定页签 */
    affix?: boolean;
    /** 是否开启缓存 */
    keepAlive?: boolean;
    /** 路由查询参数 */
    query?: any;
  }

  /**
   * 下拉选项数据类型
   */
  interface OptionType {
    /** 值 */
    value: string | number;
    /** 文本 */
    label: string;
    /** 是否叶子节点 */
    isLeaf?: string;
    /** 是否禁用 */
    disabled?: boolean;
    /** 子列表  */
    children?: OptionType[];
    /** 页签类型  */
    tagType?: "" | "primary" | "success" | "info" | "warning" | "danger";
    /** 图标类型  */
    icon?: string;
  }

  /**
   * 导入结果
   */
  interface ExcelResult {
    /** 状态码 */
    code: string;
    /** 无效数据条数 */
    invalidCount: number;
    /** 有效数据条数 */
    validCount: number;
    /** 错误信息 */
    messageList: Array<string>;
  }
}
export {};
