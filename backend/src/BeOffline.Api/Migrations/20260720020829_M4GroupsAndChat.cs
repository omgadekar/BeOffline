using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace BeOffline.Api.Migrations
{
    /// <inheritdoc />
    public partial class M4GroupsAndChat : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_UnlockRequests_Pairings_PairingId",
                table: "UnlockRequests");

            migrationBuilder.AlterColumn<Guid>(
                name: "PairingId",
                table: "UnlockRequests",
                type: "uuid",
                nullable: true,
                oldClrType: typeof(Guid),
                oldType: "uuid");

            migrationBuilder.AddColumn<Guid>(
                name: "GroupId",
                table: "UnlockRequests",
                type: "uuid",
                nullable: true);

            migrationBuilder.CreateTable(
                name: "ChatMessages",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    ConversationKey = table.Column<string>(type: "character varying(96)", maxLength: 96, nullable: false),
                    SenderUid = table.Column<string>(type: "text", nullable: false),
                    Body = table.Column<string>(type: "character varying(2000)", maxLength: 2000, nullable: false),
                    ClientMessageId = table.Column<string>(type: "text", nullable: false),
                    SentAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_ChatMessages", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "GroupInviteCodes",
                columns: table => new
                {
                    Code = table.Column<string>(type: "character varying(16)", maxLength: 16, nullable: false),
                    GroupId = table.Column<Guid>(type: "uuid", nullable: false),
                    IssuerUid = table.Column<string>(type: "text", nullable: false),
                    CreatedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    ExpiresAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    ConsumedByUid = table.Column<string>(type: "text", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_GroupInviteCodes", x => x.Code);
                });

            migrationBuilder.CreateTable(
                name: "Groups",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    Name = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false),
                    OwnerUid = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    CreatedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Groups", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "GroupMembers",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    GroupId = table.Column<Guid>(type: "uuid", nullable: false),
                    Uid = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    Status = table.Column<string>(type: "character varying(16)", maxLength: 16, nullable: false),
                    JoinedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    CanApproveAfterUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    RemovalRequestedByUid = table.Column<string>(type: "text", nullable: true),
                    RemovalRequestedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    RemovalEffectiveAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_GroupMembers", x => x.Id);
                    table.ForeignKey(
                        name: "FK_GroupMembers_Groups_GroupId",
                        column: x => x.GroupId,
                        principalTable: "Groups",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_UnlockRequests_GroupId",
                table: "UnlockRequests",
                column: "GroupId");

            migrationBuilder.CreateIndex(
                name: "IX_ChatMessages_ConversationKey_SentAtUtc",
                table: "ChatMessages",
                columns: new[] { "ConversationKey", "SentAtUtc" });

            migrationBuilder.CreateIndex(
                name: "IX_ChatMessages_SenderUid_ClientMessageId",
                table: "ChatMessages",
                columns: new[] { "SenderUid", "ClientMessageId" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_GroupMembers_GroupId_Uid",
                table: "GroupMembers",
                columns: new[] { "GroupId", "Uid" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_GroupMembers_Uid",
                table: "GroupMembers",
                column: "Uid");

            migrationBuilder.AddForeignKey(
                name: "FK_UnlockRequests_Groups_GroupId",
                table: "UnlockRequests",
                column: "GroupId",
                principalTable: "Groups",
                principalColumn: "Id");

            migrationBuilder.AddForeignKey(
                name: "FK_UnlockRequests_Pairings_PairingId",
                table: "UnlockRequests",
                column: "PairingId",
                principalTable: "Pairings",
                principalColumn: "Id");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_UnlockRequests_Groups_GroupId",
                table: "UnlockRequests");

            migrationBuilder.DropForeignKey(
                name: "FK_UnlockRequests_Pairings_PairingId",
                table: "UnlockRequests");

            migrationBuilder.DropTable(
                name: "ChatMessages");

            migrationBuilder.DropTable(
                name: "GroupInviteCodes");

            migrationBuilder.DropTable(
                name: "GroupMembers");

            migrationBuilder.DropTable(
                name: "Groups");

            migrationBuilder.DropIndex(
                name: "IX_UnlockRequests_GroupId",
                table: "UnlockRequests");

            migrationBuilder.DropColumn(
                name: "GroupId",
                table: "UnlockRequests");

            migrationBuilder.AlterColumn<Guid>(
                name: "PairingId",
                table: "UnlockRequests",
                type: "uuid",
                nullable: false,
                defaultValue: new Guid("00000000-0000-0000-0000-000000000000"),
                oldClrType: typeof(Guid),
                oldType: "uuid",
                oldNullable: true);

            migrationBuilder.AddForeignKey(
                name: "FK_UnlockRequests_Pairings_PairingId",
                table: "UnlockRequests",
                column: "PairingId",
                principalTable: "Pairings",
                principalColumn: "Id",
                onDelete: ReferentialAction.Cascade);
        }
    }
}
