import {Runner} from "../../APIModels";
import {DEFAULT_TIMEOUT_MS, defaultHeaders, fetchJsonWithTimeoutOrNull} from "../FetchUtils";
import {hostname} from "../../config";

export async function getRunner(runnerUUID: string): Promise<Runner> {
    const runner = await fetchJsonWithTimeoutOrNull<Runner>(`http://${await hostname}/api/v2/rest/runners/${runnerUUID}`, DEFAULT_TIMEOUT_MS, {
        method: "GET",
        headers: defaultHeaders
    });
    if (runner === null) {
        throw Error(`Got invalid response getting runner`)
    }
    return runner
}

export async function getAllRunners(): Promise<Runner[]> {
    const runners = await fetchJsonWithTimeoutOrNull<Runner[]>(`http://${await hostname}/api/v2/rest/runners`, DEFAULT_TIMEOUT_MS, {
        method: "GET",
        headers: defaultHeaders
    });

    if (runners === null) {
        throw Error(`Got invalid response getting runners`)
    }

    return runners
}