/**
 * app.js - Concept Inbox Application Logic with Instant Asynchronous UI & Background Workers
 */

let activeStatusFilter = 'ALL';
let deletedTopicIds = new Set();
let activeDeleteIds = new Set();
let pendingDeleteConfirmation = null;
let deleteOperationQueue = Promise.resolve();
let pendingPinOperations = new Set();
let syncServerTopicsInFlight = null;

// Background queue worker state & throttling
let isQueueProcessing = false;
let lastQueueProcessTimestamp = 0;
const MIN_QUEUE_SYNC_INTERVAL_MS = 30000; // 30 seconds debounce
let searchDebounceTimeout = null;

const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;
const THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000;

document.addEventListener('DOMContentLoaded', async () => {
    // 1. Synchronous UI Control Initialization (<10ms)
    safeCall(window.initThemeToggle, 'initThemeToggle');
    safeCall(initQuickCaptureForm, 'initQuickCaptureForm');
    safeCall(initFilterControls, 'initFilterControls');
    safeCall(initSearchEngine, 'initSearchEngine');
    safeCall(initSettingsModal, 'initSettingsModal');
    safeCall(initOnboardingWizard, 'initOnboardingWizard');
    safeCall(initDeleteConfirmation, 'initDeleteConfirmation');

    // 2. Initialize Offline IndexedDB Storage (<30ms)
    try {
        if (typeof window.initOfflineStorage === 'function') {
            await window.initOfflineStorage();
        }
    } catch (e) {
        console.error('[AppInit] initOfflineStorage error:', e);
    }

    // Clean up expired topics before rendering
    await cleanupExpiredTopics();

    // 3. Render Cached UI INSTANTLY (<50ms)
    try {
        await refreshInboxCardsUI();
    } catch (e) {
        console.error('[AppInit] refreshInboxCardsUI error:', e);
    }

    // 4. Non-Blocking Network Monitor & Background Tasks
    initNetworkMonitor();

    // 5. Asynchronously trigger silent server sync & background queue processing without blocking UI
    setTimeout(() => {
        if (navigator.onLine) {
            autoRetryFailedTopics();
            syncServerTopicsSilently();
            triggerBackgroundQueueProcessingThrottled();
        }
    }, 100);

    // 6. Periodic Background Worker (Checks every 30 seconds)
    startBackgroundQueueWorker();
});

function safeCall(fn, name) {
    try {
        if (typeof fn === 'function') fn();
    } catch (e) {
        console.error(`[AppInit] ${name} error:`, e);
    }
}

function refreshLucideIcons() {
    if (window.lucide && typeof window.lucide.createIcons === 'function') {
        window.lucide.createIcons();
    }
}

async function cleanupExpiredTopics() {
    try {
        const topics = await getAllLocalTopics();
        const now = Date.now();
        for (const topic of topics) {
            const startDate = topic.extendedUntil ? new Date(topic.extendedUntil).getTime() : new Date(topic.createdAt).getTime();
            if (now - startDate >= THIRTY_DAYS_MS) {
                await deleteTopicLocally(topic.id);
            }
        }
    } catch (e) {
        console.error('[AppInit] cleanupExpiredTopics error:', e);
    }
}

async function autoRetryFailedTopics() {
    try {
        const topics = await getAllLocalTopics();
        const needsRetry = topics.filter(t => ['WAITING_FOR_NETWORK', 'FAILED', 'AI_UNAVAILABLE'].includes(t.status));
        for (const t of needsRetry) {
            window.retryTopic(t.id, true);
        }
    } catch(e) {
        console.error('[AppInit] autoRetryFailedTopics error:', e);
    }
}

function initNetworkMonitor() {
    const statusBadge = document.getElementById('network-status');
    
    function updateOnlineStatus() {
        if (!statusBadge) return;
        if (navigator.onLine) {
            statusBadge.className = 'status-badge online';
            statusBadge.innerHTML = '<span class="dot"></span><span>Online</span>';
            autoRetryFailedTopics();
            syncServerTopicsSilently();
            triggerBackgroundQueueProcessingThrottled();
        } else {
            statusBadge.className = 'status-badge offline';
            statusBadge.innerHTML = '<span class="dot"></span><span>Offline</span>';
        }
    }

    window.addEventListener('online', updateOnlineStatus);
    window.addEventListener('offline', updateOnlineStatus);
    updateOnlineStatus();
}

