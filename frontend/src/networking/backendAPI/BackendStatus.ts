import {DEFAULT_TIMEOUT_MS, defaultHeaders, fetchWithTimeout} from "../FetchUtils";
import {hostname} from "../../config";

export type BackendStatus = "up" | "offline" | "unauthorized"

export async function getBackendStatus(): Promise<BackendStatus> {
    try {
        const statusResponse = await fetchWithTimeout(`http://${await hostname}/api/v2/rest/status`, DEFAULT_TIMEOUT_MS, {
            method: "GET",
            headers: defaultHeaders
        })

        if (statusResponse.ok) {
            return "up"
        } else if (statusResponse.status === 401 || statusResponse.status === 403) {
            return "unauthorized"
        } else {
            return "offline"
        }
    } catch {
        return "offline"
    }
}