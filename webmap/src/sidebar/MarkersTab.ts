import * as L from "leaflet";
import {Pl3xMap} from "../Pl3xMap";
import {createSVGIcon, handleKeyboardEvent} from "../util/Util";
import BaseTab from "./BaseTab";
import '../svg/marker_point.svg';
import {Lang} from "../settings/Lang";
import {MarkerLayer} from "../layergroup/MarkerLayer";
import {Marker} from "../marker/Marker";
import {Icon} from "../marker/Icon";
import {World} from "../world/World";
import Pl3xMapLeafletMap from "../map/Pl3xMapLeafletMap";

interface MarkerListItem {
    layer: MarkerLayer;
    marker: Marker;
    name: string;
    icon?: string;
}

/**
 * Lists the icon markers of the visible marker layers, grouped by layer.
 * Clicking an entry moves the map to that marker.
 */
export default class MarkersTab extends BaseTab {
    // the built-in players layer (core PlayersLayer.KEY); players have their own tab
    private static readonly PLAYERS_LAYER: string = 'pl3xmap_players';

    private readonly _skeleton: HTMLParagraphElement;
    private readonly _list: HTMLFieldSetElement;

    private _items: Map<string, MarkerListItem> = new Map();
    private _signature: string = '';
    private _timer: NodeJS.Timeout | undefined;

    constructor(pl3xmap: Pl3xMap) {
        super(pl3xmap, 'markers');

        const lang: Lang = pl3xmap.settings!.lang;

        this._button.appendChild(createSVGIcon('marker_point'));
        this._button.setAttribute('aria-label', lang.markers.label);
        this._button.title = lang.markers.label;

        const heading: HTMLHeadingElement = L.DomUtil.create('h2', '', this._content);
        heading.innerText = lang.markers.label;
        heading.id = 'markers-heading';

        this._skeleton = L.DomUtil.create('p', '', this._content);
        this._skeleton.innerText = lang.markers.value;
        this._skeleton.tabIndex = -1;

        this._list = L.DomUtil.create('fieldset', 'menu', this._content);
        this._list.setAttribute('aria-labelledby', 'markers-heading');
        this._list.setAttribute('role', 'radiogroup');
        this._list.hidden = true;

        this.initEvents();
    }

    private initEvents(): void {
        addEventListener('overlayadded', (e: CustomEvent<MarkerLayer>): void => {
            // layer shown or hidden in the layers tab
            e.detail.on('add remove', () => this.scheduleUpdate());
            this.scheduleUpdate();
        });
        addEventListener('overlayremoved', () => this.scheduleUpdate());
        addEventListener('overlayupdated', () => this.scheduleUpdate());
        addEventListener('worldselected', () => this.scheduleUpdate());

        // the map no longer shows the selected marker
        this._pl3xmap.map.on('dragstart', (): void => {
            this._list.querySelectorAll('input').forEach((input: HTMLInputElement) => input.checked = false);
        });

        this._list.addEventListener('keydown', (e: KeyboardEvent) =>
            handleKeyboardEvent(e, Array.from(this._list.querySelectorAll('input'))));
    }

    /**
     * Layers fire their events one by one, so rebuild the list once after the current batch.
     */
    private scheduleUpdate(): void {
        clearTimeout(this._timer);
        this._timer = setTimeout(() => this.update(), 0);
    }