function initQuickCaptureForm() {
    const form = document.getElementById('quick-capture-form');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const titleInput = document.getElementById('topic-title');
        const title = titleInput.value.trim();
        if (!title) return;

        const tempId = 'temp-' + Date.now();
        const tempTopic = {
            id: tempId,
            title: title,
            status: 'CAPTURED',
            createdAt: new Date().toISOString()
        };

        prependTopicCardToUI(tempTopic);
        titleInput.value = '';
        showToast(`'${title}' captured!`, 'success');

        try {
            const formData = new URLSearchParams();
            formData.append('title', title);
            formData.append('source', 'MANUAL');

            const response = await fetch('./topic', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: formData.toString()
            });

            if (response.ok) {
                const createdTopic = await response.json();
                removeTopicCardFromUI(tempId);
                await saveTopicLocally(createdTopic);
                prependTopicCardToUI(createdTopic);

                if (navigator.onLine) {
                    triggerBackgroundQueueProcessingThrottled(true);
                }
            } else {
                removeTopicCardFromUI(tempId);
                const err = await response.json();
                showToast(err.error || 'Failed to capture concept.', 'danger');
            }
        } catch (err) {
            console.warn('[OptimisticCapture] Background error:', err);
            removeTopicCardFromUI(tempId);
            tempTopic.status = 'WAITING_FOR_NETWORK';
            tempTopic.id = Date.now(); 
            await saveTopicLocally(tempTopic);
            prependTopicCardToUI(tempTopic);
        }
    });
}

function initFilterControls() {
    const filterPills = document.querySelectorAll('.inbox-filter-pill');
    filterPills.forEach(pill => {
        pill.addEventListener('click', async () => {
            filterPills.forEach(p => p.classList.remove('active'));
            pill.classList.add('active');
            activeStatusFilter = pill.getAttribute('data-filter') || 'ALL';
            await refreshInboxCardsUI();
        });
    });
}

window.captureRelatedConcept = async function(title) {
    if (!title) return;
    const tempId = 'temp-' + Date.now();
    const tempTopic = {
        id: tempId,
        title: title,
        status: 'CAPTURED',
        createdAt: new Date().toISOString()
    };
    prependTopicCardToUI(tempTopic);
    showToast(`'${title}' captured from related concepts!`, 'success');

    try {
        const formData = new URLSearchParams();
        formData.append('title', title);
        formData.append('source', 'RELATED_CONCEPT');

        const response = await fetch('./topic', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: formData.toString()
        });

        if (response.ok) {
            const createdTopic = await response.json();
            removeTopicCardFromUI(tempId);
            await saveTopicLocally(createdTopic);
            prependTopicCardToUI(createdTopic);
            if (navigator.onLine) {
                triggerBackgroundQueueProcessingThrottled(true);
            }
        }
    } catch (err) {
        console.warn('[CaptureRelated] Background error:', err);
    }
}

async function syncPendingPinChanges() {
    const localTopics = await getAllLocalTopics();
    const pendingTopics = localTopics.filter(topic => topic.pinSyncPending);

    for (const topic of pendingTopics) {
        const topicKey = String(topic.id);
        console.log('[PIN] Sync pending state for topic:', topicKey, 'isPinned:', topic.isPinned);
        const response = await fetch(`./topic?action=pin&id=${encodeURIComponent(topicKey)}&isPinned=${Boolean(topic.isPinned)}`, { method: 'POST' });
        let result = null;
        try {
            result = await response.json();
        } catch (parseError) {
            throw new Error(`Pin sync response was not valid JSON (HTTP ${response.status}).`);
        }
        if (!response.ok || !result.success) {
            throw new Error(result.error || `Pin sync failed with HTTP ${response.status}.`);
        }
        topic.isPinned = Boolean(result.isPinned);
        topic.pinSyncPending = false;
        await saveTopicLocally(topic);
        console.log('[PIN] Sync complete for topic:', topicKey, 'isPinned:', topic.isPinned);
    }
}

