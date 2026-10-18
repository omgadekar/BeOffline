using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace BeOffline.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddSoloUnlocks : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "SoloUnlocks",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    Uid = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    RuleKey = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false),
                    SessionKey = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false),
                    Level = table.Column<int>(type: "integer", nullable: false),
                    Kind = table.Column<string>(type: "character varying(24)", maxLength: 24, nullable: false),
                    PackageName = table.Column<string>(type: "character varying(256)", maxLength: 256, nullable: false),
                    AppLabel = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    GrantedMinutes = table.Column<int>(type: "integer", nullable: false),
                    ClientEventId = table.Column<string>(type: "character varying(160)", maxLength: 160, nullable: false),
                    OccurredAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    ReportedAtUtc = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_SoloUnlocks", x => x.Id);
                });

            migrationBuilder.CreateIndex(
                name: "IX_SoloUnlocks_Uid_ClientEventId",
                table: "SoloUnlocks",
                columns: new[] { "Uid", "ClientEventId" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_SoloUnlocks_Uid_RuleKey_SessionKey",
                table: "SoloUnlocks",
                columns: new[] { "Uid", "RuleKey", "SessionKey" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "SoloUnlocks");
        }
    }
}
