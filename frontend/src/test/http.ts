import { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
import { apiClient } from '../api/client';

interface Reply {
  status?: number;
  data?: unknown;
}

/**
 * Replaces the network under apiClient: records every request and answers with `handler`
 * (200 and an empty body by default). Interceptors still run, so token handling is exercised too.
 */
export function mockHttp(handler: (config: InternalAxiosRequestConfig) => Reply | void = () => ({})) {
  const calls: InternalAxiosRequestConfig[] = [];
  const adapter: AxiosAdapter = async (config) => {
    calls.push(config);
    const { status = 200, data = {} } = handler(config) ?? {};
    const response = { data, status, statusText: '', headers: {}, config };
    if (status >= 400) throw new AxiosError(`HTTP ${status}`, 'ERR_BAD_RESPONSE', config, null, response);
    return response;
  };
  apiClient.defaults.adapter = adapter;
  return calls;
}

/** JSON body of a recorded request. */
export const sentBody = (call: InternalAxiosRequestConfig) => JSON.parse(call.data as string) as unknown;
