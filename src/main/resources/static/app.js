const statusText = document.getElementById("status-text");
const summary = document.getElementById("summary");
const emptyState = document.getElementById("empty-state");
const results = document.getElementById("results");
const hacerOrdenButton = document.getElementById("hacer-orden");
let currentResults = [];

const buttons = {
    vaperaliaSession: document.getElementById("vaperalia-session"),
    ecigSession: document.getElementById("ecig-session"),
    runReorder: document.getElementById("run-reorder"),
    hacerOrden: hacerOrdenButton
};

buttons.vaperaliaSession.addEventListener("click", () => {
    runAction({
        button: buttons.vaperaliaSession,
        statusMessage: "Opening visible browser for Vaperalia login. Complete login to save the session.",
        request: () => postJson("/api/reorder/session/vaperalia"),
        onSuccess: (payload) => {
            setStatus(payload.message || "Vaperalia session saved.");
        }
    });
});

buttons.ecigSession.addEventListener("click", () => {
    runAction({
        button: buttons.ecigSession,
        statusMessage: "Opening visible browser for Eciglogistica login. Complete login to save the session.",
        request: () => postJson("/api/reorder/session/eciglogistica"),
        onSuccess: (payload) => {
            setStatus(payload.message || "Eciglogistica session saved.");
        }
    });
});

buttons.runReorder.addEventListener("click", () => {
    runAction({
        button: buttons.runReorder,
        statusMessage: "Running reorder scan. This may take a while while Playwright scrapes both distributors.",
        request: () => postJson("/api/reorder/run"),
        onSuccess: (payload) => {
            currentResults = normalizeResults(Array.isArray(payload) ? payload : []);
            renderResults(currentResults);
            setStatus(`Execution complete. Received ${currentResults.length} prioritized products.`);
        }
    });
});

buttons.hacerOrden.addEventListener("click", () => {
    runAction({
        button: buttons.hacerOrden,
        statusMessage: "Enviando selección a los carritos de distribuidora...",
        request: async () => {
            const selectedItems = collectSelectedItems();
            if (selectedItems.length === 0) {
                throw new Error("No hay URLs seleccionadas para ordenar.");
            }
            const payload = await submitSelectedItems(selectedItems);
            return {payload, selectedItems};
        },
        onSuccess: ({payload, selectedItems}) => {
            currentResults = reconcileResultsAfterOrder(currentResults, selectedItems, payload);
            renderResults(currentResults);
            setStatus(payload.message || "Orden enviada a distribuidoras.");
        }
    });
});

async function runAction({ button, statusMessage, request, onSuccess }) {
    setButtonsDisabled(true);
    setStatus(statusMessage);

    try {
        const payload = await request();
        onSuccess(payload);
    } catch (error) {
        setStatus(error.message || "Request failed.");
    } finally {
        setButtonsDisabled(false);
    }
}

async function postJson(url, body) {
    const response = await fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: body === undefined ? undefined : JSON.stringify(body)
    });

    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("application/json")
        ? await response.json()
        : await response.text();

    if (!response.ok) {
        const message = typeof payload === "string"
            ? payload
            : payload.message || `Request failed with status ${response.status}.`;
        throw new Error(message);
    }

    return payload;
}

function setButtonsDisabled(disabled) {
    Object.values(buttons).forEach((button) => {
        button.disabled = disabled;
    });
}

function setStatus(message) {
    statusText.textContent = message;
}

function renderResults(items) {
    results.innerHTML = "";

    if (items.length === 0) {
        hacerOrdenButton.hidden = true;
        summary.hidden = true;
        summary.textContent = "";
        emptyState.hidden = false;
        emptyState.textContent = "Execution completed, but no products need reorder.";
        return;
    }

    hacerOrdenButton.hidden = false;
    summary.hidden = false;
    summary.textContent = `${items.length} prioritized product${items.length === 1 ? "" : "s"} returned.`;
    emptyState.hidden = true;

    items.forEach((item, itemIndex) => {
        const card = document.createElement("article");
        card.className = "result-card";

        const heading = document.createElement("div");
        heading.className = "result-head";

        const title = document.createElement("h3");
        title.textContent = item.nombre || "Unnamed product";

        const badge = document.createElement("span");
        badge.className = "sku-badge";
        badge.textContent = item.sku || "NO-SKU";

        heading.append(title, badge);
        card.appendChild(heading);

        const cantidadComprar = Number(item.cantidadComprar) || 1;
        const quantityRow = document.createElement("div");
        quantityRow.className = "quantity-row";

        const quantityLabel = document.createElement("label");
        quantityLabel.className = "quantity-label";
        quantityLabel.htmlFor = `quantity-${itemIndex}`;
        quantityLabel.textContent = "Cantidad a añadir";

        const quantityInput = document.createElement("input");
        quantityInput.id = `quantity-${itemIndex}`;
        quantityInput.className = "quantity-input";
        quantityInput.type = "number";
        quantityInput.min = "1";
        quantityInput.step = "1";
        quantityInput.value = String(cantidadComprar > 0 ? cantidadComprar : 1);

        quantityRow.append(quantityLabel, quantityInput);
        card.appendChild(quantityRow);

        const sourceList = document.createElement("ul");
        sourceList.className = "source-list";

        const urls = Array.isArray(item.urls) ? item.urls : [];
        const distribuidoras = Array.isArray(item.distribuidoras) ? item.distribuidoras : [];

        urls.forEach((url, index) => {
            const sourceItem = document.createElement("li");
            sourceItem.className = "source-item";

            const label = document.createElement("label");
            label.className = "source-label";

            const radio = document.createElement("input");
            radio.type = "radio";
            radio.className = "source-radio";
            radio.name = `selected-priority-${itemIndex}`;
            radio.dataset.url = url;
            radio.dataset.distribuidora = distribuidoras[index] || "";
            radio.dataset.itemIndex = String(itemIndex);
            radio.checked = index === 0;

            const text = document.createElement("strong");
            text.textContent = `Priority ${index + 1}: ${distribuidoras[index] || "Unknown distributor"}`;

            const link = document.createElement("a");
            link.href = url;
            link.target = "_blank";
            link.rel = "noreferrer";
            link.textContent = url;

            label.append(radio, text);
            sourceItem.append(label, link);
            sourceList.appendChild(sourceItem);
        });

        card.appendChild(sourceList);
        results.appendChild(card);
    });
}

