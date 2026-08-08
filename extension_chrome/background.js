let isLocked = false;
let remainingSeconds = 0;
let lockInterval = null;

const blockedDomains = [
    "facebook.com",
    "instagram.com",
    "tiktok.com",
    "youtube.com",
    "x.com",
    "twitter.com",
    "reddit.com"
];

function updateNetRules() {
    if (isLocked) {
        const rules = blockedDomains.map((domain, index) => ({
            id: index + 1,
            priority: 1,
            action: { type: 'redirect', redirect: { extensionPath: '/blocked.html' } },
            condition: { urlFilter: `*://${domain}/*`, resourceTypes: ['main_frame'] }
        }));

        chrome.declarativeNetRequest.updateDynamicRules({
            removeRuleIds: blockedDomains.map((_, i) => i + 1),
            addRules: rules
        });
    } else {
        chrome.declarativeNetRequest.updateDynamicRules({
            removeRuleIds: blockedDomains.map((_, i) => i + 1)
        });
    }
}

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === 'getState') {
        sendResponse({ isLocked, remainingSeconds });
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
