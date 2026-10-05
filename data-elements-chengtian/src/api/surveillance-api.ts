import httpRequest from "@/utils/request";
import type { AxiosResponse } from "axios";

const unwrap = <T>(response: T | AxiosResponse<T>): T => {
  if (response && typeof response === "object" && "data" in response && "status" in response) {
    return (response as AxiosResponse<T>).data;
  }
  return response as T;
};

export type SurveillanceResourceTopic = {
  tid?: number;
  tenantId?: string;
  engineCode: string;
  channelCode: "person" | "mobile" | "vehicle";
  resourceCode: string;
  sourceTable: string;
  inputTopic: string;
  resultTopic: string;
  identifierType: string;
  keyField: string;
  schemaJson?: string;
  status?: string;
  createdTime?: string;
  updatedTime?: string;
};

export type SurveillanceDailyStat = {
  resourceCode: string;
  channelCode: "person" | "mobile" | "vehicle";
  inputTopic: string;
  resultTopic: string;
  statDate: string;
  inputCount: number;
  matchedCount: number;
  unmatchedCount: number;
  failureCount: number;
  totalInputCount: number;
  totalMatchedCount: number;
  lastEventTime?: string;
  updatedTime?: string;
};

export type SurveillanceResourceTopicCommand = Omit<SurveillanceResourceTopic, "tid" | "tenantId" | "createdTime" | "updatedTime">;

export type SurveillanceKafkaConnection = {
  connectionName: string;
  bootstrapServers: string;
  securityProtocol: "PLAINTEXT";
  status: "ACTIVE" | "DISABLED";
};

export type SurveillanceKafkaRole = "INPUT" | "OUTPUT";

export const getSurveillanceKafkaConnection = async (role: SurveillanceKafkaRole = "INPUT") =>
  unwrap(await httpRequest.get<SurveillanceKafkaConnection | null>("/api/v1/surveillance/kafka-connection", {
    params: { engineCode: "structured-surveillance", role },
    errorPolicy: "silent",
  } as never));

export const saveSurveillanceKafkaConnection = async (data: SurveillanceKafkaConnection, role: SurveillanceKafkaRole = "INPUT") =>
  unwrap(await httpRequest.put<SurveillanceKafkaConnection>("/api/v1/surveillance/kafka-connection", {
    ...data,
    engineCode: "structured-surveillance",
  }, { params: { role } }));

export const testSurveillanceKafkaConnection = async (role: SurveillanceKafkaRole = "INPUT") =>
  unwrap(await httpRequest.post<SurveillanceKafkaConnection>("/api/v1/surveillance/kafka-connection/test", null, {
    params: { engineCode: "structured-surveillance", role },
  }));

export const listSurveillanceKafkaTopics = async (role: SurveillanceKafkaRole = "INPUT") =>
  unwrap(await httpRequest.get<string[]>("/api/v1/surveillance/kafka-connection/topics", {
    params: { engineCode: "structured-surveillance", role },
  }));

export const createSurveillanceKafkaTopic = async (topicName: string) =>
  unwrap(await httpRequest.post<{ topicName: string; created: boolean }>(
    "/api/v1/surveillance/kafka-connection/topics",
    { topicName },
    { params: { engineCode: "structured-surveillance", role: "OUTPUT" } },
  ));

export type SurveillanceControlItem = {
  tid?: string;
  tenantId?: string;
  engineCode: string;
  channelCode: "person" | "mobile" | "vehicle";
  controlItemId: string;
  ruleCode: string;
  subjectType: string;
  identifierType: string;
  identifier: string;
  subjectName: string;
  sourceSystem: string;
  controlReason: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  status: string;
  snapshotVersion: number;
};

export const listSurveillanceControlItems = async (params: Record<string, string | undefined> = {}) =>
  unwrap(
    await httpRequest.get<SurveillanceControlItem[]>("/api/v1/surveillance/control-items", {
      params,
      errorPolicy: "silent",
    } as never)
  );

export const listSurveillanceResourceTopics = async (params: Record<string, string | undefined> = {}) =>
  unwrap(
    await httpRequest.get<SurveillanceResourceTopic[]>("/api/v1/surveillance/resource-topics", {
      params,
      // 概览页有本地资源配置兜底；后端升级或重启期间不应弹出阻断式错误弹窗。
      errorPolicy: "silent",
    } as never)
  );

export const createSurveillanceResourceTopic = async (data: SurveillanceResourceTopicCommand) =>
  unwrap(await httpRequest.post<SurveillanceResourceTopic>("/api/v1/surveillance/resource-topics", data));

export const updateSurveillanceResourceTopic = async (resourceCode: string, data: SurveillanceResourceTopicCommand) =>
  unwrap(await httpRequest.put<SurveillanceResourceTopic>(`/api/v1/surveillance/resource-topics/${encodeURIComponent(resourceCode)}`, data));

export const disableSurveillanceResourceTopic = async (resourceCode: string) =>
  unwrap(await httpRequest.delete<void>(`/api/v1/surveillance/resource-topics/${encodeURIComponent(resourceCode)}`));

export const listSurveillanceDailyStats = async (params: Record<string, string | undefined> = {}) =>
  unwrap(
    await httpRequest.get<SurveillanceDailyStat[]>("/api/v1/surveillance/statistics/daily", {
      params,
      // 统计接口不可用时由页面展示明确的空态，不阻断资源卡片加载。
      errorPolicy: "silent",
    } as never)
  );
