export default {
  //插入菜单位置
  menu: "aide",
  //图标
  icon: "icon-align-spacearound",
  //名称
  label: "标题",
  //id,唯一!
  name: "UTitle",
  //可以向内部拖入组件
  drag: true,
  //组件操作按钮生成在组件的内部还是外部
  inside: false,
  //不显示遮罩,容器组件不能显示遮罩
  mask: false,
  //生成规则
  rule() {
    return {
      type: "u-title",
      props: { name: "基础信息" },
      children: [] as any[],
    };
  },
  //属性配置规则
  props(_: any) {
    return [
      {
        type: "input",
        title: "标题",
        field: "name",
      },
    ];
  },
};
