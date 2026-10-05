import request from "@/utils/request";
import requestPolicy from "@/utils/request-policy";

const COMPONENT_BASE_URL = "/sym/component";

const ComponentAPI = {
  /** 组件列表 */
  getList() {
    return requestPolicy.data<any[]>({
      url: `${COMPONENT_BASE_URL}`,
      method: "post",
      params: { action: "list" },
      // The first request after a cache reset may need to stream all compiled
      // low-code bundles from the remote primary database.
      timeout: 120000,
    });
  },

  /** 批量导出组件代码 */
  batchExport(tids: string[]) {
    return request({
      url: "/sym/component/batchExport",
      method: "post",
      data: { tids },
    });
  },

  /** 批量导入组件代码（增量） */
  batchImport(data: { components: [] }) {
    return request({
      url: "/sym/component/batchImport",
      method: "post",
      data,
    });
  },
};

export default ComponentAPI;
