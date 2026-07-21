using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace BeOffline.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddChatMentions : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "MentionedUids",
                table: "ChatMessages",
                type: "character varying(1024)",
                maxLength: 1024,
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "MentionedUids",
                table: "ChatMessages");
        }
    }
}
