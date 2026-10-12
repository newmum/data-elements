import { ref, reactive, onMounted, onBeforeUnmount, watch, toRaw } from "vue";
import type { VxeGridProps, VxeGridListeners, VxeGridInstance } from "vxe-table";
import { merge, get, cloneDeep } from "lodash-es";

import request from "@/utils/request";
import { ElMessage } from "element-plus";
import { isRequiredSortColumn } from "../data/table-sorting";

// 定义 composable 入参类型
export interface UseVxeGridOptions {
  height?: "100%" | "300px" | string;
  options?: object;
  events?: object;
  showOperation?: boolean;
  editable?: boolean;
  // Persisted pages provide an authorized read callback; editor drafts use the pure key-order endpoint.
  sortProvider?: (sort: {sortField?:string;sortDir?:'asc'|'desc'}) => Promise<any[]|void>;
  showPage?: boolean;
  draftSort?: boolean;
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

  const businessRows:any[]=[];
  const identity=new WeakMap<object,string>();
  let activeSort: {sortField?:string;sortDir?:'asc'|'desc'}={}, sequence=0, disposed=false;
  const key=(row:object)=>{const raw=toRaw(row);let id=identity.get(raw);if(!id){id=crypto.randomUUID();identity.set(raw,id);}return id;};
  const readRows=()=>gridRef.value?.getTableData().fullData??gridOptions.data??[];
  const applySort=async(field?:string,order?:'asc'|'desc')=>{
    activeSort=order&&field?{sortField:field,sortDir:order}:{};
    configureSorting();
    const requestId=++sequence;
    try {
      gridOptions.loading=true;
      if(props.sortProvider){const rows=await props.sortProvider(activeSort);if(requestId===sequence&&!disposed&&Array.isArray(rows))gridOptions.data=rows;return;}
      if(props.showPage){emit?.('sort-change',activeSort);emit?.('page-change',{pageNo:1,pageSize:20,...activeSort});return;}
      const rows=readRows();
      if(!activeSort.sortField){const current=new Map(rows.map(row=>[toRaw(row),row]));if(businessRows.length)await gridRef.value?.loadData([...businessRows.filter(row=>current.has(row)).map(row=>current.get(row)),...rows.filter(row=>!businessRows.includes(toRaw(row)))]);return;}
      // Keep the full draft and row references. Sorting changes presentation, while getData retains business order.
      for(const row of rows){const raw=toRaw(row);if(!businessRows.includes(raw))businessRows.push(raw);}
      const col=gridOptions.columns?.find(col=>col.field===field);
      const kind=/时间|日期|最近更新/.test(String(col?.title))?'time':/数量|总数|次数|条数|字段数|长度|大小|占比|率|耗时/.test(String(col?.title))?'number':'text';
      const items=rows.map(row=>{let value=get(row,field!);if(kind==='time'&&value){const date=new Date(value);value=Number.isNaN(date.getTime())?null:date.toISOString();}return {id:key(row),value:value??null};});
      const response=await request.post<any, {keys:string[];total:number}>("/sym/table/preview-order",{items,kind,direction:order,wholeCollection:true});
      if(disposed||requestId!==sequence)return;
      const byId=new Map(rows.map(row=>[key(row),row]));
      if(response.total!==rows.length||response.keys.length!==rows.length||new Set(response.keys).size!==rows.length||response.keys.some(id=>!byId.has(id)))throw new Error('配置排序结果不完整或超出当前范围');
      await gridRef.value?.loadData(response.keys.map(id=>byId.get(id)));
    } finally {if(!disposed&&requestId===sequence)gridOptions.loading=false;}
  };
  const configureSorting=()=>{
    if(gridOptions.rowConfig)gridOptions.rowConfig.drag=!!get(originalOptions,'rowConfig.drag',false)&&!activeSort.sortField;
    let first=true;
    for(const column of gridOptions.columns??[]){
      if(column.type||!column.field)continue;
      const needed=isRequiredSortColumn({label:String(column.title??''),prop:column.field},first);first=false;
      column.sortable=!!(props.sortProvider||props.showPage||props.draftSort||props.editable)&&(needed||column.sortable===true);
    }
    gridOptions.sortConfig={...gridOptions.sortConfig,remote:true};
  };
  watch(()=>gridOptions.columns,configureSorting);
  onBeforeUnmount(()=>{disposed=true;sequence++;});

  // 表格事件配置
  const gridEvents: VxeGridListeners = {
    sortChange({field,order}) {void applySort(field,order||undefined).catch(cause=>ElMessage.error(cause instanceof Error?cause.message:'排序失败，请稍后重试'));},
    toolbarButtonClick(params) {
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
      if (emit) {
        emit("page-change", { pageNo: currentPage, pageSize, ...activeSort });
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
    sequence++;
    businessRows.splice(0,businessRows.length,...rows.map(toRaw));
    gridOptions.data = rows;
    if(activeSort.sortField&&!props.sortProvider&&!props.showPage)void gridRef.value?.loadData(rows).then(()=>applySort(activeSort.sortField,activeSort.sortDir)).catch(cause=>ElMessage.error(cause instanceof Error?cause.message:'排序失败'));
  };

  // 获取表格数据（剔除内置属性）
  const getData = (): any[] => {
    const rows=readRows();
    const current=new Map(rows.map(row=>[toRaw(row),row]));
    const ordered=activeSort.sortField?[...businessRows.filter(row=>current.has(row)).map(row=>current.get(row)),...rows.filter(row=>!businessRows.includes(toRaw(row)))]:rows;
    return ordered.map(({_X_ROW_KEY,...row}:any)=>row);
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
    configureSorting();
  });

  // 监听 editable 属性变化
  watch(
    () => props.editable,
    (newValue) => {
      updateEditStatus(newValue || false);
      configureSorting();
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
