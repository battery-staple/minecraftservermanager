import {DEFAULT_SORT_STRATEGY, SortStrategy} from "../APIModels";
import {useCallback, useEffect, useMemo, useState} from "react";
import {getPreferences, updatePreferences} from "../networking/backendAPI/Preferences";

/**
 * A hook that accesses and sets the current user's preferred sort strategy.
 * @return an array where the first element is the user's preferred sort strategy
 *         and the second element is a function that sets the strategy
 */
export function useSortStrategy(): [SortStrategy, (newSortStrategy: SortStrategy) => void] {
    const [sortStrategyState, setSortStrategyState] = useState<SortStrategy | null>(null)

    const sortStrategy = useMemo(() => sortStrategyState ?? DEFAULT_SORT_STRATEGY, [sortStrategyState])

    const setSortStrategy = useCallback((newSortStrategy: SortStrategy) => {
        setSortStrategyState(newSortStrategy)
        updatePreferences({
            serverSortStrategy: newSortStrategy
        }).then(success => {
            if (!success) {
                console.error("Failed to save preferences update to server")
            }
        })
    }, []);

    useEffect(() => {
        getPreferences()
            .then(preferences => {
                if (preferences !== null) setSortStrategyState(preferences.serverSortStrategy)
            })
    }, [setSortStrategyState])

    return [sortStrategy, setSortStrategy];
}