function collectSelectedItems() {
    const radios = Array.from(document.querySelectorAll(".source-radio:checked"));
    const selectedItems = [];

    radios.forEach((radio) => {
        const distribuidora = (radio.dataset.distribuidora || "").trim().toUpperCase();
        const url = (radio.dataset.url || "").trim();
        const itemIndex = (radio.dataset.itemIndex || "").trim();
        const quantityInput = document.getElementById(`quantity-${itemIndex}`);
        const cantidad = Number(quantityInput?.value);
        const cantidadFinal = Number.isFinite(cantidad) && cantidad > 0 ? Math.floor(cantidad) : 1;
        if (!distribuidora || !url) {
            return;
        }
        selectedItems.push({itemIndex, url, cantidad: cantidadFinal, distribuidora});
    });

    return selectedItems;
}

function normalizeResults(items) {
    return items.map((item) => ({
        ...item,
        urls: Array.isArray(item.urls) ? [...item.urls] : [],
        distribuidoras: Array.isArray(item.distribuidoras) ? [...item.distribuidoras] : []
    }));
}

function reconcileResultsAfterOrder(items, selectedItems, payload) {
    const addedUrls = new Set(Array.isArray(payload?.addedUrls) ? payload.addedUrls : []);
    const failedUrls = new Set(
        Array.isArray(payload?.failedUrls)
            ? payload.failedUrls
                .map((entry) => typeof entry?.url === "string" ? entry.url : "")
                .filter(Boolean)
            : []
    );
    const selectedUrlsByItemIndex = new Map(selectedItems.map((item) => [item.itemIndex, item.url]));
    const nextItems = [];

    items.forEach((item, itemIndex) => {
        const selectedUrl = selectedUrlsByItemIndex.get(String(itemIndex)) || "";
        if (selectedUrl && addedUrls.has(selectedUrl)) {
            return;
        }

        const nextUrls = [];
        const nextDistribuidoras = [];
        const urls = Array.isArray(item.urls) ? item.urls : [];
        const distribuidoras = Array.isArray(item.distribuidoras) ? item.distribuidoras : [];

        urls.forEach((url, urlIndex) => {
            if (failedUrls.has(url)) {
                return;
            }
            nextUrls.push(url);
            nextDistribuidoras.push(distribuidoras[urlIndex] || "");
        });

        if (nextUrls.length === 0) {
            return;
        }

        nextItems.push({
            ...item,
            urls: nextUrls,
            distribuidoras: nextDistribuidoras
        });
    });

    return nextItems;
}

async function submitSelectedItems(selectedItems) {
    const vaperaliaItems = selectedItems.filter((item) => item.distribuidora === "VAPERALIA");
    const ecigItems = selectedItems.filter((item) => item.distribuidora === "ECIGLOGISTICA");
    const responses = [];

    if (vaperaliaItems.length > 0) {
        const payload = await postJson(
            "/api/reorder/vaperalia/add-to-carrito",
            vaperaliaItems.map((item) => ({url: item.url, cantidad: item.cantidad}))
        );
        responses.push({distribuidora: "VAPERALIA", payload});
    }

    if (ecigItems.length > 0) {
        const payload = await postJson(
            "/api/reorder/eciglogistica/add-to-carrito",
            ecigItems.map((item) => ({url: item.url, cantidad: item.cantidad}))
        );
        responses.push({distribuidora: "ECIGLOGISTICA", payload});
    }

    return mergeDistributorResponses(responses);
}

function mergeDistributorResponses(responses) {
    const addedUrls = [];
    const failedUrls = [];
    const messages = [];

    responses.forEach((response) => {
        const payload = response.payload || {};
        if (Array.isArray(payload.addedUrls)) {
            addedUrls.push(...payload.addedUrls);
        }
        if (Array.isArray(payload.failedUrls)) {
            failedUrls.push(...payload.failedUrls);
        }
        if (payload.message) {
            messages.push(payload.message);
        }
    });

    return {
        message: messages.join(" | "),
        addedUrls,
        failedUrls
    };
}
