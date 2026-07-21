namespace BeOffline.Api.Legal;

/// <summary>
/// Self-contained HTML for the two pages Google Play requires to be reachable
/// on the public web: the privacy policy and an account/data deletion request
/// page. Served directly by the API so there's no separate site to host.
/// Edit CONTACT_EMAIL and review the copy with your own wording before launch.
/// </summary>
public static class LegalPages
{
    public const string ContactEmail = "gadekarom@gmail.com";

    private static string Shell(string title, string body) => $$"""
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>{{title}} — BeOffline</title>
          <style>
            :root { color-scheme: light dark; }
            body { font-family: -apple-system, Segoe UI, Roboto, sans-serif; line-height: 1.6;
                   max-width: 720px; margin: 0 auto; padding: 32px 20px 64px; }
            h1 { font-size: 1.6rem; } h2 { font-size: 1.15rem; margin-top: 1.8rem; }
            code { background: rgba(128,128,128,.15); padding: 1px 5px; border-radius: 4px; }
            .muted { opacity: .7; font-size: .9rem; }
            a { color: #0e8a6d; }
          </style>
        </head>
        <body>{{body}}</body>
        </html>
        """;

    public static string PrivacyPolicy => Shell("Privacy Policy", $"""
        <h1>BeOffline — Privacy Policy</h1>
        <p class="muted">Last updated: 2026-07-21 · Developer: OmGLabs · Contact: <a href="mailto:{ContactEmail}">{ContactEmail}</a></p>

        <h2>What BeOffline does</h2>
        <p>BeOffline is a digital-wellbeing app that helps you block apps' internet access and stop
        yourself from opening chosen apps during focus sessions, optionally with an accountability
        partner or group.</p>

        <h2>Data we collect</h2>
        <ul>
          <li><strong>Account:</strong> when you sign in with Google we receive your name, email, and a
          Firebase user ID, so your accountability pairing survives reinstalls.</li>
          <li><strong>Accountability activity:</strong> when you send an unlock request, the name/package
          of the app you asked to open and your approver's decision; in a group, the chat messages you
          send. This is visible to the partner(s) or group member(s) you chose to add.</li>
          <li><strong>Protection status events:</strong> if your app-block protection is turned off,
          disabled, or the app is uninstalled, we notify the partner(s) you added — the core
          accountability feature, disclosed in-app.</li>
          <li><strong>Device token:</strong> a push-notification token to deliver approvals and alerts.</li>
          <li><strong>Diagnostics:</strong> anonymous crash reports (Firebase Crashlytics).</li>
        </ul>

        <h2>What we do NOT collect</h2>
        <ul>
          <li>We do not read your screen, keystrokes, messages, or app content.</li>
          <li>Your block rules and app-lock rules stay on your device and are not sent to us.</li>
          <li>The VPN feature runs locally; we do not inspect, collect, or forward your traffic.</li>
          <li>We do not sell your data or share it with advertisers.</li>
        </ul>

        <h2>Accessibility service</h2>
        <p>BeOffline uses Android's accessibility service only to detect which app is in the foreground,
        so it can show a block screen for apps you chose to lock. It does not collect or transmit any
        accessibility data.</p>

        <h2>Data retention &amp; deletion</h2>
        <p>Accountability data is kept while your account is active. You can delete your account and its
        server data any time from <strong>Settings → Delete my account</strong> in the app, or request
        deletion at <a href="/account-deletion">/account-deletion</a>. Operational logs are kept for up
        to 30 days.</p>

        <h2>Security</h2>
        <p>Data in transit is encrypted with TLS. Server data is stored on access-controlled infrastructure.</p>

        <h2>Children</h2>
        <p>BeOffline is not directed to children under 13 (or the equivalent minimum age in your country).</p>

        <h2>Changes</h2>
        <p>We will update this page and the date above when this policy changes.</p>
        """);

    public static string AccountDeletion => Shell("Delete your account", $"""
        <h1>Delete your BeOffline account &amp; data</h1>

        <h2>Delete it yourself, in the app (instant)</h2>
        <p>Open BeOffline → <strong>Accountability</strong> → <strong>Delete my account</strong>. This
        immediately and permanently removes your account and all associated server data.</p>

        <h2>Request deletion without the app</h2>
        <p>Email <a href="mailto:{ContactEmail}?subject=BeOffline%20account%20deletion">{ContactEmail}</a>
        from the address you signed in with, with the subject "BeOffline account deletion". We will
        delete your account and data within 30 days and confirm by reply.</p>

        <h2>What gets deleted</h2>
        <p>Your account (name, email, user ID), device push tokens, accountability pairings and group
        memberships, unlock requests and approvals, temporary allowances, protection-status events, and
        chat messages you sent. Deletion is immediate and permanent; there is no recovery period.</p>

        <h2>What is kept, and for how long</h2>
        <ul>
          <li><strong>Content other people created</strong> (e.g. their own chat messages, or requests
          they sent you) is not deleted, as it belongs to those users.</li>
          <li><strong>Operational request logs</strong> (method, path, timestamp — not message content)
          are retained for up to <strong>30 days</strong> for security and debugging, then purged, and
          are not linked to your account after deletion.</li>
          <li><strong>Anonymous crash diagnostics</strong> are managed by Firebase Crashlytics per
          Google's retention policy and are not tied to your account.</li>
          <li>Any operational backups are rotated and purged within <strong>30 days</strong>.</li>
        </ul>

        <p class="muted">Questions? <a href="mailto:{ContactEmail}">{ContactEmail}</a> · See our
        <a href="/privacy">Privacy Policy</a>.</p>
        """);
}
