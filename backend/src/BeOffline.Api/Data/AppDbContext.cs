using BeOffline.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace BeOffline.Api.Data;

public sealed class AppDbContext(DbContextOptions<AppDbContext> options) : DbContext(options)
{
    public DbSet<AppUser> Users => Set<AppUser>();
    public DbSet<Device> Devices => Set<Device>();
    public DbSet<InviteCode> InviteCodes => Set<InviteCode>();
    public DbSet<Pairing> Pairings => Set<Pairing>();
    public DbSet<ApproverGroup> Groups => Set<ApproverGroup>();
    public DbSet<GroupMember> GroupMembers => Set<GroupMember>();
    public DbSet<GroupInviteCode> GroupInviteCodes => Set<GroupInviteCode>();
    public DbSet<ChatMessage> ChatMessages => Set<ChatMessage>();
    public DbSet<UnlockRequest> UnlockRequests => Set<UnlockRequest>();
    public DbSet<RequestApproval> RequestApprovals => Set<RequestApproval>();
    public DbSet<AllowanceRecord> Allowances => Set<AllowanceRecord>();
    public DbSet<TamperEvent> TamperEvents => Set<TamperEvent>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<AppUser>(e =>
        {
            e.HasKey(x => x.Uid);
            e.Property(x => x.Uid).HasMaxLength(128);
        });

        b.Entity<Device>(e =>
        {
            e.HasIndex(x => new { x.Uid, x.DeviceId }).IsUnique();
            e.Property(x => x.Uid).HasMaxLength(128);
        });

        b.Entity<InviteCode>(e =>
        {
            e.HasKey(x => x.Code);
            e.Property(x => x.Code).HasMaxLength(16);
        });

        b.Entity<Pairing>(e =>
        {
            e.HasIndex(x => x.UserAUid);
            e.HasIndex(x => x.UserBUid);
            e.Property(x => x.Status).HasConversion<string>().HasMaxLength(16);
        });

        b.Entity<ApproverGroup>(e =>
        {
            e.Property(x => x.Name).HasMaxLength(64);
            e.Property(x => x.OwnerUid).HasMaxLength(128);
        });

        b.Entity<GroupMember>(e =>
        {
            e.HasIndex(x => new { x.GroupId, x.Uid }).IsUnique();
            e.HasIndex(x => x.Uid);
            e.Property(x => x.Uid).HasMaxLength(128);
            e.Property(x => x.Status).HasConversion<string>().HasMaxLength(16);
            e.HasOne(x => x.Group).WithMany().HasForeignKey(x => x.GroupId);
        });

        b.Entity<GroupInviteCode>(e =>
        {
            e.HasKey(x => x.Code);
            e.Property(x => x.Code).HasMaxLength(16);
        });

        b.Entity<ChatMessage>(e =>
        {
            e.HasIndex(x => new { x.SenderUid, x.ClientMessageId }).IsUnique();
            e.HasIndex(x => new { x.ConversationKey, x.SentAtUtc });
            e.Property(x => x.ConversationKey).HasMaxLength(96);
            e.Property(x => x.Body).HasMaxLength(2000);
        });

        b.Entity<UnlockRequest>(e =>
        {
            e.HasIndex(x => new { x.RequesterUid, x.ClientRequestId }).IsUnique();
            e.HasIndex(x => new { x.Status, x.ExpiresAtUtc });
            e.Property(x => x.Status).HasConversion<string>().HasMaxLength(16);
            e.HasOne(x => x.Pairing).WithMany().HasForeignKey(x => x.PairingId);
            e.HasOne(x => x.Group).WithMany().HasForeignKey(x => x.GroupId);
        });

        b.Entity<RequestApproval>(e =>
        {
            e.HasIndex(x => x.RequestId);
            e.Property(x => x.Verdict).HasConversion<string>().HasMaxLength(16);
        });

        b.Entity<AllowanceRecord>(e => e.HasIndex(x => x.Uid));

        b.Entity<TamperEvent>(e =>
        {
            e.HasIndex(x => new { x.Uid, x.ClientEventId }).IsUnique();
            e.HasIndex(x => x.Uid);
        });
    }
}
