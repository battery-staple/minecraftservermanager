import {Runner} from "../../APIModels";
import {DEFAULT_TIMEOUT_MS, defaultHeaders, fetchJsonWithTimeoutOrNull} from "../FetchUtils";
import {getHostname} from "../../config";

export async function getRunner(runnerUUID: string): Promise<Runner> {
    const runner = await fetchJsonWithTimeoutOrNull<Runner>(`http://${await getHostname()}/api/v2/rest/runners/${runnerUUID}`, DEFAULT_TIMEOUT_MS, {
        method: "GET",
        headers: defaultHeaders
    });
    if (runner === null) {
        throw Error(`Got invalid response getting runner`)
    }
    return runner
}