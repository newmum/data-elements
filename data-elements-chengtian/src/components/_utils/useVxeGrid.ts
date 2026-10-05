import { ref, reactive, onMounted, watch } from "vue";
import type { VxeGridProps, VxeGridListeners, VxeGridInstance } from "vxe-table";
import { merge, get, cloneDeep } from "lodash-es";

// 定义 composable 入参类型
export interface UseVxeGridOptions {
  height?: "100%" | "300px" | string;
  options?: object;
  events?: object;
  showOperation?: boolean;
  editable?: boolean;
  /*新增数据默认值*/
  insertData?: Record<string, any>;
}
export function useVxeGrid(props: UseVxeGridOptions, emit?: any) {
  const gridRef = ref<VxeGridInstance>();
  const originalOptions = cloneDeep(props.options || {}); // 保存原始配置的深拷贝
  // 表格props配置
  const gridOptions = reactive<VxeGridProps<any>>({
    height: props.height,
    minHeight: "300px",
    editConfig: {
      enabled: props.editable,
    },
    formConfig: {},
    importConfig: {},
    exportConfig: {},
    printConfig: {},
    customConfig: {
      visibleMethod({ column }) {
        return !column.type && column.type !== "seq";
      },
    },
    toolbarConfig: {
      buttons: props.editable
        ? get(props, "options.toolbarConfig.buttons", [
            {
              code: "append_edit", // 底部新增
              icon: "vxe-icon-add",
              name: "新增",
              status: "primary",
              mode: "text",
            },
          ])
        : [],
      tools: get(props, "options.toolbarConfig.tools", [
        // {
        //   code: "zoom",
        //   icon: "vxe-icon-fullscreen",
        //   mode: "text",
        //   status: "primary",
        //   name: "全屏",
        // },
        {
          code: "custom",
          icon: "vxe-icon-custom-column",
          mode: "text",
          status: "primary",
          name: "显示列",
        },
        {
          code: "import",
          icon: "vxe-icon-cloud-upload",
          mode: "text",
          status: "primary",
          name: "导入",
        },
        // {
        //   code: "open_import",
        //   icon: "vxe-icon-upload",
        //   mode: "text",
        //   status: "primary",
        //   name: "高级导入",
        // },
        // {
        //   code: "export",
        //   icon: "vxe-icon-cloud-download",
        //   mode: "text",
        //   status: "primary",
        //   name: "导出",
        // },
        {
          code: "open_export",
          icon: "vxe-icon-download",
          mode: "text",
          status: "primary",
          name: "导出",
        },
        // {
        //   code: "open_print",
        //   icon: "vxe-icon-print",
        //   mode: "text",
        //   status: "primary",
        //   name: "打印",
        // },
      ]),
    },
    columns: [],
    editRules: {},
  });

  // 表格事件配置
  const gridEvents: VxeGridListeners = {
    toolbarButtonClick(params) {
      const { code } = params;
      if (emit) {
        emit("btn-click", params);
      }
      switch (code) {
        case "append_edit":
          // 末尾添加行并进入编辑状态
          console.log("append_edit", props.insertData);
          gridRef.value?.insertAt(props.insertData || {}, -1).then(({ row }) => {
            gridRef.value?.setEditRow(row);
          });
          break;
      }
    },
    toolbarToolClick(params) {
      const { code } = params;
      if (emit) {
        emit("btn-click", params);
      }
      switch (code) {
        case "append_edit":
          // 末尾添加行并进入编辑状态
          gridRef.value?.insertAt(props.insertData || {}, -1).then(({ row }) => {
            gridRef.value?.setEditRow(row);
          });
          break;
      }
    },
    pageChange({ pageSize, currentPage }) {
      console.log(pageSize, currentPage);
      if (emit) {
        emit("page-change", { pageNo: currentPage, pageSize });
      }
    },
    editClosed({ row }) {
      emit("edit-row", row);
    },
  };

  // 删除行方法
  const removeRow = async (row: any) => {
    gridRef.value?.remove(row);
  };

  // 数据校验方法
  const validate = async (): Promise<boolean> => {
    const errMap = await gridRef.value?.validate(true);
    if (errMap) {
      throw new Error("请完善数据");
    }
    return true;
  };

  // 设置表格数据
  const setData = (rows: any[] = []) => {
    gridOptions.data = rows;
  };

  // 获取表格数据（剔除内置属性）
  const getData = (): any[] => {
    return (
      gridRef.value?.getTableData().fullData.map((item) => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const { _X_ROW_KEY, ...newItem } = item;
        return newItem;
      }) || []
    );
  };

  // 更新编辑状态
  const updateEditStatus = (editable: boolean) => {
    // 更新编辑配置
    if (!gridOptions.editConfig) {
      gridOptions.editConfig = {};
    }
    gridOptions.editConfig.enabled = editable;
    gridOptions.editConfig.trigger = editable ? "click" : null;
    gridOptions.editConfig.mode = editable ? "cell" : null;

    // 更新工具栏按钮
    if (!gridOptions.toolbarConfig) {
      gridOptions.toolbarConfig = {};
    }
    if (editable) {
      // 恢复默认按钮
      if (!gridOptions.toolbarConfig.buttons || gridOptions.toolbarConfig.buttons.length === 0) {
        gridOptions.toolbarConfig.buttons = get(props, "options.toolbarConfig.buttons", [
          {
            code: "append_edit", // 底部新增
            icon: "vxe-icon-add",
            name: "新增",
            status: "primary",
            mode: "text",
          },
        ]);
      }
    } else {
      // 清空按钮
      gridOptions.toolbarConfig.buttons = [];
    }

    // 更新操作列
    if (gridOptions.columns) {
      // 检查是否已有操作列
      const hasOperationColumn = gridOptions.columns.some(
        (column) => column.slots && column.slots.default === "operation"
      );

      if (props.showOperation && editable && !hasOperationColumn) {
        // 添加操作列
        gridOptions.columns.push({
          title: "操作",
          slots: { default: "operation" },
          fixed: "right",
          width: 80,
        });
      } else if (!editable && hasOperationColumn) {
        // 移除操作列
        gridOptions.columns = gridOptions.columns.filter(
          (column) => !(column.slots && column.slots.default === "operation")
        );
      }

      // 更新列的 editRender 配置
      gridOptions.columns.forEach((column) => {
        if (editable) {
          // 编辑状态：从原始配置中恢复 editRender
          const originalColumn = (originalOptions as any)?.columns?.find(
            (col: any) => col.field === column.field
          );
          if (originalColumn?.editRender) {
            column.editRender = originalColumn.editRender;
          }
        } else {
          // 只读状态：移除 editRender
          if (column.editRender) {
            delete column.editRender;
          }
        }
      });
    }
  };

  // 初始化逻辑
  onMounted(() => {
    // 合并外部配置
    merge(gridOptions, props.options || {});
    merge(gridEvents, props.events || {});
    // 初始更新编辑状态
    updateEditStatus(props.editable || false);
  });

  // 监听 editable 属性变化
  watch(
    () => props.editable,
    (newValue) => {
      updateEditStatus(newValue || false);
    }
  );

  return {
    gridRef,
    gridOptions,
    gridEvents,
    setData,
    getData,
    removeRow,
    validate,
  };
}
