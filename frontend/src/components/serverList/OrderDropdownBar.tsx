import {SortStrategy} from "../../APIModels";
import {JSX} from "react";

export function OrderDropdownBar(props: {
    sortStrategy: SortStrategy,
    setSortStrategy: (sortStrategy: SortStrategy) => void
}): JSX.Element {
    return <div className="sort-dropdown-bar server-list-bar">
        <div className="dropdown">
            <a className="dropdown-toggle" href="#" role="button" id="dropdownMenuLink" data-bs-toggle="dropdown"
               aria-expanded="false">
                Sorting by: {displayName(props.sortStrategy)}
            </a>
            <ul className="dropdown-menu">
                <li><a className="dropdown-item" onClick={() => {
                    props.setSortStrategy("ALPHABETICAL")
                }}>Alphabetical</a></li>
                <li><a className="dropdown-item" onClick={() => {
                    props.setSortStrategy("NEWEST")
                }}>Newest</a></li>
                <li><a className="dropdown-item" onClick={() => {
                    props.setSortStrategy("OLDEST")
                }}>Oldest</a></li>
            </ul>
        </div>
    </div>;
}

function displayName(sortStrategy: SortStrategy) {
    switch (sortStrategy) {
        case "ALPHABETICAL":
            return "Alphabetical"
        case "NEWEST":
            return "Newest"
        case "OLDEST":
            return "Oldest"
    }
}