async function syncServerTopicsSilently(failLoudly = false) {
    if (syncServerTopicsInFlight) {
        try {
            return await syncServerTopicsInFlight;
        } catch (err) {
            if (failLoudly) throw err;
            console.error('[Sync] Server sync error:', err);
            return false;
        }
    }

    syncServerTopicsInFlight = (async () => {
        if (!navigator.onLine) {
            throw new Error('Sync could not be confirmed while offline.');
        }

        try {
            await syncPendingPinChanges();
        } catch (pinSyncError) {
            console.error('[PIN] Pending pin sync failed:', pinSyncError);
        }

        const response = await fetch('./sync', { headers: { 'Accept': 'application/json' } });
        if (!response.ok) {
            throw new Error(`Sync request failed with HTTP ${response.status}.`);
        }
        const serverTopics = await response.json();
        if (Array.isArray(serverTopics)) {
            for (const t of serverTopics) {
                const topicKey = String(t.id);
                if (deletedTopicIds.has(topicKey) || activeDeleteIds.has(topicKey)) {
                    console.log('[Sync] Skipping deleted topic during delete:', topicKey);
                    continue;
                }
                const localTopic = await getLocalTopicById(topicKey);
                if (localTopic && localTopic.pinSyncPending) {
                    t.isPinned = localTopic.isPinned;
                    t.pinSyncPending = true;
                    console.log('[PIN] Preserving pending local pin state during sync:', topicKey);
                }
                await saveTopicLocally(t);
            }
            await refreshInboxCardsUI();
        }
        return true;
    })();

    try {
        return await syncServerTopicsInFlight;
    } catch (err) {
        console.error('[Sync] Server sync error:', err);
        if (failLoudly) throw err;
        return false;
    } finally {
        syncServerTopicsInFlight = null;
    }
}

async function triggerBackgroundQueueProcessingThrottled(force = false) {
    if (!navigator.onLine || isQueueProcessing) return;
    
    const now = Date.now();
    if (!force && (now - lastQueueProcessTimestamp < MIN_QUEUE_SYNC_INTERVAL_MS)) {
        return;
    }

    isQueueProcessing = true;
    lastQueueProcessTimestamp = Date.now();
    showQueuePill('Processing concepts...');

    try {
        const response = await fetch('./topic?action=process_queue', { method: 'POST' });
        if (response.ok) {
            const processedTopics = await response.json();
            if (processedTopics && processedTopics.length > 0) {
                for (const updatedTopic of processedTopics) {
                    await saveTopicLocally(updatedTopic);
                    updateSingleCardInUI(updatedTopic);
                }
                showQueuePill(`Enriched ${processedTopics.length} concept(s)`, 3000);
            } else {
                hideQueuePill();
            }
        } else {
            hideQueuePill();
        }
    } catch (err) {
        console.warn('[BackgroundWorker] Queue processing error:', err);
        hideQueuePill();
    } finally {
        isQueueProcessing = false;
    }
}

function startBackgroundQueueWorker() {
    setInterval(() => {
        if (navigator.onLine) {
            triggerBackgroundQueueProcessingThrottled();
        }
    }, MIN_QUEUE_SYNC_INTERVAL_MS);
}

function showQueuePill(text, autoHideMs = 0) {
    const pill = document.getElementById('queue-processing-pill');
    const textEl = document.getElementById('queue-pill-text');
    if (!pill || !textEl) return;
    
    textEl.innerText = text;
    pill.style.display = 'block';

    if (autoHideMs > 0) {
        setTimeout(() => {
            pill.style.display = 'none';
        }, autoHideMs);
    }
}

function hideQueuePill() {
    const pill = document.getElementById('queue-processing-pill');
    if (pill) pill.style.display = 'none';
}

window.retryTopic = async function(id, silent = false) {
    updateCardStatusInDOM(id, 'GENERATING');
    if (!silent) showToast('Generation started in background...', 'info');

    try {
        const response = await fetch(`./topic?action=retry&id=${id}`, { method: 'POST' });
        if (response.ok) {
            const topic = await getLocalTopicById(id);
            if (topic) {
                topic.status = 'CAPTURED';
                await saveTopicLocally(topic);
            }
            if (navigator.onLine) {
                triggerBackgroundQueueProcessingThrottled(true);
            }
        }
    } catch (err) {
        if (!silent) showToast('Failed to trigger retry.', 'danger');
    }
};

