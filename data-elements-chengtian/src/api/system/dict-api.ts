import request from "@/utils/request";

const DictAPI = {
  /** 获取字典项列表 */
  getDictItems(dictCode: string) {
    return request<any, DictItemOption[]>({
      url: `/sym/dict/query`,
      method: "get",
      params: { code: dictCode },
    });
  },
};

export default DictAPI;

export interface DictItemOption {
  /** 值 */
  value: number | string;
  /** 标签 */
  label: string;
  /** 标签类型 */
  tagType?: "" | "primary" | "success" | "info" | "warning" | "danger";
  [key: string]: any;
}
