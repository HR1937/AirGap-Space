/**
 * theme.js - Instant Theme Switcher Engine (Dark, Light, System)
 */

(function () {
    const STORAGE_KEY = 'airgap_theme';

    function getSavedTheme() {
        return localStorage.getItem(STORAGE_KEY) || 'system';
    }

    function applyTheme(theme) {
        const root = document.documentElement;
        if (theme === 'system') {
            const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
            root.setAttribute('data-theme', prefersDark ? 'dark' : 'light');
        } else {
            root.setAttribute('data-theme', theme);
        }
        updateThemeUI(theme);
    }

    function updateThemeUI(theme) {
        const btn = document.getElementById('theme-toggle-btn');
        if (!btn) return;
        const labels = {
            dark: 'Dark',
            light: 'Light',
            system: 'System'
        };
        btn.innerHTML = `<span>${labels[theme] || 'System'}</span>`;
    }

    window.initThemeToggle = function() {
        updateThemeUI(getSavedTheme());
    };

    window.setTheme = function (theme) {
        localStorage.setItem(STORAGE_KEY, theme);
        applyTheme(theme);
    };

    window.cycleTheme = function () {
        const current = getSavedTheme();
        const sequence = ['system', 'dark', 'light'];
        const nextIndex = (sequence.indexOf(current) + 1) % sequence.length;
        window.setTheme(sequence[nextIndex]);
    };

    // Apply saved theme immediately on script load to prevent flash of unstyled theme
    applyTheme(getSavedTheme());

    // Listen to system theme changes
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        if (getSavedTheme() === 'system') {
            applyTheme('system');
        }
    });

    document.addEventListener('DOMContentLoaded', () => {
        updateThemeUI(getSavedTheme());
    });
})();
