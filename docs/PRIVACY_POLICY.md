# Block Doomscroll — Privacy Policy

_Last updated: 2026-11-28_

Block Doomscroll is a screen-time tracking and app-blocking application. This policy
explains what data the app handles and where it goes.

## The short version

**Your data stays on your device.** Block Doomscroll has no servers: it never sends
your usage, your rules or what its Accessibility Service sees to us or to anyone
else. There are no accounts, no analytics and no ads.

The app's only internet connection is the optional **Clean Instagram, Clean
Facebook and Clean YouTube** screens (premium). Each one is a web view inside the
app that shows the network's own website — instagram.com, facebook.com or
youtube.com — and talks to that website directly, the same way a browser would.
Nothing passes through us.

The app also includes Google Play Billing so you can subscribe. Purchases are
handled by Google Play.

## Clean Instagram, Clean Facebook and Clean YouTube

- These screens load instagram.com, facebook.com and youtube.com (with the content
  servers those sites use, such as Google's video servers for YouTube) directly
  from your phone. What you do there — signing in, the posts and videos you open,
  messages, comments — is exchanged between you and Meta or Google under their own
  privacy policies, exactly as in a browser.
- Your sign-in is kept as that website's cookies, stored only in the app's web
  storage on your device. Block Doomscroll never reads your password, your cookies
  or the content of these pages, and never sends them anywhere.
- To enforce your post and video limits, the app counts on your device how many
  posts or videos you opened in the current session (by their public id, e.g. a
  YouTube video id). This count stays on your device and is reset after each
  session.
- Clean Facebook shows the desktop version of facebook.com, so Facebook sees the
  app as a desktop browser.
- Block Doomscroll is not affiliated with or endorsed by Meta or Google.
- Uninstalling the app or clearing its storage deletes these sign-ins from your
  phone.

## Data the app processes on your device

| Data | Why | Where it is stored |
|---|---|---|
| App usage statistics (which apps you use and for how long) | To show your screen-time dashboard and enforce daily limits | Local database on your device |
| Device unlock / screen-on counts | Shown on your dashboard | Local database on your device |
| Your blocking rules, schedules, focus/sleep settings | To enforce the limits you configure | Local database on your device |
| Optional PIN (stored only as a salted PBKDF2 hash, never in plain text) | To protect your blocking rules from being bypassed | Local app storage on your device |
| Name of the app currently on screen, and (only if you block specific websites) the browser address bar text | Read via Android's Accessibility Service, solely to decide whether to show the block screen | Processed in memory; only aggregate usage time is stored |
| Clean feed session (posts/videos opened this session, pause end time) | To enforce your post and video limits | Local app storage on your device |
| Sign-in cookies of instagram.com, facebook.com, youtube.com (only if you use the clean feeds) | Set by those websites so you stay signed in | The app's web storage on your device |

## Accessibility Service disclosure

Block Doomscroll uses Android's Accessibility Service to detect which app is in the
foreground and, when you have configured website blocking, to read the browser
address bar. This is the only mechanism Android provides to block an app or
website at the moment it is opened. The information read this way is:

- used **only** to enforce the rules you created;
- **never** stored beyond aggregate usage time;
- **never** transmitted off the device.

## Purchases and subscriptions

Subscriptions are sold and processed by Google Play. Block Doomscroll never sees
your card, billing address, or Google account details. The app asks Google Play
only one question — whether this Google account currently has an active
subscription — and stores only the yes/no answer on your device. Cancel anytime
in the Google Play app; your purchase history lives in your Google account, not
in this app.

## Backups

Backups are disabled (`allowBackup=false`). Your usage history, rules, and PIN
hash are excluded from Google cloud backup and device-to-device transfer.

## Data retention and deletion

Usage history older than 90 days is deleted automatically. You can delete all
data at any time by clearing the app's storage in Android Settings or
uninstalling the app — there is no server-side copy to delete. (Anything you posted
or sent on Instagram, Facebook or YouTube lives with those services and is managed
in your accounts there.)

## Children

Block Doomscroll does not collect personal data from anyone, including children.

## Changes

If this policy changes, the updated version will be published at the same URL
and the "Last updated" date will change.

## Contact

Questions about this policy: supportsocobit@gmail.com
