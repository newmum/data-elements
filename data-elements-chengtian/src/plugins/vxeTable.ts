import { App } from "vue";
import "vxe-pc-ui/lib/style.css";
import "vxe-table/lib/style.css";
import VxeUIBase, { VxeUI } from "vxe-pc-ui";
import VxeUITable from "vxe-table";
import VxeUIPluginRenderElement from "@vxe-ui/plugin-render-element";
import "@vxe-ui/plugin-render-element/dist/style.css";
import VxeUIPluginExportXLSX from "@vxe-ui/plugin-export-xlsx";
import ExcelJS from "exceljs";

export function setupVxeTable(app: App) {
  VxeUI.use(VxeUIPluginExportXLSX, { ExcelJS });
  VxeUI.use(VxeUIPluginRenderElement);
  app.use(VxeUIBase).use(VxeUITable);
}

VxeUI.setConfig({
  size: "small", // 全局尺寸
  zIndex: 2010, // 全局 zIndex 起始值
  table: {
    autoResize: true,
    minHeight: 144,
    keepSource: true,
    showHeader: true,
    showOverflow: "tooltip",
    showHeaderOverflow: "tooltip",
    // showFooterOverflow: null,
    // resizeInterval: 500,
    // size: null,
    // zIndex: null,
    stripe: true,
    border: "inner",
    // round: false,
    // emptyText: '暂无数据',
    // emptyRender: {
    //   name: ''
    // },
    // rowConfig: {
    //   keyField: '_X_ROW_KEY' // 行数据的唯一主键字段名
    // },
    resizeConfig: {
      // refreshDelay: 20
    },
    resizableConfig: {
      dragMode: "auto",
      showDragTip: true,
      isSyncAutoHeight: true,
      isSyncAutoWidth: true,
      minHeight: 18,
    },
    radioConfig: {
      // trigger: 'default'
      strict: true,
    },
    rowDragConfig: {
      showIcon: true,
      animation: true,
      showGuidesStatus: true,
      showDragTip: true,
    },
    columnDragConfig: {
      showIcon: true,
      animation: true,
      showGuidesStatus: true,
      showDragTip: true,
    },
    checkboxConfig: {
      // trigger: 'default',
      strict: true,
    },
    tooltipConfig: {
      enterable: true,
    },
    headerTooltipConfig: {
      enterable: true,
    },
    footerTooltipConfig: {
      enterable: true,
    },
    validConfig: {
      showMessage: true,
      autoClear: true,
      autoPos: true,
      message: "inline",
      msgMode: "single",
      theme: "beautify",
    },
    columnConfig: {
      resizable: true,
      autoOptions: {
        isCalcHeader: true,
        isCalcBody: true,
        isCalcFooter: true,
      },
      maxFixedSize: 4,
    },
    cellConfig: {
      padding: true,
    },
    headerCellConfig: {
      height: "unset",
    },
    footerCellConfig: {
      height: "unset",
    },
    // menuConfig: {
    //   visibleMethod () {}
    // },
    customConfig: {
      // enabled: false,
      allowVisible: true,
      allowResizable: true,
      allowFixed: true,
      allowSort: true,
      showFooter: true,
      placement: "top-right",
      //  storage: false,
      storeOptions: {
        visible: true,
        resizable: true,
        sort: true,
        fixed: true,
        // rowGroup: false,
        // aggFunc: false
      },
      // autoAggGroupValues: false,
      //  checkMethod () {},
      modalOptions: {
        showMaximize: true,
        mask: true,
        lockView: true,
        resize: true,
        escClosable: true,
      },
      drawerOptions: {
        mask: true,
        lockView: true,
        escClosable: true,
        resize: true,
      },
    },
    sortConfig: {
      // remote: false,
      // trigger: 'default',
      // orders: ['asc', 'desc', null],
      // sortMethod: null,
      showIcon: true,
      allowClear: true,
      allowBtn: true,
      iconLayout: "vertical",
    },
    filterConfig: {
      // remote: false,
      // filterMethod: null,
      // destroyOnClose: false,
      // isEvery: false,
      multiple: true,
      showIcon: true,
    },
    aggregateConfig: {
      padding: true,
      rowField: "id",
      parentField: "_X_ROW_PARENT_KEY",
      childrenField: "_X_ROW_CHILDREN",
      mapChildrenField: "_X_ROW_CHILD_LIST",
      indent: 20,
      showIcon: true,
      maxGroupSize: 4,
      showAggFuncTitle: true,
    },
    treeConfig: {
      padding: true,
      rowField: "id",
      parentField: "parentId",
      childrenField: "children",
      hasChildField: "hasChild",
      mapChildrenField: "_X_ROW_CHILD",
      indent: 20,
      showIcon: true,
    },
    expandConfig: {
      // trigger: 'default',
      showIcon: true,
      mode: "fixed",
    },
    editConfig: {
      showStatus: true,
      trigger: "click",
      mode: "row",
      showIcon: true,
      showAsterisk: true,
      autoFocus: true,
    },
    importConfig: {
      modes: ["insertBottom", "covering", "insertTop"],
      mode: "insertBottom", // 导入默认方式
      types: ["xlsx", "csv", "txt"],
    },
    exportConfig: {
      type: "xlsx", // 默认导出文件类型
      types: ["xlsx", "csv", "txt"],
    },
    printConfig: {},
    mouseConfig: {
      extension: true,
    },
    keyboardConfig: {
      isAll: true,
      isEsc: true,
    },
    areaConfig: {
      autoClear: true,
      selectCellByHeader: true,
      selectCellByBody: true,
      extendDirection: {
        top: true,
        left: true,
        bottom: true,
        right: true,
      },
    },
    clipConfig: {
      isCopy: true,
      isCut: true,
      isPaste: true,
    },
    fnrConfig: {
      isFind: true,
      isReplace: true,
    },
    virtualXConfig: {
      // enabled: false,
      gt: 24,
      oSize: 0,
    },
    virtualYConfig: {
      // enabled: false,
      // mode: 'wheel',
      gt: 100, // 大于指定行时启动纵向虚拟滚动
      oSize: 0,
    },
    scrollbarConfig: {
      // width: 14,
      // height: 14
    },
  },
  // export: {
  //   types: {}
  // },
  grid: {
    // size: null,
    // zoomConfig: {
    //   escRestore: true
    // },
    formConfig: {
      enabled: true,
    },
    pagerConfig: {
      enabled: true,
      pageSize: 20,
      pageSizes: [10, 20, 50, 100, 500],
    },
    toolbarConfig: {
      enabled: true,
    },
    proxyConfig: {
      enabled: true,
      autoLoad: true,
      showLoading: true,
      showResponseMsg: true,
      showActionMsg: true,
      response: {
        list: null,
        result: "list",
        total: "total",
        message: "message",
      },
    },
  },
  toolbar: {
    // size: null,
    // import: {
    //   mode: 'covering'
    // },
    // export: {
    //   types: ["csv", "html", "xml", "txt"],
    // },
    // buttons: []
  },
});
