export type EsQueryType = "term" | "terms" | "wildcard" | "query_string" | "range";

/**
 * 获取es查询字段语句
 * @example 返回对象:
 * {
 *   【查询方式】：{ 【查询字段】: 【查询值】 }
 * }
 * @param query 查询方式
 * @param field 查询字段
 * @param value 查询值
 */
export const genEsQueryField = (query: EsQueryType, field, value: []) => {
  let esValue: any = value;
  // 处理查询值，例如：wildcard查询需要将多个值用逗号分隔
  switch (query) {
    case "wildcard":
      esValue = `*${esValue.join(",")}*`;
      break;
    case "range":
      // 时间范围
      esValue = {
        gte: esValue[0]["gte"],
        lte: esValue[0]["lte"],
      };
      break;
  }
  if (query === "multi_match") {
    return {
      [query]: {
        query: esValue.join(" "),
        fields: [field],
      },
    };
  }
  return {
    [query]: {
      [field]: esValue,
    },
  };
};

/**
 * 将Elasticsearch聚合结果转换为简洁的键值对格式
 * @param {Object} aggregations - Elasticsearch聚合结果
 * @returns {Object} 转换后的简洁格式对象
 */
export function transformAggregations(aggregations) {
  const result = {};

  for (const [fieldName, aggregation] of Object.entries(aggregations)) {
    if (aggregation.buckets && Array.isArray(aggregation.buckets)) {
      result[fieldName] = aggregation.buckets.reduce((acc, bucket) => {
        acc[bucket.key] = bucket.doc_count;
        return acc;
      }, {});
    }
  }

  return result;
}
