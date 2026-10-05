import request from "@/utils/request";
const MENU_BASE_URL = "/sym/menu";

const MenuAPI = {
  /** 获取当前用户的路由列表 */
  getRoutes() {
    return request<any, RouteVO[]>({ url: `${MENU_BASE_URL}/routes`, method: "get" });
  },
};

export default MenuAPI;
export interface RouteVO {
  /** 子路由列表 */
  children: RouteVO[];
  /** 组件路径 */
  component?: string;
  /** 组件名 */
  componentName?: string;
  /** 路由属性 */
  meta?: Meta;
  /** 路由名称 */
  name?: string;
  /** 路由路径 */
  path?: string;
  /** 跳转链接 */
  redirect?: string;
  [k: string]: any;
}
export interface Meta {
  /** 是否隐藏(true-是 false-否) */
  hidden?: boolean;
  /** ICON */
  icon?: string;
  /** 【菜单】是否开启页面缓存 */
  keepAlive?: boolean;
  /** 路由title */
  title?: string;
  /** 链接跳转方式 */
  target?: "_self" | "_blank";
  /** 打开方式 */
  openMode?: "iframe" | "link";
  /** 节点类型，group=分组标题（不可点击，不生成路由） */
  type?: "group";
  /** 布控引擎管理页面的业务分区 */
  managementType?: "input" | "rules" | "output";
}