    private update(): void {
        const world: World | undefined = this._pl3xmap.worldManager.currentWorld;
        const map: Pl3xMapLeafletMap = this._pl3xmap.map;

        const layers: MarkerLayer[] = (world?.markerLayers ?? [])
            .filter((layer: MarkerLayer): boolean => layer.key !== MarkersTab.PLAYERS_LAYER && map.hasLayer(layer))
            .sort((a: MarkerLayer, b: MarkerLayer): number =>
                a.priority - b.priority || (a.label < b.label ? -1 : (a.label > b.label ? 1 : 0)));

        const groups: MarkerListItem[][] = layers.map((layer: MarkerLayer): MarkerListItem[] =>
            Array.from(layer.markers.values())
                .filter((marker: Marker): boolean => marker instanceof Icon)
                .map((marker: Marker): MarkerListItem => {
                    const icon: L.Marker = marker.marker as L.Marker;
                    return {
                        layer,
                        marker,
                        name: getTooltipText(icon) || marker.key,
                        icon: icon.options.icon?.options.iconUrl
                    };
                })
                .sort((a: MarkerListItem, b: MarkerListItem): number => a.name.localeCompare(b.name)))
            .filter((group: MarkerListItem[]): boolean => group.length > 0);

        // only touch the DOM when something visible changed, so keyboard focus stays put
        const signature: string = JSON.stringify(groups.map((group: MarkerListItem[]) => [
            group[0].layer.key, group[0].layer.label,
            ...group.map((item: MarkerListItem) => [item.marker.key, item.name, item.icon])
        ]));
        if (signature === this._signature) {
            return;
        }
        this._signature = signature;

        // keep the selection and the keyboard focus across the rebuild
        const focused: boolean = this._list.contains(document.activeElement);
        const focusedId: string | undefined = focused ? document.activeElement!.id : undefined;
        const checkedId: string | undefined = (this._list.querySelector('input:checked') as HTMLInputElement | null)?.id;

        L.DomUtil.empty(this._list);
        this._items.clear();

        groups.forEach((group: MarkerListItem[]): void => {
            const fieldset: HTMLFieldSetElement = L.DomUtil.create('fieldset', 'menu', this._list),
                legend: HTMLLegendElement = L.DomUtil.create('legend', '', fieldset);
            legend.innerText = group[0].layer.label;
            group.forEach((item: MarkerListItem) => this.createListItem(fieldset, item));
        });

        const hasMarkers: boolean = this._items.size > 0;
        this._list.hidden = !hasMarkers;
        this._skeleton.hidden = hasMarkers;

        const checked: HTMLElement | null = checkedId ? document.getElementById(checkedId) : null;
        if (checked instanceof HTMLInputElement) {
            checked.checked = true;
        }
        if (focused) {
            ((focusedId ? document.getElementById(focusedId) : null) ?? this._list.querySelector('input') ?? this._skeleton).focus();
        }
    }

    private createListItem(parent: HTMLElement, item: MarkerListItem): void {
        const input: HTMLInputElement = L.DomUtil.create('input', 'markers', parent),
            label: HTMLLabelElement = L.DomUtil.create('label', '', parent);

        if (item.icon) {
            label.style.backgroundImage = `url('${item.icon}')`;
        }
        label.innerText = item.name;
        input.id = label.htmlFor = `marker-${L.Util.stamp(item.marker.marker)}`;
        input.type = 'radio';
        input.name = 'marker';
        input.checked = false;
        input.addEventListener('click', () => this.panTo(item.marker));

        this._items.set(input.id, item);
    }

    private panTo(marker: Marker): void {
        const map: Pl3xMapLeafletMap = this._pl3xmap.map;
        // stop following a player, or the map jumps back to them
        this._pl3xmap.playerManager.follow = undefined;
        // zoom in to at least one block per pixel
        map.flyTo((marker.marker as L.Marker).getLatLng(), Math.max(map.getZoom(), map.getMaxZoomOut()));
    }

    onActivate(): void {
        if (this._items.size) {
            ((this._list.querySelector('input:checked') ?? this._list.querySelector('input')) as HTMLElement).focus();
        } else {
            this._skeleton.focus();
        }
    }
}

/**
 * The marker's tooltip as plain text, without HTML.
 */
function getTooltipText(marker: L.Marker): string {
    const tooltip: L.Tooltip | undefined = marker.getTooltip();
    let content = tooltip?.getContent();
    if (typeof content === 'function') {
        content = content(marker);
    }
    if (content === undefined) {
        return '';
    }
    // a template parses the HTML without running scripts or loading images
    const template: HTMLTemplateElement = L.DomUtil.create('template');
    if (typeof content === 'string') {
        template.innerHTML = content;
    } else {
        template.content.appendChild(content.cloneNode(true));
    }
    template.content.querySelectorAll('br').forEach((br: HTMLBRElement) => br.replaceWith(' '));
    return (template.content.textContent ?? '').replace(/\s+/g, ' ').trim();
}
