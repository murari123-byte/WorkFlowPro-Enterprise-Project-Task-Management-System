import axios from 'axios';
import type { ApiError } from '../types';

/** A readable message for any error thrown by an API call. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return 'Cannot reach the server. Check that the API Gateway is running.';
    }
    const data = error.response.data as Partial<ApiError> | undefined;
    if (data?.message) {
      return data.message;
    }
    return `Request failed (${error.response.status})`;
  }
  return error instanceof Error ? error.message : 'Something went wrong';
}

/** Field-level validation errors from a 400 response, e.g. { email: "must be a well-formed email address" }. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as Partial<ApiError> | undefined;
    return data?.fieldErrors ?? {};
  }
  return {};
}
