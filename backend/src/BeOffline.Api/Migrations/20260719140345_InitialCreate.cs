using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace BeOffline.Api.Migrations
{
    /// <inheritdoc />
    public partial class InitialCreate : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "Allowances",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    Uid = table.Column<string>(type: "text", nullable: false),
                    PackageName = table.Column<string>(type: "text", nullable: false),
                    RequestId = table.Column<Guid>(type: "uuid", nullable: true),
                    GrantedUntilUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    Source = table.Column<string>(type: "text", nullable: false),
                    CreatedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Allowances", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "Devices",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    Uid = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    DeviceId = table.Column<string>(type: "text", nullable: false),
                    Model = table.Column<string>(type: "text", nullable: true),
                    FcmToken = table.Column<string>(type: "text", nullable: false),
                    LastHeartbeatAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Devices", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "InviteCodes",
                columns: table => new
                {
                    Code = table.Column<string>(type: "character varying(16)", maxLength: 16, nullable: false),
                    IssuerUid = table.Column<string>(type: "text", nullable: false),
                    CreatedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    ExpiresAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    ConsumedByUid = table.Column<string>(type: "text", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_InviteCodes", x => x.Code);
                });

            migrationBuilder.CreateTable(
                name: "Pairings",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    UserAUid = table.Column<string>(type: "text", nullable: false),
                    UserBUid = table.Column<string>(type: "text", nullable: false),
                    Status = table.Column<string>(type: "character varying(16)", maxLength: 16, nullable: false),
                    CreatedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    CanApproveAfterUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    RemovalRequestedByUid = table.Column<string>(type: "text", nullable: true),
                    RemovalRequestedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    RemovalEffectiveAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Pairings", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "RequestApprovals",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    RequestId = table.Column<Guid>(type: "uuid", nullable: false),
                    ApproverUid = table.Column<string>(type: "text", nullable: false),
                    Verdict = table.Column<string>(type: "character varying(16)", maxLength: 16, nullable: false),
                    DurationMinutes = table.Column<int>(type: "integer", nullable: true),
                    RespondedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_RequestApprovals", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "TamperEvents",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    Uid = table.Column<string>(type: "text", nullable: false),
                    Type = table.Column<string>(type: "text", nullable: false),
                    PackageName = table.Column<string>(type: "text", nullable: true),
                    ClientEventId = table.Column<string>(type: "text", nullable: false),
                    OccurredAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    ReportedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_TamperEvents", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "Users",
                columns: table => new
                {
                    Uid = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    DisplayName = table.Column<string>(type: "text", nullable: true),
                    Email = table.Column<string>(type: "text", nullable: true),
                    CreatedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    LastSeenAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    UninstallNotifiedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Users", x => x.Uid);
                });

            migrationBuilder.CreateTable(
                name: "UnlockRequests",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    PairingId = table.Column<Guid>(type: "uuid", nullable: false),
                    RequesterUid = table.Column<string>(type: "text", nullable: false),
                    PackageName = table.Column<string>(type: "text", nullable: false),
                    AppLabel = table.Column<string>(type: "text", nullable: false),
                    ClientRequestId = table.Column<string>(type: "text", nullable: false),
                    Status = table.Column<string>(type: "character varying(16)", maxLength: 16, nullable: false),
                    RequestedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    ExpiresAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    ResolvedByUid = table.Column<string>(type: "text", nullable: true),
                    ResolvedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    GrantedDurationMinutes = table.Column<int>(type: "integer", nullable: true),
                    GrantedUntilUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_UnlockRequests", x => x.Id);
                    table.ForeignKey(
                        name: "FK_UnlockRequests_Pairings_PairingId",
                        column: x => x.PairingId,
                        principalTable: "Pairings",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_Allowances_Uid",
                table: "Allowances",
                column: "Uid");

            migrationBuilder.CreateIndex(
                name: "IX_Devices_Uid_DeviceId",
                table: "Devices",
                columns: new[] { "Uid", "DeviceId" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_Pairings_UserAUid",
                table: "Pairings",
                column: "UserAUid");

            migrationBuilder.CreateIndex(
                name: "IX_Pairings_UserBUid",
                table: "Pairings",
                column: "UserBUid");

            migrationBuilder.CreateIndex(
                name: "IX_RequestApprovals_RequestId",
                table: "RequestApprovals",
                column: "RequestId");

            migrationBuilder.CreateIndex(
                name: "IX_TamperEvents_Uid",
                table: "TamperEvents",
                column: "Uid");

            migrationBuilder.CreateIndex(
                name: "IX_TamperEvents_Uid_ClientEventId",
                table: "TamperEvents",
                columns: new[] { "Uid", "ClientEventId" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_UnlockRequests_PairingId",
                table: "UnlockRequests",
                column: "PairingId");

            migrationBuilder.CreateIndex(
                name: "IX_UnlockRequests_RequesterUid_ClientRequestId",
                table: "UnlockRequests",
                columns: new[] { "RequesterUid", "ClientRequestId" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_UnlockRequests_Status_ExpiresAtUtc",
                table: "UnlockRequests",
                columns: new[] { "Status", "ExpiresAtUtc" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "Allowances");

            migrationBuilder.DropTable(
                name: "Devices");

            migrationBuilder.DropTable(
                name: "InviteCodes");

            migrationBuilder.DropTable(
                name: "RequestApprovals");

            migrationBuilder.DropTable(
                name: "TamperEvents");

            migrationBuilder.DropTable(
                name: "UnlockRequests");

            migrationBuilder.DropTable(
                name: "Users");

            migrationBuilder.DropTable(
                name: "Pairings");
        }
    }
}
