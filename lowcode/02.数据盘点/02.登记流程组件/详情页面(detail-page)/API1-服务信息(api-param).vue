<template>
  <div class="api-param card-container flex px-5 pt-5 flex-col">
    <!--  请求参数  -->
    <u-title class="api-param-title" name="请求参数">
      <template #default>
        <a v-if="!editStatus[0]" class="ml-3" @click="handleEdit(0)">
          <Icon icon="edit" class="custom-icon editForm" />
        </a>
        <el-button v-if="editStatus[0]" type="text" class="ml-3" @click="handleCancel(0)">
          取消
        </el-button>
        <el-button v-if="editStatus[0]" type="text" :loading="btnLoading[0]" @click="handleSave(0)">
          保存
        </el-button>
      </template>
      <template #right>
        <el-button v-if="editStatus[0]" size="mini" type="primary" @click="handleAdd(0)">
          新增
        </el-button>
      </template>
    </u-title>
    <v-table
      ref="reqRef"
      :options="reqOptions"
      :editable="editStatus[0]"
      :show-toolbar="false"
      show-operation
      class="api-param-table"
    />

    <!--  响应参数  -->
    <u-title class="api-param-title" name="响应参数">
      <template #default>
        <a v-if="!editStatus[1]" class="ml-3" @click="handleEdit(1)">
          <Icon icon="edit" class="custom-icon editForm" />
        </a>
        <el-button v-if="editStatus[1]" type="text" class="ml-3" @click="handleCancel(1)">
          取消
        </el-button>
        <el-button v-if="editStatus[1]" type="text" :loading="btnLoading[1]" @click="handleSave(1)">
          保存
        </el-button>
      </template>
      <template #right>
        <el-button v-if="editStatus[1]" size="mini" type="primary" @click="handleAdd(1)">
          新增
        </el-button>
      </template>
    </u-title>
    <v-table
      ref="resRef"
      :options="resOptions"
      :editable="editStatus[1]"
      :show-toolbar="false"
      show-operation
      class="api-param-table"
    />

    <!--  响应状态码  -->
    <u-title class="api-param-title" name="响应状态码">
      <template #default>
        <a v-if="!editStatus[2]" class="ml-3" @click="handleEdit(2)">
          <Icon icon="edit" class="custom-icon editForm" />
        </a>
        <el-button v-if="editStatus[2]" type="text" class="ml-3" @click="handleCancel(2)">
          取消
        </el-button>
        <el-button v-if="editStatus[2]" type="text" :loading="btnLoading[2]" @click="handleSave(2)">
          保存
        </el-button>
      </template>
      <template #right>
        <el-button v-if="editStatus[2]" size="mini" type="primary" @click="handleAdd(2)">
          新增
        </el-button>
      </template>
    </u-title>
    <v-table
      ref="resCodeRef"
      :options="resCodeOptions"
      :editable="editStatus[2]"
      :show-toolbar="false"
      show-operation
      class="api-param-table"
    />

    <!--  响应示例  -->
    <u-title class="api-param-title" name="响应示例">
      <template #default>
        <a v-if="!editStatus[3]" class="ml-3" @click="handleEdit(3)">
          <Icon icon="edit" class="custom-icon editForm" />
        </a>
        <el-button v-if="editStatus[3]" type="text" class="ml-3" @click="handleCancel(3)">
          取消
        </el-button>
        <el-button v-if="editStatus[3]" type="text" :loading="btnLoading[3]" @click="handleSave(3)">
          保存
        </el-button>
      </template>
    </u-title>
    <div class="api-param-table" style="height: 500px; margin-bottom: 0px">
      <template v-if="editStatus[3]">
        <code-editor
          v-model="resultSampleTemp"
          lang="javascript"
          theme="chrome"
          :read-only="!editStatus[3]"
          placeholder="请输入响应示例..."
        />
      </template>
      <template v-else>
        <Empty v-if="!resultSampleTemp"></Empty>
        <code-editor
          v-else
          v-model="resultSampleTemp"
          lang="javascript"
          theme="chrome"
          :read-only="!editStatus[3]"
          placeholder="请输入响应示例..."
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useDictStore } from "@/store";

