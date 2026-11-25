# Privacy Policy — RSS Reader (Draft)

**Status: DRAFT — not published.** Last updated: 2026-11-25.

> **NOT FOR PUBLICATION.** Internal documentation; A29 is closed as not applicable (Google Play is
> not used). Publishing would be a new decision.
Based on source revision `841b36d` (version 1.87.46 / 175). Items marked **[to confirm]** must be
verified by the responsible person before publication.

## 1. Controller

Responsible for the app “RSS Reader” (package `de.lolo.rssreader`): the developer.
Contact: kesselflickerei@gmx.de (address from the store metadata; **[to confirm]**).
A full postal address is only required if the distribution channel mandates it **[to confirm]**.

## 2. Purpose

The app subscribes to RSS/Atom feeds entered by the user and displays their articles locally.
There are no accounts, no sign-in and no synchronisation between devices.

## 3. Data processed

### 3.1 Stored locally on the device

- Subscribed feeds: URL, title, optional display name, site/icon URL, ordering, Wi-Fi rule.
- Articles: title, link, publication date, text/HTML content (truncated for very large articles),
  image URLs, read and favourite state.
- Settings: refresh interval, theme, text size, image display, per-feed filter, notifications.
- Caches: image cache (Coil) and WebView cache for displayed content.

This data does not leave the device. It is deleted when the user removes it in the app, clears the
app data or uninstalls the app.

### 3.2 Network connections

The app uses HTTPS only (cleartext HTTP is blocked). The device **IP address** and a technical
**User-Agent** (`RSS-Reader/1.x (+Android)`) are necessarily transmitted to the contacted server:

- Feed refresh: fetching the feed URLs the user subscribed to (manually or at the chosen interval,
  optionally Wi-Fi only).
- Full text: the original article page is loaded only on explicit user action.
- Images: feed icons and article images are loaded directly from the respective source server.

Recipients are only the feed/website servers the user subscribed to. There is no intermediary service
of the developer and no analytics, advertising or crash-reporting service.

### 3.3 Notifications

New articles can be announced by a local notification (`POST_NOTIFICATIONS` permission on Android 13+).
The notification is created on the device.

### 3.4 Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE`: feed fetching and network type detection.
- `POST_NOTIFICATIONS` (Android 13+): optional new-article notifications.

No further permissions are requested.

### 3.5 Logging

**Debug builds** write a technical log into the app's private storage (max. 256 KB, credentials in
URLs are masked). **Release builds do not write a log file.**

### 3.6 Backup

Automatic cloud backup and device transfer are disabled (`allowBackup="false"`, data extraction
excluded for all domains).

## 4. Third parties

The app contains no analytics, advertising or tracking SDKs. When obtained via F-Droid or Google Play,
the privacy policies of those stores additionally apply.

## 5. Retention

Local data remains stored until the user deletes it (remove feed, delete entries, clear app data,
uninstall).

## 6. User rights

All content is local, so users can inspect and delete it at any time. For questions about processing,
the contact above is responsible.

## 7. Changes

This policy is updated when the app's data processing changes; the date above is updated accordingly.