function fuzzyMatch(query, text) {
    if (!query) return 100;
    if (!text) return 0;
    
    query = query.toLowerCase();
    const originalText = text;
    text = text.toLowerCase();
    
    if (text === query) return 100;
    if (text.startsWith(query)) return 80;
    
    if (new RegExp(`\\b${query}`).test(text)) return 70;
    
    const camelCased = originalText.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase();
    if (camelCased.includes(query)) return 65;

    if (text.includes(query)) return 50;
    
    let qIdx = 0;
    let tIdx = 0;
    while (qIdx < query.length && tIdx < text.length) {
        if (query[qIdx] === text[tIdx]) qIdx++;
        tIdx++;
    }
    if (qIdx === query.length) return 30;

    return 0;
}

function initSearchEngine() {
    const searchInput = document.getElementById('topic-search-input');
    if (!searchInput) return;

    searchInput.addEventListener('input', (e) => {
        if (searchDebounceTimeout) clearTimeout(searchDebounceTimeout);
        searchDebounceTimeout = setTimeout(async () => {
            await refreshInboxCardsUI();
        }, 150);
    });
}

async function refreshInboxCardsUI() {
    const searchInput = document.getElementById('topic-search-input');
    const query = searchInput ? searchInput.value.trim() : '';
    
    let topics = await searchLocalTopics('', activeStatusFilter);
    
    if (query) {
        topics = topics.map(t => ({
            topic: t,
            score: fuzzyMatch(query, t.title)
        })).filter(t => t.score > 0)
        .sort((a, b) => b.score - a.score)
        .map(t => t.topic);
    }

    topics.sort((a, b) => {
        if (a.isPinned && !b.isPinned) return -1;
        if (!a.isPinned && b.isPinned) return 1;
        const timeA = new Date(a.createdAt).getTime();
        const timeB = new Date(b.createdAt).getTime();
        return timeB - timeA;
    });

    renderTopicsGrid(topics);
}