const resRef = ref(null);
const resCodeRef = ref(null);
const reqRef = ref(null);
const resultSampleTemp = ref("");
const editStatus = ref([false, false, false, false]);
const btnLoading = ref([false, false, false, false]);
const reqOptions = ref({
  columns: [
    {
      title: "参数名称",
      field: "paramName",
      minWidth: 200,
      editRender: { name: "VxeInput" },
    },
    {
      title: "参数中文名",
      field: "paramNameZh",
      minWidth: 200,
      editRender: { name: "VxeInput" },
    },
    {
      title: "参数说明",
      field: "paramDesc",
      minWidth: 200,
      editRender: { name: "VxeInput" },
    },
    {
      title: "参数类型",
      field: "paramType",
      width: 150,
      editRender: { name: "ElSelect", options: useDictStore().getDictItems("tableColType") },
    },
    {
      title: "参数位置",
      field: "paramPosition",
      width: 150,
      editRender: { name: "ElSelect", options: useDictStore().getDictItems("paramPosition") },
    },
    {
      title: "必填属性",
      field: "status",
      width: 150,
      editRender: { name: "ElSelect", options: useDictStore().getDictItems("requiredAttribute") },
    },
    {
      title: "默认值",
      field: "instance",
      minWidth: 200,
      editRender: { name: "VxeInput" },
    },
  ],
  editRules: {
    paramName: [{ required: true, content: "参数名称不能为空", trigger: "blur" }],
    paramNameZh: [{ required: true, content: "参数中文名不能为空", trigger: "blur" }],
    paramType: [{ required: true, content: "参数类型不能为空", trigger: "blur" }],
    paramPosition: [{ required: true, content: "参数位置不能为空", trigger: "blur" }],
    status: [{ required: true, content: "必填属性不能为空", trigger: "blur" }],
  },
});
const resOptions = ref({
  columns: [
    {
      title: "参数名称",
      field: "paramName",
      minWidth: 200,
      editRender: { name: "VxeInput" },
    },
    {
      title: "参数中文名",
      field: "paramNameZh",
      minWidth: 200,
      editRender: { name: "VxeInput" },
    },
    {
      title: "参数说明",
      field: "paramDesc",
      minWidth: 200,
      editRender: { name: "VxeInput" },
    },
    {
      title: "参数类型",
      field: "paramType",
      width: 150,
      editRender: { name: "ElSelect", options: useDictStore().getDictItems("tableColType") },
    },
    {
      title: "默认值",
      field: "instance",
      minWidth: 200,
      editRender: { name: "VxeInput" },
    },
  ],
  editRules: {
    paramName: [{ required: true, content: "参数名称不能为空", trigger: "blur" }],
    paramNameZh: [{ required: true, content: "参数中文名不能为空", trigger: "blur" }],
    paramType: [{ required: true, content: "参数类型不能为空", trigger: "blur" }],
  },
});
const resCodeOptions = ref({
  columns: [
    {
      title: "状态码",
      field: "statusCode",
      minWidth: 200,
      editRender: { name: "VxeInput" },
    },
    {
      title: "状态码描述",
      field: "statusDesc",
      minWidth: 200,
      editRender: { name: "VxeInput" },
    },
  ],
  editRules: {
    statusCode: [{ required: true, content: "状态码不能为空", trigger: "blur" }],
    statusDesc: [{ required: true, content: "状态码描述不能为空", trigger: "blur" }],
  },
});

