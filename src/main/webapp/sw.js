// sw.js - Service Worker for AirGap Study App Shell & Offline Storage
const CACHE_NAME = 'airgap-v3.0.0';

const ASSETS_TO_CACHE = [
    './',
    './dashboard',
    './assets/css/style.css',
    './assets/js/theme.js',
    './assets/js/app.js',
    './assets/js/db.js',
    './assets/js/ai.js',
    'https://cdn.jsdelivr.net/npm/marked/marked.min.js'
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => {
            console.log('[Service Worker] Caching App Shell static assets...');
            return cache.addAll(ASSETS_TO_CACHE);
        })
    );
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keyList) => {
            return Promise.all(
                keyList.map((key) => {
                    if (key !== CACHE_NAME) {
                        console.log('[Service Worker] Cleaning old cache:', key);
                        return caches.delete(key);
                    }
                })
            );
        })
    );
    self.clients.claim();
});

self.addEventListener('fetch', (event) => {
    const requestUrl = new URL(event.request.url);

    // Skip non-GET requests and API calls that mutate data
    if (event.request.method !== 'GET') return;
    if (requestUrl.pathname.includes('/topic') && requestUrl.searchParams.has('action')) return;

    // Navigation routes: Network-First with Cache Fallback
    if (event.request.mode === 'navigate' || requestUrl.pathname.includes('/dashboard') || requestUrl.pathname.includes('/topic')) {
        event.respondWith(
            fetch(event.request.clone())
                .then((networkResponse) => {
                    if (networkResponse && networkResponse.status === 200) {
                        const responseClone = networkResponse.clone();
                        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, responseClone));
                    }
                    return networkResponse;
                })
                .catch(() => {
                    return caches.match(event.request).then((cachedResponse) => {
                        if (cachedResponse) return cachedResponse;
                        return caches.match('./dashboard');
                    });
                })
        );
        return;
    }

    // Static Assets: Cache-First with Network Fallback (Clean cloned response handling)
    event.respondWith(
        caches.match(event.request).then((cachedResponse) => {
            if (cachedResponse) {
                // Background update cache
                fetch(event.request.clone()).then((networkResponse) => {
                    if (networkResponse && networkResponse.status === 200) {
                        const cloneForCache = networkResponse.clone();
                        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, cloneForCache));
                    }
                }).catch(() => {/* ignore background fetch errors when offline */});

                return cachedResponse;
            }

            return fetch(event.request.clone()).then((networkResponse) => {
                if (networkResponse && networkResponse.status === 200) {
                    const cloneForCache = networkResponse.clone();
                    caches.open(CACHE_NAME).then((cache) => cache.put(event.request, cloneForCache));
                }
                return networkResponse;
            });
        })
    );
});
