import * as vue from "vue";
import * as vueRouter from "vue-router";
import * as Store from "@/store";
import * as lodashEs from "lodash-es";
import * as ElementPlus from "element-plus";
import * as Vueuse from "@vueuse/core";
import * as VueDraggable from "vue-draggable-plus";
import * as Utils from "@/utils";
import * as Pinia from "pinia";
import request from "@/utils/request";
import FileAPI from "@/api/file-api";
import * as FormUtils from "@/utils/form";
import formCreate from "@form-create/element-ui";
import * as Composables from "@/composables";
import dayjs from "dayjs";
import * as ExcelJS from "exceljs";
import * as jsPinyin from "js-pinyin";
import * as x6 from "@antv/x6";
import * as x6VueShape from "@antv/x6-vue-shape";
import * as g2 from "@antv/g2";
import * as s2 from "@antv/s2";
import * as s2Vue from "@antv/s2-vue";
import * as sfcCompiler from "@/plugins/compiler/sfc-compiler";
import * as dynamicComponent from "@/plugins/compiler/dynamicComponent.js";
import { resolveMagicImport } from "@/plugins/compiler/magic-import-resolver.js";
// import * as VueOfficePdf from "@vue-office/pdf";
// import * as VueOfficeExcel from "@vue-office/excel";
// import * as VueOfficeDocx from "@vue-office/docx";

const libs = {
  vue,
  "vue-router": vueRouter,
  "element-plus": ElementPlus,
  "lodash-es": lodashEs,
  "js-pinyin": jsPinyin,
  "@/store": Store,
  "@vueuse/core": Vueuse,
  "vue-draggable-plus": VueDraggable,
  "@/utils": Utils,
  "@/utils/request": request,
  "@/api/file-api": FileAPI,
  "@/utils/form": FormUtils,
  "@/plugins/compiler/sfc-compiler": sfcCompiler,
  "@/plugins/compiler/dynamicComponent.js": dynamicComponent,
  "@form-create/element-ui": formCreate,
  "@/composables": Composables,
  dayjs,
  pinia: Pinia,
  "@antv/x6": x6,
  "@antv/x6-vue-shape": x6VueShape,
  "@antv/g2": g2,
  "@antv/s2": s2,
  "@antv/s2-vue": s2Vue,
  exceljs: ExcelJS,
  // "@vue-office/pdf": VueOfficePdf,
  // "@vue-office/excel": VueOfficeExcel,
  // "@vue-office/docx": VueOfficeDocx,
};

window.___magic__import__ = function (lib, name) {
  return resolveMagicImport(libs[lib], name);
};
