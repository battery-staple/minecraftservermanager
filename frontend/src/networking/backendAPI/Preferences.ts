import {UserPreferences} from "../../APIModels";
import {getHostname} from "../../config";
import {jsonHeaders} from "../FetchUtils";

export async function getPreferences(): Promise<UserPreferences | null> {
    const response = await fetch(`http://${await getHostname()}/api/v2/rest/users/current/preferences`)

    return response.ok ? response.json() : null
}

export async function updatePreferences(userPreferences: Partial<UserPreferences>): Promise<boolean> {
    const response = await fetch(`http://${await getHostname()}/api/v2/rest/users/current/preferences`, {
        headers: jsonHeaders,
        method: "PATCH",
        body: JSON.stringify(userPreferences)
    })

    return response.ok
}