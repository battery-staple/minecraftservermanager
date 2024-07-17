import {JSX} from "react";

export type HeaderButtonsState = "editing" | "creating" | "disabled";

export function HeaderButtons(props: { state: HeaderButtonsState, setEditing: (editing: boolean) => void, setCreating: (creating: boolean) => void }): JSX.Element {

    switch (props.state) {
        case "editing":
            return <>
                <button type="button" className="btn btn-primary btn-lg" onClick={() => props.setEditing(false)}>
                    Done
                </button>
            </>;
        case "creating":
            return <>
                <button type="button" className="btn btn-secondary btn-lg" onClick={() => props.setEditing(true)}>
                    Edit
                </button>
                <button type="button" className="btn btn-primary btn-lg" onClick={() => props.setCreating(true)}>
                    New
                </button>
            </>;
        case "disabled":
            return <>
                <button type="button" className="btn btn-primary btn-lg" onClick={() => props.setCreating(true)}>
                    New
                </button>
            </>;
    }
}