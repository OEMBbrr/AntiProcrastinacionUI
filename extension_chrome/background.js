let isLocked = false;
let remainingSeconds = 0;
let lockInterval = null;
let parentalEnabled = false;

let defaultDomains = [
    "facebook.com",
    "instagram.com",
    "tiktok.com",
    "youtube.com",
    "x.com",
    "twitter.com",
    "reddit.com"
];

const adultDomains = [
    // Pornhub dominios principales y TLDs internacionales
    "pornhub.com",
    "pornhub.net",
    "pornhub.org",
    "pornhub.club",
    "pornhubselect.com",
    "pornhubpremium.com",
    "pornhublive.com",
    "pornhubvids.com",
    "pornhubcasino.com",
    "pornhub.es",
    "pornhub.fr",
    "pornhub.de",
    "pornhub.it",
    "pornhub.cz",
    "pornhub.com.br",
    "phncdn.com",
    "phprcdn.com",
    "phncdn.net",
    
    // Red oficial de sitios hermanos de Pornhub (MindGeek / Aylo Network)
    "thumbzilla.com",
    "youporn.com",
    "redtube.com",
    "tube8.com",
    "spankwire.com",
    "gaytube.com",
    "extremetube.com",

    // Otros portales adultos principales
    "xvideos.com",
    "xnxx.com",
    "xhamster.com",
    "onlyfans.com",
    "chaturbate.com",
    "stripchat.com"
];

let customDomains = [...defaultDomains];

// Cargar configuración guardada
chrome.storage.local.get(['customDomains', 'parentalEnabled'], (res) => {
    if (res.customDomains && Array.isArray(res.customDomains)) {
        customDomains = res.customDomains;
    }
    if (res.parentalEnabled !== undefined) {
        parentalEnabled = res.parentalEnabled;
    }
    updateNetRules();
});

function getActiveRulesList() {
    let active = [...customDomains];
    if (parentalEnabled) {
        active = [...new Set([...active, ...adultDomains])];
    }
    return active;
}

function updateNetRules() {
    chrome.declarativeNetRequest.getDynamicRules((existingRules) => {
        const removeIds = existingRules.map(r => r.id);
        const activeList = getActiveRulesList();

        if (isLocked || parentalEnabled) {
            const rules = activeList.map((domain, index) => ({
                id: index + 1,
                priority: 1,
                action: { type: 'redirect', redirect: { extensionPath: '/blocked.html' } },
                condition: { urlFilter: `*://${domain}/*`, resourceTypes: ['main_frame'] }
            }));

            chrome.declarativeNetRequest.updateDynamicRules({
                removeRuleIds: removeIds,
                addRules: rules
            });
        } else {
            chrome.declarativeNetRequest.updateDynamicRules({
                removeRuleIds: removeIds
            });
        }
    });
}

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === 'getState') {
        sendResponse({ isLocked, remainingSeconds, customDomains, parentalEnabled });
    } else if (request.action === 'startTimer') {
        isLocked = true;
        remainingSeconds = request.minutes * 60;
        updateNetRules();
        startBackgroundTimer();
        sendResponse({ success: true });
    } else if (request.action === 'stopTimer') {
        isLocked = false;
        remainingSeconds = 0;
        clearInterval(lockInterval);
        updateNetRules();
        sendResponse({ success: true });
    } else if (request.action === 'grantTregua') {
        remainingSeconds = request.minutes * 60;
        sendResponse({ success: true });
    } else if (request.action === 'addDomain') {
        const cleanDomain = request.domain.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
        if (cleanDomain && !customDomains.includes(cleanDomain)) {
            customDomains.push(cleanDomain);
            chrome.storage.local.set({ customDomains });
            updateNetRules();
        }
        sendResponse({ success: true, customDomains });
    } else if (request.action === 'removeDomain') {
        customDomains = customDomains.filter(d => d !== request.domain);
        chrome.storage.local.set({ customDomains });
        updateNetRules();
        sendResponse({ success: true, customDomains });
    } else if (request.action === 'toggleParental') {
        parentalEnabled = request.enabled;
        chrome.storage.local.set({ parentalEnabled });
        updateNetRules();
        sendResponse({ success: true, parentalEnabled });
    }
    return true;
});

function startBackgroundTimer() {
    clearInterval(lockInterval);
    lockInterval = setInterval(() => {
        if (remainingSeconds > 0) {
            remainingSeconds--;
        } else {
            isLocked = false;
            clearInterval(lockInterval);
            updateNetRules();
        }
    }, 1000);
}
