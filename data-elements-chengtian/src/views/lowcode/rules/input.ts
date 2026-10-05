const label = "输入框";
const name = "input";

export default {
  menu: "main",
  icon: "icon-input",
  label,
  name,
  input: true,
  event: ["blur", "focus", "change", "input", "clear"],
  validate: ["string", "url", "email"],
  rule() {
    return {
      type: name,
      field: "a" + $common.uuid(),
      title: "输入框",
      info: "",
      $required: false,
      props: {},
    };
  },
  props(_: any) {
    return [
      {
        type: "select",
        field: "col",
        title: "组件宽度",
        options: [{ label: "4", value: { span: 4 } }],
      },
      {
        type: "switch",
        field: "disabled",
        title: "是否禁用",
      },
      {
        type: "switch",
        field: "readonly",
        title: "是否只读",
      },
      {
        type: "select",
        field: "type",
        title: "类型",
        options: [
          { label: "text", value: "text" },
          { label: "number", value: "number" },
          { label: "time", value: "time" },
          { label: "date", value: "date" },
          { label: "month", value: "month" },
          { label: "datetime-local", value: "datetime-local" },
        ],
      },
      {
        type: "inputNumber",
        field: "maxlength",
        props: { min: 0 },
      },
      {
        type: "input",
        field: "placeholder",
      },
      {
        type: "switch",
        field: "clearable",
      },
    ];
  },
};