function renderTopicsGrid(topics) {
    const gridEl = document.getElementById('topic-grid-container');
    if (!gridEl) return;

    topics = (topics || []).filter(topic => {
        const topicKey = String(topic.id);
        return !deletedTopicIds.has(topicKey);
    });

    if (topics.length > 0) {
        gridEl.innerHTML = topics.map(t => createTopicCardHtml(t)).join('');
    } else {
        gridEl.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 64px 24px; background: var(--bg-card); border-radius: var(--radius-lg); border: 1px dashed var(--bg-card-border);">
                <div class="empty-state-icon" aria-hidden="true"><i data-lucide="inbox"></i></div>
                <h3 style="font-size: 18px; margin-bottom: 8px; font-weight: 600; color: var(--text-main);">Every great idea starts with curiosity.</h3>
                <p style="color: var(--text-muted); font-size: 14px; max-width: 400px; margin: 0 auto; line-height: 1.6;">
                    Capture the first concept that comes to mind.
                </p>
            </div>
        `;
    }
    refreshLucideIcons();
}

function updateSingleCardInUI(topic) {
    const cardEl = document.getElementById(`topic-card-${topic.id}`);
    if (cardEl) {
        const newHtml = createTopicCardHtml(topic);
        const tempDiv = document.createElement('div');
        tempDiv.innerHTML = newHtml;
        const newCardEl = tempDiv.firstElementChild;
        if (newCardEl) {
            cardEl.replaceWith(newCardEl);
            refreshLucideIcons();
        }
    } else {
        prependTopicCardToUI(topic);
    }
}

function updateCardStatusInDOM(id, newStatus) {
    const cardEl = document.getElementById(`topic-card-${id}`);
    if (!cardEl) return;
    const badgeContainer = cardEl.querySelector('.badge');
    if (badgeContainer) {
        if (newStatus === 'GENERATING') {
            badgeContainer.className = 'badge badge-info';
            badgeContainer.innerHTML = '<i data-lucide="clock-3" aria-hidden="true"></i>Preparing Guide...';
        } else if (newStatus === 'READY_OFFLINE') {
            badgeContainer.className = 'badge badge-success';
            badgeContainer.innerHTML = '<i data-lucide="check-circle" aria-hidden="true"></i>Ready Offline';
        }
        refreshLucideIcons();
    }
}

function prependTopicCardToUI(topic) {
    const gridEl = document.getElementById('topic-grid-container');
    if (!gridEl) return;

    if (gridEl.children.length === 1 && gridEl.children[0].querySelector('h3')) {
        gridEl.innerHTML = '';
    }
    
    gridEl.insertAdjacentHTML('afterbegin', createTopicCardHtml(topic));
    refreshInboxCardsUI();
}

function removeTopicCardFromUI(id) {
    const cardEl = document.getElementById(`topic-card-${id}`);
    if (cardEl) cardEl.remove();
}

function initDeleteConfirmation() {
    const modal = document.getElementById('delete-confirm-modal');
    const cancelButton = document.getElementById('delete-cancel-btn');
    const confirmButton = document.getElementById('delete-confirm-btn');
    if (!modal || !cancelButton || !confirmButton) return;

    const closeModal = () => {
        modal.classList.remove('active');
        pendingDeleteConfirmation = null;
    };

    cancelButton.addEventListener('click', closeModal);
    modal.addEventListener('click', event => {
        if (event.target === modal) closeModal();
    });
    confirmButton.addEventListener('click', () => {
        const request = pendingDeleteConfirmation;
        closeModal();
        if (!request) return;

        const topicKey = String(request.id);
        if (activeDeleteIds.has(topicKey) || deletedTopicIds.has(topicKey)) return;
        activeDeleteIds.add(topicKey);
        setDeleteButtonDisabled(topicKey, true);
        deleteOperationQueue = deleteOperationQueue.then(() => finalizeTopicDeletion(topicKey, request.title));
    });
}

function setDeleteButtonDisabled(id, disabled) {
    const cardEl = document.getElementById(`topic-card-${id}`);
    const deleteButton = cardEl ? cardEl.querySelector('button[aria-label="Delete"]') : null;
    if (deleteButton) {
        deleteButton.disabled = disabled;
        deleteButton.setAttribute('aria-busy', disabled ? 'true' : 'false');
    }
}

window.deleteTopic = function(id) {
    const topicKey = String(id);
    if (activeDeleteIds.has(topicKey) || deletedTopicIds.has(topicKey) || pendingDeleteConfirmation) return;

    const cardEl = document.getElementById(`topic-card-${topicKey}`);
    const title = cardEl ? (cardEl.querySelector('.topic-card-title')?.innerText || 'Concept') : 'Concept';
    const modal = document.getElementById('delete-confirm-modal');
    const confirmButton = document.getElementById('delete-confirm-btn');
    if (!modal || !confirmButton) {
        console.error('[DELETE] Confirmation dialog is unavailable.');
        showToast('Delete confirmation is unavailable.', 'danger');
        return;
    }

    pendingDeleteConfirmation = { id: topicKey, title };
    modal.classList.add('active');
    confirmButton.focus();
};

async function finalizeTopicDeletion(id, title) {
    const topicKey = String(id);
    let serverDeleteConfirmed = false;
    console.log('[DELETE] Topic ID:', topicKey);
    try {
        if (!navigator.onLine) {
            throw new Error('Server deletion cannot be confirmed while offline.');
        }

        console.log('[DELETE] HTTP request -> Servlet entered');
        const response = await fetch(`./topic?action=delete&id=${encodeURIComponent(topicKey)}`);
        let result = null;
        try {
            result = await response.json();
        } catch (parseError) {
            throw new Error(`Delete response was not valid JSON (HTTP ${response.status}).`);
        }

        if (!response.ok || !result.success) {
            throw new Error(result.error || `Delete request failed with HTTP ${response.status}.`);
        }

        serverDeleteConfirmed = true;
        console.log('[DELETE] DAO delete success');
        console.log('[DELETE] MySQL affected rows:', result.affectedRows);

        deletedTopicIds.add(topicKey);
        console.log('[DELETE] Memory cache removed (deleted topic tombstone registered)');

        await deleteTopicLocally(topicKey);
        const remainingLocalTopic = await getLocalTopicById(topicKey);
        if (remainingLocalTopic !== null) {
            throw new Error('IndexedDB verification failed: topic still exists after delete.');
        }
        console.log('[DELETE] IndexedDB delete success');

        removeTopicCardFromUI(topicKey);
        console.log('[DELETE] DOM removed');

        await syncServerTopicsSilently(true);
        console.log('[DELETE] Sync complete');
        if (await getLocalTopicById(topicKey) !== null) {
            throw new Error('Final verification failed: topic still exists in IndexedDB.');
        }
        if (document.getElementById(`topic-card-${topicKey}`)) {
            throw new Error('Final verification failed: topic still exists in the DOM.');
        }
        console.log('[DELETE] Final verification: topic absent from IndexedDB, memory, DOM, and sync results');
        showToast(`'${escapeHtml(title)}' deleted.`, 'success');
        console.log('[DELETE] Finished');
    } catch (err) {
        if (!serverDeleteConfirmed) {
            deletedTopicIds.delete(topicKey);
        }
        console.error('[DELETE] Failed. Exact reason:', err);
        showToast(`Couldn't delete '${escapeHtml(title)}'. ${escapeHtml(err.message || String(err))}`, 'danger', 5000);
    } finally {
        activeDeleteIds.delete(topicKey);
        setDeleteButtonDisabled(topicKey, false);
        try {
            await refreshInboxCardsUI();
        } catch (refreshError) {
            console.error('[DELETE] Final UI refresh failed:', refreshError);
        }
    }
}

