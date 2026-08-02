/**
 * db.js - AirGapDB v4 Native IndexedDB Engine with Strict User Data Isolation
 */
const DB_NAME = 'AirGapDB';
const DB_VERSION = 4;
const STORE_TOPICS = 'topics';

function openDB() {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DB_NAME, DB_VERSION);

        request.onupgradeneeded = (event) => {
            const db = event.target.result;
            if (!db.objectStoreNames.contains(STORE_TOPICS)) {
                const store = db.createObjectStore(STORE_TOPICS, { keyPath: 'id' });
                store.createIndex('by_user', 'userId', { unique: false });
                store.createIndex('by_title', 'title', { unique: false });
                store.createIndex('by_status', 'status', { unique: false });
                store.createIndex('by_pin', 'isPinned', { unique: false });
            } else {
                const store = event.target.transaction.objectStore(STORE_TOPICS);
                if (!store.indexNames.contains('by_user')) {
                    store.createIndex('by_user', 'userId', { unique: false });
                }
                if (!store.indexNames.contains('by_status')) {
                    store.createIndex('by_status', 'status', { unique: false });
                }
            }
        };

        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

function getCurrentUserId() {
    if (window.CURRENT_USER_ID) {
        return parseInt(window.CURRENT_USER_ID, 10);
    }
    return null;
}

async function initOfflineStorage() {
    try {
        const db = await openDB();
        await purgeUnauthenticatedOrOtherUserData();
        console.log('[AirGapDB] IndexedDB storage initialized with strict User ID scoping.');
        return db;
    } catch (err) {
        console.error('[AirGapDB] IndexedDB initialization error:', err);
        return null;
    }
}

async function purgeUnauthenticatedOrOtherUserData() {
    const currentUserId = getCurrentUserId();
    if (!currentUserId) return;

    try {
        const db = await openDB();
        const tx = db.transaction(STORE_TOPICS, 'readwrite');
        const store = tx.objectStore(STORE_TOPICS);
        const req = store.getAll();

        req.onsuccess = () => {
            const all = req.result || [];
            all.forEach(topic => {
                if (topic.userId && parseInt(topic.userId, 10) !== currentUserId) {
                    store.delete(topic.id);
                }
            });
        };
    } catch (e) {
        console.warn('[AirGapDB] User data isolation purge warning:', e);
    }
}

async function saveTopicLocally(topic) {
    if (!topic || !topic.id) return;
    const currentUserId = getCurrentUserId();
    if (currentUserId) {
        topic.userId = topic.userId || currentUserId;
    }
    try {
        const db = await openDB();
        const tx = db.transaction(STORE_TOPICS, 'readwrite');
        const store = tx.objectStore(STORE_TOPICS);
        store.put(topic);
        return new Promise((resolve, reject) => {
            tx.oncomplete = () => resolve(true);
            tx.onerror = () => reject(tx.error);
        });
    } catch (err) {
        console.error('Error saving topic to IndexedDB:', err);
        throw err;
    }
}

async function getAllLocalTopics() {
    const currentUserId = getCurrentUserId();
    try {
        const db = await openDB();
        const tx = db.transaction(STORE_TOPICS, 'readonly');
        const store = tx.objectStore(STORE_TOPICS);

        return new Promise((resolve, reject) => {
            let req;
            if (currentUserId && store.indexNames.contains('by_user')) {
                const userIndex = store.index('by_user');
                req = userIndex.getAll(currentUserId);
            } else {
                req = store.getAll();
            }

            req.onsuccess = () => {
                let list = req.result || [];
                if (currentUserId) {
                    list = list.filter(t => !t.userId || parseInt(t.userId, 10) === currentUserId);
                }
                resolve(list);
            };
            req.onerror = () => reject(req.error);
        });
    } catch (err) {
        console.error('Error fetching topics from IndexedDB:', err);
        return [];
    }
}

async function getLocalTopicById(id) {
    const currentUserId = getCurrentUserId();
    try {
        const db = await openDB();
        const tx = db.transaction(STORE_TOPICS, 'readonly');
        const store = tx.objectStore(STORE_TOPICS);
        const numericId = typeof id === 'string' ? parseInt(id, 10) : id;
        return new Promise((resolve, reject) => {
            const req = store.get(numericId);
            req.onsuccess = () => {
                const topic = req.result || null;
                if (topic && currentUserId && topic.userId && parseInt(topic.userId, 10) !== currentUserId) {
                    resolve(null); // Block access if topic belongs to another user
                } else {
                    resolve(topic);
                }
            };
            req.onerror = () => reject(req.error);
        });
    } catch (err) {
        console.error(`Error fetching topic #${id} from IndexedDB:`, err);
        return null;
    }
}

async function deleteTopicLocally(id) {
    try {
        const db = await openDB();
        const tx = db.transaction(STORE_TOPICS, 'readwrite');
        const store = tx.objectStore(STORE_TOPICS);
        const numericId = typeof id === 'string' ? parseInt(id, 10) : id;
        store.delete(numericId);
        return new Promise((resolve, reject) => {
            tx.oncomplete = () => {
                console.log('[DELETE] IndexedDB delete success:', numericId);
                resolve(true);
            };
            tx.onerror = () => {
                const error = tx.error || new Error(`IndexedDB delete transaction failed for topic ${numericId}.`);
                console.error('[DELETE] IndexedDB delete failure:', numericId, error);
                reject(error);
            };
        });
    } catch (err) {
        console.error(`Error deleting topic #${id} from IndexedDB:`, err);
        throw err;
    }
}

async function searchLocalTopics(query, statusFilter = 'ALL') {
    const allTopics = await getAllLocalTopics();
    let filtered = allTopics;

    if (statusFilter === 'PREPARING') {
        filtered = filtered.filter(t => ['CAPTURED', 'WAITING_FOR_NETWORK', 'GENERATING'].includes(t.status));
    } else if (statusFilter === 'NEEDS_ATTENTION') {
        filtered = filtered.filter(t => ['FAILED', 'AI_UNAVAILABLE'].includes(t.status));
    } else if (statusFilter !== 'ALL') {
        filtered = filtered.filter(t => (t.status || 'READY_OFFLINE') === statusFilter);
    }

    if (!query || !query.trim()) return filtered;
    const q = query.toLowerCase().trim();
    return filtered.filter(t => 
        (t.title && t.title.toLowerCase().includes(q)) ||
        (t.direction && t.direction.toLowerCase().includes(q)) ||
        (t.summaryContent && t.summaryContent.toLowerCase().includes(q))
    );
}

async function syncServerTopicsToIDB(serverTopics) {
    if (!Array.isArray(serverTopics)) return;
    for (const topic of serverTopics) {
        await saveTopicLocally(topic);
    }
}

window.initOfflineStorage = initOfflineStorage;
