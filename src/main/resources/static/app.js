const statusText = document.getElementById("status-text");
const summary = document.getElementById("summary");
const emptyState = document.getElementById("empty-state");
const results = document.getElementById("results");

const buttons = {
    vaperaliaSession: document.getElementById("vaperalia-session"),
    ecigSession: document.getElementById("ecig-session"),
    runReorder: document.getElementById("run-reorder")
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
            renderResults(Array.isArray(payload) ? payload : []);
            setStatus(`Execution complete. Received ${Array.isArray(payload) ? payload.length : 0} prioritized products.`);
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

async function postJson(url) {
    const response = await fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        }
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
        summary.hidden = true;
        summary.textContent = "";
        emptyState.hidden = false;
        emptyState.textContent = "Execution completed, but no products need reorder.";
        return;
    }

    summary.hidden = false;
    summary.textContent = `${items.length} prioritized product${items.length === 1 ? "" : "s"} returned.`;
    emptyState.hidden = true;

    items.forEach((item) => {
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

        const sourceList = document.createElement("ul");
        sourceList.className = "source-list";

        const urls = Array.isArray(item.urls) ? item.urls : [];
        const distribuidoras = Array.isArray(item.distribuidoras) ? item.distribuidoras : [];

        urls.forEach((url, index) => {
            const sourceItem = document.createElement("li");
            sourceItem.className = "source-item";

            const label = document.createElement("strong");
            label.textContent = `Priority ${index + 1}: ${distribuidoras[index] || "Unknown distributor"}`;

            const link = document.createElement("a");
            link.href = url;
            link.target = "_blank";
            link.rel = "noreferrer";
            link.textContent = url;

            sourceItem.append(label, link);
            sourceList.appendChild(sourceItem);
        });

        card.appendChild(sourceList);
        results.appendChild(card);
    });
}