const init = () => {
  // @todo  api props.categoryId
  // 请求参数 - 广电业务相关
  reqRef.value.setData([
    {
      paramName: "channelId",
      paramNameZh: "频道ID",
      paramType: "varchar",
      required: true,
      paramDesc: "广电频道唯一标识",
      paramPosition: "body",
      status: "required",
    },
    {
      paramName: "programId",
      paramNameZh: "节目ID",
      paramType: "varchar",
      required: false,
      paramDesc: "节目唯一标识",
      paramPosition: "body",
      status: "optional",
    },
    {
      paramName: "startTime",
      paramNameZh: "开始时间",
      paramType: "datetime",
      required: true,
      paramDesc: "查询开始时间",
      paramPosition: "body",
      status: "required",
    },
    {
      paramName: "endTime",
      paramNameZh: "结束时间",
      paramType: "datetime",
      required: true,
      paramDesc: "查询结束时间",
      paramPosition: "body",
      status: "required",
    },
    {
      paramName: "regionCode",
      paramNameZh: "区域编码",
      paramType: "varchar",
      required: false,
      paramDesc: "广电覆盖区域编码",
      paramPosition: "body",
      status: "optional",
    },
  ]);

  // 响应参数 - 广电业务相关
  resRef.value.setData([
    {
      paramName: "channelId",
      paramNameZh: "频道ID",
      paramType: "varchar",
      paramDesc: "广电频道唯一标识",
      status: "required",
    },
    {
      paramName: "channelName",
      paramNameZh: "频道名称",
      paramType: "varchar",
      paramDesc: "广电频道名称",
      status: "required",
    },
    {
      paramName: "programList",
      paramNameZh: "节目列表",
      paramType: "array",
      paramDesc: "频道节目列表",
      status: "required",
    },
    {
      paramName: "totalCount",
      paramNameZh: "总条数",
      paramType: "integer",
      paramDesc: "节目总数",
      status: "required",
    },
    {
      paramName: "regionInfo",
      paramNameZh: "区域信息",
      paramType: "object",
      paramDesc: "广电覆盖区域信息",
      status: "optional",
    },
  ]);

  // 响应状态码 - 广电业务相关
  resCodeRef.value.setData([
    {
      statusCode: "200",
      statusDesc: "成功",
    },
    {
      statusCode: "400",
      statusDesc: "请求参数错误",
    },
    {
      statusCode: "401",
      statusDesc: "未授权访问",
    },
    {
      statusCode: "403",
      statusDesc: "权限不足",
    },
    {
      statusCode: "404",
      statusDesc: "频道或节目不存在",
    },
    {
      statusCode: "500",
      statusDesc: "服务器内部错误",
    },
    {
      statusCode: "502",
      statusDesc: "广电服务暂时不可用",
    },
  ]);

  // 响应示例 - 广电业务相关
  //   resultSampleTemp.value = `{
  //   "code": 200,
  //   "message": "success",
  //   "data": {
  //     "channelId": "GDT10086",
  //     "channelName": "广东卫视",
  //     "programList": [
  //       {
  //         "programId": "PG20240101001",
  //         "programName": "新闻联播",
  //         "startTime": "2024-01-01 19:00:00",
  //         "endTime": "2024-01-01 19:30:00",
  //         "type": "news",
  //         "description": "全国新闻联播节目"
  //       },
  //       {
  //         "programId": "PG20240101002",
  //         "programName": "天气预报",
  //         "startTime": "2024-01-01 19:30:00",
  //         "endTime": "2024-01-01 19:35:00",
  //         "type": "life",
  //         "description": "全国天气预报"
  //       },
  //       {
  //         "programId": "PG20240101003",
  //         "programName": "黄金剧场",
  //         "startTime": "2024-01-01 20:00:00",
  //         "endTime": "2024-01-01 22:00:00",
  //         "type": "drama",
  //         "description": "热播电视剧"
  //       }
  //     ],
  //     "totalCount": 3,
  //     "regionInfo": {
  //       "regionCode": "440000",
  //       "regionName": "广东省",
  //       "coverageRate": "99.8%"
  //     }
  //   },
  //   "timestamp": "2024-01-01 18:00:00"
  // }`;
};

const handleEdit = (index: number) => {
  editStatus.value[index] = true;
};

const handleCancel = (index: number) => {
  editStatus.value[index] = false;
};

const handleSave = async (index: number) => {
  try {
    btnLoading.value[index] = true;
    if (index === 0) {
      await reqRef.value.validate();
      const data = reqRef.value.getData();
      console.log("请求参数数据:", data);
    } else if (index === 1) {
      await resRef.value.validate();
      const data = resRef.value.getData();
      console.log("响应参数数据:", data);
    } else if (index === 2) {
      await resCodeRef.value.validate();
      const data = resCodeRef.value.getData();
      console.log("响应状态码数据:", data);
    } else if (index === 3) {
      console.log("响应示例数据:", resultSampleTemp.value);
    }

    // 保存成功后切换回只读状态
    editStatus.value[index] = false;
  } catch (error) {
    console.error("保存失败:", error);
  } finally {
    btnLoading.value[index] = false;
  }
};

const handleAdd = (index: number) => {
  if (index === 0) {
    reqRef.value?.gridEvents.toolbarButtonClick({ code: "append_edit" });
  } else if (index === 1) {
    console.log("handleAdd", resRef.value?.gridEvents);
    resRef.value?.gridEvents.toolbarButtonClick({ code: "append_edit" });
  } else if (index === 2) {
    resCodeRef.value?.gridEvents.toolbarButtonClick({ code: "append_edit" });
  }
};

onMounted(() => {
  init();
});
</script>

<style scoped lang="scss">
.api-param {
  overflow: auto;
  .api-param-title {
    height: 32px;
    margin-bottom: 12px;
  }
  .api-param-table {
    margin-bottom: 20px;
  }
}
</style>
