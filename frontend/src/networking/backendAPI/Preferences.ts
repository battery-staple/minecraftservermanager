import {UserPreferences} from "../../APIModels";
import {hostname} from "../../config";
import {DEFAULT_TIMEOUT_MS, defaultHeaders, fetchWithTimeout, jsonHeaders} from "../FetchUtils";

export async function getPreferences(): Promise<UserPreferences | null> {
    const response = await fetchWithTimeout(`http://${await hostname}/api/v2/rest/users/current/preferences`, DEFAULT_TIMEOUT_MS, {
        headers: defaultHeaders,
    })

    return response.ok ? response.json() : null
}

export async function updatePreferences(userPreferences: Partial<UserPreferences>): Promise<boolean> {
    const response = await fetchWithTimeout(`http://${await hostname}/api/v2/rest/users/current/preferences`,  DEFAULT_TIMEOUT_MS, {
        headers: jsonHeaders,
        method: "PATCH",
        body: JSON.stringify(userPreferences)
    })

    return response.ok
}