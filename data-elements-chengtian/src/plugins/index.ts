import type { App } from "vue";

import { setupDirective } from "@/directive";
import { setupRouter } from "@/router";
import {
  setupStore,
  useDictStore,
  useSettingStore,
  useSettingStoreHook,
  useUserStore,
} from "@/store";
import { setupElIcons } from "./icons";
import { setupPermission } from "./permission";
// import { setupWebSocket } from "./websocket";
import { setupFormCreate } from "./formCreate";
import { setupComponents } from "@/components";
import "./compiler/magic-import";
import request from "@/utils/request";
import common from "./common";
import { addEventListener, sendMessage } from "@/plugins/frame";
import { setupVxeTable } from "@/plugins/vxeTable";
import { FormUtils } from "@/utils/form";

export default {
  async install(app: App<Element>) {
    // 系统设置信息
    useSettingStoreHook().loadingSettings();

    // 状态管理(store)
    setupStore(app);
    // 自定义指令(directive)
    setupDirective(app);
    // 路由(router)
    setupRouter(app);
    // Element-plus图标
    setupElIcons(app);
    // 路由守卫
    setupPermission(app);
    // WebSocket服务
    // setupWebSocket();
    // form-crete
    setupFormCreate(app);
    // 自定义组件(components)
    setupComponents(app);
    // vxe-table
    setupVxeTable(app);

    // 绑定message监听
    addEventListener();

    window.$message = ElMessage;
    window.$dialog = ElMessageBox;
    window.$request = request;
    window.$common = common;
    window.$sendMessage = sendMessage;
    window.$user = useUserStore().userInfo;
    window.$dict = useDictStore();
    window.$setting = useSettingStore();
    window.$form = new FormUtils();

    app.config.globalProperties.$message = ElMessage;
    app.config.globalProperties.$dialog = ElMessageBox;
    app.config.globalProperties.$request = request;
    app.config.globalProperties.$common = common;
    app.config.globalProperties.$sendMessage = sendMessage;
    app.config.globalProperties.$user = useUserStore().userInfo;
    app.config.globalProperties.$dict = useDictStore();
    app.config.globalProperties.$setting = window.$setting;
    app.config.globalProperties.$form = window.$form;
  },
};
