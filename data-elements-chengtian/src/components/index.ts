import type { App } from "vue";
import { defineAsyncComponent } from "vue";
import { registerComponents } from "@/components/_utils/register-components";
// 登记流程壳组件必须作为运行时全局组件注册，BaseLayout 中的
// <register-modal> 才能被解析并承载低代码步骤页。
import RegisterModal from "@/components/business/register-modal.vue";
import AssetRegister from "@/components/business/asset-register.vue";
import RegisterBody from "@/components/business/register-body.vue";
import RegisterAction from "@/components/business/register-action.vue";

export function setupComponents(app: App<Element>) {
  registerComponents(app, {
    // 排除指定文件
    exclude: ["base/notification.vue"],
  });
  app.component("RegisterModal", RegisterModal);
  app.component("AssetRegister", AssetRegister);
  app.component("RegisterBody", RegisterBody);
  app.component("RegisterAction", RegisterAction);
  app.component(
    "SfcComponent",
    defineAsyncComponent(() => import("@/views/lowcode/sfc-component.vue"))
  );
  app.component(
    "UDesigner",
    defineAsyncComponent(() => import("@/views/lowcode/designer.vue"))
  );
  app.component(
    // eslint-disable-next-line vue/multi-word-component-names
    "Layout",
    defineAsyncComponent(() => import("@/layouts/index.vue"))
  );
  app.component(
    "WorkLayout",
    defineAsyncComponent(() => import("@/layouts/views/WorkLayout.vue"))
  );
}
