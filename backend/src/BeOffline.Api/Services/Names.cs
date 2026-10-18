namespace BeOffline.Api.Services;

/// <summary>
/// People are named by first name in everything the user actually reads.
///
/// A Google display name is usually someone's full legal name, which is both
/// more than a notification line can fit and more than you need to know who is
/// asking to open Instagram. The full name stays in the DTOs — the client still
/// shows it in group member management, where telling two people apart is the
/// entire job.
/// </summary>
public static class Names
{
    /// <summary>First word of <paramref name="displayName"/>, or <paramref name="fallback"/>.</summary>
    public static string First(string? displayName, string fallback = "Your partner")
    {
        if (string.IsNullOrWhiteSpace(displayName)) return fallback;
        var trimmed = displayName.Trim();
        var space = trimmed.IndexOf(' ');
        var first = space < 0 ? trimmed : trimmed[..space];
        return string.IsNullOrEmpty(first) ? fallback : first;
    }
}
