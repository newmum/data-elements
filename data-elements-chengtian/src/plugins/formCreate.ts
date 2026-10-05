// 引入 form-create 组件库、组件生成器
import formCreate from "@form-create/element-ui";
import { useDictStore, useUserStore } from "@/store";
import { App } from "vue";
import { get, isString, set } from "lodash-es";
import dayjs from "dayjs";

const initEffect = () => {
  // 获取用户信息
  formCreate.register({
    name: "user",
    init({ value }, rule) {
      rule.value = rule.value ? rule.value : get(useUserStore().userInfo, value);
    },
  });

  // 获取字典值信息
  formCreate.register({
    name: "dict",
    async init({ value }, rule) {
      const dictStore = useDictStore();
      await dictStore.loadDictItems(value);
      set(rule, "props.options", dictStore.getDictItems(value));
    },
  });

  // 设置当前时间
  formCreate.register({
    name: "date",
    init({ value }, rule, api) {
      const format = value || "YYYY-MM-DD HH:mm:ss";
      if (!rule.value) {
        api.setValue({ [rule.field as string]: dayjs().format(format) });
      }
    },
  });

  // app联动db
  formCreate.register({
    name: "appLinkDb",
    async value({ value }, rule, api) {
      if (rule.type === "form-text") {
        // 文本状态下，不予修改db的options，否则可能无法正常回显db
        return;
      }

      const appId = rule.value;
      // 设置原始options
      await $dict.loadDictItems("db");
      const dbOptions = $dict.getDictItems("db") || [];
      const allOptions = api.getRule("dbId").originOptions;
      if (!allOptions) {
        api.getRule("dbId").originOptions = dbOptions;
      }

      if (!appId) {
        set(api.getRule("dbId"), "props.options", api.getRule("dbId").originOptions);
        return;
      }

      set(
        api.getRule("dbId"),
        "props.options",
        dbOptions.filter((el: any) => el.appId === appId)
      );
    },
  });

  /**
   * 自动生成jdbcUrl
   */
  formCreate.register({
    name: "setJdbcUrl",
    async value(val, rule, api) {
      const dbType = api.getRule("dbType").value || "";
      let host = api.getRule("host").value || "";
      const port = api.getRule("port").value || "";
      const database = api.getRule("database").value || "";
      const serviceName = api.getRule("serviceName").value || "";
      const username = api.getRule("username").value || "";
      const password = api.getRule("password").value || "";
      let jdbcUrl = "jdbc:xxx://xxx.x.x:xxxx/";
      const jdbcType = api.getRule("jdbcType")?.value;
      switch (dbType) {
        case "mysql":
          // jdbc:mysql://localhost:3306/mydatabase?user=myusername&password=mypassword
          jdbcUrl = `jdbc:mysql://${host}${port ? ":" + port : ""}${database ? "/" + database : ""}${username ? "?user=" + username : ""}${password ? "&password=" + password : ""}`;
          break;
        case "oracle":
          // jdbc:oracle:thin:@localhost:1521:XE
          if (jdbcType == "sid") host = "@" + host;
          else if (jdbcType == "serviceName") host = "@//" + host;
          jdbcUrl = `jdbc:oracle:thin:${host}${port ? ":" + port : ""}${database ? ":" + database : ""}`;
          break;
        case "gbase8a":
          jdbcUrl = `jdbc:gbase8a://${host}${port ? ":" + port : ""}${database ? "/" + database : ""}${serviceName ? ":GBASEDBTSERVER=" + serviceName : ""}`;
          break;
        case "gbase8t":
          jdbcUrl = `jdbc:gbase://${host}${port ? ":" + port : ""}${database ? "/" + database : ""}${serviceName ? ":GBASEDBTSERVER=" + serviceName : ""}`;
          break;
        case "gbase8s":
          //jdbc:gbasedbt-sqli://localhost:19088/zhtest:GBASEDBTSERVER=gbase01
          jdbcUrl = `jdbc:gbasedbt-sqli://${host}${port ? ":" + port : ""}${database ? "/" + database : ""}${serviceName ? ":GBASEDBTSERVER=" + serviceName : ""}`;
          break;
        // jdbc:sqlserver://localhost:1433;databaseName=mydatabase;user=myusername;password=mypassword;
        case "sqlserver":
          jdbcUrl = `jdbc:sqlserver://${host}${port ? ":" + port : ""}${database ? ";databaseName=" + database : ""}`;
          break;
        case "postgresql":
          jdbcUrl = `jdbc:postgresql://${host}${port ? ":" + port : ""}${database ? "/" + database : ""}${username ? "?user=" + username : ""}${password ? "&password=" + password : ""}`;
          break;
        case "kingbase8":
          jdbcUrl = `jdbc:kingbase8://${host}${port ? ":" + port : ""}${database ? "/" + database : ""}${username ? "?user=" + username : ""}${password ? "&password=" + password : ""}`;
          break;
        case "gaussdb":
          jdbcUrl = `jdbc:gauss200://${host}${port ? ":" + port : ""}${database ? "/" + database : ""}${username ? "?user=" + username : ""}${password ? "&password=" + password : ""}`;
          break;
        case "dameng":
          jdbcUrl = `jdbc:dm://${host}${port ? ":" + port : ""}${database ? "/" + database : ""}${username ? "?user=" + username : ""}${password ? "&password=" + password : ""}`;
          break;
        default:
          jdbcUrl = `jdbc${dbType ? ":" + dbType : ""}://${host}${port ? ":" + port : ""}${database ? "/" + database : ""}`;
          break;
      }
      api.getRule("jdbcURL").value = jdbcUrl;
    },
  });
};
export function setupFormCreate(app: App) {
  initEffect();
  app.use(formCreate);
}
