import formCreate from "@form-create/element-ui";
import { get, set } from "lodash-es";

/**
 * 接口返回的单个表单数据类型
 */
interface FormItem {
  tid: string;
  formName: string;
  formJson: string; // JSON 字符串格式的表单配置
  env: string;
  createdTime: string;
  updatedTime: string;
  isDel: number;
}

/**
 * 表单配置类型（formJson 解析后的类型）
 */
type FormConfig = Record<string, any> | Array<Record<string, any>>;

/**
 * @description: 用于动态表单的各种工具：如解析json，转json，以及从表单规则rule中获取字段中文值等
 */
export class FormUtils {
  /**
   * 解析json
   * @param json
   */
  public static parseJson = (json: any) => {
    return formCreate.parseJson(json);
  };

  /**
   * 生成json
   * 支持 非数组类型和数组类型 的对象转换成json字符串，对象中的方法体也会通过特殊的方式进行转换
   * @param obj 非数组类型、数组类型
   */
  public static toJson = (obj: any) => {
    let json = "";
    if (!Array.isArray(obj)) {
      json = formCreate.toJson([obj]);
      return json?.substring(1, json.length - 1);
    } else {
      json = formCreate.toJson(obj);
    }
    return json;
  };

  public static fixJson = (rules: any, labelWidth = "180px") => {
    return rules.map((el: any) => {
      if (el.field && !el.props?.placeholder) {
        if (el.type.toLowerCase().startsWith("input") || el.type === "textarea") {
          set(el, "props.maxlength", get(el, "props.maxlength", 255));
          set(el, "props.placeholder", `请输入${el.title}`);
        } else {
          set(el, "props.placeholder", `请选择${el.title}`);
        }
      }
      if (el.props?.clearable !== false) {
        set(el, "props.clearable", true);
      }
      if (el.col?.span == 24) {
        // 表单项占满一行，则需要调整labelCol和wrapperCol的占比
        set(el, "wrap.labelWidth", labelWidth);
      }
      if (["DatePicker", "elDatePicker"].includes(el.type) && !el.style) {
        set(el, "style", "width: 100%");
      }
      if (el.type.toLowerCase() === "inputnumber") {
        set(el, "props.controlsPosition", "right");
      }
      return {
        ...el,
      };
    });
  };

  public static transFormText(rules: any) {
    const ignoreType = ["hidden", "div", "span", "a", "img", "u-title", "UTitle"];
    return rules.map((rule: any) => {
      if (!rule.field) {
        return rule;
      }
      return {
        ...rule,
        type: ignoreType.includes(rule.type) ? rule.type : "form-text",
        props: {
          ...rule.props,
          originType: rule.type,
          options: rule.props?.options ?? rule.options,
          placeholder: undefined,
        },
      };
    });
  }

  // 缓存结构：{ 表单名称: 解析后的 formJson }
  private formCache: Record<string, FormConfig> = {};
  // 完整表单元数据缓存：{ 表单名称: FormItem }
  private formMetaCache: Record<string, FormItem> = {};
  // 加载状态锁，避免重复请求
  private loadingMap: Map<string, Promise<FormConfig>> = new Map();
  private metaLoadingMap: Map<string, Promise<FormItem | null>> = new Map();

  /**
   * 调用指定接口获取表单数据
   * @param params 接口请求参数
   * @returns {Promise<FormItem[]>} 接口返回的原始表单列表
   */
  private async requestFormData(params: Record<string, any>): Promise<FormItem[]> {
    try {
      const data = await $common.post("/sym/form?action=get", {
        ...params,
        env: $setting.systemCode,
      });
      return data;
    } catch (error) {
      console.error("表单接口请求失败：", error);
      throw error;
    }
  }

  /**
   * 解析 formJson 字符串，处理解析异常
   * @param formJson 原始 JSON 字符串
   * @param formName 表单名称（用于错误提示）
   * @returns {FormConfig} 解析后的表单配置
   */
  private parseFormJson(formJson: string, formName: string): [] {
    try {
      return eval(formJson);
    } catch (error) {
      console.error(`表单【${formName}】的 formJson 解析失败：`, error, "原始内容：", formJson);
      return [];
    }
  }