window.togglePin = async function(id) {
    const topicKey = String(id);
    if (pendingPinOperations.has(topicKey) || deletedTopicIds.has(topicKey) || activeDeleteIds.has(topicKey)) return;

    const topic = await getLocalTopicById(topicKey);
    if (!topic) {
        showToast('This concept is not available locally.', 'danger');
        return;
    }

    const desiredPinState = !Boolean(topic.isPinned);
    pendingPinOperations.add(topicKey);
    const cardEl = document.getElementById(`topic-card-${topicKey}`);
    const pinButton = cardEl ? cardEl.querySelector('button[aria-label="Pin"], button[aria-label="Unpin"]') : null;
    if (pinButton) pinButton.disabled = true;
    console.log('[PIN] Topic ID:', topicKey);
    console.log('[PIN] UI requested state:', desiredPinState);

    try {
        if (!navigator.onLine) {
            topic.isPinned = desiredPinState;
            topic.pinSyncPending = true;
            await saveTopicLocally(topic);
            console.log('[PIN] IndexedDB updated offline; pending server sync:', topicKey);
            await refreshInboxCardsUI();
            showToast(desiredPinState ? 'Pinned offline. It will sync when you reconnect.' : 'Unpinned offline. It will sync when you reconnect.', 'info');
        } else {
            console.log('[PIN] POST /topic?action=pin');
            const response = await fetch(`./topic?action=pin&id=${encodeURIComponent(topicKey)}&isPinned=${desiredPinState}`, { method: 'POST' });
            let result = null;
            try {
                result = await response.json();
            } catch (parseError) {
                throw new Error(`Pin response was not valid JSON (HTTP ${response.status}).`);
            }
            if (!response.ok || !result.success) {
                throw new Error(result.error || `Pin request failed with HTTP ${response.status}.`);
            }
            console.log('[PIN] Servlet/DAO/MySQL persistence confirmed:', result.isPinned);
            topic.isPinned = Boolean(result.isPinned);
            topic.pinSyncPending = false;
            await saveTopicLocally(topic);
            console.log('[PIN] IndexedDB updated:', topicKey, 'isPinned:', topic.isPinned);
            await refreshInboxCardsUI();
            console.log('[PIN] refreshInboxCardsUI complete:', topicKey);
            showToast(topic.isPinned ? 'Pinned.' : 'Unpinned.', 'success');
        }
    } catch (err) {
        console.error('[PIN] Failed. Exact reason:', err);
        await refreshInboxCardsUI();
        showToast(`Couldn\'t update pin state. ${escapeHtml(err.message || String(err))}`, 'danger');
    } finally {
        pendingPinOperations.delete(topicKey);
    }
};

window.extendTopic = async function(id) {
    const topic = await getLocalTopicById(id);
    if (!topic) return;
    
    const baseDate = topic.extendedUntil ? new Date(topic.extendedUntil) : new Date(topic.createdAt);
    const newDate = new Date(baseDate.getTime() + THIRTY_DAYS_MS);
    topic.extendedUntil = newDate.toISOString();
    
    await saveTopicLocally(topic);
    updateSingleCardInUI(topic);
    showToast('Topic extended for 30 days.', 'success');
};

