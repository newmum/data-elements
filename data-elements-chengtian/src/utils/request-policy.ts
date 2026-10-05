import type { AxiosRequestConfig, AxiosResponse } from "axios";
import request from "@/utils/request";

export type ResponsePolicy = "data" | "raw";
export type ErrorPolicy = "notify" | "silent";

export type PolicyRequestConfig<D = unknown> = AxiosRequestConfig<D> & {
  responsePolicy?: ResponsePolicy;
  errorPolicy?: ErrorPolicy;
};

function execute<T, D = unknown>(config: PolicyRequestConfig<D>): Promise<T> {
  return request(config as AxiosRequestConfig<D>) as Promise<T>;
}

/** Explicit request intents used by API adapters instead of interceptor-only flags. */
export const requestPolicy = {
  data<T, D = unknown>(config: PolicyRequestConfig<D>): Promise<T> {
    return execute<T, D>({ ...config, responsePolicy: "data" });
  },

  quiet<T, D = unknown>(config: PolicyRequestConfig<D>): Promise<T> {
    return execute<T, D>({ ...config, responsePolicy: "data", errorPolicy: "silent" });
  },

  raw<T, D = unknown>(config: PolicyRequestConfig<D>): Promise<AxiosResponse<T>> {
    return execute<AxiosResponse<T>, D>({ ...config, responsePolicy: "raw" });
  },

  download<D = unknown>(config: PolicyRequestConfig<D>): Promise<AxiosResponse<Blob>> {
    return execute<AxiosResponse<Blob>, D>({
      ...config,
      responseType: "blob",
      responsePolicy: "raw",
    });
  },
};

export default requestPolicy;