  /**
   * 批量获取所有表单数据，并解析 formJson 存入缓存
   * @returns {Promise<Record<string, string>>} 所有表单配置（key为表单名称）
   */
  private async fetchAllFormData(): Promise<Record<string, FormConfig>> {
    // 空参数请求所有表单
    const formList = await this.requestFormData({});
    // 转换为 { 表单名称: 解析后的 formJson } 结构，同时缓存元数据
    const formMap: Record<string, string> = {};
    formList.forEach((item) => {
      formMap[item.formName] = item.formJson;
      // 缓存完整的表单元数据（tid、env、formName 等）
      this.formMetaCache[item.formName] = item;
    });
    return formMap;
  }

  /**
   * 获取单个表单数据（接口层面）
   * @param key 表单名称
   * @returns {Promise<FormConfig>} 解析后的 formJson
   */
  private async fetchSingleFormData(key: string): Promise<FormConfig> {
    // 传递 formName 参数请求单个表单
    const formList = await this.requestFormData({ formName: key });
    if (formList.length === 0) {
      throw new Error(`未找到名称为【${key}】的表单`);
    }
    // 缓存完整的表单元数据
    this.formMetaCache[key] = formList[0];
    // 解析并返回 formJson
    return formList[0].formJson;
  }

  /**
   * 加载所有表单配置到缓存
   * @returns {Promise<Record<string, FormConfig>>} 所有表单配置
   */
  async loadAll(): Promise<Record<string, FormConfig>> {
    try {
      const allData = await this.fetchAllFormData();
      this.formCache = { ...allData }; // 更新缓存
      return this.formCache;
    } catch (error) {
      console.error("加载所有表单数据失败：", error);
      throw error;
    }
  }

  /**
   * 获取单个表单的 formJson（优先缓存，无则调用接口）
   * @param {string} key 表单名称
   * @returns {Promise<[]>} 解析后的 formJson 配置
   */
  async get(key: string): Promise<[]> {
    // 1. 缓存中存在，直接返回解析后的 formJson
    if (this.formCache[key]) {
      return this.parseFormJson(this.formCache[key], key);
    }

    // 2. 避免重复请求
    if (this.loadingMap.has(key)) {
      return this.loadingMap.get(key)!;
    }

    // 3. 调用接口获取并解析
    const requestPromise = this.fetchSingleFormData(key)
      .then((data) => {
        this.formCache[key] = data; // 缓存解析后的结果
        return this.parseFormJson(data, key);
      })
      .catch((error) => {
        console.error(`获取表单【${key}】失败：`, error);
        this.loadingMap.delete(key);
        return [];
      });

    // 4. 记录加载状态
    this.loadingMap.set(key, requestPromise);
    return requestPromise;
  }

  /**
   * 获取单个表单的完整元数据（tid、env、formName、createdTime 等）
   * 优先从缓存读取，无缓存则调用接口获取
   * @param {string} key 表单名称
   * @returns {Promise<FormItem | null>} 表单元数据，未找到返回 null
   */
  async getMeta(key: string): Promise<FormItem | null> {
    // 1. 缓存命中，直接返回
    if (this.formMetaCache[key]) {
      return this.formMetaCache[key];
    }

    // 2. 避免重复请求
    if (this.metaLoadingMap.has(key)) {
      return this.metaLoadingMap.get(key)!;
    }

    // 3. 调用接口获取
    const requestPromise = this.requestFormData({ formName: key })
      .then((formList) => {
        if (formList.length === 0) {
          this.metaLoadingMap.delete(key);
          return null;
        }
        const meta = formList[0];
        // 同时缓存 formJson 和元数据
        this.formMetaCache[key] = meta;
        this.formCache[key] = meta.formJson;
        this.metaLoadingMap.delete(key);
        return meta;
      })
      .catch((error) => {
        console.error(`获取表单【${key}】元数据失败：`, error);
        this.metaLoadingMap.delete(key);
        return null;
      });

    this.metaLoadingMap.set(key, requestPromise);
    return requestPromise;
  }

  /**
   * 手动清空缓存
   */
  clearCache(): void {
    this.formCache = {};
    this.formMetaCache = {};
    this.loadingMap.clear();
    this.metaLoadingMap.clear();
  }
}