function createTopicCardHtml(topic) {
    const status = topic.status || 'READY_OFFLINE';
    const isPinned = topic.isPinned || false;
    const isDeleting = activeDeleteIds.has(String(topic.id));
    
    const startDate = topic.extendedUntil ? new Date(topic.extendedUntil).getTime() : new Date(topic.createdAt).getTime();
    const now = Date.now();
    const timeRemainingMs = (startDate + THIRTY_DAYS_MS) - now;
    const daysRemaining = Math.ceil(timeRemainingMs / (1000 * 60 * 60 * 24));
    
    let expiryHtml = '';
    let expiryDialogHtml = '';
    
    if (daysRemaining <= 3 && daysRemaining >= 0) {
        const displayDays = daysRemaining === 0 ? 'today' : `in ${daysRemaining}d`;
        expiryHtml = `<button onclick="event.stopPropagation(); document.getElementById('expiry-dialog-${topic.id}').style.display='flex'" class="badge badge-warning" style="cursor:pointer; border:none;" title="Expires soon">Expires ${displayDays}</button>`;
        
        expiryDialogHtml = `
            <div id="expiry-dialog-${topic.id}" style="display:none; position:absolute; top:0; left:0; right:0; bottom:0; background:rgba(0,0,0,0.85); backdrop-filter:blur(4px); z-index:10; border-radius:var(--radius-md); padding:20px; flex-direction:column; justify-content:center; align-items:center;">
                <p style="color:#e8e8ed; margin-bottom:14px; font-size:14px; text-align:center; font-weight:500;">Extend another 30 days?</p>
                <div style="display:flex; gap:8px;">
                    <button onclick="extendTopic('${topic.id}')" class="btn btn-primary" style="padding:8px 14px; font-size:12px;">Extend</button>
                    <button onclick="deleteTopic('${topic.id}')" class="btn btn-secondary" style="padding:8px 14px; font-size:12px; color:#ef4444;">Delete</button>
                    <button onclick="document.getElementById('expiry-dialog-${topic.id}').style.display='none'" class="btn btn-secondary" style="padding:8px 14px; font-size:12px;">Cancel</button>
                </div>
            </div>
        `;
    }

    let statusBadgeHtml = '';
    if (status === 'CAPTURED' || status === 'WAITING_FOR_NETWORK') {
        statusBadgeHtml = `<span class="badge badge-warning"><i data-lucide="clock-3" aria-hidden="true"></i>Queued</span>`;
    } else if (status === 'GENERATING') {
        statusBadgeHtml = `<span class="badge badge-info"><i data-lucide="clock-3" aria-hidden="true"></i>Preparing Guide...</span>`;
    } else if (status === 'READY_OFFLINE') {
        statusBadgeHtml = `<span class="badge badge-success"><i data-lucide="check-circle" aria-hidden="true"></i>Ready Offline</span>`;
    } else {
        statusBadgeHtml = `<button onclick="retryTopic('${topic.id}')" class="badge badge-danger" style="cursor:pointer; border:none;"><i data-lucide="triangle-alert" aria-hidden="true"></i>Failed — Retry</button>`;
    }

    const actionLabel = status === 'READY_OFFLINE' ? 'Study' : 'View Status';

    return `
        <div id="topic-card-${topic.id}" class="topic-card ${isPinned ? 'pinned' : ''}" style="position:relative; overflow:hidden;">
            ${expiryDialogHtml}
            <div>
                <div class="topic-card-header">
                    <div class="topic-card-title">${escapeHtml(topic.title)}</div>
                    <button onclick="togglePin('${topic.id}')" class="btn-icon" title="${isPinned ? 'Unpin' : 'Pin'}" aria-label="${isPinned ? 'Unpin' : 'Pin'}">
                        <i data-lucide="pin" aria-hidden="true"></i>
                    </button>
                </div>
                <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap;">
                    ${statusBadgeHtml}
                    ${expiryHtml}
                </div>
            </div>

            <div class="topic-card-actions">
                <a href="./topic?id=${topic.id}" class="btn btn-secondary" style="flex:1; text-align:center; font-size:13px; padding:8px 14px;">
                    ${actionLabel}
                </a>
                <button onclick="deleteTopic('${topic.id}')" class="btn-icon" style="color:var(--danger);" title="Delete" aria-label="Delete" ${isDeleting ? 'disabled aria-busy="true"' : ''}><i data-lucide="trash-2" aria-hidden="true"></i></button>
            </div>
        </div>
    `;
}

function escapeHtml(text) {
    if (!text) return '';
    return text.toString()
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function showToast(message, type = 'info', duration = 3500) {
    const toast = document.createElement('div');
    toast.className = `toast-notification toast-${type}`;
    toast.innerHTML = message;
    
    toast.style.padding = '12px 16px';
    toast.style.background = 'var(--bg-card)';
    toast.style.color = 'var(--text-main)';
    toast.style.border = '1px solid var(--bg-card-border)';
    toast.style.borderRadius = 'var(--radius-md)';
    toast.style.boxShadow = '0 4px 12px rgba(0,0,0,0.1)';
    toast.style.opacity = '0';
    toast.style.transition = 'opacity 0.3s ease';
    
    if (type === 'success') toast.style.borderLeft = '4px solid #10b981';
    else if (type === 'danger') toast.style.borderLeft = '4px solid #ef4444';
    else if (type === 'warning') toast.style.borderLeft = '4px solid #f59e0b';
    else toast.style.borderLeft = '4px solid #3b82f6';
    
    const container = document.getElementById('toast-container') || createToastContainer();
    container.appendChild(toast);
    
    setTimeout(() => {
        toast.style.opacity = '1';
    }, 10);

    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, duration);
}

function createToastContainer() {
    const container = document.createElement('div');
    container.id = 'toast-container';
    container.style.position = 'fixed';
    container.style.bottom = '20px';
    container.style.right = '20px';
    container.style.display = 'flex';
    container.style.flexDirection = 'column';
    container.style.gap = '10px';
    container.style.zIndex = '9999';
    container.style.pointerEvents = 'none';
    document.body.appendChild(container);
    return container;
}

function initSettingsModal() {
    const directionArea = document.getElementById('settings-direction');
    if (directionArea) {
        directionArea.addEventListener('change', async (e) => {
            const newDirection = e.target.value;
            try {
                const formData = new URLSearchParams();
                formData.append('defaultDirection', newDirection);
                await fetch('./settings', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: formData.toString()
                });
                showToast('Learning focus saved.', 'success');
            } catch (err) {
                console.warn('[Settings] Error saving focus:', err);
            }
        });
    }
}

function initOnboardingWizard() {
    const wizardEl = document.getElementById('onboarding-wizard');
    if (!wizardEl) return;
    const userKey = 'airgap_onboarded_' + (window.CURRENT_USER_ID || 'guest');
    if (!localStorage.getItem(userKey)) {
        wizardEl.style.display = '';
        wizardEl.classList.add('active');
    }

    // Onboarding option selection
    const options = wizardEl.querySelectorAll('.onboarding-option');
    const customInput = document.getElementById('custom-direction-input');
    options.forEach(opt => {
        opt.addEventListener('click', () => {
            options.forEach(o => o.classList.remove('selected'));
            opt.classList.add('selected');
            if (opt.getAttribute('data-value') === 'custom') {
                if (customInput) customInput.style.display = 'block';
            } else {
                if (customInput) customInput.style.display = 'none';
            }
        });
    });

    // Save onboarding preference
    const saveBtn = document.getElementById('save-onboarding-btn');
    if (saveBtn) {
        saveBtn.addEventListener('click', async () => {
            const selectedOpt = wizardEl.querySelector('.onboarding-option.selected');
            let direction = selectedOpt ? selectedOpt.getAttribute('data-value') : '';
            if (direction === 'custom') {
                const customText = document.getElementById('custom-direction-text');
                direction = customText ? customText.value.trim() : '';
            }
            if (direction) {
                try {
                    const formData = new URLSearchParams();
                    formData.append('defaultDirection', direction);
                    await fetch('./settings', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                        body: formData.toString()
                    });
                } catch (e) {
                    console.warn('[Onboarding] Error saving direction:', e);
                }
            }
            wizardEl.classList.remove('active');
            wizardEl.style.display = 'none';
            localStorage.setItem(userKey, 'true');
            showToast('Welcome! Start capturing concepts.', 'success');
        });
    }
}

window.dismissOnboarding = async function() {
    const wizardEl = document.getElementById('onboarding-wizard');
    const userKey = 'airgap_onboarded_' + (window.CURRENT_USER_ID || 'guest');
    if (wizardEl) {
        wizardEl.classList.remove('active');
        wizardEl.style.display = 'none';
    }
    localStorage.setItem(userKey, 'true');
};
